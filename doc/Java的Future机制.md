## 4.2.1 Java的Future机制

Future顾名思义，是一个未来完成的异步操作，可以获得未来返回的值。常用的场景如：调用一个耗时的方法search()（根据产品名称在全网查询价格，假设需要3s左右才能返回），该方法会立即返回Future对象，调使用Future.get()可以同步等待耗时方法的返回，也可以调用future的cancel()取消Future任务。如下面的程序，search方法逻辑会根据名字在全网查找价格，假设需要耗时3s，该方法会立即返回一个Future对象供用户线程使用；在主方法中可以使用get()等待获取到价格，也可以使用cancel()取消查询。

```
public Future<String> search(String prodName) {
		FutureTask<String> future = new FutureTask<String>(new Callable<String>() {
			@Override
			public String call()  {
				try {
				System.out.println(String.format("	>>search price of %s from internet!",prodName));
				Thread.sleep(3000);
				return "$99.99";
				}catch(InterruptedException e){
					System.out.println("search function is Interrupted!");
				}
				return null;
			}
		});
		new Thread(future).start();//交给线程去执行
		return future; // 立刻返回future对象
	}

JavaFuture jf = new JavaFuture();
Future<String> future = jf.search("Netty权威指南");// 返回future
System.out.println("Begin search,get future!");

// 测试1-【获取结果】等待3s后会返回
String prods = future.get();//获取prods
System.out.println("get result:"+prods);

// 测试2-【取消任务】1s后取消任务
Thread.sleep(1000);
future.cancel(false);//true时会中断线程，false不会
System.out.println("Future is canceled? " + (future.isCancelled()?"yes":"no"));
		
Thread.sleep(4000); //等待4s检查一下future所在线程是否还在执行
```

## 4.2.2 Future的实现

假如我们需要实现一个Future，考虑一下需要实现哪些功能：
```
Future<String> future = jf.search("Netty权威指南");

Future search(){
   //启动线程或者在线程池中执行业务逻辑
   return future; //立刻返回future
}
```
* search方法需要立即返回一个Future对象，并且需要启动一个线程（或线程池）执行业务逻辑；
* 由于Future对象可以等待线程执行结束或者取消线程，Future内部需要能够管理业务逻辑的执行状态。
* 业务逻辑结束或异常时需要告诉Future对象，有两种方式：在Future中启动线程执行业务逻辑；或者业务逻辑单独执行，通过创建的Future实例的方法如setSuccess(result)方法通知Future。Java的FutureTask采用了第一种方法，其本身继承了Runnable，在run方法中执行传入的业务逻辑。而Netty的Promise中采用了第二种方法。
* get()方法中，如果业务逻辑还未执行完毕，需要等待，可以用锁机制实现。

Java中的Future是一个接口，内部有如下方法：

```
boolean	cancel(boolean mayInterruptIfRunning) 试图取消对此任务的执行。
V	get() 如有必要，等待计算完成，然后获取其结果。
V	get(long timeout, TimeUnit unit) 如有必要，最多等待为使计算完成所给定的时间之后，获取其结果（如果结果可用）。
boolean	isCancelled() 如果在任务正常完成前将其取消，则返回 true。
boolean	isDone() 如果任务已完成，则返回 true。
```

下面，我们自己实现一个Future加深理解，下面定义了一个继承Future的MyFutureTask，初始化时传递一个Callable作为业务逻辑，实现Future接口是为了控制业务逻辑线程，实现Runnable接口是为了业务线程执行时能够修改Future的内部状态。

```

public class MyFutureTask<V> implements Future<V>,Runnable {
	Callable<V> callable; //业务逻辑
	boolean running = false ,done = false,cancel = false;// 业务逻辑执行状态
	ReentrantLock lock ;//锁
	V outcome;//结果
	
	public MyFutureTask(Callable<V> callable) {
		if(callable == null) {
			throw new NullPointerException("callable cannot be null!");
		}
		this.callable = callable;
		this.done = false;
		this.lock = new ReentrantLock();
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		callable = null;
		cancel = true;
		return true;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public V get() throws InterruptedException, ExecutionException {
		try {
			this.lock.lock();//先获取锁，获得后说明业务逻辑已经执行完毕
			return outcome;
		}finally{
			this.lock.unlock();
		}
	}

	@Override
	public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		try {
			this.lock.tryLock(timeout, unit);
			return outcome;
		}catch (InterruptedException e) {
			return null;
		}finally{
			this.lock.unlock();
		}
	}
	@Override
	public void run() {
		try {
			this.lock.lock(); // 启动线程，先上锁，防止get时直接返回
			running = true;
			try {
				outcome = callable.call(); // 业务逻辑
			} catch (Exception e) {
				e.printStackTrace();
			}
			done = true;
			running = false;
		}finally {
			this.lock.unlock(); // 解锁后get可获取
		}
	}
}

```
测试程序如下：
```
public Future<String> search(String prodName) {
		MyFutureTask<String> future = new MyFutureTask<String>(new Callable<String>() {
			@Override
			public String call()  {
				try {
				System.out.println(String.format("	>>search price of %s from internet!",prodName));
				Thread.sleep(3000);
				return "$99.99";
				}catch(InterruptedException e){
					System.out.println("search function is Interrupted!");
				}
				return null;
			}
		});
		new Thread(future).start();// 或提交到线程池中
		return future;
	}
```

## 4.2.3 Java的Future实现

当然，上面是自己实现的FutureTask，Java自带的FutureTask要比上面的更加复杂和健壮。下面我们进行一些分析。

1. FutureTask内部维护了state，表示运行状态，只能通过set,setException, 和 cancel来修改。
```
 	private static final int NEW          = 0;  //初始状态，
    private static final int COMPLETING   = 1; // 业务逻辑已经结束
    private static final int NORMAL       = 2;  // 正常结束
    private static final int EXCEPTIONAL  = 3; // 异常结束
    private static final int CANCELLED    = 4; // 已经取消
    private static final int INTERRUPTING = 5; // 中断中
    private static final int INTERRUPTED  = 6; // 已经中断
```
2. private volatile WaitNode waiters; 维护了等待的线程，get()方法时，如果业务逻辑还未执行完毕，则创建WaitNode q，将其q.next设置为waiters，waiters设置为q；这样组成了一个等待链表。在业务逻辑执行完毕（正常或异常结束)时，

**run方法**

run方法用来执行业务逻辑，在此过程中需要维护好业务逻辑的运行状态

```
public void run() {
		// 1. 如果state不为初始状态或者runner不为null，说明已经在运行了，直接返回
        // 如果为空，使用CAS将runner设置为当前线程，防止并发进入
        //runnerOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("runner"));
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) { // 2.业务逻辑不为空并且state为NEW时才运行
                V result;
                boolean ran;
                try {
                    result = c.call(); // 3. 执行业务逻辑
                    ran = true;  // ran为true表示正常返回
                } catch (Throwable ex) {
                    result = null;  // 发生异常，结果为null
                    ran = false; // 非正常结束
                    setException(ex); // 设置异常
                }
                if (ran)
                    set(result); // 正常结束，设置结果
            }
        } finally {
            // 为例防止并发调用run()方法，进入run时使用cas将runner设置为非空，结束时设为null
            runner = null;
            int s = state;  // 当前状态为INTERRUPTING或者INTERRUPTED 说明要取消
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);// 如果在中断进行中，则一直等待
        }
    }
```
* 执行run方法时，要判断Future状态是否正确，必须为NEW；使用CAS将runner对象设置为当前线程，若runner不为null，说明其他线程已经执行了run方法，则直接return；
* 状态为NEW，执行传入的业务逻辑，正常结束时，将结果保存到result，ran设置为true；若发生异常，设置result为空，ran为false，并设置异常setException(ex);
* 正常结束，调用set(result);设置结果
* 业务逻辑执行结束，讲runner设置为null，若线程在INTERRUPTING或者INTERRUPTED 说明要取消；如果在中断进行中，则一直等待。
* setException(ex); 业务逻辑异常时调用
```
 protected void setException(Throwable t) {
 		// 若状态为NEW，将其设置为COMPLETING-完成
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t; // 结果为抛出的异常
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // 最终状态为EXCEPTIONAL-异常
            finishCompletion();
        }
    }
```
* set(V v)  业务逻辑正常结束时设置结果
```
protected void set(V v) {
        // 若状态为NEW，将其设置为COMPLETING-完成
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // 最终状态为NORMAL-正常结束
            finishCompletion();
        }
    }
```
* finishCompletion做了一些收尾性工作，根据waiters链表，唤醒等待的线程。
```
 private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) { // 遍历链表
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t); // 唤醒线程
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }
        done();
        callable = null;        // to reduce footprint
    }
```

**get方法**

get时，如果业务逻辑尚未结束，需要使用LockSupport.park(this);将休眠等待的线程，在业务逻辑完成后，finishCompletion()会唤醒线程，之后返回业务逻辑的处理结果。
```
public V get() throws InterruptedException, ExecutionException {
        int s = state;
        // 如果状态为NEW或者COMPLETING，说明还未结束，加入等待链表waiters
        if (s <= COMPLETING)  
            s = awaitDone(false, 0L);
        return report(s); // 返回结果
    }
```

**cancel方法**

cancel方法会取消执行业务逻辑的，主要逻辑如下：
```
public boolean cancel(boolean mayInterruptIfRunning) {
		// mayInterruptIfRunning表示以中断取消
        // 如果状态为NEW，说明还未执行，无需取消；讲状态设置为打断或取消
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {    // in case call to interrupt throws exception
            if (mayInterruptIfRunning) { // 以中断取消
                try {
                    Thread t = runner;
                    if (t != null)
                        t.interrupt(); // 执行线程的interrupt方法
                } finally { // 中断完成，修改状态为INTERRUPTED-已中断
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion(); // 唤醒等待线程
        }
        return true;
    }
```

通过分析，可以看到，java的FutureTask通过state记录业务逻辑的执行状态；多线程时使用CAS防止重复进入；业务逻辑未执行完成时，会将线程加入到waiter链表，使用LockSupport.park()阻塞业务线程；业务逻辑执行完毕或发生异常或被取消时，唤醒等待列表的线程。
与我们实现时使用的ReentrantLock在原理上是一样的，ReentrantLock的lock在获取不到锁时，也会维护一个链表保存等待列表，释放锁时，唤醒等待列表上的线程。区别在与，Java的实现会同时唤醒所有的等待线程，而unlock时等线程表会依次获得锁。