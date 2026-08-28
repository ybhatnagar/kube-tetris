package com.kubetetris.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The api module bundles the ui/ static assets. This test guards that wiring. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UiIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void rootServesTheIndexPage() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + "/", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getContentType().toString()).contains("text/html");
        assertThat(r.getBody())
                .contains("Kube Tetris")
                .contains("/api/v1")
                .contains("advisor");
    }

    @Test
    void indexHtmlIsReachableAsAnAsset() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + "/index.html", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("<title>Kube Tetris</title>");
    }
}
