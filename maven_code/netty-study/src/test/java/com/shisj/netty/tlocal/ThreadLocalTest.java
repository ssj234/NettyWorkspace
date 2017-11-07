package com.shisj.netty.tlocal;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.InternalThreadLocalMap;

public class ThreadLocalTest {

	public static void main(String[] args) throws InterruptedException {
		
		
		FastThreadLocalThread a=new FastThreadLocalThread(new Runnable() {
			
			@Override
			public void run() {
				for(int i=0 ; i<20 ;i++) {
					FastThreadLocal local = new FastThreadLocal<Object>();
					set(local);
				}
				FastThreadLocal local = new FastThreadLocal<Object>();
				set(local);
				System.out.println(111);
			}
		});
		a.start();
//		System.out.println(InternalThreadLocalMap.nextVariableIndex());
		a.join();
		new FastThreadLocalThread(new Runnable() {
			
			@Override
			public void run() {
				for(int i=0 ; i<20 ;i++) {
					FastThreadLocal local = new FastThreadLocal<Object>();
					set(local);
				}
				FastThreadLocal local = new FastThreadLocal<Object>();
				set(local);
				System.out.println(111);
			}
		}).start();
		
		System.out.println(FastThreadLocal.size());
	}
	
	public static void set(FastThreadLocal local) {
		local.set("aaa");
	}
	
	public static int normalizedCapacity(int reqCapacity) {
        if (reqCapacity < 0) {
            throw new IllegalArgumentException("capacity: " + reqCapacity + " (expected: 0+)");
        }
        if (reqCapacity >= 2048 * 1024 * 8) {
            return reqCapacity;
        }

        if (reqCapacity > 512) { // >= 512
            // Doubled

            int normalizedCapacity = reqCapacity;
            normalizedCapacity --;
            normalizedCapacity |= normalizedCapacity >>>  1;
            normalizedCapacity |= normalizedCapacity >>>  2;
            normalizedCapacity |= normalizedCapacity >>>  4;
            normalizedCapacity |= normalizedCapacity >>>  8;
            normalizedCapacity |= normalizedCapacity >>> 16;
            normalizedCapacity ++;

            if (normalizedCapacity < 0) {
                normalizedCapacity >>>= 1;
            }

            return normalizedCapacity;
        }

        // Quantum-spaced
        if ((reqCapacity & 15) == 0) {
            return reqCapacity;
        }

        return (reqCapacity & ~15) + 16;
    }
}

class FastThread extends FastThreadLocalThread{
	
	@Override
	public void run() {
//		threadLocalMap().setIndexedVariable(index, value);
	}
	
}
