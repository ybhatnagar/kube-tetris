package com.kubetetris.collector.internal;

import io.fabric8.kubernetes.api.model.Quantity;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Kubernetes {@link Quantity} → engine units.
 *
 * <ul>
 *   <li>CPU is expressed in <b>millicores</b>. {@code "500m"} → 500, {@code "2"} → 2000.</li>
 *   <li>Memory is expressed in <b>MiB</b> (binary). {@code "1Gi"} → 1024, {@code "512Mi"} → 512.
 *       Sub-MiB values round down; the engine has no fractional units.</li>
 * </ul>
 */
public final class Quantities {

    private static final BigInteger MIB = BigInteger.valueOf(1024L * 1024L);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000L);

    private Quantities() {}

    public static long cpuMillicores(Quantity q) {
        if (q == null) return 0L;
        BigDecimal cores = q.getNumericalAmount();
        if (cores == null) return 0L;
        return cores.multiply(THOUSAND).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long memoryMib(Quantity q) {
        if (q == null) return 0L;
        BigDecimal bytes = q.getNumericalAmount();
        if (bytes == null) return 0L;
        BigInteger asBytes = bytes.toBigInteger();
        return asBytes.divide(MIB).longValueExact();
    }
}
