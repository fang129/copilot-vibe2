package com.productpackage.emgcallservice.model;

import org.springframework.util.MultiValueMap;

import java.util.Map;

/**
 * 转发请求的数据结构，按设计说明。
 */
public class ForwardedRequestData {

    /**
     * 请求 header（单值映射，如果需要多值请用逗号拼接或扩展为 Map<String,List<String>>）
     */
    private Map<String, String> headerMap;

    /**
     * 变换后的 body 二进制（优先使用）
     */
    private byte[] bodyBin;

    /**
     * 变换后的请求参数（当 bodyBin 为空时使用）
     */
    private MultiValueMap<String, String> bodyMap;

    /**
     * 请求 queryString（不含 ?）
     */
    private String queryString;

    /**
     * 请求原始 URI
     */
    private String requestUri;

    public Map<String, String> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, String> headerMap) {
        this.headerMap = headerMap;
    }

    public byte[] getBodyBin() {
        return bodyBin;
    }

    public void setBodyBin(byte[] bodyBin) {
        this.bodyBin = bodyBin;
    }

    public MultiValueMap<String, String> getBodyMap() {
        return bodyMap;
    }

    public void setBodyMap(MultiValueMap<String, String> bodyMap) {
        this.bodyMap = bodyMap;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }
}

