package com.shisj.netty.memeory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator.Handle;
import sun.misc.Unsafe;

public class JustTest {
	
  public static void main(String[] args) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
	  Field f = Unsafe.class.getDeclaredField("theUnsafe"); //Internal reference  
	  f.setAccessible(true);  
	  Unsafe unsafe = (Unsafe) f.get(null);  
	  System.out.println(Integer.toBinaryString(8192));
	  System.out.println(Integer.SIZE - 1 - Integer.numberOfLeadingZeros(8192));
	  System.out.println( 512 >>> 4);
	  System.out.println(normalizeCapacity(1205));
	  System.out.println(1024>>>10);
	  
	  for(int normCapacity=1 ; normCapacity< 512 ;normCapacity ++) {
		  System.out.print( normCapacity+" "+(normCapacity >>> 4) +"| ");
	  }
  }
  
  static int normalizeCapacity(int reqCapacity) {
      if (reqCapacity < 0) {
          throw new IllegalArgumentException("capacity: " + reqCapacity + " (expected: 0+)");
      }
      if (reqCapacity >= 232323232) {
          return reqCapacity;
      }

      if (reqCapacity >=512) { // >= 512
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
