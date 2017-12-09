在服务器启动过程初，我们向ServerBootstrap类传入了两个线程池，一个负责处理I/O连接请求，另一个用来处理连接后的读写操作。主事件循环主要负责接收客户端连接，之后创建与客户端连接的NioSocketChannel，然后将其注册到子事件循环上面，由子事件循环负责处理子Channel的读写操作。

## 7.2.1 Accept事件的注册

向java的channel注册Accept事件发生在bind阶段(AbstractBootstrap的doBind0方法)结束的最后，bind结束后触发了Pipeline的fireChannelActive事件，经由NioServerSocketChannel的Pipeline的TailContext传播到HeadContext，最后由unsafe向线程池提交fireChannelActive任务完成Accept的注册。
```
// AbstractChannel.java中的AbstractUnsafe的bind()
if (!wasActive && isActive()) {
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.fireChannelActive();
                    }
                });
            }
```
Pipeline执行fireChannelActive从HeadContext开始触发，Head执行readIfIsAutoRead方法
```
// DefaultChannelPipeline.java的HeadContext中
public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelActive();
            readIfIsAutoRead();
        }
```
在readIfIsAutoRead中，调用tail的read(),由于read是outbound方法，最终会调用NioServerSocketChannel的unsafe的beginRead，在里面注册Accept事件。
```
// AbstractNioChannel.java中的AbstractNioUnsafe类
protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }
```

## 7.2.2 Accept事件处理器

在服务器启动完成后，NioServerSocketChannel的Pipeline结构如下：
```
Head[I/O]  <--> ServerBootstrapAcceptor[IN] <--> Tail[IN]
```
Pipeline中的ServerBootstrapAcceptor是用来处理连接任务，其逻辑比较简单：在服务器启动时调用childHandler方法设置了ServerBootstrap的子Channel的处理器，此时会将childChannelHandler添加到子Channel中（NioSocketChannel会在连接过程中创建）；设置子Channel的配置和属性；最后将子Channel注册到子线程池组中。

```
// 子Channel是服务器接收到请求后创建与客户端连接的通道
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    final Channel child = (Channel) msg;
    child.pipeline().addLast(childHandler); // 向子Channel添加子处理器
	// 设置子Channel的配置和属性
    for (Entry<ChannelOption<?>, Object> e: childOptions) {
        try {
            if (!child.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                logger.warn("Unknown channel option: " + e);
            }
        } catch (Throwable t) {
            logger.warn("Failed to set a channel option: " + child, t);
        }
    }
    for (Entry<AttributeKey<?>, Object> e: childAttrs) {
        child.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
    }

    try {// 将子Channel注册到子线程池组这
        childGroup.register(child).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    forceClose(child, future.cause());
                }
            }
        });
    } catch (Throwable t) {
        forceClose(child, t);
    }
 }
        
```


## 7.2.3 Accept事件的处理

在Nio的事件循环中，如果select到I/O连接或读时，最终会使用服务器的NioServerSocketChannel内部的NioMessageUnsafe的read()进行处理。
```
 private final List<Object> readBuf = new ArrayList<Object>(); // Unsafe内部变量
read(){
	try {
    do {
        int localRead = doReadMessages(readBuf); // accept建立建立，创建客户端Channel
        if (localRead == 0) {
            break;
        }
        if (localRead < 0) {
            closed = true;
            break;
        }
        allocHandle.incMessagesRead(localRead);
    } while (allocHandle.continueReading()); // 是否还有读取的数据
} catch (Throwable t) {
    exception = t;
}
// 通知Pipeline
int size = readBuf.size();
for (int i = 0; i < size; i ++) {
    readPending = false;
    pipeline.fireChannelRead(readBuf.get(i));
}
allocHandle.readComplete(); 重新计算缓冲池大小
pipeline.fireChannelReadComplete();
```

1. Netty中缓冲区使用RecvByteBufAllocator和RecvByteBufAllocator.Handle来进行分配，在内存管理一节有详细说明。
2. accept获得连接成功与客户端的javaChannel，然后创建NettyChannel，NioSocketChannel（初始化过程与NioServerSocketChannel类似，有channelID，unsafe和Pipeline），也会创建Config和AdaptiveRecvByteBufAllocator。
3. 通知Pipeline，触发fireChannelRead，参数msg为创建的客户端通道NioSocketChannel，Head的channelRead没有实际内容，传给ServerBootstrapAcceptor，ServerBootstrapAcceptor负责讲用户定义的childHandler加入到子ChannelHandler的Pipeline中。
4. 最后，重新计算缓冲池大小（可能扩容或减小）
5.  触发Pipeline的fireChannelReadComplete

此时，与客户端的javaChannel已经建立，并且创建了Netty的客户端NioSocketChannel，并将其注册子线程池组中，在子线程池的事件循环中，会处理read事件。