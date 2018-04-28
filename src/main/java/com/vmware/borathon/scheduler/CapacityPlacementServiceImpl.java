package com.vmware.borathon.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.vmware.borathon.Capacity;
import com.vmware.borathon.Node;
import com.vmware.borathon.cantor.PairDepair;
import com.vmware.borathon.Pod;

@Slf4j
public class CapacityPlacementServiceImpl implements CapacityPlacementService {

    private Map<List<Pod>, Integer> migrablePodsToSize = new HashMap<>();
    private Map<Integer, Map<Pod, Integer>> nodeIdToPodMigration = new HashMap<>();
    boolean finalStatus = false;

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
            int directlyPlaceOn = computeNormalPlacement(placeCapacity, nodes);
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
                List<Integer> placementPriority = computePlacementPriority(placeCapacity, nodes);
                log.debug("Node placement priority {}", placementPriority);
                for (int priority = 0; priority < placementPriority.size(); priority++) {
                    int nodeId = placementPriority.get(priority);
                    Node node = nodes.get(nodeId);
                    Capacity availableCapacity = node.getAvailableCapacity();
                    Capacity requiredCapacity = new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                            placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
                    List<Pod> eligiblePods = computeEligiblePods(placeCapacity, requiredCapacity, new ArrayList<>(node.getPods().values()));
                    //If single node migration fails then try multi-node migration
                    if (eligiblePods != null && !eligiblePods.isEmpty()) {
                        log.debug("Eligible pods on node {} are {}", node, eligiblePods);
                        Pod migrablePod = computeMinimumMigrateablePod(eligiblePods);
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
            int directlyPlaceOn = computeNormalPlacement(placeCapacity, nodes);
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
                List<Integer> placementPriority = computePlacementPriority(placeCapacity, nodes);
                log.debug("Node placement priority {}", placementPriority);
                for (int priority = 0; priority < placementPriority.size(); priority++) {
                    int nodeId = placementPriority.get(priority);
                    Node node = nodes.get(nodeId);
                    Capacity availableCapacity = node.getAvailableCapacity();
                    Capacity requiredCapacity = new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                            placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
                    List<Pod> multipleEligiblePods = computeMultipleEligiblePods(placeCapacity, new ArrayList<>(node.getPods().values()));
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
        List<Node> nodesForSingleMigration = deepCopy(nodes);
        Pod placeCapacityPod = new Pod(-1, "wokload capacity", workloadCapacity.getMemoryMB(), workloadCapacity.getCpuMillicore());
        printAvailableCapacity(nodes, "BEFORE");
        boolean placed = placeCapacity(placeCapacityPod, nodesForSingleMigration);
        if (placed) {
            log.info("Capacity {} is placed by single migration", workloadCapacity);
            printAvailableCapacity(nodesForSingleMigration, "AFTER");
            return nodeIdToPodMigration;
        } else {
            log.info("Capacity {} was not placed by single migration.. Attempting multinode Migration", workloadCapacity);
            List<Node> nodesForMultiMigration = deepCopy(nodes);
            placed = placeCapacityWithMultipleMigration(placeCapacityPod, nodesForMultiMigration);
            if (placed) {
                log.info("Capacity {} is placed by multinode migration", workloadCapacity);
                printAvailableCapacity(nodesForMultiMigration, "AFTER");
                return nodeIdToPodMigration;
            } else {
                log.info("Failed to place capacity {} on any Node", workloadCapacity);
            }
        }
        return Collections.emptyMap();
    }

    private void printAvailableCapacity(List<Node> nodes, String state) {
        long totalAvailableCpu = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getCpuMillicore()).sum();
        long totalAvailableMem = nodes.stream().mapToLong(value -> value.getAvailableCapacity().getMemoryMB()).sum();
        log.info("Overall Available capacity "+state+" placement : cpu : {}, memory : {}", totalAvailableCpu, totalAvailableMem);
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
            sortByValues(migrablePodsToSize);
            return migrablePodsToSize.keySet().iterator().next();
        }
    }

    private void computeMinimumMigrateablePods(List<Pod> multipleEligiblePods, Capacity requiredCapacity,
                                               CopyOnWriteArrayList<Pod> currentMinimum) {

        long totalCpuMiliCore = computeTotalCpuMilicore(currentMinimum);
        long totalMemMB = computeTotalMemoryMB(currentMinimum);
        if (requiredCapacity.getCpuMillicore() > 0 && requiredCapacity.getMemoryMB() > 0) {
            if (totalCpuMiliCore >= requiredCapacity.getCpuMillicore() && totalMemMB >= requiredCapacity.getMemoryMB()) {
                List<Pod> currentMinimumSorted = sort(currentMinimum);
                migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
                return;
            }
        } else if (requiredCapacity.getCpuMillicore() > 0 && requiredCapacity.getMemoryMB() < 0) {
            if (totalCpuMiliCore >= requiredCapacity.getCpuMillicore()) {
                List<Pod> currentMinimumSorted = sort(currentMinimum);
                migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
                return;
            }
        } else if (requiredCapacity.getMemoryMB() > 0 && requiredCapacity.getCpuMillicore() < 0) {
            if (totalCpuMiliCore >= requiredCapacity.getCpuMillicore()) {
                List<Pod> currentMinimumSorted = sort(currentMinimum);
                migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
                return;
            };
        }
        for (int podId = 1; podId < multipleEligiblePods.size(); podId++) {
            Pod currentPod = multipleEligiblePods.get(podId);
            currentMinimum.add(currentPod);
            multipleEligiblePods.remove(currentPod);
            List<Pod> currentMinimumSorted = sort(currentMinimum);
            if (migrablePodsToSize.containsKey(currentMinimumSorted)) {
                return;
            }
            computeMinimumMigrateablePods(multipleEligiblePods, requiredCapacity, currentMinimum);
        }
    }

    private List<Pod> sort(CopyOnWriteArrayList<Pod> currentMinimum) {
        return currentMinimum.stream().sorted((o1, o2) -> (int) PairDepair
                .pair(o1.getRequest().getCpuMillicore(), o1.getRequest().getMemoryMB() -
                        PairDepair.pair(o2.getRequest().getCpuMillicore(), o2.getRequest().getMemoryMB()))).collect(Collectors.toList());
    }

    private long computeTotalMemoryMB(List<Pod> currentMinimum) {
        return currentMinimum.stream().mapToLong(pod -> pod.getRequest().getMemoryMB()).sum();
    }

    private long computeTotalCpuMilicore(List<Pod> currentMinimum) {
        return currentMinimum.stream().mapToLong(pod -> pod.getRequest().getCpuMillicore()).sum();
    }

    private Pod computeMinimumMigrateablePod(List<Pod> eligiblePods) {
        if (eligiblePods != null && eligiblePods.size() == 1) {
            return eligiblePods.get(0);
        } else if (eligiblePods != null && eligiblePods.size() > 1) {
            return eligiblePods.stream().min((pod1, pod2) -> (int) (pod2.getRequest().getCpuMillicore() - pod2.getRequest().getCpuMillicore())).get();
        }
        return null;
    }

    private List<Pod> computeEligiblePods(Capacity placeCapacity, Capacity requiredCapacity, List<Pod> pods) {
        return pods.stream().map(pod -> {
            Capacity podRequestrequest = pod.getRequest();
            if ((podRequestrequest.getMemoryMB() >= requiredCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() >= requiredCapacity.getCpuMillicore())
                    && (podRequestrequest.getMemoryMB() < placeCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() < placeCapacity.getCpuMillicore())) {
                return pod;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private List<Pod> computeMultipleEligiblePods(Capacity placeCapacity, List<Pod> pods) {
        return pods.stream().map(pod -> {
            Capacity podRequestrequest = pod.getRequest();
            if (podRequestrequest.getMemoryMB() < placeCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() < placeCapacity.getCpuMillicore()) {
                return pod;
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private int computeNormalPlacement(Capacity placeCapacity, List<Node> nodes) {
        for (int nodeId = 0; nodeId < nodes.size(); nodeId++) {
            Capacity requiredCapacity = computeRequiredCapacity(placeCapacity, nodes.get(nodeId));
            if (requiredCapacity.getCpuMillicore() <= 0 && requiredCapacity.getMemoryMB() <= 0) {
                return nodeId;
            }
        }
        return -1;
    }

    private List<Integer> computePlacementPriority(Capacity placeCapacity, List<Node> nodes) {
        List<Capacity> requiredCapacity = nodes.stream().map(node -> computeRequiredCapacity(placeCapacity, node)).collect(Collectors.toList());
        Map<Integer, Long> pairedCapacity = new TreeMap<>();
        for (int nodeId = 0; nodeId < requiredCapacity.size(); nodeId++) {
            Capacity capacity = requiredCapacity.get(nodeId);
            log.debug("Required Capacity for node {} is {}", nodeId, capacity);
            Long pairedValue = PairDepair.pair(Math.abs(capacity.getMemoryMB()), Math.abs(capacity.getCpuMillicore()));
            log.debug("Paired value for node {} is {}", nodeId, pairedValue);
            pairedCapacity.put(nodeId, pairedValue);
        }
        Map<Integer, Long> sortedPairedCapacity = sortByValues(pairedCapacity);
        return new ArrayList<>(sortedPairedCapacity.keySet());
    }

    private Capacity computeRequiredCapacity(Capacity placeCapacity, Node node) {
        Capacity availableCapacity = node.getAvailableCapacity();
        return new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
    }

    private List<Node> deepCopy(List<Node> nodes) {
        List<Node> deepCopies = new ArrayList<>();
        nodes.forEach(node -> deepCopies.add(node.clone()));
        return deepCopies;
    }

    private static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator = (k1, k2) -> {
            int compare =
                    map.get(k1).compareTo(map.get(k2));
            if (compare == 0)
                return 1;
            else
                return compare;
        };
        Map<K, V> sortedByValues =
                new TreeMap<K, V>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
    }
}
