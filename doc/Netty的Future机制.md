## 4.3.1 Netty的Future

Netty的Future在concurrent包的Future基础上，增加了更多的功能。在Java的Future中，主要是任务的运行/取消，而Netty的Future增加了更多的功能。
```
public interface Future<V> extends java.util.concurrent.Future<V> 

boolean isSuccess(); 只有IO操作完成时才返回true
boolean isCancellable(); 只有当cancel(boolean)成功取消时才返回true
Throwable cause();  IO操作发生异常时，返回导致IO操作以此的原因，如果没有异常，返回null
// 向Future添加事件，future完成时，会执行这些事件，如果add时future已经完成，会立即执行监听事件
Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);
// 移除监听事件，future完成时，不会触发
Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);
Future<V> sync() throws InterruptedException;  //等待future done
Future<V> syncUninterruptibly();   // 等待future done，不可打断
Future<V> await() throws InterruptedException; // 等待future完成
Future<V> awaitUninterruptibly();  // 等待future 完成，不可打断
V getNow(); // 立刻获得结果，如果没有完成，返回null
boolean cancel(boolean mayInterruptIfRunning); // 如果成功取消，future会失败，导致CancellationException
```
Netty为Future加入的功能主要是添加/删除监听事件，在Promise中会有实例演示。其他的方法是为get()方法服务的，get()方法可以通过调用await/getNow等方法实现。

## 4.3.2 Netty的Promise机制

Netty的Future与Java自带到Future略有不同，其引入了Promise机制。在Java的Future中，业务逻辑为一个Callable或Runnable实现类，该类的call()或run()执行完毕意味着业务逻辑的完结；而在Promise机制中，可以在业务逻辑中人工设置业务逻辑的成功与失败。

Netty中Promise接口的定义如下：

```
public interface Promise<V> extends Future<V> {
	// 设置future执行结果为成功
    Promise<V> setSuccess(V result);
   	// 尝试设置future执行结果为成功,返回是否设置成功
   boolean trySuccess(V result);
   // 设置失败
    Promise<V> setFailure(Throwable cause);
    boolean tryFailure(Throwable cause);
    // 设置为不能取消
    boolean setUncancellable();
    //一下省略了覆盖Future的一些方法
}
```

下面以一个例子来说明Promise的使用方法，还是以seach()查询产品报价为例：
```
// main 方法
NettyFuture4Promise test = new NettyFuture4Promise();
Promise<String> promise = test.search("Netty In Action");
String result = promise.get();
System.out.println("price is " + result); 

//
private Promise<String> search(String prod) {
		NioEventLoopGroup loop = new NioEventLoopGroup();
        // 创建一个DefaultPromise并返回
		DefaultPromise<String> promise = new DefaultPromise<String>(loop.next());
		loop.schedule(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println(String.format("	>>search price of %s from internet!",prod));
					Thread.sleep(5000);
					promise.setSuccess("$99.99");// 等待5S后设置future为成功，
                   // promise.setFailure(new NullPointerException()); //当然，也可以设置失败
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		},0,TimeUnit.SECONDS);
		
		return promise;
	}
	
```
可以看到，Promise能够在业务逻辑线程中通知Future成功或失败，由于Promise继承了Netty的Future，因此可以加入监听事件。

```
// main方法中，查询结束后获取promise，加入两个监听事件，分别给小Hong发通知和Email
Promise<String> promise = test.search("Netty In Action");
		promise.addListener(new GenericFutureListener<Future<? super String>>() {
			@Override
			public void operationComplete(Future<? super String> future) throws Exception {
				System.out.println("Listener 1, make a notifice to Hong,price is " + future.get());
			}
			
		});
		promise.addListener(new GenericFutureListener<Future<? super String>>() {
			@Override
			public void operationComplete(Future<? super String> future) throws Exception {
				System.out.println("Listener 2, send a email to Hong,price is " + future.get());
			}
			
		});
```

Future和Promise的好处在于，获取到Promise对象后可以为其设置异步调用完成后的操作，然后立即继续去做其他任务。


## 4.3.3 Netty常用的Promise类

Netty常用的纯Future机制的类，有SucceededFuture和FailedFuture，他们不需要设置业务逻辑代码，会立刻完成，只需要设置成功后的返回和抛出的异常。

Netty的常用Promise类有DefalutPromise类，这是Promise实现的基础，后续会对这个类的实现进行解读；DefaultChannelPromise是DefalutPromise的子类，加入了channel这个属性。

下面对DefaultChannelPromise进行分析，其类图如下：
![NettyFuture类图](http://www.uxiaowo.com/netty/Future/Future.png)

###  DefaultPromise的使用

Netty中涉及到异步操做的地方都使用了promise，例如，下面是服务器/客户端启动时的注册任务，最终会调用unsafe的register，调用过程中会传入一个promise，unsafe进行事件的注册时调用promise可以设置成功/失败。
```
// SingleThreadEventLoop.java
public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }
 // AbstractChannel.AbstractUnsafe
 public final void register(EventLoop eventLoop, final ChannelPromise promise) {
    if (eventLoop == null) {
        throw new NullPointerException("eventLoop");
    }
    if (isRegistered()) {
        promise.setFailure(new IllegalStateException("registered to an event loop already"));
        return;
    }
    if (!isCompatible(eventLoop)) {
        promise.setFailure(
                new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
        return;
    }
  ......
}
```

### DefaultPromise的实现

DefaultChannelPromise提供的功能可以分为两个部分：一方面是为调用者提供get()和addListener()用于获取Future任务执行结果和添加监听事件；另一方面是为业务处理任务提供setSuccess()等方法设置任务的成功或失败。

**get方法**

DefaultPromise的get方法有两个，无参数的get会阻塞等待；有参数的get会等待指定事件，若未结束抛出超时异常。这两个get()是在其父类AbstractFuture中实现的，通过调用下面四个方法实现：
```
await(); // 等待Future任务结束
await(timeout, unit)  // 等待Future任务结束，超过事件则抛出异常
cause();  // 返回Future任务的异常
getNow() //  /返回Future任务的执行结果

// 先等待，如果有异常则抛出，无异常返回getNow()
public V get() throws InterruptedException, ExecutionException {
        await();

        Throwable cause = cause();
        if (cause == null) {
            return getNow();
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new ExecutionException(cause);
    }
```
**await**

await()方法判断Future任务是否结束，之后获取this锁，如果任务未完成，则调用Object的wait()等待
```
public Promise<V> await() throws InterruptedException {
		// 判断Future任务是否结束，内部根据result是否为null判断，setSuccess或setFailure时会通过CAS修改result
        if (isDone()) {  
            return this;
        }

        if (Thread.interrupted()) { // 线程是否被中断
            throw new InterruptedException(toString());
        }

        checkDeadLock(); // 检查当前线程是否与线程池运行的线程是一个

        synchronized (this) {
            while (!isDone()) {
                incWaiters(); // waiters计数加1
                try {
                    wait();  // Object的方法，让出cpu，加入等待队列
                } finally {
                    decWaiters(); //  waiters计数减1
                }
            }
        }
        return this;
    }
```
await(long timeout, TimeUnit unit)与awite类似，只是调用了Object对象的wait(long timeout, int nanos)方法
awaitUninterruptibly()方法在内部catch住了等待线程的中断异常，因此不会抛出中断异常。

#### 监听事件相关方法

**add/remove方法**

addListener方法被调用时，将传入的回调类传入到listeners对象中，如果监听多于1个，会创建DefaultFutureListeners对象将回调方法保存在一个数组中。removeListener会将listeners设置为null(只有一个时)或从数组中移除(多个回调时)。

```
private void addListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners == null) {
            listeners = listener;
        } else if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).add(listener);
        } else {
            listeners = new DefaultFutureListeners((GenericFutureListener<? extends Future<V>>) listeners, listener);
        }
    }
    
private void removeListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).remove(listener);
        } else if (listeners == listener) {
            listeners = null;
        }
    }
```

**notifyListeners()**

在添加监听器的过程中，如果任务刚好执行完毕done(),则立即触发监听事件。触发监听通过notifyListeners()实现。主要逻辑为：如果当前addListener的线程（准确来说应该是调用notifyListeners的线程，因为addListener和setSuccess都会调用notifyListeners()和Promise内的线程池当前执行的线程是同一个线程，则放在线程池中执行，否则提交到线程池去执行；例如，main线程中调用addListener时任务完成，notifyListeners()执行回调，会提交到线程池中执行；而如果是执行Future任务的线程池中setSuccess()时调用notifyListeners()，会放在当前线程中执行。  
内部维护了notifyingListeners用来记录是否已经触发过监听事件，只有未触发过且监听列表不为空，才会依次便利并调用operationComplete
```
private static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            l.operationComplete(future);
        } catch (Throwable t) {
            logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
        }
    }
```


#### setSuccess()方法

Future任务在执行完成后调用setSuccess()或setFailure()通知Future执行结果；主要逻辑是：修改result的值，若有等待线程则唤醒，通知监听事件。

```
if (setSuccess0(result)) { // 设置成功后唤醒等待线程
            notifyListeners(); // 通知
            return this;
}
// 通知成功时将结果保存在变量result，通知失败时，使用CauseHolder包装Throwable赋值给result
// RESULT_UPDATER 是一个使用CAS更新内部属性result的类，
// 如果result为null或UNCANCELLABLE，更新为成功/失败结果；UNCANCELLABLE是不可取消状态
private boolean setValue0(Object objResult) {
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
            RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {
            checkNotifyWaiters();// 调用Object的notifyAll();通知等待线程
            return true;
        }
        return false;
    }
```

#### cancel()方法

cancel用来取消任务，根据result判断，如果可以取消，则唤醒等待线程，通知监听事件。

```
public boolean cancel(boolean mayInterruptIfRunning) {
	//如果result为null，说明未setUncancellable()/setSuccess/setFailure
        if (RESULT_UPDATER.compareAndSet(this, null, CANCELLATION_CAUSE_HOLDER)) {
            checkNotifyWaiters(); // 唤醒等待线程
            notifyListeners(); // 触发监听事件
            return true;
        }
        return false;
    }
```


通过上面的分析，我们可以看到DefaultPromise内部通过result记录Future任务的执行状态：
```
null - 未完成
CANCELLATION_CAUSE_HOLDER -被取消
UNCANCELLABLE - 不可取消
业务处理调用setSuccess时传入的结果
业务处理调用setFailure时包装Throws的CauseHolder
```
DefaultPromise内部维护了一个监听列表保存监听事件，在任务完成或取消时通知监听事件（提交到线程池中执行）；任务的等待与唤醒通过Object的wait()和notifyAll()完成

### DefaultChannelPromise实现

DefaultChannelPromise是DefaultPromise的子类，内部维护了一个通道变量Channel channel;Promise机制相关的方法都是调用父类方法。  
除此之外，还实现了FlushCheckpoint接口，供ChannelFlushPromiseNotifier使用，我们可以将ChannelFuture注册到ChannelFlushPromiseNotifier类，当有数据写入或到达checkpoint时使用。
```
    interface FlushCheckpoint {
        long flushCheckpoint();
        void flushCheckpoint(long checkpoint);
        ChannelPromise promise();
    }
```