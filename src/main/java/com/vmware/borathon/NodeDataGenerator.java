package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NodeDataGenerator {
    static final long NODE_CPU_IN_MILI_CORE = 8000;
    static final long NODE_MEMORY_IN_MB = 16000;


    public static List<Node> generate(int nodes, int podOnEachNode) {
        List<Node> kubeEnv = new ArrayList<>();
        for(int node=0; node < nodes; node++) {
            Node node1 = new Node(node, node+"", NODE_MEMORY_IN_MB, NODE_CPU_IN_MILI_CORE);
            for (int pod=0; pod < podOnEachNode; pod++) {
                boolean added = node1.addPod(generatePod(pod, node));
                if (!added)
                    break;
            }
            kubeEnv.add(node1);
        }
        return kubeEnv;
    }

    private static Pod generatePod(int podId, int prefix) {
        int cpuMiliCoreForPod = generateRandomIntBetween(1, 20) * 50;
        int memMB = generateRandomIntBetween(1, 20) * 100;
        return new Pod(podId,prefix+"_"+podId ,memMB, cpuMiliCoreForPod);
    }

    private static int generateRandomIntBetween(int start, int end) {
        Random r = new Random();
        return r.nextInt(end-start) + start;
    }

    public static List<Node> generateFixed() {
        List<Node> kubeEnv = new ArrayList<>();
        Node node = new Node(0, "first", 2100, 1100);
        Pod pod1 = new Pod(0, "first_first", 200, 50);
        Pod pod2 = new Pod(1, "first_second", 400, 250);
        Pod pod3 = new Pod(2, "first_third", 700, 300);
        Pod pod4 = new Pod(3, "first_fourth", 500, 300);
        Pod pod5 = new Pod(4, "first_fifth", 150, 100);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);node.addPod(pod5);
        kubeEnv.add(node);
        node = new Node(1, "second", 2100, 1100);
        pod1 = new Pod(0, "second_first", 100, 200);
        pod2 = new Pod(1, "second_second", 500, 350);
        pod3 = new Pod(2, "second_third", 600, 200);
        pod4 = new Pod(3, "second_fourth", 650, 200);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);
        kubeEnv.add(node);
        node = new Node(2, "third", 2100, 1100);
        pod1 = new Pod(0, "third_first", 250, 150);
        pod2 = new Pod(1, "third_second", 200, 200);
        pod3 = new Pod(2, "third_third", 800, 300);
        pod4 = new Pod(3, "third_fourth", 650, 250);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);
        kubeEnv.add(node);
        return kubeEnv;
    }
}
