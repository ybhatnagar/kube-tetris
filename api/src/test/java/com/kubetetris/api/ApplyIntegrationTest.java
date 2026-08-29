package com.kubetetris.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
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
 * Exercises the synth-cluster apply path end-to-end: /pending → apply → /snapshot mutated
 * → /history reflects the run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplyIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }

    @BeforeEach
    void refresh() {
        rest.postForEntity(base() + "/api/v1/clusters/synth/collect", null, JsonNode.class);
    }

    @Test
    void applySchedulerPlanMutatesSyntheticSnapshotAndAppendsHistory() {
        // Find pending pod uid for 'checkout'
        JsonNode pending = rest.getForEntity(base() + "/api/v1/clusters/synth/pending", JsonNode.class)
                .getBody().get("pending");
        String checkoutUid = null;
        for (JsonNode f : pending) {
            if ("checkout".equals(f.get("pending_pod_name").asText())) {
                checkoutUid = f.get("pending_pod_uid").asText();
            }
        }
        assertThat(checkoutUid).isNotNull();

        Map<String, Object> req = new HashMap<>();
        req.put("ack", true);
        req.put("pending_pod_uid", checkoutUid);
        req.put("opts", Map.of("dry_run", false));

        ResponseEntity<JsonNode> apply = rest.postForEntity(
                base() + "/api/v1/clusters/synth/apply", req, JsonNode.class);
        assertThat(apply.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apply.getBody().get("status").asText()).isEqualTo("applied");
        String journalId = apply.getBody().get("journal_id").asText();

        // Snapshot should no longer list checkout as pending
        JsonNode snap = rest.getForEntity(base() + "/api/v1/clusters/synth/snapshot", JsonNode.class).getBody();
        boolean stillPending = false;
        for (JsonNode p : snap.get("pending")) {
            if ("checkout".equals(p.get("name").asText())) stillPending = true;
        }
        assertThat(stillPending).as("checkout should be placed after apply").isFalse();

        // History has one applied entry
        JsonNode history = rest.getForEntity(base() + "/api/v1/clusters/synth/history", JsonNode.class)
                .getBody().get("entries");
        assertThat(history.isArray()).isTrue();
        assertThat(history.size()).isGreaterThanOrEqualTo(1);
        boolean found = false;
        for (JsonNode h : history) {
            if (journalId.equals(h.get("journal_id").asText())) {
                assertThat(h.get("kind").asText()).isEqualTo("Scheduler");
                assertThat(h.get("outcome").asText()).isEqualTo("applied");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void applyBalancerSwapMutatesSyntheticSnapshot() {
        rest.postForEntity(base() + "/api/v1/clusters/synth/balance/plan", Map.of(), JsonNode.class);

        Map<String, Object> req = new HashMap<>();
        req.put("ack", true);
        req.put("swap_ref", "s0");
        req.put("opts", Map.of("dry_run", false));

        ResponseEntity<JsonNode> apply = rest.postForEntity(
                base() + "/api/v1/clusters/synth/apply", req, JsonNode.class);
        assertThat(apply.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apply.getBody().get("status").asText()).isEqualTo("applied");

        // After the swap, entropy should be lower.
        JsonNode snap = rest.getForEntity(base() + "/api/v1/clusters/synth/snapshot", JsonNode.class).getBody();
        assertThat(snap.get("entropy").asDouble()).isLessThan(1.0);
    }

    @Test
    void applyWithoutAckReturns400() {
        Map<String, Object> req = Map.of("ack", false, "pending_pod_uid", "c4");
        ResponseEntity<JsonNode> apply = rest.postForEntity(
                base() + "/api/v1/clusters/synth/apply", req, JsonNode.class);
        assertThat(apply.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pollApplyReturnsJournalState() {
        JsonNode pending = rest.getForEntity(base() + "/api/v1/clusters/synth/pending", JsonNode.class)
                .getBody().get("pending");
        String checkoutUid = pending.get(0).get("pending_pod_uid").asText();

        Map<String, Object> req = new HashMap<>();
        req.put("ack", true);
        req.put("pending_pod_uid", checkoutUid);
        req.put("opts", Map.of("dry_run", true));

        ResponseEntity<JsonNode> apply = rest.postForEntity(
                base() + "/api/v1/clusters/synth/apply", req, JsonNode.class);
        String journalId = apply.getBody().get("journal_id").asText();

        ResponseEntity<JsonNode> poll = rest.getForEntity(
                base() + "/api/v1/clusters/synth/apply/" + journalId, JsonNode.class);
        assertThat(poll.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(poll.getBody().get("journal_id").asText()).isEqualTo(journalId);
        assertThat(poll.getBody().has("steps")).isTrue();
    }
}
