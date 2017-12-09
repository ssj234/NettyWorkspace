netty在内存分配中大量使用了线程本地缓存，并对ThreadLocal进行了扩展。

## 9.3.1 java的ThreadLocal

**使用**

ThreadLocal为每个线程存储了其对应的副本，下面的例子中，我们启动多个线程，每个线程保存不同的数据。
```
public class JavaThreadLocalDemo2 {

	public static void main(String[] args) {
		
		Task task = new Task();
		Thread tasks[] = new Thread[10];
		for(int i=0;i<10;i++) {
			tasks[i] = new Thread(task);
		}
		for(int i=0;i<10;i++) {
			tasks[i].start();
		}
		
	}
	
	static class Task implements Runnable{
		ThreadLocal<Float> local = new ThreadLocal<Float>();
		
		@Override
		public void run() {
			Float process = new Random().nextFloat();
			local.set(process);
			try {
				Thread.sleep(new Random().nextInt(1000));
			} catch (InterruptedException e) {}
			System.out.println(Thread.currentThread().getName() +" "+local.get() +" "+ (local.get()==process));
		}
	}
}
```


**实现**

每个Thread实例的内部有一个ThreadLocal.ThreadLocalMap对象的实例threadLocals，内部有一个Entry[] table用来保存threadLocal和其对应的Object；每个ThreadLocal有一个唯一的threadLocalHashCode，每次调用set时，先获取当前线程的ThreadLocalMap，然后使用map的set方法将ThreadLocal和value设置进去。
```
public void set(T value) {
	Thread t = Thread.currentThread();
	ThreadLocalMap map = getMap(t);
	if (map != null)
	    map.set(this, value);
	else
	    createMap(t, value);
}
```
map的set中，首先根据ThreadLocal的哈希值获取其在table中的位置，如果该位置内容为null，保存到这个元素上即可；如果该位置元素不为null且key为相同ThreadLocal，替换value；如果该位置元素不为null且key为null（出现null的原因是由于Entry的key是继承了软引用，在下一次GC时不管它有没有被引用都会被回收掉，Value不会被回收）。当出现null时，会调用replaceStaleEntry()方法接着循环寻找相同的key，如果存在，直接替换旧值。如果不存在，则在当前位置上重新创建新的Entry。如果该位置元素不为相同ThreadLocal且不为null，说明其他ThreadLocal已经使用，遍历链表查找其他位置。
保存到table之后，cleanSomeSlots会查询是否有过期的元素，如果有并且大于阀值(超过2/3)，执行rehash()
```
private void set(ThreadLocal<?> key, Object value) {

    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);

    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        ThreadLocal<?> k = e.get();

        if (k == key) {
            e.value = value;
            return;
        }

        if (k == null) {
            replaceStaleEntry(key, value, i);
            return;
        }
    }

    tab[i] = new Entry(key, value);
    int sz = ++size;
    if (!cleanSomeSlots(i, sz) && sz >= threshold)
        rehash();
}
```
map的get方法中，获取当前线程的ThreadLocalMap后，在getEntry中获取ThreadLocal对于的值。getEntry方法会根据index查找，由于可能发生冲突，会调用getEntryAfterMiss遍历数组。
```
public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        return setInitialValue();
    }
    
    private Entry getEntry(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }
```

通过对java的ThreadLocal实现的了解，我们可以看到，在set时会发生冲突，会遍历寻找合适的位置，而在get时如果首次未命中，也会遍历寻找ThreadLocal对应的值。在Netty高并发中，会有频繁的读取，因此Netty自己实现了ThreadLocal以提高效率。


# Netty的ThreadLocal

首先，Netty将线程分为了两种，一种是Netty实现的可快速存取本地缓存的FastThreadLocalThread，一种是普通的Thread。这点在线程池创建过程中使用的`DefaultThreadFactory`中可以看到，线程池创建的线程都是FastThreadLocalThread。FastThreadLocalThread中有一个变量InternalThreadLocalMap保存本地的相关数据。

在线程中，netty讲线程的本地变量都保存在InternalThreadLocalMap中，这个对象看名字是个Map，但其实里面由数组indexedVariables保存设置的本地变量。数组indexedVariables的大小默认为32，每个线程内部都有一个InternalThreadLocalMap的实例，可以设置多个ThreadLocal，这个ThreadLocal不是Java默认的ThreadLocal，而是使用FastThreadLocal，每个FastThreadLocal有一个唯一的Id保存在index中，对应着FastThreadLocalThread内部的InternalThreadLocalMap的位置。因此，netty相比Java的ThreadLocal来说，每个key都有固定的index，这样不会发生冲突，从而提高了效率。

netty中如果使用的是普通线程的ThreadLocal，那么会在Thread的ThreadLocalMap中保存InternalThreadLocalMap，FastThreadLocal存放在其index对应的位置上。

![NettyThreadLocal](http://www.uxiaowo.com/netty/Future/ThreadLocal.png) 


# 测试

CompareJavaTL使用java的ThreadLocal，写/读时间为338ms和248ms  
CompareNettyTL使用Netty的FastThreadLocal，写/读时间为283ms和8ms   
可见，Netty的ThreadLocal机制在读写时效率都要比java的高，根据之前的分析，
```
public class CompareJavaTL {
	static int count = 100000000;
	static int times = 100000000;
	public static void main(String[] args) {
		ThreadLocal<String> tl = new ThreadLocal<String>();
		long begin = System.currentTimeMillis();
		for(int i=0;i<count;i++) {
			tl.set("javatl");
		}
		System.out.println(System.currentTimeMillis()-begin);
		begin = System.currentTimeMillis();
		for(int i=0;i<times;i++) {
			tl.get();
		}
		System.out.println(System.currentTimeMillis()-begin);
	}
}

```
```
public class CompareNettyTL {
	static int count = 100000000;
	static int times = 100000000;
	public static void main(String[] args) {
		
		new FastThreadLocalThread(new Runnable() {
			
			@Override
			public void run() {
				FastThreadLocal<String> tl = new FastThreadLocal<String>();
				long begin = System.currentTimeMillis();
				for(int i=0;i<count;i++) {
					tl.set("javatl");
				}
				System.out.println(System.currentTimeMillis()-begin);
				begin = System.currentTimeMillis();
				for(int i=0;i<times;i++) {
					tl.get();
				}
				System.out.println(System.currentTimeMillis()-begin);
			}
		}).start();
	}
}
```