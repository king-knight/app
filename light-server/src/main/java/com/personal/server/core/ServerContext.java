package com.personal.server.core;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServerContext {
	private int port;
	/**
	 * 待扫描业务包
	 */
	private String scanPackage;
	private String contextPath;
	private String resourceDir;
	private String resourceMapping;
}
