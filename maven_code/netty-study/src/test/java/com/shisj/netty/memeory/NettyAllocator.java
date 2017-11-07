package com.shisj.netty.memeory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator.Handle;

public class NettyAllocator {

	public static void main(String[] args) {
		System.out.println((int) (((long) Integer.MAX_VALUE + 1) / 2));
		// 分配内存
		/**/PooledByteBufAllocator allocator = new PooledByteBufAllocator();
//		AdaptiveRecvByteBufAllocator all = new AdaptiveRecvByteBufAllocator(64,16777516,26777516);
		AdaptiveRecvByteBufAllocator all = new AdaptiveRecvByteBufAllocator(64,240,26777516);
		Handle handle = all.newHandle();
		ByteBuf buf = handle.allocate(allocator);
		buf.writeBytes("ABCDE".getBytes()); // 查看如何写到正确位置
		buf.release();
//		System.out.println(buf.memoryAddress());
//		(()buf).deallocate();
		buf = handle.allocate(allocator);
//		System.out.println(buf.memoryAddress());
		buf = handle.allocate(allocator);
//		System.out.println(buf.memoryAddress());
		
//		validateAndCalculateChunkSize(8192, 5);
	}
	
	
	private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
		int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i --) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        System.out.println(chunkSize/pageSize);
        System.out.println(Math.pow(2, 12));
        return chunkSize;
    }
}
