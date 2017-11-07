package com.shisj.netty.embed;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

public class FixedLengthFrameDecoderTest {

	public static void main(String[] args) {
		ByteBuf buf = Unpooled.buffer();
		for(int i = 0; i < 9; i++) {
			buf.writeByte(i);
		}
		
		ByteBuf input = buf.duplicate();
		EmbeddedChannel channel = new EmbeddedChannel(new FixedLengthFrameDecoder(3));
		
		channel.writeInbound(input);
		channel.finish();
		
		System.out.println((channel.readInbound()).getClass());
		System.out.println(channel.readInbound().toString());
		System.out.println(channel.readInbound().toString());
	}
}
