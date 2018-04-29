package com.vmware.borathon.balancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vmware.borathon.SystemController;
import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;

public class WorkLoadBalancerImpl implements WorkLoadBalancer{

    private static final Logger log = LoggerFactory.getLogger(WorkLoadBalancerImpl.class);

    private static int currIterations = 1;

    private SystemController controller;
    private int ITERATIONS;

    public WorkLoadBalancerImpl(SystemController controller, int iterations){
        this.ITERATIONS = iterations;
        this.controller = controller;
    }

    // check if in the system all are on each side of pivot ratio
    private boolean isSchedulingDone(double pivotRatio){
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

    private boolean swapIfPossible(Node nodeA, Node nodeB, Pod podA, Pod podB, double pivotRatio ){

        double entropyBeforeSwap = controller.getSystemEntropy();
        double entropyAfterSwap;

        boolean aRemovedFromA = nodeA.removePod(podA);
        boolean bRemovedFromB = nodeB.removePod(podB);

        if(aRemovedFromA && bRemovedFromB) {
            boolean bAddedToA = nodeA.addPod(podB);
            boolean aAddedToB = nodeB.addPod(podA);

            if (bAddedToA && aAddedToB) {
                //Swap is successful, check for entropy again
                entropyAfterSwap = controller.getSystemEntropy();
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
                //Populate MigrationPlanDto here
                log.info("swap is successful for node {} , pod {} and node {} , pod {}" ,nodeA, podA, nodeB, podB);


                log.info("Swap is successful and entropy changed from {} to {}", entropyBeforeSwap, entropyAfterSwap);
                return true;
            } else if(bAddedToA){
                nodeA.removePod(podB);
                if (nodeA.addPod(podA) && nodeB.addPod(podB)) {
                    log.info("Swap reverted...");
                }
                return false;
            } else if(aAddedToB){
                nodeB.removePod(podA);
                if (nodeA.addPod(podA) && nodeB.addPod(podB)) {
                    log.info("Swap reverted...");
                }
                return false;
            }
            else {
                log.info("Swap failed since available capacity was less");
                if (nodeA.addPod(podA) && nodeB.addPod(podB)) {
                    log.info("Swap reverted...");
                }
                return false;
            }
        } else if(aRemovedFromA){
            if(nodeA.addPod(podA))
                log.info("Swap reverted...");
            return false;
        } else if(bRemovedFromB){
            if(nodeB.addPod(podB))
                log.info("Swap reverted...");
            return false;
        } else{
            return false;
        }
    }

    // actual scheduler
    public void balance(){
        double pivotRatio = controller.getPivotRatio();
        double entropyBeforeBalancing = controller.getSystemEntropy();
        log.debug("Nodes information before balancing : ");
        controller.getNodes().forEach(n -> {
            log.debug("{}", n);
            log.debug("{}", n.getPods());
        });

        while (currIterations <= ITERATIONS && !isSchedulingDone(pivotRatio)){

            log.info("This is the {} iteration" , currIterations);

            List<Node> sortedNodes = controller.getNodesSortedByRatio();
            int left=0,right = sortedNodes.size()-1;

            //Recompute the distance for both the nodes because after swap pivotRatio might have changed
            double leftNodeDistance = sortedNodes.get(left).getDistanceFromPivot(pivotRatio);
            double rightNodeDistance = sortedNodes.get(right).getDistanceFromPivot(pivotRatio);

            while (sortedNodes.get(left).getAvailableCapacity().getCpuMemoryRatio() < pivotRatio && sortedNodes.get(right).getAvailableCapacity().getCpuMemoryRatio() > pivotRatio){

                //sort first by mem and last by cpu
                List<Pod> podsMemSorted = sortedNodes.get(left).getPodsSortedByMem();
                List<Pod> podsCpuSorted = sortedNodes.get(right).getPodsSortedByCpu();

                int leftPods = 0;
                int rightPods = 0;

                boolean isSwapped = false;
                while(leftPods < podsMemSorted.size() && rightPods < podsCpuSorted.size()){

                    double pivotRatioBeforeSwap = controller.getPivotRatio();
                    isSwapped = swapIfPossible(sortedNodes.get(left), sortedNodes.get(right), podsMemSorted.get(leftPods), podsCpuSorted.get(rightPods),pivotRatio);
                    double pivotRatioAfterSwap = controller.getPivotRatio();

                    if(isSwapped){
                        if(pivotRatioAfterSwap > pivotRatioBeforeSwap)
                            log.error("After swap entropy should not be coming more, pivotRatioBeforeSwap:{}, pivotRatioAfterSwap:{}", pivotRatioBeforeSwap, pivotRatioAfterSwap);
                        log.info("Swap done and entropy reduced to better value");

                        pivotRatio = controller.getPivotRatio();
                        break;
                    } else{
                        if(pivotRatioAfterSwap > pivotRatioBeforeSwap)
                            log.error("After swap entropy should not be coming more, pivotRatioBeforeSwap:{}, pivotRatioAfterSwap:{}", pivotRatioBeforeSwap, pivotRatioAfterSwap);

                        log.info("swap failed for node {} , pod {} and node {} , pod {}" ,sortedNodes.get(left), podsMemSorted.get(leftPods), sortedNodes.get(right), podsCpuSorted.get(rightPods));
                        log.info("continuing with next");
                    }

                    if (leftNodeDistance < rightNodeDistance){
                        if(leftPods<podsMemSorted.size()-1){
                            leftPods++;
                        }else{
                            leftPods=0;
                            rightPods++;
                        }
                    }else{
                        if(rightPods<podsCpuSorted.size()-1){
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
            currIterations++;
        }
        double entropyAfterBalancing = controller.getSystemEntropy();
        log.info("System entropy before  balancing : {} ", entropyBeforeBalancing);
        log.info("System entropy after  balancing : {} ", entropyAfterBalancing);
        log.debug("Nodes information after balancing : ");
        controller.getNodes().forEach(n -> {
            log.debug("{}", n);
            log.debug("{}", n.getPods());
        });
    }
}
