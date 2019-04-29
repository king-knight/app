package com.jiaparts.server.core;

import java.io.File;

import com.jiaparts.server.provide.ResourceService;
import com.jiaparts.server.provide.RestService;
import com.jiaparts.server.utils.StringUtils;

import lombok.Getter;

public class PathMathcer {
	public static ServerConfig serverConfig;
	public static RestService matcherService(String rawPath) {
		if (serverConfig == null) {
			return null;
		}
		// 优先按后缀匹配资源
		String resourcePath = getResourcePath(rawPath);
		if(StringUtils.isNotBlank(resourcePath)) {
			return new ResourceService();
		}
		// 匹配全局
		if (serverConfig.getMatchAllService() != null) {
			return serverConfig.getMatchAllService();
		}
		if(serverConfig.getUrlMapping()!=null) {
			
		}
		
		return null;
	}

	private static String getResourceBySufix(String rawPath) {
		if(!Extend.isIn(rawPath)) {
			return null;
		}
		String path=serverConfig.getResourceDir()+rawPath;
		File f=new File(path);
		if(f.exists()) {
			return path;
		}
		return null;
	}

	@Getter
	public enum Extend{
		JS(".js"),
		CSS(".css"),
		MAP(".map"),
		ICO(".ico");
		private String sufix;

		private Extend(String sufix) {
			this.sufix = sufix;
		}
		public static boolean isIn(String uri) {
			Extend[] values = Extend.values();
			for(Extend ex:values) {
				if(uri.endsWith(ex.getSufix())) {
					return true;
				}
			}
			return false;
		}
	}
	
	
	public static String getResourcePath(String rawPath) {
		if (serverConfig == null) {
			return null;
		}
		//选匹配后缀名
		String resourcePath=getResourceBySufix(rawPath);
		if(StringUtils.isNotBlank(resourcePath)) {
			return resourcePath;
		}
		String resourceMapping = serverConfig.getResourceMapping();
		String resourceDir = serverConfig.getResourceDir();
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
				boolean match = match(s1, s2);
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
			if(StringUtils.isNotBlank(relative)) {
				relative="/"+relative;
			}
			String path = resourceDir+ relative;
			return path;
		}
		// 任意一段匹配
		for (int j = 0; j <= split2.length - split1.length; j++) {
			boolean match=true;
			for(int i=0;i<split1.length;i++) {
				match = match(split1[i],split2[i+j]);
				if(!match) {
					break;
				}
			}
			if(!match) {
				continue;
			}
			String relative = "";
			for(int k=j+1;k<split2.length;k++) {
				relative+=split2[k];
				if (k != split2.length - 1) {
					relative += "/";
				}
			}
			if(StringUtils.isNotBlank(relative)) {
				relative="/"+relative;
			}
			String path = resourceDir+ relative;
			return path;
		}
		return null;
	}

	/**
	 * 
	 * @param s1 mapping split
	 * @param s2 uri split
	 * @return
	 */
	public static boolean match(String s1, String s2) {
		if (!s1.contains("*")) {
			return s1.equals(s2);
		}
		s1 = s1.replace("*", ".*");
		return s2.matches("^" + s1 + "$");
	}
}
