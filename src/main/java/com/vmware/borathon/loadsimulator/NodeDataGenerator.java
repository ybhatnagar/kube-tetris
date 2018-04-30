package com.vmware.borathon.loadsimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;

public class NodeDataGenerator {
    static final long NODE_CPU_IN_MILI_CORE = 8000;
    static final long NODE_MEMORY_IN_MB = 16000;


    public static List<Node> generate(int nodes, int podOnEachNode) {
        List<Node> kubeEnv = new ArrayList<>();
        for(int node=0; node < nodes; node++) {
            Node node1 = new Node(node+"", node+"", NODE_MEMORY_IN_MB, NODE_CPU_IN_MILI_CORE);
            for (int pod=0; pod < podOnEachNode; pod++) {
                boolean added = node1.addPod(generatePod(node+"_"+pod, node+"_"+pod));
                if (!added)
                    break;
            }
            kubeEnv.add(node1);
        }
        return kubeEnv;
    }

    private static Pod generatePod(String podId, String name) {
        int cpuMiliCoreForPod = generateRandomIntBetween(1, 20) * 50;
        int memMB = generateRandomIntBetween(1, 20) * 100;
        return new Pod(podId,name ,memMB, cpuMiliCoreForPod,false);
    }

    private static int generateRandomIntBetween(int start, int end) {
        Random r = new Random();
        return r.nextInt(end-start) + start;
    }

    public static List<Node> generateFixed() {
        List<Node> kubeEnv = new ArrayList<>();
        Node node = new Node("0", "first", 2100, 1100);
        Pod pod1 = new Pod("0_0", "first_first", 200, 50,false);
        Pod pod2 = new Pod("0_1", "first_second", 400, 250,false);
        Pod pod3 = new Pod("0_2", "first_third", 700, 300,false);
        Pod pod4 = new Pod("0_3", "first_fourth", 500, 300,false);
        Pod pod5 = new Pod("0_4", "first_fifth", 150, 100,false);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);node.addPod(pod5);
        kubeEnv.add(node);
        node = new Node("1", "second", 2100, 1100);
        pod1 = new Pod("1_0", "second_first", 100, 200,false);
        pod2 = new Pod("1_1", "second_second", 500, 350,false);
        pod3 = new Pod("1_2", "second_third", 600, 200,false);
        pod4 = new Pod("1_3", "second_fourth", 650, 200,false);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);
        kubeEnv.add(node);
        node = new Node("2", "third", 2100, 1100);
        pod1 = new Pod("2_0", "third_first", 250, 150,false);
        pod2 = new Pod("2_1", "third_second", 200, 200,false);
        pod3 = new Pod("2_2", "third_third", 800, 300,false);
        pod4 = new Pod("2_3", "third_fourth", 650, 250,false);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);
        kubeEnv.add(node);
        return kubeEnv;
    }


    public static List<Node> generateFixedReal() {
        List<Node> kubeEnv = new ArrayList<>();
        Node node = new Node("0", "ip-172-20-0-120.ec2.internal", 3000, 1300);
        Pod pod1 = new Pod("0_0", "first_first", 600, 100,false);
        Pod pod2 = new Pod("0_1", "first_second", 800, 400,false);
        Pod pod3 = new Pod("0_2", "first_third", 400, 200,false);
        Pod pod5 = new Pod("0_4", "first_fifth", 700, 100,false);

        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod5);
        kubeEnv.add(node);
        node = new Node("1", "ip-172-20-0-247.ec2.internal", 3700, 1500);
        pod1 = new Pod("1_0", "second_first", 400, 300,false);
        pod2 = new Pod("1_1", "second_second", 400, 200,false);
        pod3 = new Pod("1_2", "second_third", 800, 400,false);
        Pod pod4 = new Pod("1_3", "second_fourth", 1000, 300,false);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);
        kubeEnv.add(node);
        node = new Node("2", "ip-172-20-0-79.ec2.internal", 3150, 1400);
        pod1 = new Pod("2_0", "third_first", 1200, 500,false);
        pod2 = new Pod("2_1", "third_second", 500, 200,false);
        pod3 = new Pod("2_2", "third_third", 800, 300,false);
        pod4 = new Pod("1_3", "third_fourth", 300, 100,false);
        node.addPod(pod1); node.addPod(pod2); node.addPod(pod3); node.addPod(pod4);
        kubeEnv.add(node);
        return kubeEnv;
    }
}
