package com.personal.server.utils;

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
