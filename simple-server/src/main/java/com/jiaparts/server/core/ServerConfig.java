package com.jiaparts.server.core;

import java.util.HashMap;
import java.util.Map;

import com.jiaparts.server.provide.RestService;
import com.jiaparts.server.utils.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServerConfig {
	private String contextPath;
	private String resourceDir;
	private String resourceMapping;
	private Map<String,RestService>urlMapping;
	private RestService matchAllService;
	public ServerConfig registerMapping(String urlPattern,RestService service) {
		if(StringUtils.isBlank(urlPattern)||service==null) {
			throw new RuntimeException("url mapping is not acceptable");
		}
		if(urlPattern.startsWith("/*")) {
			matchAllService=service;
			return this;
		}
		if(urlMapping==null) {
			urlMapping=new HashMap<>();
		}
		urlMapping.put(urlPattern, service);
		return this;
	}
}
