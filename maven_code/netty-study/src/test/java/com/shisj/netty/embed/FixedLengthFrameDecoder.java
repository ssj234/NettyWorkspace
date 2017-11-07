package com.shisj.netty.embed;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class FixedLengthFrameDecoder extends ByteToMessageDecoder {
	int fixedLength;
	public FixedLengthFrameDecoder(int fixedLength) {
		this.fixedLength = fixedLength;
	}
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		while (in.readableBytes() >= fixedLength) {
			ByteBuf buf = in.readBytes(fixedLength);
			out.add(buf);
		}
	}

}
