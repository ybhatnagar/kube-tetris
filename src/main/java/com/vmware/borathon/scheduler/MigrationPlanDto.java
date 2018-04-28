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
    Pod pod;
    int fromNode;
    int toNode;
}
