package com.vmware.borathon;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;
import com.vmware.borathon.loadsimulator.NodeDataGenerator;

@Slf4j
public class Main {
    public static void main(String[] args) {
        //Create SystemController and nodes and pods
        SystemController systemController = new SystemControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generate(10, 300);
        inputNodes.forEach(node -> systemController.addNode(node));
        triggerWorkLoadBalancer(systemController, 50);
    }


    private static void triggerWorkLoadBalancer(SystemController systemController, int iterations){
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(systemController, 50);
        workLoadBalancer.balance();
    }
}
