package com.vmware.borathon;

import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;

public class WorkloadBalancerTest {
    private static final Logger log = LoggerFactory.getLogger(WorkloadBalancerTest.class);

    private static MigrationController migrationController;

    @BeforeClass
    public static void setup(){

        //Create MigrationController and nodes and pods
        migrationController = new MigrationControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generate(50, 20);
        inputNodes.forEach(node -> migrationController.addNode(node));
    }

    @Before
    public void prepare(){

    }

    @Test
    public void scenarioTest() throws Exception{
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(migrationController, 50);
        workLoadBalancer.balance();
    }
}