package com.vmware.borathon;

import java.util.List;

public class WorkloadBalancerUtil {

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
}
