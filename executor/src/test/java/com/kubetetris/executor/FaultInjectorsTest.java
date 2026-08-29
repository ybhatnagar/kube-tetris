package com.kubetetris.executor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaultInjectorsTest {

    @Test
    void noneNeverFails() {
        FaultInjector fi = FaultInjectors.none();
        for (FailurePoint p : FailurePoint.values()) {
            assertThat(fi.shouldFail(0, p)).isNull();
            assertThat(fi.shouldFail(5, p)).isNull();
        }
    }

    @Test
    void atFailsExactlyTheTargetMoveAndLeg() {
        FaultInjector fi = FaultInjectors.at(1, FailurePoint.EVICT, "boom");
        assertThat(fi.shouldFail(0, FailurePoint.EVICT)).isNull();
        assertThat(fi.shouldFail(1, FailurePoint.EVICT)).isEqualTo("boom");
        assertThat(fi.shouldFail(1, FailurePoint.WAIT_READY)).isNull();
        assertThat(fi.shouldFail(2, FailurePoint.EVICT)).isNull();
    }
}
