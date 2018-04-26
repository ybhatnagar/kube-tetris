package com.vmware.borathon;

public class Main {
    public static void main(String[] args) {
        //Create MigrationController and nodes and continers
        MigrationController migrationController = new MigrationControllerImpl();
        Node node1 = new Node(20, 2000);
        Node node2 = new Node(18, 1500);
        Node node3 = new Node(25, 2200);

        //Add Nodes
        migrationController.addNode(node1);
        migrationController.addNode(node2);
        migrationController.addNode(node3);

        //Add containers
        Pod pod1 = new Pod(2, 150);
        Pod pod2 = new Pod(4, 400);
        Pod pod3 = new Pod(100, 200);

        node1.addPod(pod1);
        node1.addPod(pod2);
        node1.addPod(pod3);

    }
}
