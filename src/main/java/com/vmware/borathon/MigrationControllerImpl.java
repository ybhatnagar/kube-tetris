package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;

public class MigrationControllerImpl implements MigrationController{

    private final List<Node> nodes;

    public MigrationControllerImpl() {
        this.nodes = new ArrayList<>();
    }

    @Override
    public void addNode(Node node) {
        nodes.add(node);
        node.joinedMigrationController(this);
    }

    @Override
    public List<Node> getNodes() {
        return nodes;
    }
}
