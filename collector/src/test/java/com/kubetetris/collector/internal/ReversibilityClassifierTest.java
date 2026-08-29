package com.kubetetris.collector.internal;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReversibilityClassifierTest {

    @Test
    void deploymentPodIsReversible() {
        Pod p = ownedBy("Deployment", "web");
        assertThat(ReversibilityClassifier.isReversible(p)).isTrue();
        assertThat(ReversibilityClassifier.isSystemCritical(p)).isFalse();
    }

    @Test
    void statefulSetPodIsNotReversible() {
        Pod p = ownedBy("StatefulSet", "db");
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void daemonSetPodIsNotReversible() {
        Pod p = ownedBy("DaemonSet", "kube-proxy");
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void barePodIsNotReversible() {
        Pod p = new PodBuilder().withNewMetadata().withName("bare").endMetadata()
                .withNewSpec().endSpec().build();
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void systemCriticalPodIsFlaggedRegardlessOfOwner() {
        Pod p = new PodBuilder()
                .withNewMetadata().withName("kube-scheduler").endMetadata()
                .withNewSpec().withPriorityClassName("system-cluster-critical").endSpec()
                .build();
        assertThat(ReversibilityClassifier.isSystemCritical(p)).isTrue();
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void mirrorPodAnnotationMarksNonReversible() {
        Pod p = new PodBuilder()
                .withNewMetadata().withName("kube-apiserver")
                    .withAnnotations(Map.of("kubernetes.io/config.mirror", "abc"))
                .endMetadata()
                .withNewSpec().endSpec()
                .build();
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void deploymentPodWithHostPathVolumeIsNotReversible() {
        Pod p = new PodBuilder(ownedBy("Deployment", "web"))
                .editSpec()
                    .addToVolumes(new VolumeBuilder().withName("data")
                            .withNewHostPath("/mnt/data", "DirectoryOrCreate").build())
                .endSpec()
                .build();
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void deploymentPodWithPvcIsNotReversible() {
        Pod p = new PodBuilder(ownedBy("Deployment", "web"))
                .editSpec()
                    .addToVolumes(new VolumeBuilder().withName("v")
                            .withNewPersistentVolumeClaim("pvc-1", false).build())
                .endSpec()
                .build();
        assertThat(ReversibilityClassifier.isReversible(p)).isFalse();
    }

    @Test
    void ownerKindPrefersTheControllerReference() {
        Pod p = new PodBuilder()
                .withNewMetadata().withName("p")
                    .addNewOwnerReference()
                        .withKind("ReplicaSet").withName("web-rs").withApiVersion("apps/v1")
                        .withController(true).withUid("rs-uid")
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec().endSpec()
                .build();
        assertThat(ReversibilityClassifier.ownerKind(p)).isEqualTo("ReplicaSet");
        assertThat(ReversibilityClassifier.ownerName(p)).isEqualTo("web-rs");
    }

    private static Pod ownedBy(String kind, String name) {
        return new PodBuilder()
                .withNewMetadata().withName("p-" + name)
                    .addNewOwnerReference()
                        .withKind(kind).withName(name).withApiVersion("apps/v1")
                        .withController(true).withUid("owner-uid")
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec().endSpec()
                .build();
    }
}
