package com.shisj.netty.future;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class JavaFutureMThread {

	public static void main(String[] args) throws Exception {
		JavaFutureMThread jf = new JavaFutureMThread();
		final Future<String> future = jf.search("Netty权威指南");
		System.out.println("Begin search,get future!");
		
		for(int i = 0 ; i < 10 ; i++)
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					String prods = future.get();//获取prods
					System.out.println(Thread.currentThread().getName()+" " + prods);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		},"Thread-"+i).start();
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

