package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;

public class Node {
    private final List<Container> containers;

    public Node() {
        this.containers = new ArrayList<>();
    }

    void addContainer(Container container) {
        containers.add(container);
        container.joinedNode(this);
    }

    void joinedMigrationController(MigrationController migrationController){

    }
}
