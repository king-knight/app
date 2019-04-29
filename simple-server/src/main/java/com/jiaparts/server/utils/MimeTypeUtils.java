package com.jiaparts.server.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import com.alibaba.fastjson.JSON;
import com.jiaparts.server.SimpleServer;
import com.jiaparts.server.model.MimeType;

import io.netty.handler.codec.http.HttpHeaderValues;

public class MimeTypeUtils {
	
	private static Map<String,String>mimeMappings;
	static {
		Yaml yaml = new Yaml();
		Object obj = yaml.load(SimpleServer.class.getResourceAsStream("/mimeMapping.yml"));
		List<MimeType> types = JSON.parseArray(JSON.toJSONString(obj), MimeType.class);
		mimeMappings=new HashMap<>();
		types.forEach(type->{
			mimeMappings.put(type.getExtension(), type.getContentType());
		});
	}
	
	public static String getContentType(String fileName) {
		String contentType=HttpHeaderValues.APPLICATION_OCTET_STREAM.toString();
		String[] split = fileName.split("\\.");
		if(split==null || split.length<2) {
			return contentType;
		}
		contentType = mimeMappings.get(split[split.length-1]);
		if(StringUtils.isBlank(contentType)) {
			contentType="application/octet-stream";
		}
		return contentType;
	}

}
