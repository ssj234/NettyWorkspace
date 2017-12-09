jemalloc的另一个重要的概念是本地缓冲Thread-Local Storage,将释放后的内存使用信息保存在线程中以提高内存分配效率。
在Netty中，担负TLS的类有：
* PoolThreadLocalCache 类似ThreadLocal对象，内部保存线程本地缓存
* PoolThreadCache 缓冲池，每个线程一个实例，保存回收的内存信息
* MemoryRegionCache  内部有一个队列，保存了内存释放时的数据Chunk和Handle
* Recycler 一个轻量级对象池，

## 8.2.1 PoolThreadLocalCache

PoolThreadLocalCache继承FastThreadLocal对象，FastThreadLocal是netty自己实现的一直ThreadLocal机制，详细实现可以参见ThreadLocal一节。我们可以将其当做一个普通的FastThreadLocal理解，每个线程保存了PoolThreadCache对象，使用get时，如果ThreadLocal内部没有则会调用initialValue()方法创建。PoolThreadCache创建过程中会选择内存使用最少的Arena来创建PoolThreadCache。

```
final class PoolThreadLocalCache extends FastThreadLocal<PoolThreadCache> {

    @Override
    protected synchronized PoolThreadCache initialValue() {
        final PoolArena<byte[]> heapArena = leastUsedArena(heapArenas);
        final PoolArena<ByteBuffer> directArena = leastUsedArena(directArenas);

        return new PoolThreadCache(
                heapArena, directArena, tinyCacheSize, smallCacheSize, normalCacheSize,
                DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);
    }

    @Override
    protected void onRemoval(PoolThreadCache threadCache) {
        threadCache.free();
    }

    private <T> PoolArena<T> leastUsedArena(PoolArena<T>[] arenas) {
        if (arenas == null || arenas.length == 0) {
            return null;
        }

        PoolArena<T> minArena = arenas[0];
        for (int i = 1; i < arenas.length; i++) {
            PoolArena<T> arena = arenas[i];
            if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                minArena = arena;
            }
        }

        return minArena;
    }
    }
```

## 8.2.2 PoolThreadCache

PoolThreadCache记录了线程本地保存的内存池，分配的ByteBuf释放时会被保存到该对象的实例中。PoolThreadCache内部保存了tiny/small/normal的堆内存和直接内存的MemoryRegionCache数组
```
final PoolArena<byte[]> heapArena; // 堆Arena
final PoolArena<ByteBuffer> directArena; // 直接内存Arena
private final MemoryRegionCache<byte[]>[] tinySubPageHeapCaches;// tiny-heap
private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;// small-heap
private final MemoryRegionCache<byte[]>[] normalHeapCaches;// normal-heap
private final MemoryRegionCache<ByteBuffer>[] tinySubPageDirectCaches;// tiny-direct
private final MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;// small-direct
private final MemoryRegionCache<ByteBuffer>[] normalDirectCaches;// normal-direct
```
数组的大小与Arena中tinySubpagePools和smallSubpagePools的大小一样，NormalMemoryRegionCache继承了MemoryRegionCache对象，内部的queue保存了chunk和handle，根据这两个可以定位到chunk中对应的范围。


## 8.2.3 Recycler

Recycler是一个基于ThreadLocal栈的轻量级的对象池，在实现上，线程内部的threadLocal保存Stack对象，Stack内部保存了Handler，

内部有一个Handle接口，recycle方法用来回收对象
```
public interface Handle<T> {
    void recycle(T object);
}
```
在使用时，需要重写Recycler的newObject方法，该方法会在get时使用，如果本地线程池没有可重复使用的对象则调用newObject返回一个新对象。
```
//
public static Recycler<MObject> RECYCLER = new Recycler<MObject>() {
	    @Override
	    protected MObject newObject(Handle<MObject> handle) {
	        return new MObject(handle);
	    }
	};
```
之后，我们就可以讲对象的获取交给RECYCLER处理
```
public static void main(String[] args) {
	MObject obj1 = RECYCLER.get();// 获取对象
	System.out.println(obj1);  // obj1 地址 1418370913
	MObject obj2 = RECYCLER.get(); // 再次获取
	System.out.println(obj2); // obj2 地址 361993357
	
	obj1.free();
	obj2.free();
	System.out.println(RECYCLER.get());// 地址1418370913，重用了obj1
	System.out.println(RECYCLER.get());//	地址625576447 创建了新对象
}
```

Recycler的get方法，首先从本地获取stack，如果为空会创建并保存到线程本地。之后从stack中获取对象，如果存在则返回。注意stack中的对象是Handler对象，Handler的value才是newObject返回的对象。
```
 public final T get() {
        if (maxCapacityPerThread == 0) {
            return newObject((Handle<T>) NOOP_HANDLE);
        }
        Stack<T> stack = threadLocal.get(); // 从本地stack中获取 没有则创建
        DefaultHandle<T> handle = stack.pop(); // 获取stack里面的handler
        if (handle == null) { // 如果handler为空，则创建一个，
            handle = stack.newHandle();
            handle.value = newObject(handle); // 创建对象
        }
        return (T) handle.value;
    }
```
释放对象时，需要通过Handler的recycle方法完成，
```
public void recycle(Object object) {
        if (object != value) {
            throw new IllegalArgumentException("object does not belong to handle");
        }
        stack.push(this);
}
```

