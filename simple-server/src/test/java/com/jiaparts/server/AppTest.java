package com.jiaparts.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.TypeReference;
import com.jiaparts.server.core.PathMathcer;
import com.jiaparts.server.core.ServerConfig;
import com.jiaparts.server.model.MimeType;

/**
 * Unit test for simple App.
 */
public class AppTest {
	
	@Test
	public void testYamlLoad() {
		Yaml yaml = new Yaml();
		Object load = yaml.load(SimpleServer.class.getResourceAsStream("/mimeMapping.yml"));
		System.err.println(load.getClass().getName());
		System.err.println(JSON.toJSONString(load));
	}

	@Test
	public void testSax() throws ParserConfigurationException, SAXException, IOException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		File f = new File("D:\\apache-tomcat-8.5.23\\conf\\web.xml");
		List<MimeType> list = new ArrayList<>();
		parser.parse(f, new DefaultHandler() {
			MimeType type = null;
			String content = null;

			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes)
					throws SAXException {
				switch (qName) {
				case "mime-mapping":
					type = new MimeType();
					break;
				case "extension":
				case "mime-type":
				default:
					break;
				}

			}

			@Override
			public void characters(char[] ch, int start, int length) throws SAXException {
				content = new String(ch, start, length);
			}

			@Override
			public void endElement(String uri, String localName, String qName) throws SAXException {

				switch (qName) {
				case "mime-mapping":
					list.add(type);
					break;
				case "extension":
					type.setExtension(content);
					content = null;
					break;
				case "mime-type":
					type.setContentType(content);
					content = null;
					break;
				default:
					break;
				}
			}

		});

		list.forEach(t -> {
			System.err.println(t.getExtension() + ":" + t.getContentType());
		});
		String dir = System.getProperty("user.dir");

		Yaml yaml = new Yaml();
		Writer writer =new FileWriter(new File(dir+"/src/main/resources/mimeMapping.yml"));
		List<Map<String, String>> obj = JSONArray.parseObject(JSONArray.toJSONString(list), new TypeReference<List<Map<String,String>>>(){});
		yaml.dump(obj, writer);

	}

	@Test
	public void testPathMatch() {
		ServerConfig config = new ServerConfig();
		config.setContextPath("pro");
		config.setResourceMapping("static");
		config.setResourceDir("D:\\HbuilderPros");
		PathMathcer.serverConfig = config;
		String resourcePath = PathMathcer.getResourcePath("/jiaparts-support-api/jsb/static/dist/abc.js");
		System.err.println(resourcePath);
	}

	@Test
	public void testRegex() {
		String a = "abcdefg.xsl";
		String b = "abc*g.xs*";
		boolean matches = a.matches("^" + b.replace("*", ".*") + "$");
		System.err.println(matches);
	}

}
