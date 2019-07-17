package com.personal.server.core;

import java.lang.reflect.Method;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class MethodInvoker {
	private Method method;
	private Object service;
	
	public Object invok(Object... args) throws Exception {
		
		return method.invoke(service, args);
	}

	public String getFullMethodName() {
		return service.getClass().getName()+"."+method.getName();
	}
}
