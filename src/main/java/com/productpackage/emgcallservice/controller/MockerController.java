package com.productpackage.emgcallservice.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.productpackage.emgcallservice.model.ForwardedRequestData;
import com.productpackage.emgcallservice.service.RequestProcessor;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * TODO Mocker用-提交版要删除
 *
 * @author NTTD.
 */
@RestController
@Slf4j
public class MockerController {


    /**
     * 转送服务
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping({"/file/upload"})
    public ResponseEntity<String> testUpload(
            HttpServletRequest request,@RequestBody(required = false) final byte[] body) throws IOException, InterruptedException {

        ForwardedRequestData parseRequest = this.createForwardedRequest(request,body);
        Thread.sleep(5000);
        ObjectMapper objectMapper = new ObjectMapper();
        // 将对象的所有字段全部列入
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        // 忽略空Bean转json的错误
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        // 忽略 在json字符串中存在，但是在对象中不存在对应属性的情况，防止错误。
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String asString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parseRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "text/html");
        headers.add("x-d-authenticate", "abcdefd");
        return new ResponseEntity<String>(asString, headers, HttpStatus.OK);
    }

    public ForwardedRequestData createForwardedRequest(final HttpServletRequest request, byte[] body) {

        final ForwardedRequestData forward = new ForwardedRequestData();

        // headers
        final Map<String, String> headers = new HashMap<>();
        final Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                final String hn = headerNames.nextElement();
                final Enumeration<String> hv = request.getHeaders(hn);
                final List<String> values = new ArrayList<>();
                while (hv.hasMoreElements()) {
                    values.add(hv.nextElement());
                }
                headers.put(hn, String.join(",", values));
            }
        }
        forward.setHeaderMap(headers);

        forward.setQueryString(request.getQueryString());
        forward.setRequestUri(request.getRequestURI());

        // body 处理委托给 transformBodyByPattern
        forward.setBodyBin(body);

        return forward;
    }


}
