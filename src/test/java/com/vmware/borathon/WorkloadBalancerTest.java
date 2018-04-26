package com.vmware.borathon;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkloadBalancerTest {
    private static final Logger log = LoggerFactory.getLogger(WorkloadBalancerTest.class);

    @BeforeClass
    public void setup(){
        MigrationController migrationController = new MigrationControllerImpl();
        Node node1 = new Node("Node1",15, 4000);
        Node node2 = new Node("Node2",15, 4000);
        Node node3 = new Node("Node3",15, 4000);

        //Add Nodes
        migrationController.addNode(node1);
        migrationController.addNode(node2);
        migrationController.addNode(node3);

        //Add containers
        Pod pod1 = new Pod("Pod1",2, 150);
        Pod pod2 = new Pod("Pod2",4, 400);
        Pod pod3 = new Pod("Pod3",100, 200);

        node1.addPod(pod1);
        node1.addPod(pod2);
        node1.addPod(pod3);
    }

    @Before
    public void prepare(){

    }

    @Test
    public void scenarioTest() throws Exception{

    }
}