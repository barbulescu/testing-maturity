package ch.barbulescu.testability.examples.demo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireMockPresenceTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Test
    void stubsAResponse() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/hello")).willReturn(ok("world")));

        java.net.URI uri = java.net.URI.create(wireMock.baseUrl() + "/hello");
        String body;
        try (java.io.InputStream in = uri.toURL().openStream()) {
            body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        assertTrue(body.contains("world"));
    }
}
