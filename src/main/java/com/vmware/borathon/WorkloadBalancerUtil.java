package com.vmware.borathon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WorkloadBalancerUtil {

    private static final Logger log = LoggerFactory.getLogger(WorkloadBalancerUtil.class);

    public double getPivotRatio(List<Node> nodes){
        long totalAvailableCpu = 0, totalAvailableMem=0;

        for(int i=0;i<nodes.size();i++){
            totalAvailableCpu += nodes.get(i).getAvailableCapacity().getCpuMillicore();
            totalAvailableMem += nodes.get(i).getAvailableCapacity().getMemoryMB();
        }
        return totalAvailableCpu/totalAvailableMem;
    }

    public double getSystemEntropy(List<Node> nodes, double pivotRatio){
        double sum = 0;
        for (int i=0;i<nodes.size();i++){
            sum += Math.abs(pivotRatio - nodes.get(i).getAvailableCapacity().getCpuMemoryRatio());
        }
        return sum;
    }

    public double getDistanceFromPivot(Node node, double pivotratio){
        return Math.abs(pivotratio-node.getAvailableCapacity().getCpuMemoryRatio());
    }
}
