package com.vmware.borathon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
    public void removeNode(Node node) {
        nodes.remove(node);
        node.leftMigrationController();
    }

    @Override
    public List<Node> getNodes() {
        return nodes;
    }

    @Override
    public List<Node> getNodesSortedByRatio(){
        return nodes.stream()
                .sorted(Comparator.comparingDouble(n -> n.getAvailableCapacity().getCpuMemoryRatio()))
                .collect(Collectors.toList());
    }
}
