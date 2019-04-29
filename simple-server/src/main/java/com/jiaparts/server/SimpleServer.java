package com.jiaparts.server;

import com.jiaparts.server.core.HttpRequestHandler;
import com.jiaparts.server.core.PathMathcer;
import com.jiaparts.server.core.ServerConfig;

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
public class SimpleServer {

	private ServerBootstrap bootstrap;
	
	private EventLoopGroup accept;
	
	private EventLoopGroup work;
	
	private ServerConfig serverConfig;
	private ChannelFuture channelFuture;
	private int port;
	
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
				.addLast(new HttpRequestHandler());
			}
			
		});
	}

	public SimpleServer() {
		init();
	}

	public void start() throws InterruptedException {
		PathMathcer.serverConfig=this.serverConfig;
		if(port==0) {
			port=8080;
		}
		channelFuture = bootstrap.bind(port).sync();
		log.info("server run success...");
		//f.channel().closeFuture().sync();
	}

	public void stop() {
		accept.shutdownGracefully();
		 work.shutdownGracefully();
	}
	
	public static void main(String[] args) throws InterruptedException {
		SimpleServer server=new SimpleServer();
		server.setPort(8080);
		ServerConfig config=new ServerConfig();
		config.setContextPath("pro");
		config.setResourceMapping("static");
		config.setResourceDir("D:\\HbuilderPros\\dev-tools\\dist");
		server.setServerConfig(config);
		server.start();
	}
}
