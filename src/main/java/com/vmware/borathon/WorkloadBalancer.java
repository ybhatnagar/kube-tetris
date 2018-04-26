package com.vmware.borathon;

import java.util.List;

public class WorkloadBalancer {

    private List<Node> nodes;

    public static double pivotRatio;
    public static double smallestEntropy;

    public WorkloadBalancer(MigrationController controller){
        nodes = controller.getNodes();
        pivotRatio=getPivotRatio();
        smallestEntropy=pivotRatio;
    }

    public double getPivotRatio(){
        long totalAvailableCpu = 0, totalAvailableMem=0;

        for(int i=0;i<nodes.size();i++){
            totalAvailableCpu += nodes.get(i).getAvailableCapacity().getCpuMillicore();
            totalAvailableMem += nodes.get(i).getAvailableCapacity().getMemoryMB();
        }
        return totalAvailableCpu/totalAvailableMem;
    }

    public double getEntropy(List<Node> nodes){
        double sum = 0;
        for (int i=0;i<nodes.size();i++){
            sum += Math.abs(pivotRatio-nodes.get(i).getAvailableCapacity().getCpuMemoryRatio());
        }
        return sum;
    }

    


}
