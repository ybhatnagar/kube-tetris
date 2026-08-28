package com.kubetetris.engine.domain;

public record ResourceReq(long cpuMillicore, long memoryMB) {

    public static final ResourceReq ZERO = new ResourceReq(0, 0);

    public ResourceReq plus(ResourceReq other) {
        return new ResourceReq(cpuMillicore + other.cpuMillicore, memoryMB + other.memoryMB);
    }

    public ResourceReq minus(ResourceReq other) {
        return new ResourceReq(cpuMillicore - other.cpuMillicore, memoryMB - other.memoryMB);
    }

    public boolean covers(ResourceReq need) {
        return cpuMillicore >= need.cpuMillicore && memoryMB >= need.memoryMB;
    }

    public boolean isZero() {
        return cpuMillicore == 0 && memoryMB == 0;
    }

    /**
     * CPU-to-memory ratio used by the balancer's entropy math. Clamped so degenerate
     * ratios stay finite: {@code mem==0 → 1_000_000} (very-high CPU-per-mem);
     * {@code cpu==0 → 0}.
     */
    public double cpuMemRatio() {
        if (memoryMB == 0) return 1_000_000d;
        if (cpuMillicore == 0) return 0d;
        return (double) cpuMillicore / (double) memoryMB;
    }
}
