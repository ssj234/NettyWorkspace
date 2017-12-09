## 3.2 Java的Executor框架

Java平台本身提供了Executor框架用来帮助我们使用线程池。

![Executor框架](http://www.uxiaowo.com/netty/Future/Executor.png)

Executor框架最核心的类是ThreadPoolExecutor，这是各个线程池的实现类，有如下几个属性：

* corePool：核心线程池的大小 m
* maximumPool：最大线程池的大小
* keepAliveTime： 休眠等待时间
* TimeUnit unit ： 休眠等待时间单位，如微秒/纳秒等
* BlockingQueue workQueue：用来保存任务的工作队列
* ThreadFactory： 创建线程的工厂
* RejectedExecutionHandler：当线程池已经关闭或线程池Executor已经饱和，execute()方法将要调用的Handler

通过Executor框架的根据类Executors，可以创建三种基本的线程池：

* FixedThreadPool
* SingleThreadExecutor
* CachedThreadPool

### FixedThreadPool

FixedThreadPool被称为可重用固定线程数的线程池。

```
// 获取fixedThreadPool
ExecutorService fixedThreadPool=Executors.newFixedThreadPool(paramInt);

//内部会调用下面的方法，参数 corePoolSize、maximumPoolSize、keepAliveTime、workQueue
return new ThreadPoolExecutor(paramInt, paramInt, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue());
```

FixedTheadPool设置的线程池大小和最大数量一样；keepAliveTime为0，代表多余的空闲线程会立刻终止；保存任务的队列使用LinkedBlockingQueue，当线程池中的线程执行完任务后，会循环反复从队列中获取任务来执行。
FixedThreadPool适用于限制当前线程数量的应用场景，适用于`负载比较重`的服务器。

### SingleThreadExecutor

SingleThreadExecutor的核心线程池数量corePoolSize和最大数量maximumPoolSize都设置为1，适用于需要`保证顺序执行`的场景
```
ExecutorService singleThreadExecutor=Executors.newSingleThreadExecutor();

     return new FinalizableDelegatedExecutorService(new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue()));
```

### CachedThreadPool
CachedThreadPool是一个会根据需要创建新线程的线程池，适用于短期异步的小任务，或`负载教轻`的服务器。
```
ExecutorService cachedThreadPool=Executors.newCachedThreadPool();

     return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue());
```

SynchronousQueue是一种阻塞队列，其中每个插入操作必须等待另一个线程的对应移除操作 ，反之亦然。corePoolSize是0，maximumPoolSize都最大，无界的。keepAliveTime为60秒，空闲线程超过60S会被终止。


### ScheduleThreadPoolExecutor

ScheduleThreadPoolExecutor和Timer类似，可以设置延时执行或周期执行，但比Timer有更多的功能。Timer和TimerTask只创建一个线程，任务执行时间超过周期会产生一些问题。Timer创建的线程没有处理异常，因此一旦抛出非受检异常，会立刻终止。
```
ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(5);
//可以直接执行
executor.execute(new JobTaskR("executor", 0));
executor.execute(new JobTaskR("executor", 1));

System.out.println("5S后执行executor3");
//隔5秒后执行一次，但只会执行一次。
executor.schedule(new JobTaskR("executor", 3), 5, TimeUnit.SECONDS);

System.out.println("开始周期调度");
//设置周期执行，初始时6S后执行，之后每2s执行一次
executor.scheduleAtFixedRate(new JobTaskR("executor", 4), 6, 2, TimeUnit.SECONDS);
```
scheduleAtFixedRate或者scheduleWithFixedDelay方法，它们不同的是前者以固定频率执行，后者以相对固定延迟之后执行。
