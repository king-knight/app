package com.personal.server;

import com.personal.server.core.HttpRequestHandler;
import com.personal.server.core.ServerContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class LightServer {
	private ServerBootstrap bootstrap;
	
	private EventLoopGroup accept;
	
	private EventLoopGroup work;
	private ChannelFuture channelFuture;
	private ServerContext context;
	
	private void init() {
		bootstrap=new ServerBootstrap();
		accept=new NioEventLoopGroup();
		work=new NioEventLoopGroup();
		bootstrap.group(accept, work)
		.channel(NioServerSocketChannel.class)
		.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
		.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ch.pipeline()
				.addLast(new HttpServerCodec())
				.addLast(new ChunkedWriteHandler())
				.addLast(new HttpRequestHandler(context));
			}
			
		});
	}
	
	public LightServer(ServerContext context) {
		this.context=context;
		init();
	}

	public void start() throws InterruptedException {
		int port = context.getPort();
		if(port==0) {
			port=8080;
		}
		channelFuture = bootstrap.bind(port).sync();
		
		log.info("server run success...");
	}

	public void stop() {
		accept.shutdownGracefully();
		 work.shutdownGracefully();
	}
	public static void main(String[] args) {
		
	}
}
