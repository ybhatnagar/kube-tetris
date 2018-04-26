package com.vmware.borathon;

import java.util.List;

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
        log.info("Capacity {} cannot be placed on total Available cpu {} , memory {}",placeCapacity, totalAvailableCpu, totalAvailableMem);
        return false;
    }

    @Override
    public boolean placeCapacity(Capacity placeCapacity, List<Node> nodes) {
        if (checkPlacementElibility(placeCapacity, nodes)) {
            //requiredCapacities stores the amount of resource required to place this capacity on ith node
        }
        return false;
    }
}
