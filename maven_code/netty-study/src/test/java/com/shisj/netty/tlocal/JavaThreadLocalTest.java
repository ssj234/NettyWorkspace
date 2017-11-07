package com.shisj.netty.tlocal;

public class JavaThreadLocalTest {

	public static void main(String[] args) {
		
		for(int i = 0 ; i< 16 ; i++) {
			ThreadLocal<String> local1 = new ThreadLocal<String>();
			
			local1.set("name");
		}
		
		while(true) {
			ThreadLocal<String> local2 = new ThreadLocal<String>();
			local2.set("223");
		}
	}
	
}
