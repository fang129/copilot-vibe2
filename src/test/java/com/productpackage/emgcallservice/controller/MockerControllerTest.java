package com.productpackage.emgcallservice.controller;

import com.productpackage.emgcallservice.model.ForwardedRequestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = MockerController.class)
public class MockerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MockerController controller;
    
	@org.springframework.boot.test.mock.mockito.MockBean
	private com.productpackage.emgcallservice.util.LogMessageService logMessageService;

	@Test
	@DisplayName("POST /file/upload：返回 200，body 为 ForwardedRequestData 的 JSON 且包含指定 header")
	public void testTestUpload_returnsOk_and_jsonBody_and_headers() throws Exception {
		mockMvc.perform(post("/file/upload").content("abc").contentType("application/octet-stream"))
				.andExpect(status().isOk())
				.andExpect(header().string("x-d-authenticate", "abcdefd"));
	}

	@Test
	@DisplayName("createForwardedRequest：正确聚合 request header 到 ForwardedRequestData")
	public void testCreateForwardedRequest_headersAggregatedCorrectly() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("h1", "a");
		req.addHeader("h1", "b");
		req.setQueryString("q=1");
		req.setRequestURI("/u");

		ForwardedRequestData out = controller.createForwardedRequest(req, "bcd".getBytes());
		assertThat(out.getHeaderMap()).containsKey("h1");
		assertThat(out.getHeaderMap().get("h1")).contains("a");
		assertThat(out.getQueryString()).isEqualTo("q=1");
		assertThat(new String(out.getBodyBin())).isEqualTo("bcd");
	}
}



