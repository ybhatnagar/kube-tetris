package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.borathon.loadsimulator.NodeDataGenerator;
import com.vmware.borathon.scheduler.CapacityPlacementService;
import com.vmware.borathon.scheduler.CapacityPlacementServiceImpl;
import com.vmware.borathon.scheduler.MigrationPlanDto;

public class CapacityPlacementServiceTest {
    private static final Logger log = LoggerFactory.getLogger(CapacityPlacementServiceTest.class);

    private static MigrationController migrationController;

    @BeforeClass
    public static void setup(){

        //Create MigrationController and nodes and pods
        migrationController = new MigrationControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generateFixed();
        inputNodes.forEach(node -> migrationController.addNode(node));
    }

    @Before
    public void prepare(){

    }

    @Test
    public void scenarioTest() throws Exception{
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();
        Capacity placeCapacity = new Capacity(450, 350);
        List<Node> nodes = migrationController.getNodes();
        List<Node> copyForSingleMigration = deepCopy(nodes);
        List<Node> copyForMultiMigration = deepCopy(nodes);
        capacityPlacementService.initData();
        Pod placeCapacityPod = new Pod("-1","wokload capacity", placeCapacity.getMemoryMB(), placeCapacity.getCpuMillicore());
        boolean placed = capacityPlacementService.placeCapacity(placeCapacityPod, copyForSingleMigration);
        if(placed) {
            log.info("Capacity {} is placed by single migration", placeCapacity);
        } else {
            log.info("Capacity {} was not placed by single migration.. Attempting multinode Migration", placeCapacity);
            capacityPlacementService.initData();
            placed = capacityPlacementService.placeCapacityWithMultipleMigration(placeCapacityPod, copyForMultiMigration);
            if(placed) {
                log.info("Capacity {} is placed by multinode migration", placeCapacity);
            } else {
                log.info("Failed to place capacity {} on any Node", placeCapacity);
            }
        }
    }

    @Test
    public void testPlaceWorkload() throws Exception{
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();
        Capacity placeCapacity = new Capacity(500, 360);
        List<Node> nodes = migrationController.getNodes();
        List<MigrationPlanDto> placement = capacityPlacementService.placeMyWorkload(placeCapacity, nodes);
        log.info("Final result obtained : {}", placement);
    }

    private List<Node> deepCopy(List<Node> nodes) {
        List<Node> deepCopies = new ArrayList<>();
        nodes.forEach(node -> deepCopies.add(node.clone()));
        return deepCopies;
    }
}