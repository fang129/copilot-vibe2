package com.productpackage.emgcallservice.controller;

import com.productpackage.emgcallservice.model.ForwardedRequestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MockerController to cover branches not exercised by MockMvc tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MockerController 纯单元测试")
public class MockerControllerUnitTest {

    private final MockerController controller = new MockerController();

    @Test
    @DisplayName("createForwardedRequest_headerNamesNull_producesEmptyHeaderMap_and_nullBody")
    void testCreateForwardedRequest_headerNamesNull_producesEmptyHeaderMap_and_nullBody() {
        final HttpServletRequest req = mock(HttpServletRequest.class);

        // simulate no headers
        when(req.getHeaderNames()).thenReturn(null);
        when(req.getQueryString()).thenReturn(null);
        when(req.getRequestURI()).thenReturn("/no-headers");

        final ForwardedRequestData out = controller.createForwardedRequest(req, null);

        assertThat(out).isNotNull();
        assertThat(out.getHeaderMap()).isNotNull();
        assertThat(out.getHeaderMap()).isEmpty();
        assertThat(out.getQueryString()).isNull();
        assertThat(out.getRequestUri()).isEqualTo("/no-headers");
        assertThat(out.getBodyBin()).isNull();
    }
}

