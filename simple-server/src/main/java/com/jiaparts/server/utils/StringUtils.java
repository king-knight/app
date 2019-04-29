package com.jiaparts.server.utils;

public class StringUtils {
	public static boolean isBlank(String s) {
		if(s!=null && !s.trim().equals("")) {
			return false;
		}
		return true;
	}
	public static boolean isNotBlank(String s) {
		return !isBlank(s);
	}
}
