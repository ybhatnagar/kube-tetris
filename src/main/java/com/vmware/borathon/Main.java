package com.vmware.borathon;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        //Create MigrationController and nodes and containers
        MigrationController migrationController = new MigrationControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generate(10, 300);
        inputNodes.forEach(node -> migrationController.addNode(node));
        PodPlacementService podPlacementService = new PodPlacementServiceImpl();
        Capacity placeCapacity = new Capacity(1555, 1650);
        boolean placed = podPlacementService.placeCapacity(placeCapacity, migrationController.getNodes());
        if(placed) {
            log.info("Capacity {} is placed by single migration", placeCapacity);
        } else {
            log.info("Capacity {} was not placed by single migration.. Attempting multinode Migration", placeCapacity);
            placed = podPlacementService.placeCapacityWithMultipleMigration(placeCapacity, migrationController.getNodes());
            if(placed) {
                log.info("Capacity {} is placed by multinode migration", placeCapacity);
            } else {
                log.info("Failed to place capacity {} on any Node");
            }
        }
    }
}
