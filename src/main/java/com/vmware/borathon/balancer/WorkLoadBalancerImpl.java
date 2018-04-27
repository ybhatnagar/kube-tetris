package com.vmware.borathon.balancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vmware.borathon.MigrationController;
import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;

public class WorkLoadBalancerImpl implements WorkLoadBalancer{

    private static final Logger log = LoggerFactory.getLogger(WorkLoadBalancerImpl.class);

    private static int currIterations = 1;

    private WorkLoadBalancerUtil workLoadBalancerUtil;
    private MigrationController controller;
    private int ITERATIONS = 50;

    public WorkLoadBalancerImpl(MigrationController controller, int iterations){
        workLoadBalancerUtil = new WorkLoadBalancerUtil();
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

    private boolean swapIfPossible(Node nodeA, Node nodeB, Pod podA, Pod podB,double pivotRatio ){

        double entropyBeforeSwap = workLoadBalancerUtil.getSystemEntropy(controller.getNodes(), pivotRatio);
        double entropyAfterSwap;
        if(nodeA.removePod(podA) && nodeB.removePod(podB)) {
            if (nodeA.addPod(podB) && nodeB.addPod(podA)) {
                //Swap is successful, check for entropy again
                entropyAfterSwap = workLoadBalancerUtil.getSystemEntropy(controller.getNodes(), pivotRatio);
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
    public void balance(){
        double pivotRatio = controller.getPivotRatio();
        double entropyBeforeBalancing = workLoadBalancerUtil.getSystemEntropy(controller.getNodes(), pivotRatio);
        log.info("Nodes information before balancing : ");
        log.info("{}", controller.getNodes());

        while (currIterations <= ITERATIONS && !isSchedulingDone(pivotRatio)){

            log.info("This is the {} iteration" , currIterations);

            List<Node> sortedNodes = controller.getNodesSortedByRatio();
            controller.getNodesSortedByRatio();
            int left=0,right = sortedNodes.size()-1;

            double leftNodeDistance = workLoadBalancerUtil.getDistanceFromPivot(sortedNodes.get(left), pivotRatio);
            double rightNodeDistance = workLoadBalancerUtil.getDistanceFromPivot(sortedNodes.get(right), pivotRatio);

            while (sortedNodes.get(left).getAvailableCapacity().getCpuMemoryRatio() < pivotRatio && sortedNodes.get(right).getAvailableCapacity().getCpuMemoryRatio() > pivotRatio){

                //sort first by mem and last by cpu
                List<Pod> podsMemSorted = sortedNodes.get(left).getPodsSortedByMem();
                List<Pod> podsCpuSorted = sortedNodes.get(right).getPodsSortedByCpu();

                int leftPods = 0;
                int rightPods = 0;

                boolean isSwapped = false;
                while(leftPods < podsCpuSorted.size() && rightPods < podsMemSorted.size()){

                    isSwapped = swapIfPossible(sortedNodes.get(left), sortedNodes.get(right), podsMemSorted.get(0), podsCpuSorted.get(0),pivotRatio);

                    if(isSwapped){
                        log.info("Swap done and entropy reduced to better value");
                        break;
                    } else{
                        log.info("swap failed for node {} , pod {} and node {} , pod {}" ,sortedNodes.get(0), podsMemSorted.get(0), sortedNodes.get(sortedNodes.size()-1), podsCpuSorted.get(0));
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
            pivotRatio = controller.getPivotRatio();
            currIterations++;
        }
        log.info("System entropy before balancing : {} ", entropyBeforeBalancing);
        double entropyAfterBalancing = workLoadBalancerUtil.getSystemEntropy(controller.getNodes(), pivotRatio);
        log.info("System entropy after balancing : {} ", entropyAfterBalancing);
        log.info("Nodes information after balancing : ");
        log.info("{}", controller.getNodes());
    }
}
