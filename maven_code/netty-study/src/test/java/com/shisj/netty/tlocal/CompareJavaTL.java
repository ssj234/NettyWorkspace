package com.shisj.netty.tlocal;

public class CompareJavaTL {
	static int count = 100000000;
	static int times = 100000000;
	public static void main(String[] args) {
		ThreadLocal<String> tl = new ThreadLocal<String>();
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
}
