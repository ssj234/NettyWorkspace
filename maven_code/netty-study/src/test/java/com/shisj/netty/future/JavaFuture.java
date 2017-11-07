package com.shisj.netty.future;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class JavaFuture {

	public static void main(String[] args) throws Exception {
		JavaFuture jf = new JavaFuture();
		Future<String> future = jf.search("Netty权威指南");
		System.out.println("Begin search,get future!");
		String prods = future.get();//获取prods
		System.out.println("get result:"+prods);
		
//		Thread.sleep(1000);
//		future.cancel(false);//true时会中断线程，false不会
//		System.out.println("Future is canceled? " + (future.isCancelled()?"yes":"no"));
		
		Thread.sleep(4000);
	}
	
	/**
	 * 在Internet上查找商品,这是一个耗时任务，假设为5s
	 */
	public Future<String> search(final String prodName) {
		FutureTask<String> future = new FutureTask<String>(new Callable<String>() {
			@Override
			public String call()  {
				try {
				System.out.println(String.format("	>>search price of %s from internet!",prodName));
				Thread.sleep(3000);
				return "$99.99";
				}catch(InterruptedException e){
					System.out.println("search function is Interrupted!");
				}
				return null;
			}
		});
		new Thread(future).start();
		return future;
	}
}

