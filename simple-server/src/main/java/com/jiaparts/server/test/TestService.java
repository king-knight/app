package com.jiaparts.server.test;

import java.util.Map;

import com.jiaparts.server.provide.RestRequest;
import com.jiaparts.server.provide.RestResponse;
import com.jiaparts.server.provide.RestService;

public class TestService implements RestService{

	@Override
	public void doService(RestResponse restResp, RestRequest restReq) {
		Map<String, String> parameters = restReq.getParameters();
		System.err.println("入参:");
		parameters.forEach((k,v)->{
			System.err.println(k+":"+v);
		});
		String resp="abcdeeff";
		byte[] bytes = resp.getBytes();
		restResp.setBody(bytes);
	}

}
