package com.kubetetris;

import java.io.IOException;
import java.util.List;

import com.kubetetris.balancer.WorkLoadBalancer;
import com.kubetetris.balancer.WorkLoadBalancerImpl;
import com.kubetetris.loadsimulator.NodeDataGenerator;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class WorkloadBalancerTest {

    private static SystemController systemController;

    @BeforeClass
    public static void setup() throws IOException {

    }

    @Before
    public void prepare() throws Exception{
        //Create SystemController and nodes and pods
        systemController = new SystemControllerImpl();
        //List<Node> inputNodes = NodeDataGenerator.generate(3, 5);
        List<Node> inputNodes = NodeDataGenerator.generateFixedReal();
        inputNodes.forEach(node -> systemController.addNode(node));
    }

    @Test
    public void scenarioTest() throws Exception{
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(systemController, 5);
        workLoadBalancer.balance();
    }
}