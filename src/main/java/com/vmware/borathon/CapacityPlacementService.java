package com.vmware.borathon;

import java.util.List;
import java.util.Map;

public interface CapacityPlacementService {
    boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacity(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacityWithMultipleMigration(Capacity placeCapacity, List<Node> nodes);
    Map<Pod, Node> placeMyWorkload(Capacity workloadCapacity, List<Node> nodes);
    void initData();
}
