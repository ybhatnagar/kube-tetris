package com.kubetetris.collector.internal;

import io.fabric8.kubernetes.api.model.Quantity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuantitiesTest {

    @Test
    void cpuStringsParseToMillicores() {
        assertThat(Quantities.cpuMillicores(new Quantity("500m"))).isEqualTo(500L);
        assertThat(Quantities.cpuMillicores(new Quantity("2"))).isEqualTo(2000L);
        assertThat(Quantities.cpuMillicores(new Quantity("1500m"))).isEqualTo(1500L);
        assertThat(Quantities.cpuMillicores(new Quantity("0"))).isEqualTo(0L);
    }

    @Test
    void memoryStringsParseToMib() {
        assertThat(Quantities.memoryMib(new Quantity("1Gi"))).isEqualTo(1024L);
        assertThat(Quantities.memoryMib(new Quantity("512Mi"))).isEqualTo(512L);
        assertThat(Quantities.memoryMib(new Quantity("2048Mi"))).isEqualTo(2048L);
    }

    @Test
    void nullQuantityYieldsZero() {
        assertThat(Quantities.cpuMillicores(null)).isEqualTo(0L);
        assertThat(Quantities.memoryMib(null)).isEqualTo(0L);
    }
}
