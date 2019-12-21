package com.kubetetris;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SystemControllerImpl implements SystemController {

    private final List<Node> nodes;

    public SystemControllerImpl() {
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

    @Override
    public double getPivotRatio(){
        long totalAvailableCpu = 0, totalAvailableMem=0;

        for(int i=0;i<nodes.size();i++){
            totalAvailableCpu += nodes.get(i).getAvailableCapacity().getCpuMillicore();
            totalAvailableMem += nodes.get(i).getAvailableCapacity().getMemoryMB();
        }
        return ((double)totalAvailableCpu/totalAvailableMem);
    }

    @Override
    public double getSystemEntropy(){
        double sum = 0;
        for (int i=0;i<nodes.size();i++){
            sum += Math.abs(getPivotRatio() - nodes.get(i).getAvailableCapacity().getCpuMemoryRatio());
        }
        return sum;
    }

    @Override
    public void getStatus(){
        System.out.print("\n\n******************* System Availability Status ********************\n\n");
        nodes.forEach(node -> System.out.println("Node : " + node.getName() + " Available " + node.getAvailableCapacity()));
    }
}
