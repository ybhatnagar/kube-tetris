package com.vmware.borathon.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import com.vmware.borathon.Capacity;
import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;
import com.vmware.borathon.scheduler.helpers.CapacityPlacementServiceHelper;

@Slf4j
public class CapacityPlacementServiceImpl implements CapacityPlacementService {

    private Map<List<Pod>, Integer> migrablePodsToSize = new HashMap<>();
    private Map<Integer, Map<Pod, Integer>> nodeIdToPodMigration = new HashMap<>();
    boolean finalStatus = false;

    CapacityPlacementServiceHelper helper = new CapacityPlacementServiceHelper();

    @Override
    public void initData() {
        migrablePodsToSize = new HashMap<>();
        nodeIdToPodMigration = new HashMap<>();
        finalStatus = false;
    }

    @Override
    public boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes) {
        long totalAvailableCpu = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getCpuMillicore()).sum();
        long totalAvailableMem = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getMemoryMB()).sum();
        if (totalAvailableCpu >= placeCapacity.getCpuMillicore()
                && totalAvailableMem >= placeCapacity.getMemoryMB()) {
            return true;
        }
        log.info("Capacity {} cannot be placed on total Available cpu {} , memory {}", placeCapacity, totalAvailableCpu, totalAvailableMem);
        return false;
    }

    @Override
    public boolean placeCapacity(Pod placePod, List<Node> nodes) {
        Capacity placeCapacity = placePod.getRequest();
        if (checkPlacementElibility(placeCapacity, nodes)) {
            int directlyPlaceOn = helper.computeNormalPlacement(placeCapacity, nodes);
            if (directlyPlaceOn >= 0) {
                log.info("Capacity {} is directly placed on {} node", placeCapacity, nodes.get(directlyPlaceOn));
                Node placedNode = nodes.get(directlyPlaceOn);
                Pod placedPod = new Pod(placedNode.getPods().size() - 1, "Directly Placed", placeCapacity.getMemoryMB(), placeCapacity
                        .getCpuMillicore());
                updateNodeAndPodMigration(placedPod, placedNode, 1);
                placedNode.addPod(placedPod);
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
                            log.info("Place capacity {} on Node {} and Migrate pod {} to other Node", placeCapacity, node, migrablePod);
                            node.removePod(migrablePod);
                            updateNodeAndPodMigration(migrablePod, node, -1);
                            placePod = new Pod(node.getPods().size() - 1, migrablePod.getId() + "", placeCapacity.getMemoryMB(), placeCapacity
                                    .getCpuMillicore());
                            node.addPod(placePod);
                            updateNodeAndPodMigration(placePod, node, 1);
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

    private void updateNodeAndPodMigration(Pod placedPod, Node placedNode, int placeOrDelete) {
        Map<Pod, Integer> podPlacement = nodeIdToPodMigration.getOrDefault(placedNode.getId(), null);
        if (podPlacement != null) {
            podPlacement.put(placedPod, placeOrDelete);
        } else {
            podPlacement = new HashMap<>();
            podPlacement.put(placedPod, placeOrDelete);
        }
        nodeIdToPodMigration.put(placedNode.getId(), podPlacement);
    }

    @Override
    public boolean placeCapacityWithMultipleMigration(Pod placePod, List<Node> nodes) {
        Capacity placeCapacity = placePod.getRequest();
        if (checkPlacementElibility(placeCapacity, nodes)) {
            int directlyPlaceOn = helper.computeNormalPlacement(placeCapacity, nodes);
            if (directlyPlaceOn >= 0) {
                log.info("Capacity {} is directly placed on {} node", placeCapacity, nodes.get(directlyPlaceOn));
                Node placeNode = nodes.get(directlyPlaceOn);
                Pod placedPod = new Pod(nodes.get(directlyPlaceOn).getPods().size() - 1, "Directly Placed",
                        placeCapacity.getMemoryMB(), placeCapacity.getCpuMillicore());
                placeNode.addPod(placedPod);
                updateNodeAndPodMigration(placePod, placeNode, 1);
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
                    log.debug("Multiple eligible pods for node {} and placement capacity {} is {}", node, placeCapacity, multipleEligiblePods);
                    List<Pod> migrablePods = computeMinimumMigrateablePods(multipleEligiblePods, requiredCapacity);
                    if (migrablePods != null && !migrablePods.isEmpty()) {
                        log.info("Place capacity {} on Node {} and Migrate pods {} to other Node", placeCapacity, node, migrablePods);
                        for (Pod pod : migrablePods) {
                            updateNodeAndPodMigration(pod, node, -1);
                            node.removePod(pod);
                        }
                        Pod placedPod = new Pod(node.getPods().size() - 1, "PlacedInMultinode", placeCapacity.getMemoryMB(), placeCapacity
                                .getCpuMillicore());
                        node.addPod(placedPod);
                        updateNodeAndPodMigration(placedPod, node, 1);
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
    public Map<Integer, Map<Pod, Integer>> placeMyWorkload(Capacity workloadCapacity, List<Node> nodes) {
        List<Node> nodesForSingleMigration = helper.deepCopy(nodes);
        Pod placeCapacityPod = new Pod(-1, "wokload capacity", workloadCapacity.getMemoryMB(), workloadCapacity.getCpuMillicore());
        helper.printAvailableCapacity(nodes, "BEFORE");
        boolean placed = placeCapacity(placeCapacityPod, nodesForSingleMigration);
        if (placed) {
            log.info("Capacity {} is placed by single migration", workloadCapacity);
            helper.printAvailableCapacity(nodesForSingleMigration, "AFTER");
            return nodeIdToPodMigration;
        } else {
            log.info("Capacity {} was not placed by single migration.. Attempting multinode Migration", workloadCapacity);
            List<Node> nodesForMultiMigration = helper.deepCopy(nodes);
            placed = placeCapacityWithMultipleMigration(placeCapacityPod, nodesForMultiMigration);
            if (placed) {
                log.info("Capacity {} is placed by multinode migration", workloadCapacity);
                helper.printAvailableCapacity(nodesForMultiMigration, "AFTER");
                return nodeIdToPodMigration;
            } else {
                log.info("Failed to place capacity {} on any Node", workloadCapacity);
            }
        }
        return Collections.emptyMap();
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
            };
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
