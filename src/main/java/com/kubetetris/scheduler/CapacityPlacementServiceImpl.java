package com.kubetetris.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import com.kubetetris.Capacity;
import com.kubetetris.Node;
import com.kubetetris.Pod;
import com.kubetetris.scheduler.helpers.Address;
import com.kubetetris.scheduler.helpers.CapacityPlacementServiceHelper;

@Slf4j
public class CapacityPlacementServiceImpl implements CapacityPlacementService {

    private Map<List<Pod>, Integer> migrablePodsToSize = new HashMap<>();
    private Map<Pod, List<Address>> migrationMoves = new LinkedHashMap<>();
    private List<MigrationPlanDto> migrationPlans = new LinkedList<>();
    boolean finalStatus = false;

    CapacityPlacementServiceHelper helper = new CapacityPlacementServiceHelper();

    @Override
    public void initData() {
        migrablePodsToSize = new HashMap<>();
        migrationPlans = new LinkedList<>();
        migrationMoves = new LinkedHashMap<>();
        finalStatus = false;
        helper = new CapacityPlacementServiceHelper();
    }

    @Override
    public boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes) {
        long totalAvailableCpu = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getCpuMillicore()).sum();
        long totalAvailableMem = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getMemoryMB()).sum();
        if (totalAvailableCpu >= placeCapacity.getCpuMillicore()
                && totalAvailableMem >= placeCapacity.getMemoryMB()) {
            return true;
        }
        //log.info("Capacity {} cannot be placed on total Available cpu {} , memory {}", placeCapacity, totalAvailableCpu, totalAvailableMem);
        return false;
    }

    @Override
    public boolean placeCapacity(Pod placePod, List<Node> nodes) {
        Capacity placeCapacity = placePod.getRequest();
        if (checkPlacementElibility(placeCapacity, nodes)) {
            int directlyPlaceOn = helper.computeNormalPlacement(placeCapacity, nodes);
            if (directlyPlaceOn >= 0) {
                log.debug("Pod {} is directly placed on {} node", placePod, nodes.get(directlyPlaceOn));
                Node placedNode = nodes.get(directlyPlaceOn);
                helper.addAddressToMigrationMoves(migrationMoves, null,placedNode.getName(), placePod);
                helper.addToMigrationPlans(migrationPlans, migrationMoves);
                placedNode.addPod(placePod);
                return true;
            } else {
                //placementPriority stores the information about which node should be tried first
                List<Integer> placementPriority = helper.computePlacementPriority(placeCapacity, nodes);
                log.debug("Node placement priority {}", placementPriority);
                for (int priority = 0; priority < placementPriority.size(); priority++) {
                    int nodeId = placementPriority.get(priority);
                    Node node = nodes.get(nodeId);
                    Capacity availableCapacity = node.getAvailableCapacity();
                    Capacity requiredCapacity = new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                            placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
                    List<Pod> eligiblePods = helper.computeEligiblePods(placeCapacity, requiredCapacity, new ArrayList<>(node.getPods().values()));
                    //If single node migration fails then try multi-node migration
                    if (eligiblePods != null && !eligiblePods.isEmpty()) {
                        log.debug("Eligible pods on node {} are {}", node, eligiblePods);
                        Pod migrablePod = helper.computeMinimumMigrateablePod(eligiblePods);
                        if (migrablePod != null) {
                            log.debug("Place Pod {} on Node {} and Migrate pod {} to other Node", placePod, node, migrablePod);
                            node.removePod(migrablePod);
                            helper.addAddressToMigrationMoves(migrationMoves, node.getName(),null, migrablePod);
                            helper.addAddressToMigrationMoves(migrationMoves, null,node.getName(), placePod);
                            node.addPod(placePod);
                            log.debug("Try placing {} pod on {} nodes", migrablePod, nodes);
                            return placeCapacity(migrablePod, nodes);
                        } else {
                            log.debug("Failed to place {} on node {}", placeCapacity, node);
                        }
                    }
                }
            }
        } else {
            return false;
        }
        return false;
    }

    @Override
    public boolean placeCapacityWithMultipleMigration(Pod placePod, List<Node> nodes) {
        Capacity placeCapacity = placePod.getRequest();
        if (checkPlacementElibility(placeCapacity, nodes)) {
            int directlyPlaceOn = helper.computeNormalPlacement(placeCapacity, nodes);
            if (directlyPlaceOn >= 0) {
                log.debug("Pod {} is directly placed on {} node", placePod, nodes.get(directlyPlaceOn));
                Node placeNode = nodes.get(directlyPlaceOn);
                helper.addToMigrationPlans(migrationPlans, migrationMoves);
                placeNode.addPod(placePod);
                return true;
            } else {
                //placementPriority stores the information about which node should be tried first
                List<Integer> placementPriority = helper.computePlacementPriority(placeCapacity, nodes);
                log.debug("Node placement priority {}", placementPriority);
                for (int priority = 0; priority < placementPriority.size(); priority++) {
                    int nodeId = placementPriority.get(priority);
                    Node node = nodes.get(nodeId);
                    Capacity availableCapacity = node.getAvailableCapacity();
                    Capacity requiredCapacity = new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                            placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
                    List<Pod> multipleEligiblePods = helper.computeMultipleEligiblePods(placeCapacity, new ArrayList<>(node.getPods().values()));
                    log.debug("Multiple removable pods on node {} for placement of pod {} is {}", node, placePod, multipleEligiblePods);
                    List<Pod> migrablePods = computeMinimumMigrateablePods(multipleEligiblePods, requiredCapacity);
                    if (migrablePods != null && !migrablePods.isEmpty()) {
                        log.debug("Place pod {} on Node {} and Migrate pods {} to other Node", placePod, node, migrablePods);
                        for (Pod pod : migrablePods) {
                            node.removePod(pod);
                            helper.addAddressToMigrationMoves(migrationMoves, node.getName(),null, pod);
                        }
                        helper.addAddressToMigrationMoves(migrationMoves, null,node.getName(), placePod);
                        node.addPod(placePod);
                        for (Pod migrate : migrablePods) {
                            finalStatus = placeCapacity(migrate, nodes);
                            if (!finalStatus) {
                                finalStatus = placeCapacityWithMultipleMigration(migrate, nodes);
                            } else {
                                continue;
                            }
                        }
                        return finalStatus;
                    } else {
                        log.debug("Failed to place {} on node {}", placeCapacity, node);
                    }
                }
            }
        } else {
            return false;
        }
        return finalStatus;
    }

    @Override
    public List<MigrationPlanDto> placeMyWorkload(Capacity workloadCapacity, List<Node> nodes) {
        List<MigrationPlanDto> migrationPlans = Collections.emptyList();
        try {
            migrationPlans = placeWorkload(workloadCapacity, nodes);
            helper.printMigrationPlan(migrationPlans);
        } catch (Exception exp) {
           log.error("Error in placing the workload capacity {} on nodes {} is exception {}", workloadCapacity, nodes, exp);
           return migrationPlans;
        }
        return migrationPlans;
    }

    private List<MigrationPlanDto> placeWorkload(Capacity workloadCapacity, List<Node> nodes) {
        List<Node> nodesForSingleMigration = helper.deepCopy(nodes);
        Pod placeCapacityPod = new Pod("-1", "wokload capacity", workloadCapacity.getMemoryMB(), workloadCapacity.getCpuMillicore(),false);
        System.out.print("\n\n******************* Workload Placement Status ********************\n");
        helper.printAvailableCapacity(nodes, "BEFORE");
        boolean placedSingle = placeCapacity(placeCapacityPod, nodesForSingleMigration);
        log.debug("Single Node Migration status : {}\n", placedSingle);
        List<MigrationPlanDto> singlePlacePlan = new ArrayList<>();
        migrationPlans.forEach(migrationPlan -> singlePlacePlan.add(migrationPlan));
        List<Node> nodesForMultiMigration = helper.deepCopy(nodes);
        initData();
        placeCapacityPod = new Pod("-1", "wokload capacity", workloadCapacity.getMemoryMB(), workloadCapacity.getCpuMillicore(),false);
        boolean placedMulti = placeCapacityWithMultipleMigration(placeCapacityPod, nodesForMultiMigration);
        log.debug("Multi Node Migration status : {}", placedMulti);
        List<MigrationPlanDto> multiPlacePlan = migrationPlans;
        if (placedSingle && placedMulti) {
            if (singlePlacePlan.size() >= multiPlacePlan.size()) {
//                log.info("\n\nCapacity {} is placed by single-pod migration\n\n", workloadCapacity);
                System.out.print("\nCapacity "+workloadCapacity+" is placed by single-pod migration.");
                helper.printAvailableCapacity(nodesForSingleMigration, "AFTER");
                return singlePlacePlan;
            } else {
//                log.info("\n\nCapacity {} is placed by multi-pod migration\n\n", workloadCapacity);
                System.out.print("\nCapacity "+workloadCapacity+" is placed by multi-pod migration.");
                helper.printAvailableCapacity(nodesForMultiMigration, "AFTER");
                return multiPlacePlan;
            }
        } else if (placedSingle) {
//            log.info("\n\nCapacity {} is placed by single-pod migration\n\n", workloadCapacity);
            System.out.print("\nCapacity "+workloadCapacity+" is placed by single-pod migration");
            helper.printAvailableCapacity(nodesForSingleMigration, "AFTER");
            return singlePlacePlan;
        } else if (placedMulti) {
//            log.info("\n\nCapacity {} is placed by multi-pod migration\n\n", workloadCapacity);
            System.out.print("\nCapacity "+workloadCapacity+" is placed by multi-pod migration");
            helper.printAvailableCapacity(nodesForMultiMigration, "AFTER");
            return multiPlacePlan;
        }
        log.info("\nFailed to place capacity {} on any Node\n", workloadCapacity);
        return Collections.emptyList();
    }

    private List<Pod> computeMinimumMigrateablePods(List<Pod> multipleEligiblePods, Capacity requiredCapacity) {
        if (multipleEligiblePods == null || multipleEligiblePods.isEmpty()) {
            return Collections.emptyList();
        }
        Pod firstPod = multipleEligiblePods.get(0);
        CopyOnWriteArrayList currentMinimum = new CopyOnWriteArrayList();
        currentMinimum.add(firstPod);
        computeMinimumMigrateablePods(multipleEligiblePods, requiredCapacity, currentMinimum);
        return computeMinimumPods();
    }

    private List<Pod> computeMinimumPods() {
        log.debug("Final Eligible pods : {}", migrablePodsToSize);
        if (migrablePodsToSize == null || migrablePodsToSize.isEmpty()) {
            return Collections.emptyList();
        } else {
            helper.sortByValues(migrablePodsToSize);
            return migrablePodsToSize.keySet().iterator().next();
        }
    }

    private void computeMinimumMigrateablePods(List<Pod> multipleEligiblePods, Capacity requiredCapacity,
                                               CopyOnWriteArrayList<Pod> currentMinimum) {

        long totalCpuMiliCore = helper.computeTotalCpuMilicore(currentMinimum);
        long totalMemMB = helper.computeTotalMemoryMB(currentMinimum);
        if (requiredCapacity.getCpuMillicore() > 0 && requiredCapacity.getMemoryMB() > 0) {
            if (totalCpuMiliCore >= requiredCapacity.getCpuMillicore() && totalMemMB >= requiredCapacity.getMemoryMB()) {
                List<Pod> currentMinimumSorted = helper.sort(currentMinimum);
                migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
                return;
            }
        } else if (requiredCapacity.getCpuMillicore() > 0 && requiredCapacity.getMemoryMB() < 0) {
            if (totalCpuMiliCore >= requiredCapacity.getCpuMillicore()) {
                List<Pod> currentMinimumSorted = helper.sort(currentMinimum);
                migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
                return;
            }
        } else if (requiredCapacity.getMemoryMB() > 0 && requiredCapacity.getCpuMillicore() < 0) {
            if (totalCpuMiliCore >= requiredCapacity.getCpuMillicore()) {
                List<Pod> currentMinimumSorted = helper.sort(currentMinimum);
                migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
                return;
            }
            ;
        }
        for (int podId = 1; podId < multipleEligiblePods.size(); podId++) {
            Pod currentPod = multipleEligiblePods.get(podId);
            currentMinimum.add(currentPod);
            multipleEligiblePods.remove(currentPod);
            List<Pod> currentMinimumSorted = helper.sort(currentMinimum);
            if (migrablePodsToSize.containsKey(currentMinimumSorted)) {
                return;
            }
            computeMinimumMigrateablePods(multipleEligiblePods, requiredCapacity, currentMinimum);
        }
    }
}
