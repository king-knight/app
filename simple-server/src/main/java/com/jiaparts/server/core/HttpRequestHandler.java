package com.jiaparts.server.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.jiaparts.server.provide.RestRequest;
import com.jiaparts.server.provide.RestResponse;
import com.jiaparts.server.provide.RestService;
import com.jiaparts.server.utils.MimeTypeUtils;
import com.jiaparts.server.utils.StringUtils;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpRequestHandler extends ChannelInboundHandlerAdapter {
	private RestRequest restReq;
	private RestResponse restResp;
	private RestService service;
	private HttpPostRequestDecoder postDecoder;

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof DefaultHttpRequest) {
			HttpRequest req = (HttpRequest) msg;
			HttpHeaders headers = req.headers();
			StringBuilder builder=new StringBuilder();
			headers.forEach(h->{
				builder.append(h.getKey()).append(":").append(h.getValue()).append("\r\n");
			});
			log.debug("请求头:\n{}",builder.toString());
			String clientIP = headers.get("X-Forwarded-For");
			if (clientIP == null) {
				InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
				clientIP = insocket.getAddress().getHostAddress();
			}
			log.debug("client ip is:{}", clientIP);
			boolean isChunked = HttpUtil.isTransferEncodingChunked(req);
			restReq = new RestRequest();
			restReq.setChunked(isChunked);
			restReq.setHeaders(headers);
			HttpMethod method = req.method();
			log.debug("http method:{}", method.name());
			String uri = req.uri();
			uri = URLDecoder.decode(uri,"utf-8");
			QueryStringDecoder queryDecoder = new QueryStringDecoder(uri);
			String rawPath = queryDecoder.rawPath();
			restReq.setRawPath(rawPath);
			log.debug("uri:{},rawPath:{}", uri,rawPath);
			service = PathMathcer.matcherService(rawPath);
			log.debug("match rest service:{}",service.getClass().getName());
			Map<String, List<String>> parameters = queryDecoder.parameters();
			Map<String, String> restMap = new HashMap<>();
			restReq.setParameters(restMap);
			parameters.forEach((k, v) -> {
				if (v != null) {
					if(k.endsWith("[]")) {
						restMap.put(k.replace("[]", ""), JSONArray.toJSONString(v));
					}else {
						restMap.put(k, v.get(0));
					}
				}
			});
			restResp = new RestResponse();
			HttpHeaders respHeaders=new DefaultHttpHeaders(true);
			restResp.setHeaders(respHeaders);
			if(HttpUtil.isKeepAlive(req)) {
				HttpUtil.setKeepAlive(respHeaders, HttpVersion.HTTP_1_1, true);
			}
			if (method == HttpMethod.GET) {
				service.doService(restResp, restReq);
				response(ctx, restResp);
			} else {
				postDecoder = new HttpPostRequestDecoder(req);
			}
		} else if (msg instanceof HttpContent && postDecoder!=null) {
			HttpContent content = (HttpContent) msg;
			postDecoder.offer(content);
			if (msg instanceof LastHttpContent) {
				List<InterfaceHttpData> bodyHttpDatas = postDecoder.getBodyHttpDatas();
				bodyHttpDatas.forEach(data -> {
					log.debug("post body type:{}", data.getHttpDataType().name());
					if (data.getHttpDataType() == HttpDataType.Attribute) {
						Attribute ma = (Attribute) data;
						try {
							restReq.getParameters().put(ma.getName(), ma.getValue());
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				service.doService(restResp, restReq);
				postDecoder.destroy();
				response(ctx, restResp);
			}

		}

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.error("", cause);
		ctx.close();
	}

	private void response(ChannelHandlerContext ctx, RestResponse restResp) {

		if (restResp.getFile() == null) {
			byte[] body = restResp.getBody();
			
			HttpResponse response = null;
			if(body!=null) {
				response=new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
						Unpooled.wrappedBuffer(body));
			}else {
				response=new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
			}
			
			HttpHeaders headers = response.headers();
			String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
			if(StringUtils.isBlank(contentType)) {
				headers.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
			}
			String contentLength = headers.get(HttpHeaderNames.CONTENT_LENGTH);
			if(StringUtils.isBlank(contentLength)) {
				String length="0";
				if(body!=null) {
					length=body.length+"";
				}
				headers.add(HttpHeaderNames.CONTENT_LENGTH,length);
			}
			response.headers().add(restResp.getHeaders());
			ctx.writeAndFlush(response);
			return;
		}
		File file = restResp.getFile();
		RandomAccessFile rf;
		long fileLength;
		try {
			rf = new RandomAccessFile(file, "r");
			fileLength = rf.length();
			HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
			response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
			response.headers().set(HttpHeaderNames.CONTENT_TYPE, MimeTypeUtils.getContentType(file.getPath()));
			ctx.write(response);
			ChannelFuture sendFileFuture = ctx.write(new DefaultFileRegion(rf.getChannel(), 0, fileLength),
					ctx.newProgressivePromise());
			sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
				@Override
				public void operationComplete(ChannelProgressiveFuture future) throws Exception {
					log.debug("文件:{}传输完毕",file.getName());
				}

				@Override
				public void operationProgressed(ChannelProgressiveFuture future, long progress, long total)
						throws Exception {
					log.debug("文件:{}传输进度:{}/{}", file.getName(), progress, total);
				}
			});
			ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

}
