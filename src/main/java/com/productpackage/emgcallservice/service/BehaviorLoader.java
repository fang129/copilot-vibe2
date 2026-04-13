package com.productpackage.emgcallservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productpackage.emgcallservice.util.Constants;
import com.productpackage.emgcallservice.util.LogMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Iterator;
import java.util.Optional;

/**
 * 加载 behaviors 定义文件，并提供按 path 查找 behavior 的能力（仅 countrycode="CN"）。
 */
@Component
public class BehaviorLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorLoader.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LogMessageService logMessageService;

    private JsonNode rootNode;

    // 配置可扩展为注入 AppProperties；当前从默认路径尝试加载
    public BehaviorLoader(final LogMessageService logMessageService) {
        this.logMessageService = logMessageService;
    }

    @PostConstruct
    public void init() {
        final String filePath = System.getProperty("behaviordefinition.path", Constants.DEFAULT_BEHAVIOR_FILE);
        try {
            final File f = new File(filePath);
            if (!f.exists() || !f.isFile()) {
                final String msg = logMessageService.format("log.behavior.fileNotFound",
                        Constants.BUSINESS_CODE,
                        filePath
                );
                LOGGER.warn(msg);
                this.rootNode = null;
                return;
            }
            this.rootNode = objectMapper.readTree(f);
            final String msg = logMessageService.format("log.behavior.loaded",
                    Constants.BUSINESS_CODE,
                    f.getAbsolutePath()
            );
            LOGGER.info(msg);
        } catch (final Exception e) {
            final String msg = logMessageService.format("log.behavior.loadFailed",
                    Constants.BUSINESS_CODE,
                    e.getMessage()
            );
            LOGGER.error(msg, e);
            this.rootNode = null;
        }
    }

    /**
     * 找到 countrycode='CN' 下匹配 PatternURL == path 的 Behavior 节点。
     * 如果找不到匹配，将尝试返回 PatternURL == '/' 的节点（若存在）。
     *
     * 返回 Optional.empty() 表示没有可用数据。
     */
    public Optional<JsonNode> findBehaviorForPath(final String path) {
        if (rootNode == null) {
            return Optional.empty();
        }

        try {
            final JsonNode connected = rootNode.path("ConnectedGatewayBehaviors");
            if (connected.isMissingNode()) {
                return Optional.empty();
            }
            final JsonNode behaviorGroups = connected.path("BehaviorGroup");
            if (behaviorGroups.isMissingNode()) {
                return Optional.empty();
            }
            final Iterator<JsonNode> groupIt = behaviorGroups.elements();
            while (groupIt.hasNext()) {
                final JsonNode group = groupIt.next();
                final JsonNode country = group.path("countrycode");
                if (country.isMissingNode() || !"CN".equals(country.asText())) {
                    continue;
                }
                final JsonNode behaviors = group.path("Behavior");
                if (behaviors.isMissingNode()) {
                    continue;
                }
                final Iterator<JsonNode> bIt = behaviors.elements();
                JsonNode defaultNode = null;
                while (bIt.hasNext()) {
                    final JsonNode b = bIt.next();
                    final String patternUrl = b.path("PatternURL").asText(null);
                    if (patternUrl == null) {
                        continue;
                    }
                    if ("/".equals(patternUrl)) {
                        defaultNode = b;
                    }
                    if (patternUrl.equals(path)) {
                        return Optional.of(b);
                    }
                }
                if (defaultNode != null) {
                    return Optional.of(defaultNode);
                }
            }
        } catch (final Exception e) {
            final String msg = logMessageService.format("log.behavior.searchError",
                    Constants.BUSINESS_CODE,
                    path,
                    e.getMessage()
            );
            LOGGER.error(msg, e);
        }
        return Optional.empty();
    }
}


