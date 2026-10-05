package com.kubetetris.api.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the file-persistence path of {@link ClusterRegistry} (set via
 * {@code kubetetris.registry.path}). The in-memory path is already covered end-to-end
 * by the http integration tests.
 */
class ClusterRegistryPersistenceTest {

    @Test
    void mutationsSurviveARestart(@TempDir Path tmp) {
        ClusterRegistry first = new ClusterRegistry(tmp.toString());
        first.loadFromDisk();
        ClusterRecord created = first.create("prod", "https://10.0.0.1:6443",
                "kubeconfig", "secret-ref", "/path/to/kubeconfig");

        assertThat(tmp.resolve(created.id() + ".json")).exists();

        ClusterRegistry second = new ClusterRegistry(tmp.toString());
        second.loadFromDisk();
        assertThat(second.find(created.id())).isPresent();
        assertThat(second.find(created.id()).orElseThrow().name()).isEqualTo("prod");
    }

    @Test
    void deleteRemovesTheFile(@TempDir Path tmp) {
        ClusterRegistry reg = new ClusterRegistry(tmp.toString());
        reg.loadFromDisk();
        ClusterRecord created = reg.create("prod", null, "kubeconfig", null, null);
        Path file = tmp.resolve(created.id() + ".json");
        assertThat(file).exists();

        assertThat(reg.remove(created.id())).isTrue();
        assertThat(file).doesNotExist();
    }

    @Test
    void seqContinuesAfterReloadWithoutIdCollisions(@TempDir Path tmp) {
        ClusterRegistry first = new ClusterRegistry(tmp.toString());
        first.loadFromDisk();
        ClusterRecord a = first.create("a", null, "kubeconfig", null, null);
        ClusterRecord b = first.create("b", null, "kubeconfig", null, null);

        ClusterRegistry second = new ClusterRegistry(tmp.toString());
        second.loadFromDisk();
        ClusterRecord c = second.create("c", null, "kubeconfig", null, null);

        assertThat(c.id()).isNotEqualTo(a.id());
        assertThat(c.id()).isNotEqualTo(b.id());
    }

    @Test
    void nullPathIsInMemoryOnly(@TempDir Path tmp) {
        ClusterRegistry reg = new ClusterRegistry(null);
        reg.loadFromDisk();
        reg.create("prod", null, "kubeconfig", null, null);
        assertThat(tmp.resolve("c1.json")).doesNotExist();
    }
}
