package com.kubetetris.engine.domain;

/**
 * One step in a scheduler plan. Mirrors PlanStepDTO in doc 04.
 * MOVE: evict {@code pod} from {@code fromNode}, place on {@code toNode}.
 * PLACE: place a currently-pending {@code pod} on {@code toNode} (no source).
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
