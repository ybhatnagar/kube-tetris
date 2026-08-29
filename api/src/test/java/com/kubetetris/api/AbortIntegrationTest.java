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
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The synth apply is fast enough that racing the abort endpoint against it can be
 * flaky. This test just guards the endpoint's contract:
 *
 * <ul>
 *   <li>abort against an unknown journal returns 404</li>
 *   <li>abort against a completed journal is a no-op that still returns the current
 *       (terminal) execution DTO with 200</li>
 * </ul>
 *
 * The AbortSignal wiring itself is covered end-to-end at the executor layer by the
 * {@code AbortSignalTest} in that module.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AbortIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }

    @BeforeEach
    void refresh() throws Exception {
        Thread.sleep(300);
        rest.postForEntity(base() + "/api/v1/clusters/synth/collect", null, JsonNode.class);
    }

    @Test
    void abortOnUnknownJournalReturns404() {
        ResponseEntity<JsonNode> r = rest.postForEntity(
                base() + "/api/v1/clusters/synth/apply/j-nonexistent:abort", null, JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void abortAfterCompletionReturnsTerminalState() throws Exception {
        JsonNode pending = rest.getForEntity(base() + "/api/v1/clusters/synth/pending", JsonNode.class)
                .getBody().get("pending");
        String checkoutUid = pending.get(0).get("pending_pod_uid").asText();

        Map<String, Object> req = new HashMap<>();
        req.put("ack", true);
        req.put("pending_pod_uid", checkoutUid);
        req.put("opts", Map.of("dry_run", true));

        String journalId = rest.postForEntity(base() + "/api/v1/clusters/synth/apply", req, JsonNode.class)
                .getBody().get("journal_id").asText();

        for (int i = 0; i < 40; i++) {
            JsonNode poll = rest.getForEntity(
                    base() + "/api/v1/clusters/synth/apply/" + journalId, JsonNode.class).getBody();
            if (!"running".equals(poll.get("status").asText())) break;
            Thread.sleep(50);
        }

        ResponseEntity<JsonNode> abortResponse = rest.postForEntity(
                base() + "/api/v1/clusters/synth/apply/" + journalId + ":abort", null, JsonNode.class);
        assertThat(abortResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(abortResponse.getBody().get("status").asText()).isEqualTo("applied");
    }
}
