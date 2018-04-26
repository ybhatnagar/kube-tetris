package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Node extends Capacity{

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private MigrationController migrationController;

    private List<Pod> pods;

    private String name;

    public Node(String name, long memoryGB, long cpuMillicore) {
        super(memoryGB, cpuMillicore);
        this.name = name;
        this.pods = new ArrayList<>();
    }

    boolean addPod(Pod pod) {
        if (this.memoryMB >= pod.memoryMB && this.cpuMillicore >= pod.cpuMillicore) {
            this.memoryMB = this.memoryMB - pod.memoryMB;
            this.cpuMillicore = this.cpuMillicore - pod.cpuMillicore;
            pods.add(pod);
            pod.joinedNode(this);
            return true;
        }
        return false;
    }

    void joinedMigrationController(MigrationController migrationController){
        this.migrationController = migrationController;
        log.info("{} joins the migration controller", this.name);
    }

    @Override
    public String toString() {
        return "Node{" +
                "name='" + name + '\'' +
                ", memoryMB=" + memoryMB +
                ", cpuMillicore=" + cpuMillicore +
                '}';
    }
}
