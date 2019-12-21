package com.kubetetris.scheduler;

import java.util.List;

import com.kubetetris.Capacity;
import com.kubetetris.Node;
import com.kubetetris.Pod;

public interface CapacityPlacementService {
    boolean checkPlacementElibility(Capacity placeCapacity, List<Node> nodes);
    boolean placeCapacity(Pod placePod, List<Node> nodes);
    boolean placeCapacityWithMultipleMigration(Pod placePod, List<Node> nodes);
    List<MigrationPlanDto> placeMyWorkload(Capacity workloadCapacity, List<Node> nodes);
    void initData();
}
