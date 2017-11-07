package com.shisj.netty.memeory;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;

public class RecyclerTest {
	Handle<MObject> handle;
	public static Recycler<MObject> RECYCLER = new Recycler<MObject>() {
	    @Override
	    protected MObject newObject(Handle<MObject> handle) {
	        return new MObject(handle);
	    }
	};
	
	public static void main(String[] args) {
		MObject obj1 = RECYCLER.get();// 获取对象
		System.out.println(obj1);
		MObject obj2 = RECYCLER.get(); // 再次获取
		System.out.println(obj2);
		
		obj1.free();
		obj2.free();
		System.out.println(RECYCLER.get());//
		System.out.println(RECYCLER.get());//
		
	}
	
}

class MObject{
	int beginIndex;
	int endIndex;
	public Handle<MObject> handle;
	public MObject(Handle<MObject> handle) {
		this.handle = handle;
	}
	public int getBeginIndex() {
		return beginIndex;
	}
	public void setBeginIndex(int beginIndex) {
		this.beginIndex = beginIndex;
	}
	public int getEndIndex() {
		return endIndex;
	}
	public void setEndIndex(int endIndex) {
		this.endIndex = endIndex;
	}
	public void free() {
		handle.recycle(this);
	}
	
	@Override
	public String toString() {
		return String.format("[%d]%d-%d", this.hashCode(),beginIndex,endIndex);
	}
	
}