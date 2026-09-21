package org.finos.fluxnova.bpm.test.rules;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.WireMockServer;

public class MockConnectorRule extends WireMockServer {

    public MockConnectorRule(int port) {
        super(WireMockConfiguration.wireMockConfig().port(port).extensions(new ConnectorResponseTransformer()));
    }

    protected static class ConnectorResponseTransformer implements ResponseTransformerV2 {
        @Override
        public Response transform(Response response, ServeEvent serveEvent) {
            HttpHeaders httpHeaders = response.getHeaders();
            return Response.Builder.like(response)
                    .but()
                    .headers(updateHeaders(httpHeaders))
                    .build();
        }

        @Override
        public String getName() {
            return "ConnectionHeaderTransformer";
        }

        @Override
        public boolean applyGlobally() {
            return true;
        }

        private HttpHeaders updateHeaders(HttpHeaders originalHeaders) {
            return new HttpHeaders(originalHeaders.all().stream()
                        .filter(header -> !"Connection".equalsIgnoreCase(header.key()))
                        .toList()
            ).plus(HttpHeader.httpHeader("Connection", "close"));
        }
    }
}
