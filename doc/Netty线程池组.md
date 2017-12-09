线程池是一个在多线程场景中运用很广泛的并发框架，需要异步执行或并发执行任务的程序都可以使用线程池。有任务到来时，如果不使用线程池，我们需要不断的创建/销毁线程，还需要对线程进行管理；而使用线程池，直接将任务提交到线程池即可。使用线程池有几个好处：无需重复创建/销毁线程，降低资源消耗；提高程序响应速度；提高线程的可管理性。

## 3.1 实现原理

线程池内部一般包含一个核心线程池，其内部的线程在创建之后一般不会销毁，执行完任务后线程会阻塞等待新任务到来。  
当向线程池提交任务时，线程池会做如下判断：
* 核心线程池未满，创建线程执行任务
* 核心线程池已满，若等待队列未满，则加入到等待队列；若等待队列已满但线程池未满，创建新线程执行任务；若等待队列和线程池均已满，则按照指定策略退出/拒绝任务/丢弃任务等。

![线程池执行流程](http://www.uxiaowo.com/netty/Future/ExecutorFlow.jpg)

了解了实现原理，我们先来自己实现一个线程池，首先定义线程池的接口

**ThreadPool**
线程池的接口里面最重要的方法是execute执行任务
```
public interface ThreadPool<Job extends Runnable> {
	//提交一个Job，这个Job需要实现Runnable接口
	void execute(Job job);
	//关闭线程池
	void shutdown();
	//增加工作者线程
	void addWorkers(int num);
	//减少工作者线程
	void removeWorker(int num);
	//得到正在等待执行的任务数量
	int getJobSize();
}
```

**CommonThreadPool**
在实现线程池时，我们需要定义线程池的大小，以及保存任务的列表jobs，下面是变量定义：
```
	// 线程池最大限制数
	private static final int MAX_WORKER_NUMBERS = 100;
	// 线程池默认的数量
	private static final int DEFAULT_WORKER_NUMBERS = 1;
	// 线程池最小数量
	private static final int MIN_WORKER_NUMBERS = 1;
    // 工作列表
	private final LinkedList<Job> jobs = new LinkedList<Job>();
```
在线程池初始化时，我们要将核心线程池进行初始化，创建多个Worker线程，然后启动Worker线程。
```
// num 为DEFAULT_WORKER_NUMBERS 默认线程池大小
private void initializeWokers(int num) {
		// 创建多个线程，加入workers中，并启动
		for (int i = 0; i < num; i++) {
			Worker worker = new Worker();
			workers.add(worker);
			Thread thread = new Thread(worker, "ThreadPool-Worker-"
					+ threadNum.getAndIncrement());
			thread.start();
		}
	}
```

Worker启动后，一直没有任务，需要阻塞在jobs上（jobs是上面定义的任务列表），Worker等待任务到来后唤醒获取队列中的任务并执行。下面的代码中，如果jobs为空，则线程等待；
```
// worker的代码，首先要获取jobs的锁，
synchronized (jobs) {
					while (jobs.isEmpty()) {// 如果jobs是空的，则执行jobs.wait，使用while而不是if，因为wait后可能已经为空了，需要继续等待
						try {
							jobs.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
							Thread.currentThread().interrupt();// 中断
							return;// 结束
						}
					}
					job = jobs.removeFirst();// 第一个job
					if (job != null) {
						try {
							job.run();//注意，这里是run而不是start，传入的Job
						} catch (Exception e) {
							// 忽略Job执行中的Exception
							e.printStackTrace();
						}
					}
				}
```

提交任务时，只需要将任务加入jobs中，然后通知worker线程即可。worker线程获得锁后会取第一个任务执行。执行完毕，若jobs为空，worker线程继续进行休眠等待任务到来。
```
@Override
	public void execute(Job job) {
		if (job == null)
			return;
		synchronized (jobs) {
			jobs.addLast(job);
			jobs.notify();
		}
	}
```
完整的代码可以查看https://github.com/ssj234/JavaStudy_IO/tree/master/IOResearch/src/net/ssj/pool

