java是一个安全性很高的平台，开发者在使用过程中，并不需要直接对内存进行操作，Unsafe对象字如其名，是一个不安全的工具类，可以突破java语言的限制，对内存进行操作。

## 初始化

Unsafe的构造方法是私有的，我们不能直接通过new来获取实例，Unsafe内部有一个静态方法getUnsafe可以获取实例；
```
private Unsafe() {}
public static Unsafe getUnsafe() {  
	Class cc = sun.reflect.Reflection.getCallerClass(2);  
	if (cc.getClassLoader() != null)  //没有被任何类加载器加载时是null。它会抛出SecurityException 异常
		throw new SecurityException("Unsafe");  
	return theUnsafe;  
} 
```
getUnsafe()方法获取实例，直接调用时会抛出异常java.lang.SecurityException: Unsafe，只有java认为是安全的代码才可以加载并获取Unsafe实例。如此一来，我们编写的代码就不能直接通过该方法获取Unsafe实例了。  
不过Unsafe的内部有一个theUnsafe变量,我们可以通过反射来获取这个实例。获取实例的方法很简单，获取theUnsafe属性，在调用get方法即可
```
private static final Unsafe theUnsafe = new Unsafe();
// 通过反射获取Unsafe实例
public static Unsafe getUnsafe()  throws Exception{
		Field f = Unsafe.class.getDeclaredField("theUnsafe"); //Internal reference  
		f.setAccessible(true);  
		Unsafe unsafe = (Unsafe) f.get(null);
		return unsafe;
}
```


## 相关操作

### 1.分配内存
allocateMemory方法可以用来分配本地内存，下面模拟一个数组UnsafeArray,内部保存byte类型数据；初始化时，根据数组大小分配内存，返回address为数组的基地址，set和get时根据偏移量调用putByte和getByte修改数组内数据。
```
int BYTESIZE = 1;
public UnsafeArray(long size) {
		address = UNSAFE.allocateMemory(size *BYTESIZE);
	}
	
	public byte get(long index) {
		return UNSAFE.getByte(address + (index * BYTESIZE));
	}
	
	public void set(long index,byte result) {
		UNSAFE.putByte(address + (index * BYTESIZE), result);
	}
```

### 2.对象属性
objectFieldOffset方法可以类的某个属性在Class中的偏移，根据偏移量调用getInt()和UNFASE.getAndSetInt()可以获取/设置对象实例该属性的值。下面以SampleClass类为例，内部有一个整型i默认为5，long型默认为10
```
public final class SampleClass{
    private int i = 5;
    private long l = 10;
    private byte[] buf = new byte[4];
}
```
下面是具体的使用过程
```
public static void testObject() throws NoSuchFieldException, SecurityException {
		// 由于对象头是8+8 int的offset应该是16
		long offseti = UNFASE.objectFieldOffset(SampleClass.class.getDeclaredField("i"));
		System.out.println("offset of i is " + offseti);//offset of i is 12
		long offsetl = UNFASE.objectFieldOffset(SampleClass.class.getDeclaredField("l"));
		System.out.println("offset of l is " + offsetl);//offset of l is 16
		
		SampleClass sampleClass = new SampleClass();
		System.out.println("testObject offset of offseti : "+ UNFASE.getInt(sampleClass, offseti));// testObject offset of offseti : 5
		System.out.println("testObject offset of offsetl : "+ UNFASE.getInt(sampleClass, offsetl));// testObject offset of offsetl : 10
		
		//
		UNFASE.getAndSetInt(sampleClass, offseti, 99);
		System.out.println("sampleClass.getI() = " + sampleClass.getI());// sampleClass.getI() = 99
		
		UNFASE.getAndSetLong(sampleClass, offsetl, 88);
		System.out.println("sampleClass.getL() = " + sampleClass.getL());// sampleClass.getL() = 88
	}
```

## 3.CAS

CAS是java并发包中最常见的无锁方法，底层均由unsafe实现，有如下方法：
```

/** 
* 如果对象offset的当前值是expected则更新为x
*/  
public final native boolean compareAndSwapObject(Object o, long offset,  
                                               Object expected,  
                                               Object x);  

public final native boolean compareAndSwapInt(Object o, long offset,  
                                            int expected,  
                                            int x);  

public final native boolean compareAndSwapLong(Object o, long offset,  
                                             long expected,  
                                             long x);  
```
在AtomicInteger的源码中，可以看到cas的使用，其内部使用value记录了实际的int值，初始化时获取了value在AtomicInteger的偏移valueOffset，在AtomicInteger提供的方法中，会使用unsafe的相关方法完成cas操作。

```
//AtomicInteger类源码
public final int getAndSet(int newValue) {
    return unsafe.getAndSetInt(this, valueOffset, newValue);
}


public final boolean compareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
}


public final boolean weakCompareAndSet(int expect, int update) {
    return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
}
```

## 4.park

unsafe有两个方法park()和unpark()可以用来停止线程和恢复线程，并发包中的很多锁机制都使用了该方法，下面的例子中会启动一个线程，休眠5秒或被唤醒后结束；main线程休眠2秒后unpark恢复了内部线程。

```
Thread thread = new Thread(new Runnable() {
    public void run() {
        UNFASE.park(false, TimeUnit.SECONDS.toNanos(5));
        System.out.println("innrt thread end");
    }
});
thread.start();
System.out.println("begin thread!");
TimeUnit.SECONDS.sleep(2);
UNFASE.unpark(thread);
System.out.println("main thread unpark!");
```
打印内容为：
```
0s-begin thread!
2s-main thread unpark!
2s-innrt thread end
```

## 5.monitor

临界区相关的方法有
```
public native void monitorEnter(Object o);
public native void monitorExit(Object o);  
public native boolean tryMonitorEnter(Object o); 
```
使用方法类似与synchronized类似，进入临界区之前需要获得锁，跳出临界区后要释放锁。park中的例子可以改写为下面的方式：
```
final Object lock = new Object();
Thread thread = new Thread(new Runnable() {
    public void run() {
        UNFASE.monitorEnter(lock);
        System.out.println("innrt thread end");
    }
});
thread.start();
UNFASE.monitorEnter(lock);
System.out.println("begin thread!");
TimeUnit.SECONDS.sleep(2);
UNFASE.monitorExit(lock);
System.out.println("main thread unpark!");

```

# netty对unsafe的使用

netty中在内存相关的操作和cas中使用了unsafe对象，具体程序在PlatformDependent0中。

1.获取DirectByteBuffer的地址

由于unsafe主要用于分配直接内存，首先获取了nio的Buffer中是否有address属性，如果内有则不需要获取Unsafe对象实例了。
```
inal ByteBuffer direct = ByteBuffer.allocateDirect(1);
......
final Field field = Buffer.class.getDeclaredField("address");
field.setAccessible(true);
// if direct really is a direct buffer, address will be non-zero
if (field.getLong(direct) == 0) {
	return null;
}
return field;
```
2. 获取Unsafe实例，使用反射的方法获取theUnsafe属性
3. 大量使用了内存相关的方法

