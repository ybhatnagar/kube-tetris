package com.vmware.borathon;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;
import com.vmware.borathon.loadsimulator.NodeDataGenerator;

public class WorkloadBalancerTest {

    private static SystemController systemController;

    @BeforeClass
    public static void setup() throws IOException {

    }

    @Before
    public void prepare() throws Exception{
        //Create SystemController and nodes and pods
        systemController = new SystemControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generate(3, 5);
        inputNodes.forEach(node -> systemController.addNode(node));
    }

    @Test
    public void scenarioTest() throws Exception{
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(systemController, 5);
        workLoadBalancer.balance();
    }
}