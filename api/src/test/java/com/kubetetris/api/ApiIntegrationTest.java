package com.kubetetris.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }

    @BeforeEach
    void refreshSnapshot() {
        rest.postForEntity(base() + "/api/v1/clusters/synth/collect", null, JsonNode.class);
    }

    @Test
    void healthzReturnsOk() {
        ResponseEntity<JsonNode> r = rest.getForEntity(base() + "/healthz", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("status").asText()).isEqualTo("ok");
    }

    @Test
    void listClustersIncludesTheSyntheticCluster() {
        ResponseEntity<JsonNode> r = rest.getForEntity(base() + "/api/v1/clusters", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode clusters = r.getBody().get("clusters");
        assertThat(clusters.isArray()).isTrue();
        boolean hasSynth = false;
        for (JsonNode c : clusters) if ("synth".equals(c.get("id").asText())) hasSynth = true;
        assertThat(hasSynth).as("expected a cluster with id 'synth'").isTrue();
    }

    @Test
    void snapshotShapeMatchesTheContract() {
        ResponseEntity<JsonNode> r = rest.getForEntity(base() + "/api/v1/clusters/synth/snapshot", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode s = r.getBody();

        assertThat(s.get("cluster_id").asText()).isEqualTo("synth");
        assertThat(s.hasNonNull("taken_at")).isTrue();
        assertThat(s.get("stale").asBoolean()).isFalse();
        assertThat(s.get("pivot").asDouble()).isBetween(0.54, 0.56);
        assertThat(s.get("entropy").asDouble()).isBetween(2.80, 2.81);
        assertThat(s.get("nodes").size()).isEqualTo(3);
        assertThat(s.get("pending").size()).isEqualTo(2);

        JsonNode node0 = s.get("nodes").get(0);
        for (String field : new String[]{
                "name","cpu_alloc","mem_alloc","cpu_free","mem_free","ratio",
                "cordoned","ready","taints"}) {
            assertThat(node0.has(field)).as("node." + field).isTrue();
        }
        // Fields defaulted for now but present.
        assertThat(node0.has("zone")).isTrue();
        assertThat(node0.has("instance_type")).isTrue();

        JsonNode pod0 = s.get("pending").get(0);
        for (String field : new String[]{
                "uid","name","namespace","kind","cpu_req","mem_req","qos",
                "owner_ref","reversible","pdb_ok","pins"}) {
            assertThat(pod0.has(field)).as("pod." + field).isTrue();
        }
    }

    @Test
    void pendingComputesFeasibilityForCheckoutAndReturnsSingleMovePlan() {
        ResponseEntity<JsonNode> r = rest.getForEntity(base() + "/api/v1/clusters/synth/pending", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pending = r.getBody().get("pending");
        assertThat(pending.isArray()).isTrue();

        JsonNode checkout = null;
        for (JsonNode f : pending) if ("checkout".equals(f.get("pending_pod_name").asText())) checkout = f;
        assertThat(checkout).as("checkout entry present").isNotNull();
        assertThat(checkout.get("feasible").asBoolean()).isTrue();
        assertThat(checkout.get("strategy").asText()).isEqualTo("single");
        assertThat(checkout.get("moves").asInt()).isEqualTo(1);
        assertThat(checkout.get("touches_nonreversible").asBoolean()).isFalse();
        assertThat(checkout.get("plan").size()).isEqualTo(2);
    }

    @Test
    void balancePlanReturnsRankerThumbnailerAsTopSwap() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of(), headers);
        ResponseEntity<JsonNode> r = rest.exchange(
                base() + "/api/v1/clusters/synth/balance/plan",
                HttpMethod.POST, req, JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = r.getBody();

        assertThat(body.get("base_entropy").asDouble()).isBetween(2.80, 2.81);
        JsonNode swaps = body.get("swaps");
        assertThat(swaps.isArray()).isTrue();
        assertThat(swaps.size()).isGreaterThan(0);

        JsonNode top = swaps.get(0);
        assertThat(top.get("swap_ref").asText()).isEqualTo("s0");
        assertThat(top.get("entropy_after").asDouble()).isBetween(0.28, 0.29);

        String a = top.get("pod_a").asText();
        String b = top.get("pod_b").asText();
        assertThat(java.util.Set.of(a, b)).isEqualTo(java.util.Set.of("ranker", "thumbnailer"));
    }

    @Test
    void balanceWhyReturnsEvidenceForTopSwap() {
        rest.postForEntity(base() + "/api/v1/clusters/synth/balance/plan", null, JsonNode.class);
        ResponseEntity<JsonNode> r = rest.getForEntity(
                base() + "/api/v1/clusters/synth/balance/s0/why", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("evidence").asText())
                .contains("ranker")
                .contains("thumbnailer");
    }

    @Test
    void unknownClusterReturns404() {
        ResponseEntity<JsonNode> r = rest.getForEntity(base() + "/api/v1/clusters/does-not-exist/snapshot", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
