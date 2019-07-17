package com.personal.server.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.personal.server.utils.MimeTypeUtils;
import com.personal.server.utils.StringUtils;

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
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpRequestHandler extends ChannelInboundHandlerAdapter {
	private RestRequest restReq;
	private RestResponse restResp;
	private RestService service;
	private HttpPostRequestDecoder postDecoder;
	private ServerContext context;
	private static Map<String,String>srcMap;
	public HttpRequestHandler(ServerContext context) {
		this.context=context;
		if(srcMap==null) {
			srcMap=new HashMap<>();
		}
	}

	private RestService matchService(String rawPath) {
		String resourcePath = srcMap.get(rawPath);
		if(StringUtils.isBlank(resourcePath)) {
			resourcePath = getStaticPath(rawPath);
		}
		if(StringUtils.isNotBlank(resourcePath)) {
			srcMap.put(rawPath, resourcePath);
			return ResourceService.newInstance(srcMap);
		}
		return DisparcherController.newInstance(context.getScanPackage());
	}

	private String getStaticPath(String rawPath) {
		// 先匹配后缀名
		if (Extend.isIn(rawPath)) {
			String path = context.getResourceDir() + removeContext(context.getContextPath(), rawPath);
			File f = new File(path);
			if (f.exists()) {
				return path;
			}
		}
		// 再匹配资源
		String resourceMapping = context.getResourceMapping();
		String resourceDir = context.getResourceDir();
		if (StringUtils.isBlank(resourceMapping) || StringUtils.isBlank(resourceDir)) {
			return null;
		}
		String[] split1 = resourceMapping.split("/");
		String[] split2 = rawPath.split("/");
		if (split1.length == 0 || split2.length == 0 || split2.length < split1.length) {
			return null;
		}
		if (resourceMapping.startsWith("/")) {// 从头匹配
			for (int i = 0; i < split1.length; i++) {
				String s1 = split1[i];
				String s2 = split2[i];
				boolean match = StringUtils.match(s1, s2);
				if (!match) {
					return null;
				}
			}
			String relative = "";
			for (int i = split1.length; i < split2.length; i++) {
				relative += split2[i];
				if (i != split2.length - 1) {
					relative += "/";
				}
			}
			if (StringUtils.isNotBlank(relative)) {
				relative = "/" + relative;
			}
			String path = resourceDir + relative;
			log.debug("uri:{}匹配到静态文件：{}", rawPath, path);
			return path;
		}
		// 任意一段匹配
		for (int j = 0; j <= split2.length - split1.length; j++) {
			boolean match = true;
			for (int i = 0; i < split1.length; i++) {
				match = StringUtils.match(split1[i], split2[i + j]);
				if (!match) {
					break;
				}
			}
			if (!match) {
				continue;
			}
			String relative = "";
			for (int k = j + 1; k < split2.length; k++) {
				relative += split2[k];
				if (k != split2.length - 1) {
					relative += "/";
				}
			}
			if (StringUtils.isNotBlank(relative)) {
				relative = "/" + relative;
			}
			String path = resourceDir + relative;
			log.debug("uri:{}匹配到静态文件：{}", rawPath, path);
			return path;
		}
		return null;
	}

	private String removeContext(String contextPath, String rawPath) {
		if (StringUtils.isBlank(contextPath)) {
			return rawPath;
		}
		return rawPath.substring(("/" + contextPath + "/").length() - 1);
	}

	@Getter
	private enum Extend {
		JS(".js"), CSS(".css"), MAP(".map"), ICO(".ico");
		private String sufix;

		private Extend(String sufix) {
			this.sufix = sufix;
		}

		public static boolean isIn(String uri) {
			Extend[] values = Extend.values();
			for (Extend ex : values) {
				if (uri.endsWith(ex.getSufix())) {
					return true;
				}
			}
			return false;
		}
	}
	
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof DefaultHttpRequest) {
			HttpRequest req = (HttpRequest) msg;
			HttpHeaders headers = req.headers();
			StringBuilder builder = new StringBuilder();
			headers.forEach(h -> {
				builder.append(h.getKey()).append(":").append(h.getValue()).append("\r\n");
			});
			log.debug("请求头:\n{}", builder.toString());
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
			uri = URLDecoder.decode(uri, "utf-8");
			QueryStringDecoder queryDecoder = new QueryStringDecoder(uri);
			String rawPath = queryDecoder.rawPath();
			restReq.setRawPath(rawPath);
			log.debug("uri:{},rawPath:{}", uri, rawPath);
			service =matchService(rawPath);
			log.debug("match rest service:{}", service.getClass().getName());
			Map<String, List<String>> parameters = queryDecoder.parameters();
			Map<String, String> restMap = new HashMap<>();
			restReq.setParameters(restMap);
			parameters.forEach((k, v) -> {
				if (v != null) {
					if (k.endsWith("[]")) {
						restMap.put(k.replace("[]", ""), JSONArray.toJSONString(v));
					} else {
						restMap.put(k, v.get(0));
					}
				}
			});
			restResp = new RestResponse();
			HttpHeaders respHeaders = new DefaultHttpHeaders(true);
			restResp.setHeaders(respHeaders);
			if (HttpUtil.isKeepAlive(req)) {
				HttpUtil.setKeepAlive(respHeaders, HttpVersion.HTTP_1_1, true);
			}
			if (method == HttpMethod.GET) {
				service.doService(restResp, restReq);
				response(ctx, restResp);
			} else {
				postDecoder = new HttpPostRequestDecoder(req);
			}
		} else if (msg instanceof HttpContent && postDecoder != null) {
			HttpContent content = (HttpContent) msg;
			postDecoder.offer(content);
			if (msg instanceof LastHttpContent) {
				List<InterfaceHttpData> bodyHttpDatas = postDecoder.getBodyHttpDatas();
				bodyHttpDatas.forEach(data -> {
					log.debug("post body type:{}", data.getHttpDataType().name());
					try {
						HttpDataType httpDataType = data.getHttpDataType();
						switch (httpDataType) {
						case Attribute:
							Attribute ma = (Attribute) data;
							restReq.getParameters().put(ma.getName(), ma.getValue());
							log.debug("解析body参数:key:{},value:{}",ma.getName(),ma.getValue());
							break;
						case FileUpload:
							FileUpload fu=(FileUpload) data;
							Map<String, FileUpload[]> files = restReq.getFiles();
							if(files==null) {
								files=new HashMap<>();
								restReq.setFiles(files);
							}
							files.put(fu.getName(), new FileUpload[]{fu});
							break;
						case InternalAttribute:
							System.err.println("InternalAttribute");
							break;
						default :
							break;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}

				});
				service.doService(restResp, restReq);
				postDecoder = null;
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
			if (body != null) {
				response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
						Unpooled.wrappedBuffer(body));
			} else {
				response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
			}

			HttpHeaders headers = response.headers();
			String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
			if (StringUtils.isBlank(contentType)) {
				headers.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
			}
			String contentLength = headers.get(HttpHeaderNames.CONTENT_LENGTH);
			if (StringUtils.isBlank(contentLength)) {
				String length = "0";
				if (body != null) {
					length = body.length + "";
				}
				headers.add(HttpHeaderNames.CONTENT_LENGTH, length);
			}
			headers.add(HttpHeaderNames.ACCEPT_ENCODING, "utf-8");
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
					log.debug("文件:{}传输完毕", file.getName());
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
