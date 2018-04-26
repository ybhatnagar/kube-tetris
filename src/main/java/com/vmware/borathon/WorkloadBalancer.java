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

    public boolean isSwappable(Node nodeA, Node nodeB, int podIdA, int podIdB ){

        long cpuAvailableAfterPodAGone = nodeA.getAvailableCapacity().getCpuMillicore() + nodeA.getPods().get(podIdA).getRequest().getCpuMillicore();
        long memAvailableAfterPodAGone = nodeA.getAvailableCapacity().getMemoryMB() + nodeA.getPods().get(podIdA).getRequest().getMemoryMB();

        long cpuAvailableAfterPodBGone = nodeB.getAvailableCapacity().getCpuMillicore() + nodeB.getPods().get(podIdB).getRequest().getCpuMillicore();
        long memAvailableAfterPodBGone = nodeB.getAvailableCapacity().getMemoryMB() + nodeB.getPods().get(podIdB).getRequest().getMemoryMB();


        long cpuRequiredByPodIdB = nodeB.getPods().get(podIdB).getRequest().getCpuMillicore();
        long memoryRequiredByPodIdB = nodeB.getPods().get(podIdB).getRequest().getMemoryMB();

        long cpuRequiredByPodIdA = nodeA.getPods().get(podIdA).getRequest().getCpuMillicore();
        long memoryRequiredByPodIdA = nodeA.getPods().get(podIdA).getRequest().getMemoryMB();


        if(cpuAvailableAfterPodAGone < cpuRequiredByPodIdB || memAvailableAfterPodAGone < memoryRequiredByPodIdB)
            return false; //pod B cannot fit at A
        else if(cpuAvailableAfterPodBGone < cpuRequiredByPodIdA || memAvailableAfterPodBGone < memoryRequiredByPodIdA)
            return false; //pod a cannot fit at B
        else{
            //change the memory and cpu after the swap
            nodeA.setAvailableCapacity(new Capacity(memAvailableAfterPodAGone-memoryRequiredByPodIdB,cpuAvailableAfterPodAGone - cpuRequiredByPodIdB));
            nodeB.setAvailableCapacity(new Capacity(memAvailableAfterPodBGone-memoryRequiredByPodIdA,cpuAvailableAfterPodBGone - cpuRequiredByPodIdA));
            return true;
        }
    }


}
