package com.shisj.netty.memeory;

import java.nio.ByteBuffer;

/**
 * 测试一下DirectByteBuffer的duplicate()
 * 向copy的ByteBuffer写入数据
 * 原来的ByteBuffer也改变了
 * @author shisj
 *
 */
public class DirectByteBufferTest {

	public static void main(String[] args) {
		ByteBuffer buff = ByteBuffer.allocateDirect(1024); // 分配一块直接内存
		ByteBuffer buffCopy = buff.duplicate(); // 复杂
		buffCopy.put("11111".getBytes()); // 向复制的写入数据
		
		byte b[] = new byte[5];
		buff.get(b);// 原来的ByteBuffer获取数据
		System.out.println(new String(b)); // 输出为11111
	}
}
