package com.productpackage.emgcallservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BehaviorLoaderTest {

    @Mock
    private LogMessageService logMessageService;

    private String originalProp;

    @BeforeEach
    void setUp() {
        // lenient stub the log message service for all tests to avoid unnecessary stubbing errors
        org.mockito.Mockito.lenient()
                .when(logMessageService.format(any(String.class), any(Object[].class)))
                .thenReturn("msg");
        if (originalProp == null) {
            originalProp = System.getProperty("behaviordefinition.path");
        }
    }

    @AfterEach
    void tearDown() {
        if (originalProp != null) {
            System.setProperty("behaviordefinition.path", originalProp);
        } else {
            System.clearProperty("behaviordefinition.path");
        }
    }

    @Test
    @DisplayName("init：行为定义文件不存在时 rootNode 为 null 且 findBehaviorForPath 返回 empty")
    public void testInit_fileNotExist_rootNodeNull() throws Exception {
        System.setProperty("behaviordefinition.path", "nonexistent-file.json");
        BehaviorLoader loader = new BehaviorLoader(logMessageService);
        loader.init();
        Optional<JsonNode> result = loader.findBehaviorForPath("/any");
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
        Optional<JsonNode> out = loader.findBehaviorForPath("/a");
        assertThat(out).isPresent();
        Optional<JsonNode> out2 = loader.findBehaviorForPath("/notfound");
        assertThat(out2).isPresent();
    }

    @Test
    @DisplayName("init: path 指向目录时视为不存在文件分支")
    public void testInit_pathIsDirectory_treatedAsNotFile() throws Exception {
        File dir = Files.createTempDirectory("behav-dir").toFile();
        dir.deleteOnExit();
        System.setProperty("behaviordefinition.path", dir.getAbsolutePath());

        BehaviorLoader loader = new BehaviorLoader(logMessageService);
        loader.init();
        Optional<JsonNode> r = loader.findBehaviorForPath("/x");
        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("init：无效 json 文件时 rootNode 为 null")
    public void testInit_invalidJson_setsRootNodeNull() throws Exception {
        final File tmp = Files.createTempFile("behav-invalid", ".json").toFile();
        try (FileWriter w = new FileWriter(tmp)) {
            w.write("{ invalid json");
        }
        System.setProperty("behaviordefinition.path", tmp.getAbsolutePath());

        BehaviorLoader loader = new BehaviorLoader(logMessageService);
        loader.init();

        Optional<JsonNode> r = loader.findBehaviorForPath("/any");
        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("findBehavior：覆盖各种缺失节点和异常路径分支")
    public void testFindBehavior_variousMissingNodes_and_exceptionPath() throws Exception {
        BehaviorLoader loader = new BehaviorLoader(logMessageService);

        // rootNode == null
        Optional<JsonNode> r1 = loader.findBehaviorForPath("/x");
        assertThat(r1).isEmpty();

        ObjectMapper om = new ObjectMapper();
        // ConnectedGatewayBehaviors missing
        JsonNode rootNoConnected = om.readTree("{}\n");
        setPrivateRootNode(loader, rootNoConnected);
        Optional<JsonNode> r2 = loader.findBehaviorForPath("/x");
        assertThat(r2).isEmpty();

        // BehaviorGroup missing
        JsonNode rootNoGroup = om.readTree("{\"ConnectedGatewayBehaviors\":{}}\n");
        setPrivateRootNode(loader, rootNoGroup);
        Optional<JsonNode> r3 = loader.findBehaviorForPath("/x");
        assertThat(r3).isEmpty();

        // group with non-CN countrycode -> skipped
        final String s4 = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"countrycode\":\"US\",\"Behavior\":[{\"PatternURL\":\"/a\"}]}]}}";
        JsonNode rootNonCN = om.readTree(s4);
        setPrivateRootNode(loader, rootNonCN);
        Optional<JsonNode> r4 = loader.findBehaviorForPath("/a");
        assertThat(r4).isEmpty();

        // Behavior missing in CN group -> skip
        final String s5 = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"countrycode\":\"CN\"}]}}";
        JsonNode rootBehMissing = om.readTree(s5);
        setPrivateRootNode(loader, rootBehMissing);
        Optional<JsonNode> r5 = loader.findBehaviorForPath("/a");
        assertThat(r5).isEmpty();

        // PatternURL missing on behavior -> skip
        final String s6 = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"countrycode\":\"CN\",\"Behavior\":[{\"name\":\"noPattern\"}]}]}}";
        JsonNode rootPatMissing = om.readTree(s6);
        setPrivateRootNode(loader, rootPatMissing);
        Optional<JsonNode> r6 = loader.findBehaviorForPath("/a");
        assertThat(r6).isEmpty();

        // exception thrown during search -> should be caught and return empty
        JsonNode mockedRoot = mock(JsonNode.class);
        when(mockedRoot.path("ConnectedGatewayBehaviors")).thenThrow(new RuntimeException("boom"));
        setPrivateRootNode(loader, mockedRoot);
        Optional<JsonNode> r7 = loader.findBehaviorForPath("/x");
        assertThat(r7).isEmpty();
    }

    @Test
    @DisplayName("findBehavior: multiple groups and missing country field scenarios")
    public void testFindBehavior_multipleGroups_and_missingCountry() throws Exception {
        BehaviorLoader loader = new BehaviorLoader(logMessageService);
        ObjectMapper om = new ObjectMapper();

        // group missing country field -> should be skipped
        final String s1 = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"Behavior\":[{\"PatternURL\":\"/a\",\"name\":\"ok\"}]}]}}";
        JsonNode rootMissingCountry = om.readTree(s1);
        setPrivateRootNode(loader, rootMissingCountry);
        Optional<JsonNode> r1 = loader.findBehaviorForPath("/a");
        assertThat(r1).isEmpty();

        // multiple groups: first CN group no default and no match, second CN group has default -> should return default from second
        final String s2 = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"countrycode\":\"CN\",\"Behavior\":[{\"PatternURL\":\"/other\"}]},{\"countrycode\":\"CN\",\"Behavior\":[{\"PatternURL\":\"/\",\"name\":\"secondDefault\"}]}]}}";
        JsonNode rootMulti = om.readTree(s2);
        setPrivateRootNode(loader, rootMulti);
        Optional<JsonNode> r2 = loader.findBehaviorForPath("/notfound");
        assertThat(r2).isPresent();
        assertThat(r2.get().path("name").asText()).isEqualTo("secondDefault");

        // multiple groups: match in second group exact
        final String s3 = "{\"ConnectedGatewayBehaviors\":{\"BehaviorGroup\":[{\"countrycode\":\"CN\",\"Behavior\":[{\"PatternURL\":\"/other\"}]},{\"countrycode\":\"CN\",\"Behavior\":[{\"PatternURL\":\"/match\",\"name\":\"found\"}]}]}}";
        JsonNode rootMultiMatch = om.readTree(s3);
        setPrivateRootNode(loader, rootMultiMatch);
        Optional<JsonNode> r3 = loader.findBehaviorForPath("/match");
        assertThat(r3).isPresent();
        assertThat(r3.get().path("name").asText()).isEqualTo("found");
    }

    private static void setPrivateRootNode(final BehaviorLoader loader, final JsonNode node) throws Exception {
        Field f = BehaviorLoader.class.getDeclaredField("rootNode");
        f.setAccessible(true);
        f.set(loader, node);
    }
}


