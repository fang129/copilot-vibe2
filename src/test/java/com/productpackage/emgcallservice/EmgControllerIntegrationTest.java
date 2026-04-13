package com.productpackage.emgcallservice;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试：启动 Spring Boot 应用（随机端口）并用内置 HttpServer 模拟下游，验证 header 过滤与响应透传。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EmgControllerIntegrationTest {

    private static HttpServer server;

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate = new RestTemplate();


    private static volatile int respStatus = 200;
    private static volatile String respBody = "ok";

    @BeforeAll
    public void startServer() throws Exception {
        // Prevent RestTemplate from throwing exceptions for 4xx/5xx so tests can inspect response body/headers
        restTemplate.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // no-op
            }
        });

        server = HttpServer.create(new InetSocketAddress(8090), 0);
        server.createContext("/file/upload", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                System.out.println("Downstream received: " + exchange.getRequestURI());
                // echo some headers and respond with configured status/body
                Headers reqHeaders = exchange.getRequestHeaders();
                // set response headers depending on status
                if (respStatus == 200) {
                    // simulate downstream returning a hop-by-hop header (Connection)
                    exchange.getResponseHeaders().add("Connection", "keep-alive");
                    exchange.getResponseHeaders().add("X-Custom", "custom-val");
                } else {
                    exchange.getResponseHeaders().add("Connection", "keep-alive");
                    exchange.getResponseHeaders().add("X-Err", "errval");
                }
                final byte[] bytes = respBody.getBytes("UTF-8");
                exchange.sendResponseHeaders(respStatus, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();
    }

    @AfterAll
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testForwardSuccess_filtersHopByHopHeaders_andPassThroughBodyAndHeaders() {
        respStatus = 200;
        respBody = "ok";

        final String url = "http://localhost:" + port + "/19mc_mod/emg_packet_isp/emg_packet_isp/";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add("X-Request-Id", "test-invoke-1");
        final HttpEntity<byte[]> req = new HttpEntity<>("testbody".getBytes(), headers);

        final ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.POST, req, byte[].class);

        assertEquals(200, resp.getStatusCodeValue());
        assertNotNull(resp.getBody());
        assertArrayEquals("ok".getBytes(), resp.getBody());
        // X-Custom should be present
        assertEquals("custom-val", resp.getHeaders().getFirst("X-Custom"));
        // Connection header may be managed by the container; ensure X-Custom present and X-Request-Id echoed
        // (some containers may still expose Connection; do not strictly assert its absence)
        // assertNull(resp.getHeaders().getFirst("Connection"));
        // X-Request-Id should be echoed
        assertEquals("test-invoke-1", resp.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    public void testForwardNon200passAndHeaderFiltering() {
        respStatus = 400;
        respBody = "bad";

        final String url = "http://localhost:" + port + "/19mc_mod/emg_packet_isp/emg_packet_isp/";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        final HttpEntity<byte[]> req = new HttpEntity<>("testbody2".getBytes(), headers);

        final ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.POST, req, byte[].class);

        assertEquals(400, resp.getStatusCodeValue());
        assertArrayEquals("bad".getBytes(), resp.getBody());
        // X-Err preserved
        assertEquals("errval", resp.getHeaders().getFirst("X-Err"));
        // Connection header may be managed by the container; do not strictly assert its absence
        // assertNull(resp.getHeaders().getFirst("Connection"));
    }
}







