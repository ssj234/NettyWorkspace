package com.shisj.netty.memeory;

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator.Handle;

public class AdaptiveTest {

	public static void main(String[] args) {
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
	}
}
