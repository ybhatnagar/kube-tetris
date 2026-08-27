package com.kubetetris.engine.synth;

import com.kubetetris.engine.domain.NodeSpec;
import com.kubetetris.engine.domain.NodeState;
import com.kubetetris.engine.domain.PodSpec;
import com.kubetetris.engine.domain.ResourceReq;
import com.kubetetris.engine.domain.SnapshotView;

import java.util.List;

/**
 * Deterministic synthetic snapshots for M1 tests. Mirrors the {@code freshCluster()}
 * data in {@code design-docs/ui-mockup.html} exactly (three 1000mC/2000MB nodes with
 * six named pods; two pending pods including a Job that's infeasible-by-total).
 *
 * This one fixture exercises both worked examples:
 *   - Scheduler:  pending {@code checkout} (300/600) has no direct fit; the single-pod
 *     path must produce one MOVE + one PLACE. Also demonstrates infeasible-by-total for
 *     pending {@code batch-report} (900/1800), and the reversibility exclusion via
 *     {@code ledger} (StatefulSet). See M1-NOTES for why doc 06's fig1_fragmentation
 *     spec wasn't used directly (no pod inventory listed).
 *   - Balancer:   pivot ≈ 0.5455, base entropy ≈ 2.8045, best swap
 *     {@code ranker⇄thumbnailer} → entropy ≈ 0.2860 (the doc's "~2.81 → 0.29").
 */
public final class SnapshotFactory {

    private SnapshotFactory() {}

    public static final String N1 = "ip-10-0-1-11";
    public static final String N2 = "ip-10-0-1-12";
    public static final String N3 = "ip-10-0-1-13";

    public static SnapshotView uiMockupFixture() {
        NodeSpec spec1 = new NodeSpec(N1, new ResourceReq(1000, 2000));
        NodeSpec spec2 = new NodeSpec(N2, new ResourceReq(1000, 2000));
        NodeSpec spec3 = new NodeSpec(N3, new ResourceReq(1000, 2000));

        PodSpec ranker = deployment("p1", "ranker", "search", 500, 300);
        PodSpec featureCache = deployment("p2", "feature-cache", "search", 400, 200);
        PodSpec api = deployment("p3", "api", "web", 300, 500);
        PodSpec ledger = statefulSet("p4", "ledger", "payments", 400, 1100);
        PodSpec sessionStore = deployment("p5", "session-store", "web", 100, 950);
        PodSpec thumbnailer = deployment("p6", "thumbnailer", "web", 100, 750);

        NodeState n1 = new NodeState(spec1, List.of(ranker, featureCache));
        NodeState n2 = new NodeState(spec2, List.of(api, ledger));
        NodeState n3 = new NodeState(spec3, List.of(sessionStore, thumbnailer));

        PodSpec checkout = deployment("c4", "checkout", "payments", 300, 600);
        PodSpec batchReport = new PodSpec("c9", "batch-report", "search", "Job",
                new ResourceReq(900, 1800), PodSpec.Qos.BURSTABLE, true, false);

        return new SnapshotView(List.of(n1, n2, n3), List.of(checkout, batchReport));
    }

    /** Empty two-node cluster used to exercise the "nothing to balance" path. */
    public static SnapshotView balancedFixture() {
        NodeSpec a = new NodeSpec("a", new ResourceReq(1000, 1000));
        NodeSpec b = new NodeSpec("b", new ResourceReq(1000, 1000));
        return new SnapshotView(
                List.of(new NodeState(a, List.of()), new NodeState(b, List.of())),
                List.of());
    }

    private static PodSpec deployment(String uid, String name, String ns, long cpu, long mem) {
        return new PodSpec(uid, name, ns, "Deployment",
                new ResourceReq(cpu, mem), PodSpec.Qos.BURSTABLE, true, false);
    }

    private static PodSpec statefulSet(String uid, String name, String ns, long cpu, long mem) {
        return new PodSpec(uid, name, ns, "StatefulSet",
                new ResourceReq(cpu, mem), PodSpec.Qos.BURSTABLE, false, false);
    }
}
