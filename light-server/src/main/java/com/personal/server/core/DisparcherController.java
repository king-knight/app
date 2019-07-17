package com.personal.server.core;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.alibaba.fastjson.JSON;
import com.personal.server.annotation.RequestMapping;
import com.personal.server.annotation.Service;
import com.personal.server.model.BaseResponse;
import com.personal.server.utils.StringUtils;

import io.netty.handler.codec.http.multipart.FileUpload;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisparcherController implements RestService {
	private Map<String, MethodInvoker> registerMappings;

	private Map<String, MethodInvoker> cachedMappings;
	
	private static RestService service;
	
	public static RestService newInstance(String scanPackage) {
		if(service==null) {
			try {
				service=new DisparcherController(scanPackage);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return service;
	}

	private DisparcherController(String scanPackage) throws Exception {
		URL url = this.getClass().getClassLoader().getResource(scanPackage.replace(".", "/"));
		File f = new File(url.getPath());
		registerMappings = new HashMap<>();
		cachedMappings = new HashMap<>();
		String protocol = url.getProtocol();
		if ("file".equals(protocol)) {
			File[] listFiles = f.listFiles();
			for (File lf : listFiles) {
				registerMappingMethodByFile(lf, scanPackage);
			}
		} else if ("jar".equals(protocol)) {
			JarFile jf = ((JarURLConnection) url.openConnection()).getJarFile();
			registerByJar(jf, scanPackage);
		}

	}

	private void registerByJar(JarFile jf, String scanPackage) throws Exception {
		Enumeration<JarEntry> entries = jf.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			if (entry.isDirectory()) {
				continue;
			}
			String name = entry.getName();
			String jarPackage = name.replace("/", ".");
			if (!jarPackage.startsWith(scanPackage)) {
				continue;
			}
			if (!jarPackage.endsWith(".class")) {
				continue;
			}
			String fullClassName = jarPackage.replace(".class", "");
			registerMethod(fullClassName);

		}
	}

	private void registerMappingMethodByFile(File f, String scanPackage) throws Exception {
		if (!f.exists()) {
			return;
		}
		String name = f.getName();
		if (f.isFile()) {
			name = name.replace(".class", "");
			String className = scanPackage + "." + name;
			registerMethod(className);
		} else if (f.isDirectory()) {
			File[] listFiles = f.listFiles();
			if (listFiles == null || listFiles.length == 0) {
				return;
			}
			scanPackage += "." + name;
			for (File file : listFiles) {
				registerMappingMethodByFile(file, scanPackage);
			}
		}
	}

	private void registerMethod(String className) throws Exception {
		Class<?> clazz = Class.forName(className);
		if (clazz.isInterface()) {
			return;
		}
		Service annotation = clazz.getAnnotation(Service.class);
		if (annotation == null) {
			return;
		}
		Method[] methods = clazz.getMethods();
		if (methods == null || methods.length == 0) {
			return;
		}
		Object service = clazz.newInstance();
		for (Method m : methods) {
			RequestMapping ma = m.getAnnotation(RequestMapping.class);
			if (ma == null) {
				continue;
			}
			String mapping = ma.value();
			if (registerMappings.containsKey(mapping)) {
				throw new RuntimeException("映射重复:" + mapping);
			}
			MethodInvoker invoker = new MethodInvoker();
			invoker.setMethod(m);
			invoker.setService(service);
			registerMappings.put(mapping, invoker);
			log.debug("register mapping:{},{}", mapping, invoker.getFullMethodName());
		}
	}

	@Override
	public void doService(RestResponse restResp, RestRequest restReq) {
		try {
			String rawPath = restReq.getRawPath();
			MethodInvoker invoker = null;
			invoker = cachedMappings.get(rawPath);
			if (invoker == null) {
				invoker = dispach(rawPath);
			}
			log.debug("disparch to " + invoker.getService().getClass().getName() + "." + invoker.getMethod().getName());
			Object resp = null;
			// 解析参数
			int parameterCount = invoker.getMethod().getParameterCount();
			Object[] params = null;
			if (parameterCount <= 0) {
				params = null;
			} else {
				params = resolveParams(invoker, restReq);
				if (params == null) {
					params = new Object[parameterCount];
				}
			}

			resp = invoker.invok(params);
			if (resp instanceof RestResponse) {
				RestResponse rresp = (RestResponse) resp;
				restResp.setBody(rresp.getBody());
				restResp.setFile(rresp.getFile());
				restResp.setHeaders(rresp.getHeaders());
				return;
			}
			byte[] body = wrapResponse(resp);
			restResp.setBody(body);
			// restResp.setBody(JSON.toJSONString(resp).getBytes("utf-8"));
		} catch (Exception e) {
			e.printStackTrace();
			BaseResponse br = new BaseResponse();
			br.setCode(501);
			br.setRetMsg("系统错误");
			byte[] bresp = writeAsJsonByte(br);
			restResp.setBody(bresp);
		}

	}

	private byte[] wrapResponse(Object resp) throws UnsupportedEncodingException {
		BaseResponse br = new BaseResponse();
		br.setCode(200);
		br.setRetMsg("处理成功");
		if (!(resp instanceof Void)) {
			br.setResult(resp);
		}
		return JSON.toJSONString(br).getBytes("utf-8");
	}

	private byte[] writeAsJsonByte(Object obj) {
		try {
			return JSON.toJSONString(obj).getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Object[] resolveParams(MethodInvoker invoker, RestRequest restReq) {
		if (restReq.getParameters().isEmpty() && restReq.getFiles()==null) {
			return null;
		}
		Method method = invoker.getMethod();
		int parameterCount = method.getParameterCount();
		Object[] params = new Object[parameterCount];
		for (int i = 1; i <= parameterCount; i++) {
			Object param = resolveParam(method.getParameters()[i - 1], method.getParameterTypes()[i - 1], restReq);
			params[i - 1] = param;
		}
		return params;
	}

	private Object resolveParam(Parameter parameter, Class<?> type, RestRequest restReq) {
		String name = parameter.getName();
		if (FileUpload.class.isAssignableFrom(type)) {
			return Optional.of(restReq.getFiles()).map(map->map.get(name)).map(f->f[0]).orElse(null);
		}
		String value = restReq.getParameters().get(name);
		if (StringUtils.isBlank(value)) {
			if (type == String.class) {
				return value;
			} else {
				return null;
			}
		}
		if (type == String.class) {
			return value;
		}
		if (type.isPrimitive()) {
			if (type == int.class || type == Integer.class) {
				return Integer.valueOf(value);
			} else if (type == short.class || type == Short.class) {
				return Short.valueOf(value);
			} else if (type == long.class || type == Long.class) {
				return Long.valueOf(value);
			} else if (type == float.class || type == Float.class) {
				return Float.valueOf(value);
			} else if (type == double.class || type == Double.class) {
				return Double.valueOf(value);
			} else if (type == boolean.class || type == Boolean.class) {
				return Boolean.valueOf(value);
			} else if (type == char.class || type == Character.class) {
				if (value.length() > 1) {
					return null;
				} else {
					return value.charAt(0);
				}
			} else if (type == byte.class || type == Byte.class) {
				return Byte.valueOf(value);
			}
		} else if (type == BigDecimal.class) {
			return new BigDecimal(value);
		} else if (type.isArray()) {
			if (value.startsWith("[")) {
				Class<?> componentType = type.getComponentType();
				List<String> list = JSON.parseArray(value, String.class);
				Object newInstance = Array.newInstance(componentType, list.size());
				for (int i = 0; i < list.size(); i++) {
					Object obj = JSON.parseObject(list.get(i), componentType);
					Array.set(newInstance, i, obj);
				}
				return newInstance;
			}
		} else {
			try {
				Object o = type.newInstance();
				if (o instanceof List) {
					Type parameterizedType = parameter.getParameterizedType();
					if (!(parameterizedType instanceof ParameterizedType)) {
						return null;
					}
					ParameterizedType pt = (ParameterizedType) parameterizedType;
					Type type2 = pt.getActualTypeArguments()[0];
					if (!(type2 instanceof Class)) {
						return null;
					}
					Class<?> c = (Class<?>) type2;
					if (value.startsWith("[")) {
						return JSON.parseArray(value, c);
					}
				} else {
					return JSON.parseObject(value, type);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	private MethodInvoker dispach(String rawPath) {
		MethodInvoker methodInvoker = null;
		for (String key : registerMappings.keySet()) {
			String[] split1 = key.split("/");
			String[] split2 = rawPath.split("/");
			if (split1.length == 0 || split2.length == 0 || split2.length < split1.length) {
				continue;
			}
			if (key.startsWith("/")) {// 从头匹配
				boolean match = true;
				for (int i = 0; i < split1.length; i++) {
					String s1 = split1[i];
					String s2 = split2[i];
					match = StringUtils.match(s1, s2);
					if (!match) {
						break;
					}
				}
				if (!match) {
					continue;
				}
				methodInvoker = registerMappings.get(key);
			} else {
				// 任意一段匹配
				for (int j = 0; j <= split2.length - split1.length; j++) {
					boolean match = true;
					for (int i = 0; i < split1.length; i++) {
						match = StringUtils.match(split1[i], split2[i + j]);
						if (!match) {
							break;
						}
					}
					if (!match) {
						continue;
					}
					methodInvoker = registerMappings.get(key);
				}
			}
		}
		if (methodInvoker != null) {
			cachedMappings.put(rawPath, methodInvoker);
			return methodInvoker;
		}
		throw new RuntimeException("找不到服务");
	}
	
}
