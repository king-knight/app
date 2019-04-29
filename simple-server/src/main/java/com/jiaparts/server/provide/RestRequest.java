package com.jiaparts.server.provide;

import java.io.Serializable;
import java.util.Map;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class RestRequest implements Serializable {

	private static final long serialVersionUID = -3248975509945042001L;
	private boolean isChunked;
	private String uri;
	private String rawPath;
	private HttpHeaders headers;
	private HttpMethod httpMethod;
	private Map<String, String> parameters;
	
}
