package com.vmware.borathon.balancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import com.vmware.borathon.Node;

public class WorkLoadBalancerUtil {

    private static final Logger log = LoggerFactory.getLogger(WorkLoadBalancerUtil.class);

    public double getDistanceFromPivot(Node node, double pivotratio){
        return Math.abs(pivotratio-node.getAvailableCapacity().getCpuMemoryRatio());
    }
}
