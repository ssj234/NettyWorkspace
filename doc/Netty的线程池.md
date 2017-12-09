## 3.3 Netty的EventLoop与线程池

Netty的事件循环和事件循环组的实现中，类的层级关系比较复杂，其底层是Java线程池的实现，不过在实际使用中还是比较简单的，我们只需要使用如下的代码即可，

```
EventLoopGroup bossGroup=new NioEventLoopGroup();
EventLoopGroup workGroup=new NioEventLoopGroup();
ServerBootstrap b=new ServerBootstrap();
b.group(bossGroup,workGroup)//设置事件循环组
```

Netty的事件循环机制有两个基本接口：EventLoop和EventLoopGroup。前者是事件循环，后者是由多个事件循环组成的组。每个EventLoop被包装为一个Task放在在线程池中运行，但其本身也可以看做一个线程池，如Nio的事件循环会不断select后获取任务并执行。Nio的事件循环在实现时，使用死循环的方式不断select(),然后处理提交给EventLoop的系统任务。因此，我们可以将NioEventLoop当做线程池，EventLoopGroup作为线程池组，线程池组的意义是采用给的的策略选取一个EventLoop并提交任务。

EventLoop的定义如下，其继承了一个顺序执行的线程池接口和EventLoopGroup，也就是说EventLoop之间有父子关系，通过parent();返回任务循环组，通过next()选取一个事件循环。线程池组的register用于将Netty的Channel注册到线程池中。
```
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}

public interface EventLoopGroup extends EventExecutorGroup {
    EventLoop next();
    ChannelFuture register(Channel channel);
}
```

### NioEventLoopGroup

NioEventLoopGroup除了处理网络的异步I/O任务，还用于完成异步提交的系统任务。NioEventLoopGroup初始化时，有如下几个参数可以配置，主要用于设置线程池的相关配置。

* nThreads 子线程池数量
* Executor executor 用来执行任务的线程池
* chooserFactory ：next()时选择线程池的策略
* selectorProvider 用于打开selector
* selectStrategyFactory  用来控制select循环行为的策略
* RejectedExecutionHandlers 线程池执行的异常处理策略
```
public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                RejectedExecutionHandlers.reject());
    }
```
NioEventLoopGroup初始化过程为：

1. 如果传入的executor 为空，会默认使用`ThreadPerTaskExecutor`，该线程池针对每个任务会创建一个线程，创建线程方式使用`DefaultThreadFactory`提供的newThread方法。
2. 初始化开始，首先会根据创建nThread个子线程池，保存在childrens变量中，创建逻辑比较简单，将初始化NioEventLoopGroup时设置的参数传递给NioEventLoop对象。在创建子线程池NioEventLoop的过程中，如果一旦有失败的，就需要关闭已经创建的所有子线程池并等待这些线程池结束。
3. 之后，使用chooserFactory创建`chooser`，用来在next()选择事件循环时从childrens变量选择一个返回。默认使用2的倍数的策略，也可以设置为顺序依次选择。
4. 向组中所有的事件循环的`terminationFuture`注册事件，目的是等待所有事件循环结束后将事件循环组的`terminatedChildren`设置为成功完成。
5. 最后，将children复制保存为一个只读的集合，保存在变量`readonlyChildren`中。

至此，NioEventLoopGroup的初始化过程就结束了。我们可以看到，NioEventLoopGroup主要的用来聚合多个线程池，对其进行调度。


### NioEventLoop

在NioEventLoopGroup的初始化过程中，会创建多个NioEventLoop，NioEventLoop用来执行实际的事件循环，初始化时有如下几个属性：

* NioEventLoopGroup parent 线程池所在的Group
* Executor executor 执行任务的线程池，默认是ThreadPerTaskExecutor
* SelectorProvider selectorProvider 用来打开selector
* SelectStrategy strategy 用来控制select循环行为的策略
* RejectedExecutionHandlers 线程池执行的异常处理策略

* addTaskWakesUp addTask(Runnable)添加任务时是否唤醒线程池，默认是false
* maxPendingTasks 线程池中等待任务的最大数量
* scheduledTaskQueue 保存定时任务的QUeue
* tailTasks ：保存任务的Queue，netty选择使用jctools的MpscChunkedArrayQueue，原因是为了提高效率，因为Nio线程池的线程消费者只有一个，就是一直进行的select循环，而生产者可能有多个。具体实现参见 http://blog.csdn.net/youaremoon/article/details/50351929

### 提交任务

NioEventLoop初始化时，会创建/设置其包含的属性，最重要的是打开selector和创建tailTasks两个步骤；这时，由于没有任何任务，NioEventLoop不会启动线程。在netty中，向线程池提交任务可以使用下面的方法：
```
EventLoopGroup loop = new NioEventLoopGroup();
loop.next().submit(Callable<T> task)
loop.next().submit(Runnable task)
loop.next().execute(Runnable command);
```
也可以直接通过EventLoopGroup提交任务，只是EventLoopGroup内部会调用next()后再执行相关的方法。
```
EventLoopGroup loop = new NioEventLoopGroup();
loop.submit(Callable<T> task)
loop.submit(Runnable task)
loop.execute(Runnable command);
```
submit方法的内部会将Callable或Runnable包装后交给execute方法执行。

```
// AbstractExecutorService.java
public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task); // 包装task为 ftask
        execute(ftask);
        return ftask;
    }
```

execute方法被NioEventLoop的父类SingleThreadEventExecutor覆盖，程序如下：

```
public void execute(Runnable task) {
        boolean inEventLoop = inEventLoop();
        if (inEventLoop) {
            addTask(task); // 添加到任务队列
        } else {
            startThread(); // 启动线程，向EventLoop内部的线程池提交任务，会执行NioEventLoop run
            addTask(task);
            if (isShutdown() && removeTask(task)) {
                reject();
            }
        }

        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }
```
1. 判断当前线程（提交任务的线程）与当前线程池是同一个线程，也就是说是如果是当前线程池提交的任务，则直接将任务加入线程池队列即可；
2. 如果不是，则需要启动线程后添加任务。启动线程的过程是，向NioEventLoop内部包含的executor提交一个任务，任务内部执行NioEventLoop的run方法（executor是实际使用的线程池，初始化是传入，默认是ThreadPerTaskExecutor）。
3. 最后根据addTaskWakesUp标志和任务是否实现了NonWakeupRunnable判断是否需要唤醒，唤醒的方法是提交一个默认的空任务WAKEUP_TASK。

NioEventLoop的run方法内部是一个死循环，会一直执行select()查询准备就绪的I/O描述符并做相应的I/O处理，还会对提交到NioEventLoop的任务进行处理。
![EventLoopGroup](http://www.uxiaowo.com/netty/Future/EventLoopGroup.png)

当我们向线程池组提交任务时，group先选择一个EventLoop（通过next()），如果EventLoop未启动则向线程池提交一个任务执行EventLoop的run，然后将任务加入到该线程池的队列中，等待事件EventLoop下次处理。
