Nio事件循环在NioEventLoop中，主要功能：

* 处理网络I/O读写事件
* 执行系统任务和定时任务

在主循环中我们可以看到netty对I/O任务和提交到事件循环中的系统任务的调度。
![EventLoop事件循环](http://www.uxiaowo.com/netty/Future/EventLoopTask.png)

## 6.1 I/O事件

1. 由于NIO的I/O读写需要使用选择符，因此，netty在NioEventLoop初始化时，会使用SelectorProvider打开selector。在类加载时，netty会从系统设置中读取相关配置参数：

* sun.nio.ch.bugLevel 用来修复JDK的NIO在Selector.open()的一个BUG
* io.netty.selectorAutoRebuildThreshold select()多少次数后重建selector
```
static {
        int selectorAutoRebuildThreshold = SystemPropertyUtil.getInt("io.netty.selectorAutoRebuildThreshold", 512);
        if (selectorAutoRebuildThreshold < MIN_PREMATURE_SELECTOR_RETURNS) {
            selectorAutoRebuildThreshold = 0;
        }
        SELECTOR_AUTO_REBUILD_THRESHOLD = selectorAutoRebuildThreshold;
    }
```

2. NioEventLoop的构造方法中，会调用provider.openSelector()打开Selector;如果设置`io.netty.noKeySetOptimization`为true，则会启动优化，优化内容是将Selector的selectedKeys和publicSelectedKeys属性设置为可写并替换为Netty实现的集合以提供效率。

```
private Selector openSelector() {
        final Selector selector;
        try {
            selector = provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }

        if (DISABLE_KEYSET_OPTIMIZATION) {
            return selector;
        }
	   //  下面是优化程序,此处省略
       ...
        return selector;
    }
```
3. NioEventLoop最核心的地方在于事件循环，具体代码在NioEventLoop.java在run方法中

* 首先根据默认的选择策略DefaultSelectStrategy判断本次循环是否select，具体逻辑为：如果当前有任务则使用selectNow立刻查询是否有准备就绪的I/O；如果当前没有任务则返回SelectStrategy.SELECT，并将wakenUp设置为false，并调用select()进行查询。

```
 protected void run() {
        for (;;) {  // 事件循环
            try {
                // select策略
                switch (selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())) {
                    case SelectStrategy.CONTINUE:
                        continue;
                    case SelectStrategy.SELECT:
                        select(wakenUp.getAndSet(false));  // select()
                        if (wakenUp.get()) {
                            selector.wakeup(); // 唤醒select()的线程
                        }
                    default:
                        // fallthrough
                }
			.... 后续处理
```
* select()时需要判断当前是否有scheduledTask(定时任务)，如果有则需要计算任务delay的时间，如果定时任务需要立刻执行了，那么必须马上selectNow()并返回，之后执行任务。如果没有scheduledTask，会判断当前是否有任务在等待列表，如果有任务时将wakenUp设置为true并selectNow()；如果没有任务，那么会 selector.select(1000); 阻塞等待1s，直到有I/O就绪，或者有任务等待，或需要唤醒时退出，否则，会继续循环，直到前面的几种情况发生后退出。
 
* 之后，事件循环开始处理IO和任务。如果查询到有IO事件，会调用processSelectedKeysOptimized（优化的情况下），对SelectionKey进行处理。
```
if (ioRatio == 100) {
	try {
			processSelectedKeys();
        } finally {
            runAllTasks();
       }
} else {
	final long ioStartTime = System.nanoTime();
    try {
    	processSelectedKeys();
      } finally {
      	final long ioTime = System.nanoTime() - ioStartTime; // io花费的时间
        runAllTasks(ioTime * (100 - ioRatio) / ioRatio); // 按照iorate计算task的时间
}
}

```
* processSelectedKeysOptimized处理I/O，主要是NIO的select操作，处理相关的事件。

```
private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
        final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
       ......
        try {
            int readyOps = k.readyOps();
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                
                int ops = k.interestOps();
                ops &= ~SelectionKey.OP_CONNECT;
                k.interestOps(ops);

                unsafe.finishConnect();
            }
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
              
                ch.unsafe().forceFlush();
            }
            if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                unsafe.read();
                if (!ch.isOpen()) {
                    // Connection already closed - no need to handle write.
                    return;
                }
            }
        } catch (CancelledKeyException ignored) {
            unsafe.close(unsafe.voidPromise());
        }
    }
```

## 6.2 任务处理

* runAllTasks执行提交到EventLoop的任务，首先从scheduledTaskQueue获取需要执行的任务，加入到taskQueue，然后依次执行taskQueue的任务。
```
protected boolean runAllTasks() {
        assert inEventLoop();
        boolean fetchedAll;
        boolean ranAtLeastOne = false;

        do {
            fetchedAll = fetchFromScheduledTaskQueue(); // 获取定时任务
            if (runAllTasksFrom(taskQueue)) {
                ranAtLeastOne = true;
            }
        } while (!fetchedAll); // keep on processing until we fetched all scheduled tasks.

        if (ranAtLeastOne) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        afterRunningAllTasks();
        return ranAtLeastOne;
    }
```
* ioRatio不为100时，会调用runAllTasks(ioTime * (100 - ioRatio) / ioRatio)，首先计算出I/O处理的事件，然后按照比例为执行task分配事件，内部主要逻辑与runAllTasks()主要逻辑相同。