在事件循环读取到数据之后，会进入unsafe的read方法。unsafe内部使用了两个类处理内存的分配，ByteBufAllocator和RecvByteBufAllocator。ByteBufAllocator用来处理内存的分配，RecvByteBufAllocator用来计算此次读循环应该分配多少内存。
主事件循环组收到Accept事件后，会创建与客户端连接的NioSocketChannel，并将READ注册在子事件循环组中的selector上面，由事件循环不断select()查询就绪读I/O后交给NioSocketChannel处理。NioSocketChannel在初始化时创建了NioSocketChannelConfig，config内部会创建AdaptiveRecvByteBufAllocator实例用来计算内存大小，ByteBufAllocator.DEFAULT作为事件分配内存的工具类。


## 8.1.1 RecvByteBufAllocator

RecvByteBufAllocator是用于计算下次读循环应该分配多少内存的接口，只有一个方法。读循环是因为分配的初始ByteBuf不一定能够容纳所有读取到的数据，因此可能会多次读取，直到读完客户端发送的数据。（具体逻辑可见AbstractNioByteChannel的read()）
```
Handle newHandle();
```
newHandle用来返回RecvByteBufAllocator内部的计算器Handle，Handle提供了实际的计算操作，内部保存了记录每次分配多少内存的信息，提供预测缓冲大小等功能，下面是Handle接口：
```
ByteBuf allocate(ByteBufAllocator alloc); // 创建一个空间合理的缓冲，在不浪费空间的情况下能够容纳需要读取的所有inbound的数据，内部由alloc来进行实际的分配
int guess(); // 猜测所需的缓冲区大小，不进行实际的分配
void reset(ChannelConfig config); // 每次开始读循环之前，重置相关属性
void incMessagesRead(int numMessages); // 增加本地读循环的次数
void lastBytesRead(int bytes); // 设置最后一次读到的字节数
int lastBytesRead(); // 最后一次读到的字节数
void attemptedBytesRead(int bytes); // 设置读操作尝试读取的字节数
void attemptedBytesRead(); // 获取尝试读取的字节数
boolean continueReading(); // 判断需要继续读
void readComplete(); // 读结束后调用
```

AdaptiveRecvByteBufAllocator是我们实际使用的缓冲管理区，这个类可以动态计算下次需要分配的内存大小，其根据读取到的数据预测所需字节大小，从而自动增加或减少；如果上一次读循环将缓冲填充满，那么预测的字节数会变大。如果连续两次读循环不能填满已分配的缓冲区，则会减少所需的缓冲大小。需要注意的是，这个类只是计算大小，真正的分配动作由ByteBufAllocator完成。
AdaptiveRecvByteBufAllocator内部维护了一个SIZE_TABLE数组，使用slab的思想记录了不同的内存块大小，按照分配需要的大小寻找最合适的内存块。SIZE_TABLE数组中的值都是2的n次方，这样便于软硬件进行处理。位置0从16开始，之后每次增加16，直到512；而从512之后起，每次增加一倍，直到int的最大值；这是因为当我需要的内存很小时，增长的幅度也不大，而较大时增长幅度也很大。例如，当我们需要分配一块40的缓冲时，根据SIZE_TABLE会定位到64，index为2。这是SIZE_TABLE的主要作用。
```
16 32 48 64 80 96 112 128 144 160 176 192 208 224 240 256
```
AdaptiveRecvByteBufAllocator在初始化时，会设置三个大小属性：缓冲最小值，初始值和最大值，并根据SIZE_TABLE定位到相应的index，保存在minIndex，initial，maxIndex中。

HandleImpl在创建时内部保存了AdaptiveRecvByteBufAllocator的缓冲最小/最大和初始的index，并记录了下次需要分配的缓冲大小nextReceiveBufferSize，guess()时返回的即是该值。每次读循环完成后，会根据实际读取到的字节数和当前缓冲大小重新设置下次需要分配的缓冲大小。程序如下：
```
private void record(int actualReadBytes) {
    if (actualReadBytes <= SIZE_TABLE[Math.max(0, index - INDEX_DECREMENT - 1)]) {
        if (decreaseNow) { // 因为连续两次小于缓冲大小才会减小
            index = Math.max(index - INDEX_DECREMENT, minIndex);
            nextReceiveBufferSize = SIZE_TABLE[index];
            decreaseNow = false;
        } else {
            decreaseNow = true;
        }
    } else if (actualReadBytes >= nextReceiveBufferSize) {//读到的值大于缓冲大小
        index = Math.min(index + INDEX_INCREMENT, maxIndex); // INDEX_INCREMENT=4 index前进4
        nextReceiveBufferSize = SIZE_TABLE[index];
        decreaseNow = false;
    }
}

@Override
public void readComplete() { //读取完成后调用
    record(totalBytesRead());
}
```
了解了AdaptiveRecvByteBufAllocator之后，以一个实例进行演示。每次读循环开始时，先reset重置此次循环读取到的字节数，读取完成后readComplete会计算并调整下次循环需要分配的缓冲大小。
```

// 默认最小64 初始1024 最大65536
AdaptiveRecvByteBufAllocator adAlloctor = new AdaptiveRecvByteBufAllocator();
Handle handle =  adAlloctor.newHandle();

System.out.println("------------读循环1----------------------------");
handle.reset(null);// 读取循环开始前先重置，将读取的次数和字节数设置为0
System.out.println(String.format("读循环1-1：需要分配的大小：%d", handle.guess()));
handle.lastBytesRead(1024);
System.out.println(String.format("读循环1-2：需要分配的大小：%d", handle.guess()));// 读循环中缓冲大小不变
handle.lastBytesRead(1024);

handle.readComplete();
System.out.println("------------读循环2----------------------------");
handle.reset(null);// 读取循环开始前先重置，将读取的次数和字节数设置为0
System.out.println(String.format("读循环2-1：需要分配的大小：%d", handle.guess()));
handle.lastBytesRead(1024);

handle.readComplete();
System.out.println("------------读循环3----------------------------");
handle.reset(null);// 读取循环开始前先重置，将读取的次数和字节数设置为0
System.out.println(String.format("读循环3-1：需要分配的大小：%d", handle.guess()));
handle.lastBytesRead(1024);

handle.readComplete();
System.out.println("------------读循环4----------------------------");
handle.reset(null);// 读取循环开始前先重置，将读取的次数和字节数设置为0
System.out.println(String.format("读循环4-1：需要分配的大小：%d", handle.guess()));
handle.readComplete();

//###############################
//------------读循环1----------------------------
//读循环1-1：需要分配的大小：1024 
//读循环1-2：需要分配的大小：1024
//------------读循环2----------------------------
//读循环2-1：需要分配的大小：16384 （1024 × 2^INDEX_INCREMENT）
//------------读循环3----------------------------
//读循环3-1：需要分配的大小：16384
//------------读循环4----------------------------
//读循环4-1：需要分配的大小：8192  （16384 /  2^INDEX_DECREMENT）
```


## 8.1.2 内存分配算法

Netty采用了jemalloc的思想，这是FreeBSD实现的一种并发malloc的算法。jemalloc依赖多个Arena来分配内存，运行中的应用都有固定数量的多个Arena，默认的数量与处理器的个数有关。系统中有多个Arena的原因是由于各个线程进行内存分配时竞争不可避免，这可能会极大的影响内存分配的效率，为了缓解高并发时的线程竞争，Netty允许使用者创建多个分配器（Arena）来分离锁，提高内存分配效率，当然是以内存来作为代价的。  
线程首次分配/回收内存时，首先会为其分配一个固定的Arena。线程选择Arena时使用round-robin的方式，也就是顺序轮流选取，这是因为jemalloc任务依靠线程地址进行hash选取是不可靠的。
jemalloc的另一个思路是使用Thread-local storage，每个线程各种保存Arena和缓存池信息，这样可以减少竞争并提高访问效率。Arena将内存分为很多Chunk进行管理，Chunk内部保存Page，以页为单位申请。
申请内存分配时，会讲分配的规格分为几类：TINY，SAMLL，NORMAL和HUGE，分别对应不同的范围，处理过程也不相同。
![Arena](http://www.uxiaowo.com/netty/Future/Arena.png)

## 8.1.3 ByteBufAllocator

这个类用来进行实际的内存分配，默认使用的是ByteBufAllocator.DEFAULT,初始化时会根据配置和平台进行赋值。`io.netty.allocator.type`可以设置为`unpooled`和`pooled`指定是否需要缓冲池，如果不设置则会根据平台判断。一般情况下，我们会在linux运行，使用的是有缓冲池的内存分配器。
```
// 
String allocType = SystemPropertyUtil.get(
        "io.netty.allocator.type", PlatformDependent.isAndroid() ? "unpooled" : "pooled");
allocType = allocType.toLowerCase(Locale.US).trim();

ByteBufAllocator alloc;
if ("unpooled".equals(allocType)) {
    alloc = UnpooledByteBufAllocator.DEFAULT;
    logger.debug("-Dio.netty.allocator.type: {}", allocType);
} else if ("pooled".equals(allocType)) {
    alloc = PooledByteBufAllocator.DEFAULT;
    logger.debug("-Dio.netty.allocator.type: {}", allocType);
} else {
    alloc = PooledByteBufAllocator.DEFAULT;
    logger.debug("-Dio.netty.allocator.type: pooled (unknown: {})", allocType);
}
```

## 8.1.4 PooledByteBufAllocator

Netty实际使用内存分配器会根据配置采用PooledByteBufAllocator.DEFAULT或PooledByteBufAllocator.DEFAULT，所有事件循环线程使用的是一个分配器实例。
PooledByteBufAllocator将内存分为PoolArena，PoolChunk和PoolPage，Chunk中包含多个内存页，Arena包含3个Chunk。在PooledByteBufAllocator类加载时，会对这些配置进行初始化设置。
* 最大chunk大小：(Integer.max_value+1)/2 约为1GB
* 最大page大小：默认为8192，要求大于4096且为2的n次方
* 最大顺序：默认为11，在0-14之间
* 默认chunk大小：页大小* 2^order，即chunk由2的order个page组成
* Arena个数： Arena分为堆内存和直接内存，默认有3个chunk。由于pool的大小不能超过最大内存的一半，并且我们在事件循环组中使用了2×cores个线程，为了避免通过jvm进行同步，尽量选取大于2×cores的值。在netty中，使用2×cores和堆/直接内存/2/3的最小值作为Arena的数量
* 在内存分配的使用上，使用tiny：512，small：256;，normal：64作为阀值
* 默认缓存大小为32KB，这是jemalloc的推荐
* DEFAULT_CACHE_TRIM_INTERVAL：默认为8192，超过这个阀值会被free

PooledByteBufAllocator内部有两个重要数组`HeapArena`和`DirectArena`，用来记录堆内存和直接内存当前的使用状态。PoolArena都实现了PoolArenaMetric接口，用于测量内存使用状况。PooledByteBufAllocator初始化时，会根据之前的配置，初始化Arena信息，保存在heapArenas和directArenas，并分布使用两个list记录Metric。除此之外，还有一个重要的对象PoolThreadLocalCache，其继承了FastThreadLocal，用于线程的本地缓存，在内存管理中，线程本地内存缓区的信息会保存在PoolThreadCache对象中。
PooledByteBufAllocator覆盖的newHeapBuffer和newDirectBuffer用来分配内存，我们以newHeapBuffer为例学习。

## 8.1.5 PoolArena

PoolArena内部有三个重要的链表，tinySubpagePools/smallSubpagePools和PoolChunkList。前两个用于保存page的使用状态，最后一个用来保存chunk的使用状态。

**tinySubpagePools** 

用来保存为tiny规格分配的内存页的链表，共有32个这样的链表，保存着从16开始到512字节的内存页，32的大小是固定的，因为正好匹配tiny规格的范围(0,512),间隔为16。
![Tiny](http://www.uxiaowo.com/netty/Future/Tiny.png)
例如，当分配64字节的内存时，会从tinySubpagePools查找合适的内存页面，如果找到，会调用该页的allocation方法，尝试在该页继续分配bytebuf，如果未找到则会创建新的页，然后加入到这个链表。


**smallSubpagePools** 

用来保存为small规格分配的内存页的链表，共有4个这样的链表，保存着从1024开始到8192字节的内存页，链表数组的大小不是固定的，根据PageSize有所变化，计算公式是1024 * 2^(4-1) = PageSIze，也就是说从1024开始直到PageSize，每次乘以2，共需要几次。默认的PageSize为8192，2的13次方，1024*2的3次方=8192，因此共有4个。

![SMALL](http://www.uxiaowo.com/netty/Future/Small.png)

Arena在分配samll范围内的内存时，会从这个链表进行查找。


**PoolChunkList** 

Arena内部有6个Chunk链表，保存在ChunkList对象中；而ChunkList本身也是链表，共有6个：
* qInit：存储剩余内存0-25%的chunk
* q000：存储剩余内存1-50%的chunk
* q025：存储剩余内存25-75%的chunk
* q050：存储剩余内存50-100%个chunk
* q075：存储剩余内存75-100%个chunk
* q100：存储剩余内存100%chunk
![Tiny](http://www.uxiaowo.com/netty/Future/ChunkList.png)
当分配内存时，Arena会在chunklist查找可用的chunk，如果没有才会创建新的chunk，chunk内部也保存了页的当前使用状态。

至此，我们只是简单了解了一下Arena相关的几个数据结构，需要记住的是所有线程共享使用一个Allocator，Allocator内部保存了内存分配的相关配置信息，包含多个Arena；每个线程会固定使用一个Arena，Arena中记录了Chunk链表和Page的使用信息。这些信息对于之后的内存分配是很重要的。
