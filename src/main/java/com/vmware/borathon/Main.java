package com.vmware.borathon;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;

@Slf4j
public class Main {
    public static void main(String[] args) {
        //Create MigrationController and nodes and pods
        MigrationController migrationController = new MigrationControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generate(10, 300);
        inputNodes.forEach(node -> migrationController.addNode(node));
    }


    private void triggerWorkLoadBalancer(MigrationController migrationController, int iterations){
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(migrationController, 50);
        workLoadBalancer.balance();
    }
}
