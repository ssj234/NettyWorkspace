
## 7.3.1 read

在与客户端建立连接时，会创建客户端通道NioSocKetChannel，并注册到`子事件循环`中，由其不断select()查询就绪的I/O，然后再针对I/O进行处理。处理读操作的是NioSocketChannel的unsafe，由NioSocketChannelUnsafe实现，read()在AbstractNioByteChannel$NioByteUnsafe中。其主要的过程比较简单：

1. 子Channel的创建过程在，其ChannelConfig会创建ByteBufAllocator，用于分配内存，默认的分配器为PooledByteBufAllocator；内存分配的过程比较复杂，在内存管理一节介绍；RecvByteBufAllocator用于计算缓冲区的大小；通过这两个对象，可以获取缓冲区byteBuf = allocHandle.allocate(allocator);
2. doReadBytes(byteBuf)中，每次从javaChannel中读取数据；之后通知pipeline触发fireChannelRead(byteBuf)；结束后判断是否还有未读取的数据，如果有则继续循环读取。
3. 最后，allocHandle.readComplete();重新计算缓冲区大小并通知pipeline触发fireChannelReadComplete();
```
do {
    byteBuf = allocHandle.allocate(allocator);
    allocHandle.lastBytesRead(doReadBytes(byteBuf));
    if (allocHandle.lastBytesRead() <= 0) {
        byteBuf.release();
        byteBuf = null;
        close = allocHandle.lastBytesRead() < 0;
        break;
    }

    allocHandle.incMessagesRead(1);
    readPending = false;
    pipeline.fireChannelRead(byteBuf);
    byteBuf = null;
} while (allocHandle.continueReading());

allocHandle.readComplete();
pipeline.fireChannelReadComplete();

if (close) {
    closeOnRead(pipeline);
}
```


## 7.3.2 write

在Netty使用过程中，一般会在业务逻辑的ChannelInboundHandlerAdapter的channelRead中，调用ctx.write();和ctx.flush()向客户端发送数据。写入的逻辑比较简单：

1. 查找下一个Outbound的Handler，中间可能有一些业务逻辑Handler，最终处理write的是HeadContext，如果需要flush则调用invokeWriteAndFlush，否则调用invokeWrite
2. invokeWrite会调用unsafe的write(msg, promise); 将其加入到outboundBuffer中，这里不会flush通过网络发送
3. flush()方法，调用unsafe的flush();最终通过javaChannel写入网络
