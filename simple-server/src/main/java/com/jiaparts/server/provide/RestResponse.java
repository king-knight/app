package com.jiaparts.server.provide;

import java.io.File;
import java.io.Serializable;

import io.netty.handler.codec.http.HttpHeaders;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class RestResponse implements Serializable {
	private static final long serialVersionUID = -4645557040453341046L;
	private byte[]body;
	private File file;
	private HttpHeaders headers;
}
