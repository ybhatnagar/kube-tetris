package com.vmware.borathon.scheduler;

import java.util.List;

import com.vmware.borathon.Capacity;
import com.vmware.borathon.Node;
import com.vmware.borathon.Pod;

public interface CapacityPlacementService {
    boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacity(Pod placePod, List<Node> nodes);
    boolean placeCapacityWithMultipleMigration(Pod placePod, List<Node> nodes);
    List<MigrationPlanDto> placeMyWorkload(Capacity workloadCapacity, List<Node> nodes);
    void initData();
}
