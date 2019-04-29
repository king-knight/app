package com.jiaparts.server.model;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MimeType implements Serializable{
	private static final long serialVersionUID = 2163222757851221341L;
	private String extension;
	private String contentType;
}
