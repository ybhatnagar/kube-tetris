package com.vmware.borathon;

import java.util.List;
import java.util.Map;

public interface CapacityPlacementService {
    boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacity(Pod placePod, List<Node> nodes);
    boolean placeCapacityWithMultipleMigration(Pod placePod, List<Node> nodes);
    Map<Integer, Map<Pod, Integer>> placeMyWorkload(Capacity workloadCapacity, List<Node> nodes);
    void initData();
}
