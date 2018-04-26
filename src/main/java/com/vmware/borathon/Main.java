package com.vmware.borathon;

public class Main {
    public static void main(String[] args) {
        //Create MigrationController and nodes and continers
        MigrationController migrationController = new MigrationControllerImpl();
        Node node1 = new Node();
        Node node2 = new Node();
        Node node3 = new Node();

        Container container1 = new Container();
        Container container2 = new Container();
        Container container3 = new Container();

        //Add Nodes
        migrationController.addNode(node1);
        migrationController.addNode(node2);
        migrationController.addNode(node3);

        //Add containers


    }
}
