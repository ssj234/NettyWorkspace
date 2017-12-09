## 9.2.1 ChannelPipeline机制

Pipleline中可以讲ChannelHandler维护成一个双向链表，实现上通过将ChannelHandler包装为AbstractChannelHandlerContext，然后将各个Context连接起来。

**初始化Pipeline**

初始化时机：在Netty启动时，创建Channel的构造方法中会初始化一个默认的DefaultChannelPipeline

```
// DefaultChannelPipeline的构造方法
tail = new TailContext(this);
head = new HeadContext(this);

head.next = tail;
tail.prev = head;
```
TailContext继承了AbstractChannelHandlerContext并实现ChannelInboundHandler，可以看做是一个处理输入的处理器；而HeadContext最靠近java的Channel，因此需要实现in/outbound，处理输入和输出。通过代码可以看到，HeadContext会调用Channel的unsafe处理所有的I/O操作。

```
class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler 
class HeadContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler
```

***PendingHandlerCallback链表**

DefaultChannelPipeline内部还维护了一个PendingHandlerCallback链表，通过名称我们可以看出来这个链表上面的ChannelHandler等待被回调。  
在Netty服务器启动过程中，Channel创建并初始化完成后，才会同时进行注册和绑定。由于初始化过程中需要在注册完成后向链表加入ServerBootstrapAcceptor用来处理连接操作，因此，在addLast()时只能先加入到了这个等待链表中，注册完成后，会遍历这个链表，执行ChannelHandler的initChannel方法。初始化阶段中注册后的操作写在了initChannel里面。

```
// ServerBootstrap的init() 初始化阶段加入的初始化Handler
p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }
                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        });
//addLast中，若未注册，则加入PendingHandlerCallback链表
if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }
```

**通知机制**

在Netty的启动过程以及后续的I/O操作中，很多阶段都会通知Pipeline上面的ChannelHandler。
例如，在启动过程在，注册完成后，调用pipeline.fireChannelRegistered();绑定完成后调用pipeline.fireChannelActive();
我们以fireChannelRead为例，看看如何实现的按照链表通知。
```
//DefaultChannelPipeline.java
 public final ChannelPipeline fireChannelRead(Object msg) {
        AbstractChannelHandlerContext.invokeChannelRead(head, msg);
        return this;
}
    
// AbstractChannelHandlerContext.java
void invokeChannelRead(final AbstractChannelHandlerContext next, Object msg) {
		// ReferenceCountUtil 引用计数
        final Object m = next.pipeline.touch(ObjectUtil.checkNotNull(msg, "msg"), next);
        // 调用invokeChannelRead
       next.invokeChannelRead(m); 
    }
private void invokeChannelRead(Object msg) {
        if (invokeHandler()) { // 判断是否已经添加完成
            try {
            // 调用ChannelHandler的channelRead
                ((ChannelInboundHandler) handler()).channelRead(this, msg);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelRead(msg); // 未完成继续下一个
        }
    }
```

上面的程序中，从head出发查找ChannelInboundHandler，调用其channelRead，如果需要继续调用链表后面的channelRead，需要调用ctx.fireChannelRead(msg);继续通知，在今后自己定义channelRead时需要注意，需要手动继续传递消息。