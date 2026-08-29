package com.kubetetris.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the /clusters + /collect wiring for non-synthetic clusters. The collector
 * itself is covered by its own module's mock-server tests; this test guards the api-side
 * plumbing (registration, error paths, and that the synth path still short-circuits).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollectorWiringIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }

    @Test
    void registeringAClusterWithKubeConfigPathPersistsThePath() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "kind-local");
        body.put("api_url", "https://127.0.0.1:6443");
        body.put("auth_method", "kubeconfig");
        body.put("credential_ref", "kubeconfig-secret");
        body.put("kube_config_path", "/tmp/does-not-need-to-exist.yaml");

        ResponseEntity<JsonNode> created = rest.postForEntity(base() + "/api/v1/clusters", body, JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").asText();
        assertThat(id).startsWith("c");

        ResponseEntity<JsonNode> list = rest.getForEntity(base() + "/api/v1/clusters", JsonNode.class);
        boolean found = false;
        for (JsonNode c : list.getBody().get("clusters")) {
            if (id.equals(c.get("id").asText())) {
                found = true;
                assertThat(c.get("name").asText()).isEqualTo("kind-local");
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void collectOnNonSynthClusterWithoutKubeConfigPathFallsBackToAutoConfig() {
        // With no kubeconfig path the collector tries auto-configuration (KUBECONFIG,
        // ~/.kube/config, or in-cluster ServiceAccount). In the test environment none of
        // those points at a reachable cluster, so the collection fails at the wire — 502.
        Map<String, Object> body = Map.of(
                "name", "no-path", "auth_method", "kubeconfig");
        ResponseEntity<JsonNode> created = rest.postForEntity(base() + "/api/v1/clusters", body, JsonNode.class);
        String id = created.getBody().get("id").asText();

        ResponseEntity<JsonNode> r = rest.postForEntity(base() + "/api/v1/clusters/" + id + "/collect",
                null, JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody().get("error").asText()).contains("collection failed");
    }

    @Test
    void collectOnNonSynthClusterWithBogusKubeConfigPathReturns502() {
        Map<String, Object> body = Map.of(
                "name", "bogus", "auth_method", "kubeconfig",
                "kube_config_path", "/does/not/exist/kubeconfig.yaml");
        ResponseEntity<JsonNode> created = rest.postForEntity(base() + "/api/v1/clusters", body, JsonNode.class);
        String id = created.getBody().get("id").asText();

        ResponseEntity<JsonNode> r = rest.postForEntity(base() + "/api/v1/clusters/" + id + "/collect",
                null, JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody().get("error").asText()).contains("collection failed");
    }

    @Test
    void collectOnSynthClusterStillReturnsSnapshot() {
        ResponseEntity<JsonNode> r = rest.postForEntity(base() + "/api/v1/clusters/synth/collect",
                null, JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("cluster_id").asText()).isEqualTo("synth");
        assertThat(r.getBody().get("nodes").size()).isEqualTo(3);
    }
}
