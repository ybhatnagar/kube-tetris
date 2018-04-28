package com.vmware.borathon.scheduler;

import lombok.Getter;
import lombok.Setter;

import com.vmware.borathon.Pod;

/**
 * This DTO says move Pod {pod} from Node with id {fromNode} to Node with Id {toNode}
 *
 * */

@Getter
@Setter
public class MigrationPlanDto {
    private static int instanceCounter = 0;

    private Pod pod;
    private int fromNode;
    private int toNode;
    private int order;

    public MigrationPlanDto(Pod pod, int fromNode, int toNode) {
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
