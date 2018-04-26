package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NodeDataGenerator {
    static final long NODE_CPU_IN_MILI_CORE = 8000;
    static final long NODE_MEMORY_IN_MB = 32000;


    public static List<Node> generate(int nodes, int podOnEachNode) {
        List<Node> kubeEnv = new ArrayList<>();
        for(int node=0; node < nodes; node++) {
            Node node1 = new Node(node, node+"", NODE_MEMORY_IN_MB, NODE_CPU_IN_MILI_CORE);
            for (int pod=0; pod < podOnEachNode; pod++) {
                boolean added = node1.addPod(generatePod(pod));
                if (!added)
                    break;
            }
            kubeEnv.add(node1);
        }
        return kubeEnv;
    }

    private static Pod generatePod(int podId) {
        int cpuMiliCoreForPod = generateRandomIntBetween(1, 20) * 50;
        int memMB = generateRandomIntBetween(1, 20) * 100;
        return new Pod(podId,podId+"" ,memMB, cpuMiliCoreForPod);
    }

    private static int generateRandomIntBetween(int start, int end) {
        Random r = new Random();
        return r.nextInt(end-start) + start;
    }
}
