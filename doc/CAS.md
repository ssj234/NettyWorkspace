# CAS

CAS是compare and swap的缩写，是一种多线程无锁的编程方式。当修改多个线程可能同时操作的属性时，给定`期望值`和`要更新的值`，如果`当前值`与`期望值`一致，则更新为要设置的值。CAS的底层是通过将读取-比较-设置三个操作作为一个指令执行，在执行期间不会有其他线程改变变量。这保证了操作的原子性。
CAS其以乐观的态度进行操作，不断循环等待进行，对比而言，线程切换需要8万个cup时钟周期，而循环重试只需要几个cup时钟。

## 性能对比

下面以synchronized与cas进行对比，重点在于
```
// 1. 使用synchronized
static int count = 0;
final Object lock = new Object();

Thread ths [] = new Thread[10000];
for(int i=0;i<ths.length;i++) {
 Thread thread = new Thread(new Runnable() {
    
    public void run() {
        for(int i=0;i<10000;i++) {
            synchronized (lock) {
                count ++;
            }
        }
    }
});
ths[i] = thread;
}
long begin = System.currentTimeMillis();
for(int i=0;i<ths.length;i++) {
 ths[i].start();
 ths[i].join();
}
System.out.println(count);// 100000000
System.out.println(System.currentTimeMillis() - begin); //5269
```
使用CAS的测试
```

final AtomicInteger count = new AtomicInteger(0);
Thread ths [] = new Thread[10000];
for(int i=0;i<ths.length;i++) {
 Thread thread = new Thread(new Runnable() {
    
    public void run() {
        for(int i=0;i<10000;i++) {
            count.incrementAndGet();
        }
        
    }
});
ths[i] = thread;
}
long begin = System.currentTimeMillis();
for(int i=0;i<ths.length;i++) {
 ths[i].start();
 ths[i].join();
}
System.out.println(count.get());// 100000000
System.out.println(System.currentTimeMillis() - begin); // 1863
```
从上面可以看到，CAS比线程切换的方式快2.8倍以上。

## 主要类
* AtomicBoolean
* AtomicInteger
* AtomicIntegerArray
* AtomicIntegerFieldUpdater
* AtomicLong
* AtomicLongArray
* AtomicLongFieldUpdater
* AtomicMarkableReference     原子更新带有标记位的引用类型
* AtomicReference          原子更新引用类型
* AtomicReferenceArray       原子更新引用数组
* AtomicReferenceFieldUpdater     原子更新引用类型里的字段
* AtomicStampedReference

**主要方法：**
* set(newVal)/get() 赋值、获取当前值
* lazySet(newVal)
* getAndSet(newVal) 原子性设置值
* compareAndSet(exp,upd) 比较并设置值
* getAndIncrement()
* incrementAndGet()

## ABA问题

假如链表为： head->A->B->C，线程t1要将head->B,cas(A,B)，此过程中线程t2进行了head->A->C->D，此时B已经处于游离状态，B.next=null，切换到线程t1，t1发现header还是A，就进行交换
head->B，这时链表丢失了C和D。
以上就是由于ABA问题带来的隐患，各种乐观锁的实现中通常都会用版本戳version来对记录或对象标记，避免并发操作带来的问题，在Java中，AtomicStampedReference<E>也实现了这个功能。
```
private static AtomicStampedReference atomicStampedRef = new AtomicStampedReference(100, 0); //初始值为100，初始时间戳为0
atomicStampedRef.compareAndSet(100, 101, atomicStampedRef.getStamp(), atomicStampedRef.getStamp() + 1); //cas 还要判断时间戳
atomicStampedRef.compareAndSet(101, 100, atomicStampedRef.getStamp(), atomicStampedRef.getStamp() + 1);
```
