package com.shisj.netty.pool;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;

public class NettyPoolTest {
	private static  AtomicInteger idx = new AtomicInteger();
	public static void main(String[] args) {
		EventLoopGroup loop = new NioEventLoopGroup();
		EventLoop eLoop = loop.next();
		eLoop.submit(new Callable<String>() {

			@Override
			public String call() throws Exception {
				Thread.sleep(5000);
				System.out.println("Thread.sleep(5000);");
				eLoop.submit(new Callable<String>() {

					@Override
					public String call() throws Exception {
						System.out.println("Thread.sleep(12000);");
						Thread.sleep(12000);
						return null;
					}
				});
				return null;
			}
		});
		
		loop.submit(new Callable<String>() {

			@Override
			public String call() throws Exception {
				System.out.println("》》Thread.sleep(12000);");
//				Thread.sleep(12000);
				return null;
			}
		} );
	}

}
