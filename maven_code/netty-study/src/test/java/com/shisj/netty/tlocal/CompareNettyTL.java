package com.shisj.netty.tlocal;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;

public class CompareNettyTL {
	static int count = 100000000;
	static int times = 100000000;
	public static void main(String[] args) {
		
		new FastThreadLocalThread(new Runnable() {
			
			@Override
			public void run() {
				FastThreadLocal<String> tl = new FastThreadLocal<String>();
				long begin = System.currentTimeMillis();
				for(int i=0;i<count;i++) {
					tl.set("javatl");
				}
				System.out.println(System.currentTimeMillis()-begin);
				begin = System.currentTimeMillis();
				for(int i=0;i<times;i++) {
					tl.get();
				}
				System.out.println(System.currentTimeMillis()-begin);
			}
		}).start();
	}
}
