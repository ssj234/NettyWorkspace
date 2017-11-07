package com.shisj.netty.future;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class NettyFuture4Promise {

	public static void main(String[] args) {
		NettyFuture4Promise test = new NettyFuture4Promise();
		Promise<String> promise = test.search("Netty In Action");
		System.out.println("Begin search,get future!");
		try {
			String result = promise.get();
			System.out.println("price is " + result);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	private Promise<String> search(final String prod) {
		NioEventLoopGroup loop = new NioEventLoopGroup();
		final DefaultPromise<String> promise = new DefaultPromise<String>(loop.next());
		loop.schedule(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println(String.format("	>>search price of %s from internet!",prod));
					Thread.sleep(5000);
//					promise.setSuccess("$99.99");
					promise.setFailure(new NullPointerException());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		},0,TimeUnit.SECONDS);
		
		return promise;
	}
	
}
