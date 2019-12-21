package com.kubetetris.scheduler;

import lombok.Getter;
import lombok.Setter;

import com.kubetetris.Pod;

/**
 * This DTO says move Pod {pod} from Node with id {fromNode} to Node with Id {toNode}
 *
 * */

@Getter
@Setter
public class MigrationPlanDto {
    private static int instanceCounter = 0;

    private Pod pod;
    private String fromNode;
    private String toNode;
    private int order;

    public MigrationPlanDto(Pod pod, String fromNode, String toNode) {
        this.pod = pod;
        this.fromNode = fromNode;
        this.toNode = toNode;
        order = instanceCounter++;

    }

    @Override
    public String toString() {
        return "MigrationPlanDto{" +
                "pod=" + pod +
                ", fromNode=" + fromNode +
                ", toNode=" + toNode +
                ", order=" + order +
                '}';
    }
}
