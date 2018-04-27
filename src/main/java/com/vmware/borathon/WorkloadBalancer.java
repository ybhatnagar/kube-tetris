package com.vmware.borathon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WorkloadBalancer {

    private static final Logger log = LoggerFactory.getLogger(WorkloadBalancer.class);

    public static double pivotRatio;
    public static int ITERATIONS = 50;
    public static int currIterations = 1;

    private WorkloadBalancerUtil workloadBalancerUtil;
    private MigrationController controller;

    public WorkloadBalancer(MigrationController controller){
        workloadBalancerUtil = new WorkloadBalancerUtil();
        this.controller = controller;
        pivotRatio = workloadBalancerUtil.getPivotRatio(controller.getNodes());
    }

    // check if in the system all are on each side of pivot ratio
    public boolean isSchedulingDone(){
        List<Double> values = new ArrayList<>();
        controller.getNodes().forEach(node ->{
            values.add(pivotRatio - node.getAvailableCapacity().getCpuMemoryRatio());
        });
        boolean result = values.stream().filter(Objects::nonNull).allMatch(i -> i < 0) || values.stream().filter(Objects::nonNull).allMatch(i -> i > 0);

        if(result){
            log.info("all the nodes are on one side of thepivot ratio. No mode balancing of CPU/Memory possible");
        }
        return result;
    }

    private boolean swapIfPossible(Node nodeA, Node nodeB, Pod podA, Pod podB ){

        double entropyBeforeSwap = workloadBalancerUtil.getSystemEntropy(controller.getNodes(), pivotRatio);
        double entropyAfterSwap;
        if(nodeA.removePod(podA) && nodeB.removePod(podB)) {
            if (nodeA.addPod(podB) && nodeB.addPod(podA)) {
                //Swap is successful, check for entropy again
                entropyAfterSwap = workloadBalancerUtil.getSystemEntropy(controller.getNodes(), pivotRatio);
                if (entropyAfterSwap >= entropyBeforeSwap) {
                    //Revert swapping
                    if (nodeA.removePod(podB) && nodeB.removePod(podA)) {
                        if (nodeA.addPod(podA) && nodeB.addPod(podB)) {
                            log.info("Swap reverted since new entropy {} was greater than old {}", entropyAfterSwap, entropyBeforeSwap);
                        } else {
                            log.info("Swap revert failed do not proceed further and exit");
                        }
                    }
                    return false;
                }
                log.info("Swap is successful...");
                return true;
            } else {
                log.info("Swap failed since available capacity was less");
                nodeA.removePod(podB);
                nodeB.removePod(podA);
                if (nodeA.addPod(podA) && nodeB.addPod(podB)) {
                    log.info("Swap reverted...");
                }
                return false;
            }
        } else{
            log.info("Should not have come here....pods removal failed!!");
            return false;
        }
    }



    // actual scheduler
    public void schedule(){
        while (currIterations++ <= ITERATIONS && !isSchedulingDone()){
            log.info("This is the {} iteration" , currIterations);

            List<Node> sortedNodes = controller.getNodesSortedByRatio();
            controller.getNodesSortedByRatio();
            int left=0,right = sortedNodes.size()-1;

            double leftNodeDistance = workloadBalancerUtil.getDistanceFromPivot(sortedNodes.get(left), pivotRatio);
            double rightNodeDistance = workloadBalancerUtil.getDistanceFromPivot(sortedNodes.get(right), pivotRatio);

            while (sortedNodes.get(left).getAvailableCapacity().getCpuMemoryRatio() < pivotRatio && sortedNodes.get(right).getAvailableCapacity().getCpuMemoryRatio() > pivotRatio){

                //sort first by mem and last by cpu
                List<Pod> podsMemSorted = sortedNodes.get(left).getPodsSortedByMem();
                List<Pod> podsCpuSorted = sortedNodes.get(right).getPodsSortedByCpu();

                int leftPods = 0;
                int rightPods = 0;

                boolean isSwapped = false;
                while(leftPods < podsCpuSorted.size() && rightPods < podsMemSorted.size()){

                    isSwapped = swapIfPossible(sortedNodes.get(left), sortedNodes.get(right), podsMemSorted.get(0), podsCpuSorted.get(0));

                    if(isSwapped){
                        log.info("Swap done and entropy reduced to better value");
                        break;
                    } else{
                        log.info("swap failed for node {} , pod {} and node {} , pod {}" ,sortedNodes.get(0).getName(),podsMemSorted.get(0).getName(),sortedNodes.get(sortedNodes.size()-1).getName(),podsCpuSorted.get(0).getName());
                        log.info("continuing with next");
                    }

                    if (leftNodeDistance < rightNodeDistance){
                        if(leftPods<podsCpuSorted.size()-1){
                            leftPods++;
                        }else{
                            leftPods=0;
                            rightPods++;
                        }
                    }else{
                        if(rightPods<podsMemSorted.size()-1){
                            rightPods++;
                        }else{
                            rightPods=0;
                            leftPods++;
                        }
                    }
                }

                if(isSwapped){
                    break;
                }

                if(leftNodeDistance < rightNodeDistance){
                    if(sortedNodes.get(left+1).getAvailableCapacity().getCpuMemoryRatio() < pivotRatio ){
                        left++;
                    }else{
                        left=0;
                        right--;
                    }
                }else{
                    if(sortedNodes.get(right-1).getAvailableCapacity().getCpuMemoryRatio() > pivotRatio ){
                        right--;
                    }else{
                        right=sortedNodes.size()-1;
                        left++;
                    }
                }

            }
        }
    }

}
