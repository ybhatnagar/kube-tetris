package com.vmware.borathon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PodPlacementServiceImpl implements PodPlacementService {

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
                log.info("Capacity {} is directly placed on {} node", placeCapacity, directlyPlaceOn);
                return true;
            } else {
                //placementPriority stores the information about which node should be tried first
                List<Integer> placementPriority = computePlacementPriority(placeCapacity, nodes);
                log.info("Node placement priority {}", placementPriority);
                for (int priority=0; priority < placementPriority.size(); priority++) {
                    int nodeId = placementPriority.get(priority);
                    Node node = nodes.get(nodeId);
                    Capacity availableCapacity = node.getAvailableCapacity();
                    Capacity requiredCapacity = new Capacity(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB(),
                            placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore());
                    List<Pod> eligiblePods = computeEligiblePods(placeCapacity, requiredCapacity, new ArrayList<>(node.getPods().values()));
                    log.info("Eligible pods on node {} are {}", node, eligiblePods);
                    Pod migrablePod = computeMinimumMigrateablePod(eligiblePods);
                    if(migrablePod != null) {
                        log.info("Place capacity {} on Node {} and Migrate pod {} to other Node", placeCapacity, node, migrablePod);
                        node.addPod(new Pod(migrablePod.getId(), migrablePod.getId()+"", placeCapacity.getMemoryMB(), placeCapacity
                                .getCpuMillicore()));
                        node.removePod(migrablePod);
                        placeCapacity.setMemoryMB(migrablePod.getRequest().getMemoryMB());
                        placeCapacity.setCpuMillicore(migrablePod.getRequest().getCpuMillicore());
                        log.info("Try placing {} capacity on {} nodes", placeCapacity, nodes);
                        placeCapacity(placeCapacity, nodes);
                    } else {
                        log.info("Failed to place {} on node {}", placeCapacity, node);
                    }
                }
            }
        }
        return false;
    }

    private Pod computeMinimumMigrateablePod(List<Pod> eligiblePods) {
        if (eligiblePods != null && eligiblePods.size() == 1) {
            return eligiblePods.get(0);
        } else if(eligiblePods != null && eligiblePods.size() > 1) {
            return eligiblePods.stream().min((pod1, pod2) -> (int)(pod2.getRequest().getCpuMillicore() -  pod2.getRequest().getCpuMillicore())).get();
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

    private int computeNormalPlacement(Capacity placeCapacity, List<Node> nodes) {
        for(int nodeId=0; nodeId < nodes.size(); nodeId++) {
            Capacity requiredCapacity = computeRequiredCapacity(placeCapacity, nodes.get(nodeId));
            if(requiredCapacity.getCpuMillicore() <=0 && requiredCapacity.getMemoryMB() <= 0) {
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
            log.info("Required Capacity for node {} is {}", nodeId, capacity);
            Long pairedValue = PairDepair.pair(capacity.getMemoryMB(), capacity.getCpuMillicore());
            log.info("Paired value for node {} is {}", nodeId, pairedValue);
            pairedCapacity.put(nodeId, pairedValue);
        }
        Map<Integer, Long> sortedPairedCapacity = sortByValues(pairedCapacity);
        return new ArrayList<>(sortedPairedCapacity.keySet());
    }

    private Capacity computeRequiredCapacity(Capacity placeCapacity, Node node) {
            Capacity availableCapacity = node.getAvailableCapacity();
            return new Capacity(Math.abs(placeCapacity.getMemoryMB() - availableCapacity.getMemoryMB()),
                    Math.abs(placeCapacity.getCpuMillicore() - availableCapacity.getCpuMillicore()));
    }

    private static <K, V extends Comparable<V>> Map<K, V>   sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator =  new Comparator<K>() {
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
}
