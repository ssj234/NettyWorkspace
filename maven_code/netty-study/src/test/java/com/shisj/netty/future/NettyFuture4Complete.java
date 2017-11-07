package com.shisj.netty.future;

import java.util.concurrent.ExecutionException;

import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.SucceededFuture;

public class NettyFuture4Complete {

	public static void main(String[] args) {
		NettyFuture4Complete test = new NettyFuture4Complete();
		Future<String> future = test.search("Netty");
		try {
			System.out.println(future.get());
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	
	public Future<String> search(String prod) {
		NioEventLoopGroup loop = new NioEventLoopGroup();
//		SucceededFuture<String> future = new SucceededFuture<String>(loop.next(),"name");
		FailedFuture<String> future = new FailedFuture<String>(loop.next(),new NullPointerException("name"));
		return future;
		
	}
}
