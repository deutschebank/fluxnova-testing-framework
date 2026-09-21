package org.finos.fluxnova.bpm.test.rules;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockConnectorRuleTest {

    @Test
    void returnsWireMockServer_onMockConnectorInitialization() {
        WireMockServer wireMockRule = new MockConnectorRule(8080);
        assertNotNull(wireMockRule);
    }

    @Test
    void ConnectorResponseTransformer_getName_returnsCorrectName() {
        assertEquals("ConnectionHeaderTransformer", new MockConnectorRule.ConnectorResponseTransformer().getName());
    }

    @Test
    void ConnectorResponseTransformer_applyGlobally_returnsTrue() {
        assertTrue(new MockConnectorRule.ConnectorResponseTransformer().applyGlobally());
    }

    @Test
    void ConnectorResponseTransformer_transform_addConnectionCloseHeader() {
        Response response = new Response.Builder().headers(HttpHeaders.noHeaders()).build();
        Response transformedResponse =
                new MockConnectorRule.ConnectorResponseTransformer().transform(response, null);
        assertEquals("close", transformedResponse.getHeaders().getHeader("Connection").firstValue());
    }

    @Test
    void ConnectorResponseTransformer_transform_overridesConnectionHeader() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders = httpHeaders
                .plus(HttpHeader.httpHeader("Connection", "keep-alive"))
                .plus(HttpHeader.httpHeader("AnotherHeader", "another-header-value"));
        Response response = new Response.Builder().headers(httpHeaders).build();
        Response transformedResponse =
                new MockConnectorRule.ConnectorResponseTransformer().transform(response, null);
        assertEquals("close",
                transformedResponse.getHeaders().getHeader("Connection").firstValue());
        assertEquals("another-header-value",
                transformedResponse.getHeaders().getHeader("AnotherHeader").firstValue());
    }
}
