package com.kubetetris.collector.internal;

import com.kubetetris.engine.domain.ResourceReq;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestsCalculatorTest {

    @Test
    void sumsContainerRequests() {
        var pod = new PodBuilder()
                .withNewMetadata().withName("p").endMetadata()
                .withNewSpec()
                    .addToContainers(container("app", "200m", "256Mi"))
                    .addToContainers(container("proxy", "100m", "128Mi"))
                .endSpec()
                .build();

        ResourceReq r = RequestsCalculator.forPod(pod);
        assertThat(r).isEqualTo(new ResourceReq(300L, 384L));
    }

    @Test
    void regularInitContainerContributesTheMaxNotTheSum() {
        var pod = new PodBuilder()
                .withNewMetadata().withName("p").endMetadata()
                .withNewSpec()
                    .addToContainers(container("app", "100m", "100Mi"))
                    .addToInitContainers(container("init-a", "500m", "50Mi"))
                    .addToInitContainers(container("init-b", "200m", "300Mi"))
                .endSpec()
                .build();

        ResourceReq r = RequestsCalculator.forPod(pod);
        // container sum: 100/100. init max: 500 cpu, 300 mem. Effective = max of each.
        assertThat(r).isEqualTo(new ResourceReq(500L, 300L));
    }

    @Test
    void sidecarInitContainerIsSummedInsteadOfMaxed() {
        var container = container("app", "100m", "100Mi");
        var sidecar = new ContainerBuilder(container("sidecar", "150m", "80Mi"))
                .withRestartPolicy("Always").build();

        var pod = new PodBuilder()
                .withNewMetadata().withName("p").endMetadata()
                .withNewSpec()
                    .addToContainers(container)
                    .addToInitContainers(sidecar)
                .endSpec()
                .build();

        ResourceReq r = RequestsCalculator.forPod(pod);
        assertThat(r).isEqualTo(new ResourceReq(250L, 180L));
    }

    @Test
    void missingRequestsAreTreatedAsZero() {
        var pod = new PodBuilder()
                .withNewMetadata().withName("p").endMetadata()
                .withNewSpec()
                    .addToContainers(new ContainerBuilder().withName("app").build())
                .endSpec()
                .build();
        assertThat(RequestsCalculator.forPod(pod)).isEqualTo(ResourceReq.ZERO);
    }

    private static io.fabric8.kubernetes.api.model.Container container(String name, String cpu, String mem) {
        return new ContainerBuilder()
                .withName(name)
                .withResources(new ResourceRequirementsBuilder()
                        .addToRequests("cpu", new io.fabric8.kubernetes.api.model.Quantity(cpu))
                        .addToRequests("memory", new io.fabric8.kubernetes.api.model.Quantity(mem))
                        .build())
                .build();
    }
}
