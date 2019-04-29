package com.jiaparts.server.provide;

import java.io.File;

import com.jiaparts.server.core.PathMathcer;

public class ResourceService implements RestService {

	@Override
	public void doService(RestResponse restResp, RestRequest restReq) {
		String resourcePath = PathMathcer.getResourcePath(restReq.getRawPath());
		File f=new File(resourcePath);
		restResp.setFile(f);
	}

}
