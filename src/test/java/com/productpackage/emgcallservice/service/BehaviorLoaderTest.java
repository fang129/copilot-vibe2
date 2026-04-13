package com.productpackage.emgcallservice.service;

import com.productpackage.emgcallservice.util.LogMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class BehaviorLoaderTest {

	@Mock
	private LogMessageService logMessageService;

	@Test
	@DisplayName("init：行为定义文件不存在时 rootNode 为 null 且 findBehaviorForPath 返回 empty")
	public void testInit_fileNotExist_rootNodeNull() throws Exception {
		System.setProperty("behaviordefinition.path", "nonexistent-file.json");
		BehaviorLoader loader = new BehaviorLoader(logMessageService);
		loader.init();
		Optional result = loader.findBehaviorForPath("/any");
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("init：有效 json 文件时加载 rootNode 并能根据 path 查找")
	public void testInit_validFile_loadsRootNode() throws Exception {
		File tmp = Files.createTempFile("behaviors", ".json").toFile();
		tmp.deleteOnExit();
		String json = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"countrycode\":\"CN\",\"Behavior\":[{\"PatternURL\":\"/a\",\"RewriteUrl\":\"/r\"},{\"PatternURL\":\"/\",\"RewriteUrl\":\"/default\"}] }]}}";
		try (FileWriter fw = new FileWriter(tmp)) {
			fw.write(json);
		}
		System.setProperty("behaviordefinition.path", tmp.getAbsolutePath());
		BehaviorLoader loader = new BehaviorLoader(logMessageService);
		loader.init();
		Optional out = loader.findBehaviorForPath("/a");
		assertThat(out).isPresent();
		Optional out2 = loader.findBehaviorForPath("/notfound");
		assertThat(out2).isPresent();
	}
}


