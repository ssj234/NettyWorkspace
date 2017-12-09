服务器的启动过程大量使用了EventLoop和Future/Promise，在阅读源码之前，建议首先要对Netty的这两种机制进行了解。由于Netty更多是在服务器端使用，因此以服务器的启动过程为例进行学习。

## 5.1 阶段：配置config

配置阶段的工作很简单，主要就是初始化启动类，设置相关参数。  
Bootstrap启动类主要功能是初始化启动器，为启动器设置相关属性。我们先来看一下Bootstrap的类结构，启动类有一个AbstractBootstrap基类，有两个实现类Bootstrap和ServerBootstrap，分别用于客户端和服务器的启动。

### AbstractBootstrap

**属性**

```
EventLoopGroup group; //线程组,对于ServerBootstrap来说，group为ServerSocketChannel服务
ChannelFactory<? extends C> channelFactory; //用于获取channel的工厂类
SocketAddress localAddress;//绑定的地址
Map<ChannelOption<?>, Object> options;//channel可设置的选项，包含java-channel和netty-channel
Map<AttributeKey<?>, Object> attrs;//channel属性，便于保存用户自定义数据
ChannelHandler handler;//Channel处理器
```
**方法**

```
group() 设置线程组
channelFactory()及channel() 设置channel工厂和channel类型
localAddress() 设置地址
option() 添加channel选项
attr() 添加属性
handler() 设置channelHander
```
上面这些方法主要用于设置启动器的相关参数，除此之外，还有一些启动时调用的方法
```
register() 内部调用initAndRegister() 用来初始化channel并注册到线程组
bind() 首先会调用initAndRegister()，之后绑定IP地址，使用Promise保证先initAndRegister()在bind()
initAndRegister()，主要是创建netty的channel，设置options和attrs，注册到线程组
```

### ServerBootstrap

ServerBootstrap在AbstractBootstrap的基础上添加了如下属性，用来设置子Channel，也就是客户端连接后创建的Channel的属性。另外，还实现了抽象类中定义的init()方法。
```
 Map<ChannelOption<?>, Object> childOptions
 Map<AttributeKey<?>, Object> childAttrs
EventLoopGroup childGroup;
ChannelHandler childHandler;
```

## 5.2 阶段：初始化init

初始化init阶段的主要功能是：创建并初始化服务器的Netty-Channel；分为两个步骤：创建和初始化。

**创建NettyChannel**

* 使用SelectorProvider打开java通道
* 为Channel分配全局唯一的ChannelID
* 创建NioMessageUnsafe，用于netty底层的读写操作
* 创建ChannelPipeline，默认的是DefaultChannelPipeline

下面是初始init阶段的主要代码：
```
Channel channel = null;
        try {
            channel = channelFactory.newChannel();// 创建NettyChannel
            init(channel);//初始化NettyChannel
        } catch (Throwable t) {
            if (channel != null) {
                // channel can be null if newChannel crashed (eg SocketException("too many open files"))
                channel.unsafe().closeForcibly();
            }
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
        }
```
channelFactory用于获取Channel实例，启动时，channelFactory在调用channel(NioServerSocketChannel.class)设置channel类型时创建,由于我们使用的是设置class的方法，会使用ReflectiveChannelFactory作为工厂类，其会直接调用class的newInstance获取Channel实例。Netty中，服务器端的Channel为NioServerSocketChannel，客户端为NioSocketChannel。  

Channel的创建过程如下：
1. 打开java通道：NioServerSocketChannel创建时，首先使用SelectorProvider的openServerSocketChannel打开服务器套接字通道。SelectorProvider是Java的NIO提供的抽象类，是选择器和可选择通道的服务提供者。具体的实现类有SelectorProviderImpl，EPollSelectorProvide，PollSelectorProvider。选择器的主要工作是根据操作系统类型和版本选择合适的Provider：如果LInux内核版本>=2.6则，具体的SelectorProvider为EPollSelectorProvider，否则为默认的PollSelectorProvider。至此，底层的Java ServerSocketChannel创建完毕。
```
public NioServerSocketChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }
 private static ServerSocketChannel newSocket(SelectorProvider provider) {
        return provider.openServerSocketChannel();
    }
```
2. Java ServerSocketChannel创建完毕后，会进入netty-Channel的构造方法,首先初始化ChannelId，ChannelId是一个全局唯一的值；
3. 之后，创建NioMessageUnsafe实例，该类为Channel提供了用于完成网络通讯相关的底层操作，如connect(),read(),register(),bind(),close()等；
4. 为Channel创建DefaultChannelPipeline，初始化双向链表；
5. 讲java-channel设置为非阻塞，将关注的操作设置为SelectionKey.OP_ACCEPT（服务器）
```
 protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise =  new VoidChannelPromise(channel, true);

		// 初始化双向链表
        tail = new TailContext(this); // 创建head 
        head = new HeadContext(this); // 创建tail

        head.next = tail;
        tail.prev = head;
    }
```


**初始化NettyChannel**

创建NettyChannel后，下一步需要进行初始化，由于服务器端和客户端的Channel不一样，因此init方法被分别实现到了ServerBootstrap和Bootstrap中，我们主要分析服务器的init。服务器的init分为几个步骤：

* 将启动器设置的选项和属性设置到NettyChannel上面
* 向Pipeline添加初始化Handler，供注册后使用

具体实现在ServerBootstrap类的init方法中，程序比较简单。每个NettyChannel对象保护一个ChannelConfig类保存相关配置，还有Map<AttributeKey<?>, Object> attrs用来保存自定义属性。至于初始化Handler，我们先记住，在bind中会说明其作用。

在addLast时，由于还未注册，因此会加入到Pipeline的一个等待链表中，待注册后执行。
```
if (!registered) {
	newCtx.setAddPending();
    callHandlerCallbackLater(newCtx, true);
    return this;
}
```

总结一下，这个阶段的代码我们可以看出，channel内部包含几个重要对象：
```
ChannelID 全局唯一ID
ChannelConfig  保存配置
ChannelPipeline  通道的流水线
Unsafe  Netty底层封装的网络I/O操作
```

## 5.3 阶段：注册register

这个阶段的主要工作是将创建并初始化后的NettyChannel注册到selector上面。具体过程：

* 将打开NettyChannel注册到线程池组的selector上；
* 触发Pipeline上面ChannelHandler的channelRegistered，

```
// AbstractBootstrap类 initAndRegister()
 ChannelFuture regFuture = config().group().register(channel);
```

上面的程序会使用传入的线程池组的register(channel);注册NettyChannel，具体方法定义在SingleThreadEventLoop中,其会使用NettyChannel的unsafe的register方法，该方法首先会判断当前线程是否是指定线程池正在运行的线程，如果不是提交到要注册的线程池中执行。执行时调用下面的程序。

```
// AbstractUnsafe，删去了部分校验代码
private void register0(ChannelPromise promise) {
            try {
                boolean firstRegistration = neverRegistered;// 是否为首次注册
                doRegister(); //  1. 注册
                neverRegistered = false;
                registered = true;
                pipeline.invokeHandlerAddedIfNeeded();// 2. 将注册之前加入的handler加入进来
                safeSetSuccess(promise); // 注册成功，通知promise
                pipeline.fireChannelRegistered();// 4. Pipeline通知触发注册成功
      
                if (isActive()) { // 是否已经绑定 因为register和bind阶段是异步的
                    if (firstRegistration) { 
                        pipeline.fireChannelActive(); // 5.首次注册，通知
                    } else if (config().isAutoRead()) {// Channel会deregister后重新注册到线程组时，且配置了AutoRead
                        beginRead();
                    }
                }
            } catch (Throwable t) {
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }
```

1. 注册：将NettyChannel内部的javaChannel注册到线程池的selector上面，由线程池不断执行select()查询准备就绪的文件描述符。具体实现在AbstractNioChannel中的doRegister()
2. invokeHandlerAddedIfNeeded: 注册成功后，找到初始化阶段通过pipeline.addLast()加入的ChannelInitializer，执行其ChannelInitializer的initChannel方法，之后将其删除（在ChannelInitializer的initChannel方法中）；初始化NettyChannel阶段，我们addLast了一个初始化Handler，现在来看看其作用
```
// init初始化阶段添加了一个ChannelInitializer
 p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();// 获取config时设置的handler
                if (handler != null) {
                    pipeline.addLast(handler); // 将其添加到链表尾部
                }
                 // 加入一个ServerBootstrapAcceptor处理器，用于处理Accept
                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        });
```

从上面的程序中可以看到，由于初始化时，还未将javaChannel注册到线程池的selector上，此时还无法设置Channel将Accept注册到选择器上，因此先加入了一个ChannelInitializer，等待register后向Pipeline加入ServerBootstrapAcceptor。此时，NettyChannel的Pipeline的链表结构为:
```
Head <-->  InitialHandler  <--> ServerBootstrapAcceptor <--> Tail
```
在initChannel执行的最后会将InitialHandler从Pipeline移除，此时NioServerSocketChannel的链表结构为

```
Head   <--> ServerBootstrapAcceptor <--> Tail
```

3. fireChannelRegistered,沿着pipeline的head到tail，调用ChannelHandler的channelRegistered方法，
```
public final ChannelPipeline fireChannelRegistered() {
        AbstractChannelHandlerContext.invokeChannelRegistered(head);
        return this;
    }
    
private void invokeChannelRegistered() {
        if (invokeHandler()) { // 状态是否正确
            try {
                ((ChannelInboundHandler) handler()).channelRegistered(this); // 触发
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelRegistered();// 状态不正确，通知下一个Handler
        }
    }
```
4. fireChannelActive 由于注册阶段和绑定bind阶段都是异步的，如果此时注册完成时bind阶段已经绑定了本地端口，会沿着pipeline的head到tail，调用各个Handler的channelActive方法

## 5.4 阶段：绑定bind

本阶段的主要内容是：将NettyChannel内部的java的ServerSocketChannel绑定到本地的端口上面，结束后使用fireChannelActive通知Pipeline里的ChannelHandle，执行其channelActive方法。

bind的入口为AbstractBootstrap的doBind0()，内部会调用pipeline中的bind方法，逻辑为从tail出发，调用outbound的ChannelHandler的bind方法，从上面我们可以看到当前的链表如下：

```
Head[I/O] <-->  ServerBootstrapAcceptor[IN] <--> Tail[IN]
```

只有Head可以用来处理Outbound，Head的bind方法调用了channel创建过程中生成的unsafe对象NioMessageUnsafe的实例，该实例的bind方法首先java的channel bind本地地址，然后触发fireChannelActive。
```
public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            boolean wasActive = isActive();
            try {
                doBind(localAddress);
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
                return;
            }

            if (!wasActive && isActive()) {
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.fireChannelActive();
                    }
                });
            }
            safeSetSuccess(promise);
        }
```

至此，Netty的服务器段已经启动，Channel和ChannelPipeline已经建立。EventLoop也在不断的select()查找准备好的I/O。

