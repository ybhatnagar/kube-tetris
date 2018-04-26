package com.vmware.borathon;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node extends Capacity{

    private Logger log = LoggerFactory.getLogger(Node.class);

    private MigrationController migrationController;

    private final List<Pod> pods;

    public Node(int memoryGB, int cpuMillicore) {
        super(memoryGB, cpuMillicore);
        this.pods = new ArrayList<>();
    }

    void addPod(Pod pod) {
        pods.add(pod);
        pod.joinedNode(this);
    }

    void joinedMigrationController(MigrationController migrationController){
        log.info("{} joins the migration controller", this);
        this.migrationController = migrationController;
    }
}
