package com.vmware.borathon;

import java.util.List;

public interface PodPlacementService {
    boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacity(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacityWithMultipleMigration(Capacity placeCapacity, List<Node> nodes);
}
