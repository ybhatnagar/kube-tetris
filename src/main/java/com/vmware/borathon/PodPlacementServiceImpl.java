package com.vmware.borathon;

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

@Slf4j
public class PodPlacementServiceImpl implements PodPlacementService {

    Map<List<Pod>, Integer> migrablePodsToSize = new HashMap<>();

    private static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator = new Comparator<K>() {
            public int compare(K k1, K k2) {
                int compare =
                        map.get(k1).compareTo(map.get(k2));
                if (compare == 0)
                    return 1;
                else
                    return compare;
            }
        };
        Map<K, V> sortedByValues =
                new TreeMap<K, V>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
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
    public boolean placeCapacity(Capacity placeCapacity, List<Node> nodes) {
        if (checkPlacementElibility(placeCapacity, nodes)) {
            int directlyPlaceOn = computeNormalPlacement(placeCapacity, nodes);
            if (directlyPlaceOn >= 0) {
                log.info("Capacity {} is directly placed on {} node", placeCapacity, nodes.get(directlyPlaceOn));
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
                            node.addPod(new Pod(migrablePod.getId(), migrablePod.getId() + "", placeCapacity.getMemoryMB(), placeCapacity
                                    .getCpuMillicore()));
                            node.removePod(migrablePod);
                            placeCapacity.setMemoryMB(migrablePod.getRequest().getMemoryMB());
                            placeCapacity.setCpuMillicore(migrablePod.getRequest().getCpuMillicore());
                            log.debug("Try placing {} capacity on {} nodes", placeCapacity, nodes);
                            return placeCapacity(placeCapacity, nodes);
                        } else {
                            log.debug("Failed to place {} on node {}", placeCapacity, node);
                        }
                    }
                }
            }
        }
        return false;
    }

    boolean finalResult = false;

    @Override
    public boolean placeCapacityWithMultipleMigration(Capacity placeCapacity, List<Node> nodes) {
        if (checkPlacementElibility(placeCapacity, nodes)) {
            int directlyPlaceOn = computeNormalPlacement(placeCapacity, nodes);
            if (directlyPlaceOn >= 0) {
                log.info("Capacity {} is directly placed on {} node", placeCapacity, nodes.get(directlyPlaceOn));
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
                            node.addPod(new Pod(migrablePod.getId(), migrablePod.getId() + "", placeCapacity.getMemoryMB(), placeCapacity
                                    .getCpuMillicore()));
                            node.removePod(migrablePod);
                            placeCapacity.setMemoryMB(migrablePod.getRequest().getMemoryMB());
                            placeCapacity.setCpuMillicore(migrablePod.getRequest().getCpuMillicore());
                            log.debug("Try placing {} capacity on {} nodes", placeCapacity, nodes);
                            return placeCapacityWithMultipleMigration(placeCapacity, nodes);
                        } else {
                            log.debug("Failed to place {} on node {}", placeCapacity, node);
                        }
                    } else {
                        List<Pod> multipleEligiblePods = computeMultipleEligiblePods(placeCapacity, requiredCapacity, new ArrayList<>(node.getPods()
                                .values()));
                        log.debug("Multiple eligible pods for node {} and placement capacity {} is {}", node, placeCapacity, multipleEligiblePods);
                        List<Pod> migrablePods = computeMinimumMigrateablePods(multipleEligiblePods, placeCapacity, requiredCapacity);
                        if (migrablePods != null && !migrablePods.isEmpty()) {
                            log.info("Place capacity {} on Node {} and Migrate pods {} to other Node", placeCapacity, node, migrablePods);
                            for (Pod migrate : migrablePods) {
                                Capacity migrateCapacity = new Capacity(migrate.getRequest().getMemoryMB(), migrate.getRequest().getCpuMillicore());
                                finalResult = finalResult || placeCapacity(migrateCapacity, nodes) || placeCapacityWithMultipleMigration
                                        (migrateCapacity, nodes);
                            }
                        }else {
                            log.debug("Failed to place {} on node {}", placeCapacity, node);
                        }
                    }
                }
            }
        }
        return finalResult;
    }

    private List<Pod> computeMinimumMigrateablePods(List<Pod> multipleEligiblePods, Capacity placeCapacity, Capacity requiredCapacity) {
        if(multipleEligiblePods==null || multipleEligiblePods.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pod> firstPod = multipleEligiblePods.subList(0, 1);
        CopyOnWriteArrayList currentMinimum = new CopyOnWriteArrayList();
        currentMinimum.addAll(firstPod);
        multipleEligiblePods.remove(firstPod.get(0));
        computeMinimumMigrateablePods(multipleEligiblePods, placeCapacity, requiredCapacity, currentMinimum);
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

    private void computeMinimumMigrateablePods(List<Pod> multipleEligiblePods, Capacity placeCapacity, Capacity requiredCapacity,
                                               CopyOnWriteArrayList<Pod> currentMinimum) {

        long totalCpuMiliCore = computeTotalCpuMilicore(currentMinimum);
        long totalMemMB = computeTotalMemoryMB(currentMinimum);
        if (requiredCapacity.getCpuMillicore() > 0 && totalCpuMiliCore >= requiredCapacity.getCpuMillicore()
                && totalCpuMiliCore < placeCapacity.getCpuMillicore() && requiredCapacity.getMemoryMB() > 0
                && totalMemMB >= requiredCapacity.getMemoryMB() && totalMemMB < placeCapacity.getMemoryMB()) {
            List<Pod> currentMinimumSorted = sort(currentMinimum);
            migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
            return;
        } else if (requiredCapacity.getCpuMillicore() > 0 && totalCpuMiliCore >= requiredCapacity.getCpuMillicore()
                && totalCpuMiliCore < placeCapacity.getCpuMillicore() && totalMemMB < placeCapacity.getMemoryMB()) {
            List<Pod> currentMinimumSorted = sort(currentMinimum);
            migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
            return;
        }  else if (requiredCapacity.getMemoryMB() > 0 && totalMemMB >= requiredCapacity.getMemoryMB() && totalMemMB < placeCapacity.getMemoryMB()
                && totalCpuMiliCore < placeCapacity.getCpuMillicore()) {
            List<Pod> currentMinimumSorted = sort(currentMinimum);
            migrablePodsToSize.put(currentMinimumSorted, currentMinimum.size());
            return;
        }
        for (int podId = 0; podId < multipleEligiblePods.size(); podId++) {
            Pod currentPod = multipleEligiblePods.get(podId);
            currentMinimum.add(currentPod);
            multipleEligiblePods.remove(currentPod);
            List<Pod> currentMinimumSorted = sort(currentMinimum);
            if (migrablePodsToSize.containsKey(currentMinimumSorted)) {
                return;
            }
            computeMinimumMigrateablePods(multipleEligiblePods, placeCapacity, requiredCapacity, currentMinimum);
        }
    }

    private List<Pod> sort(CopyOnWriteArrayList<Pod> currentMinimum) {
        return currentMinimum.stream().sorted((o1, o2) -> (int)PairDepair.pair(o1.getRequest().getCpuMillicore(), o1.getRequest().getMemoryMB() -
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

    private List<Pod> computeMultipleEligiblePods(Capacity placeCapacity, Capacity requiredCapacity, List<Pod> pods) {
        return pods.stream().map(pod -> {
            Capacity podRequestrequest = pod.getRequest();
            if ((requiredCapacity.getMemoryMB() >= 0 && podRequestrequest.getMemoryMB() <= requiredCapacity.getMemoryMB()
                    && (requiredCapacity.getCpuMillicore() >= 0 || podRequestrequest.getCpuMillicore() <= requiredCapacity.getCpuMillicore()))) {
                return pod;
            } else if ((requiredCapacity.getMemoryMB() < 0 && podRequestrequest.getMemoryMB() <= placeCapacity.getMemoryMB()
                    && podRequestrequest.getCpuMillicore() <= placeCapacity.getCpuMillicore())
                    || (requiredCapacity.getCpuMillicore() < 0 && podRequestrequest.getCpuMillicore() <= placeCapacity.getCpuMillicore()
                    && podRequestrequest.getMemoryMB() <= placeCapacity.getCpuMillicore())) {
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
}
