package com.kubetetris.engine.domain;

/**
 * One step in a scheduler plan.
 * <ul>
 *   <li>{@code MOVE} — evict {@code pod} from {@code fromNode}, place it on {@code toNode}.</li>
 *   <li>{@code PLACE} — place a currently-pending {@code pod} on {@code toNode} (no source).</li>
 * </ul>
 */
public record PlanStep(Kind kind, PodSpec pod, String fromNode, String toNode, String note) {

    public enum Kind { MOVE, PLACE }

    public static PlanStep move(PodSpec pod, String fromNode, String toNode) {
        return new PlanStep(Kind.MOVE, pod, fromNode, toNode, null);
    }

    public static PlanStep place(PodSpec pod, String toNode) {
        return new PlanStep(Kind.PLACE, pod, null, toNode, null);
    }
}
