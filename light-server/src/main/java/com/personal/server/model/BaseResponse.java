package com.personal.server.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class BaseResponse implements Serializable{

	private static final long serialVersionUID = 1412790282683340928L;
	private int code;
	private String retMsg;
	private Object result;

}
