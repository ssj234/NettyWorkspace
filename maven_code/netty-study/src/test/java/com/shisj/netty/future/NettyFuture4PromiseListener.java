package com.shisj.netty.future;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

public class NettyFuture4PromiseListener {

	public static void main(String[] args) {
		NettyFuture4PromiseListener test = new NettyFuture4PromiseListener();
		Promise<String> promise = test.search("Netty In Action");
		promise.addListener(new GenericFutureListener<Future<? super String>>() {
			@Override
			public void operationComplete(Future<? super String> future) throws Exception {
				System.out.println("Listener 1, make a notifice to Hong,price is " + future.get());
			}
			
		});
		promise.addListener(new GenericFutureListener<Future<? super String>>() {
			@Override
			public void operationComplete(Future<? super String> future) throws Exception {
				System.out.println("Listener 2, send a email to Hong,price is " + future.get());
			}
			
		});
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
					promise.setSuccess("$99.99");
//					promise.setFailure(new NullPointerException());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		},0,TimeUnit.SECONDS);
		
		return promise;
	}
	
}
