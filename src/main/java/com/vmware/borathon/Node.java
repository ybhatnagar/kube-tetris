package com.vmware.borathon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Node extends Resource{

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private MigrationController migrationController;

    private Map<Integer,Pod> pods;

    private Capacity totalCapacity;

    private Capacity availableCapacity;

    public Node(int id, String name, long memoryMB, long cpuMillicore) {
        super(id, name);
        this.totalCapacity = new Capacity(memoryMB, cpuMillicore);
        this.availableCapacity = new Capacity(memoryMB, cpuMillicore);
        this.pods = new HashMap<>();
    }

    public boolean addPod(Pod pod) {
        if (availableCapacity.getMemoryMB() >= pod.getRequest().getMemoryMB()
                && availableCapacity.getCpuMillicore() >= pod.getRequest().getCpuMillicore()) {
            availableCapacity.setMemoryMB(availableCapacity.getMemoryMB() - pod.getRequest().getMemoryMB());
            availableCapacity.setCpuMillicore(availableCapacity.getCpuMillicore() - pod.getRequest().getCpuMillicore());
            pods.put(pod.getId(),pod);
            pod.joinedNode(this);
            return true;
        }
        return false;
    }

    public void joinedMigrationController(MigrationController migrationController){
        this.migrationController = migrationController;
        log.info("{} joins the migration controller", this);
    }

    @Override
    public String toString() {
        return "Node{" +
                "name='" + this.getName() + '\'' +
                ", totalCapacity=" + totalCapacity +
                ", availableCapacity=" + availableCapacity +
                '}';
    }
}
