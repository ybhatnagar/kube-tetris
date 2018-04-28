package com.vmware.borathon;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.borathon.balancer.WorkLoadBalancer;
import com.vmware.borathon.balancer.WorkLoadBalancerImpl;
import com.vmware.borathon.balancer.WorkLoadBalancerUtil;
import com.vmware.borathon.loadsimulator.NodeDataGenerator;

@RunWith(Parameterized.class)
public class WorkloadBalancerTest {
    private static final Logger log = LoggerFactory.getLogger(WorkloadBalancerTest.class);

    private static MigrationController migrationController;

    private static int iteration_count;

    private static BufferedWriter writer;

    private static WorkLoadBalancerUtil workLoadBalancerUtil;

    private StringBuilder sb;

    @Parameterized.Parameters
    public static Object[][] data() {
        return new Object[100][0];
    }

    @BeforeClass
    public static void setup() throws IOException {
        writer = new BufferedWriter(new FileWriter("Validate_Algo.txt"));
        iteration_count = 0;
        workLoadBalancerUtil = new WorkLoadBalancerUtil();
        writer.write("Recording results...................\n");
    }

    @Before
    public void prepare() throws Exception{
        //Create MigrationController and nodes and pods
        migrationController = new MigrationControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generate(3, 5);
        inputNodes.forEach(node -> migrationController.addNode(node));
        sb = new StringBuilder();
        sb.append("===============================================================Iteration : ").append(iteration_count).append("==================================================================\n");
        sb.append("Nodes information before balancing with entropy : ").append(workLoadBalancerUtil
                .getSystemEntropy(migrationController.getNodes(), migrationController.getPivotRatio())).append("\n");
        migrationController.getNodes().forEach(n -> {
            sb.append(n).append("\n");
            sb.append(n.getPods()).append("\n");
        });
        writer.append(sb.toString());
    }

    @Test
    public void scenarioTest() throws Exception{
        WorkLoadBalancer workLoadBalancer = new WorkLoadBalancerImpl(migrationController, 5);
        workLoadBalancer.balance();
        sb = new StringBuilder();
        sb.append("\nNodes information after balancing with entropy : ").append(workLoadBalancerUtil
                .getSystemEntropy(migrationController.getNodes(), migrationController.getPivotRatio())).append("\n");
        migrationController.getNodes().forEach(n -> {
            sb.append(n).append("\n");
            sb.append(n.getPods()).append("\n");
        });
        writer.append(sb.toString());
        sb.append("=======================================================================================================================================================================\n");
        ++iteration_count;
    }

    @After
    public void increment() throws Exception{

    }
}