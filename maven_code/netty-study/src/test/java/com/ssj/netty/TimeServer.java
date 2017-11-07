package com.ssj.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LoggingHandler;

public class TimeServer {

	
	public void bind(int port) throws Exception{
		//创建两个线程组,专门用于网络事件的处理，Reactor线程组
		//一个用来接收客户端的连接，
		//一个用来进行SocketChannel的网络读写
		EventLoopGroup bossGroup=new NioEventLoopGroup();
		EventLoopGroup workGroup=new NioEventLoopGroup();
		
		try{
			//辅助启动类
			ServerBootstrap b=new ServerBootstrap();
			b.group(bossGroup,workGroup)
				.channel(NioServerSocketChannel.class)//创建的channel为NioServerSocketChannel【nio-ServerSocketChannel】
				.option(ChannelOption.SO_BACKLOG, 1024)
//				.handler(new ChildChannelHandler())
				.childOption(ChannelOption.SO_KEEPALIVE, true) //配置accepted的channel
				.childHandler(new ChildChannelHandler());//处理IO事件的处理类，处理网络事件

			ChannelFuture f=b.bind(port).sync();//绑定端口后同步等待
			System.out.println(1111111111);
			f.channel().closeFuture().sync();//阻塞
			System.out.println(222222);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			bossGroup.shutdownGracefully();
			workGroup.shutdownGracefully();
		}
	}
	
	
	public static void main(String[] args) {
		try {
			new TimeServer().bind(9898);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}

 class ChildChannelHandler extends ChannelInitializer<SocketChannel> {

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		System.out.println(11);// 来请求时初始化调用
		ch.pipeline().addLast(new LoggingHandler())
						.addLast(new StringEncoder())
						.addLast(new StringDecoder())
					.addLast(new TimeServerHandler());
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("1111");
	}
}