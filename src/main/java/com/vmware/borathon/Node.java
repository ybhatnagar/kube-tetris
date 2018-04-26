package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private MigrationController migrationController;

    private List<Pod> pods;

    private String name;

    private Capacity totalCapacity;

    private Capacity availableCapacity;

    public Node(String name, long memoryMB, long cpuMillicore) {
        this.totalCapacity = new Capacity(memoryMB, cpuMillicore);
        this.availableCapacity = new Capacity(memoryMB, cpuMillicore);
        this.name = name;
        this.pods = new ArrayList<>();
    }

    public boolean addPod(Pod pod) {
        if (availableCapacity.getMemoryMB() >= pod.getRequest().getMemoryMB()
                && availableCapacity.getCpuMillicore() >= pod.getRequest().getCpuMillicore()) {
            availableCapacity.setMemoryMB(availableCapacity.getMemoryMB() - pod.getRequest().getMemoryMB());
            availableCapacity.setCpuMillicore(availableCapacity.getCpuMillicore() - pod.getRequest().getCpuMillicore());
            pods.add(pod);
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
                "name='" + name + '\'' +
                ", totalCapacity=" + totalCapacity +
                ", availableCapacity=" + availableCapacity +
                '}';
    }
}
