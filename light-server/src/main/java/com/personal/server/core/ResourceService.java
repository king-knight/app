package com.personal.server.core;

import java.io.File;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class ResourceService implements RestService {
	private Map<String,String>srcMap;
	
	private static RestService service;
	
	public static RestService newInstance(Map<String,String>srcMap) {
		if(service==null) {
			service=new ResourceService(srcMap);
		}
		return service;
	}
	

	@Override
	public void doService(RestResponse restResp, RestRequest restReq) {
		String resourcePath =srcMap.get(restReq.getRawPath());
		File f=new File(resourcePath);
		restResp.setFile(f);
	}




	private ResourceService(Map<String, String> srcMap) {
		this.srcMap = srcMap;
	}

}
