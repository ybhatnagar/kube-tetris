package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WorkloadBalancer {

    private List<Node> nodes;

    public static double pivotRatio;
    public static int ITERATIONS = 50;
    public static int currIterations = 1;

    private WorkloadBalancerUtil workloadBalancerUtil;

    public WorkloadBalancer(MigrationController controller){
        workloadBalancerUtil = new WorkloadBalancerUtil();
        nodes = controller.getNodesSortedByRatio();
        pivotRatio = workloadBalancerUtil.getPivotRatio(nodes);
    }

    // check if in the system all are on each side of pivot ratio
    public boolean isSchedulingDone(){
        List<Double> values = new ArrayList<>();
        nodes.forEach(node ->{
            values.add(pivotRatio - node.getAvailableCapacity().getCpuMemoryRatio());
        });
        return values.stream().filter(Objects::nonNull).allMatch(i -> i < 0) || values.stream().filter(Objects::nonNull).allMatch(i -> i > 0);
    }

    public boolean isSwappable(Node nodeA, Node nodeB, int podIdA, int podIdB ){
        Capacity oldAvailableCapacityForA = nodeA.getAvailableCapacity();
        Capacity oldAvailableCapacityForB = nodeB.getAvailableCapacity();
        double entropyBeforeSwap = workloadBalancerUtil.getSystemEntropy(nodes, pivotRatio);

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
            Capacity newAvailableCapacityForA = new Capacity(memAvailableAfterPodAGone-memoryRequiredByPodIdB,cpuAvailableAfterPodAGone - cpuRequiredByPodIdB);
            Capacity newAvailableCapacityForB = new Capacity(memAvailableAfterPodBGone-memoryRequiredByPodIdA,cpuAvailableAfterPodBGone - cpuRequiredByPodIdA);

            nodeA.setAvailableCapacity(newAvailableCapacityForA);
            nodeB.setAvailableCapacity(newAvailableCapacityForB);

            double entropyAfterSwap = workloadBalancerUtil.getSystemEntropy(nodes, pivotRatio);
            if(entropyAfterSwap >= entropyBeforeSwap) {
                //Revert swapping
                nodeA.setAvailableCapacity(oldAvailableCapacityForA);
                nodeB.setAvailableCapacity(oldAvailableCapacityForB);
                return false;
            }
            //TODO : pods ki adla badli karna hai satya bhejne se pehle
            return true;
        }
    }

    // actual scheduler
    public void schedule(){
        while (currIterations++ <= ITERATIONS && !isSchedulingDone()){
            //sort first by mem and last by cpu
            List<Pod> podsMemSorted = nodes.get(0).getPodsSortedByMem();
            List<Pod> podsCpuSorted = nodes.get(nodes.size()-1).getPodsSortedByCpu();

            isSwappable(nodes.get(0), nodes.get(nodes.size()-1), podsMemSorted.get(0).getId(), podsCpuSorted.get(0).getId());

        }
    }

}
