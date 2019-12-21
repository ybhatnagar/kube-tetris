package com.kubetetris;

import java.util.ArrayList;
import java.util.List;

import com.kubetetris.loadsimulator.NodeDataGenerator;
import com.kubetetris.scheduler.CapacityPlacementService;
import com.kubetetris.scheduler.CapacityPlacementServiceImpl;
import com.kubetetris.scheduler.MigrationPlanDto;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapacityPlacementServiceTest {
    private static final Logger log = LoggerFactory.getLogger(CapacityPlacementServiceTest.class);

    private static SystemController systemController;

    @BeforeClass
    public static void setup(){

        //Create SystemController and nodes and pods
        systemController = new SystemControllerImpl();
        List<Node> inputNodes = NodeDataGenerator.generateFixedReal();
        inputNodes.forEach(node -> systemController.addNode(node));
    }

    @Before
    public void prepare(){

    }

    @Test
    public void scenarioTest() throws Exception{
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();
        Capacity placeCapacity = new Capacity(450, 350);
        List<Node> nodes = systemController.getNodes();
        List<Node> copyForSingleMigration = deepCopy(nodes);
        List<Node> copyForMultiMigration = deepCopy(nodes);
        capacityPlacementService.initData();
        Pod placeCapacityPod = new Pod("-1","wokload capacity", placeCapacity.getMemoryMB(), placeCapacity.getCpuMillicore(),false);
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
    public void testPlaceWorkloadSingle() throws Exception{
        systemController.getStatus();
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();
        Capacity placeCapacity = new Capacity(1200, 200);
        List<Node> nodes = systemController.getNodes();
        List<MigrationPlanDto> placement = capacityPlacementService.placeMyWorkload(placeCapacity, nodes);
        log.debug("Final result obtained : {}", placement);
        log.debug("Number of movements required : {}", placement.size());
    }

    @Test
    public void testPlaceWorkloadMulti() throws Exception{
        systemController.getStatus();
        CapacityPlacementService capacityPlacementService = new CapacityPlacementServiceImpl();
        Capacity placeCapacity = new Capacity(1300, 200);
        List<Node> nodes = systemController.getNodes();
        List<MigrationPlanDto> placement = capacityPlacementService.placeMyWorkload(placeCapacity, nodes);
        log.debug("Final result obtained : {}", placement);
        log.debug("Number of movements required : {}", placement.size());
    }

    private List<Node> deepCopy(List<Node> nodes) {
        List<Node> deepCopies = new ArrayList<>();
        nodes.forEach(node -> deepCopies.add(node.clone()));
        return deepCopies;
    }
}