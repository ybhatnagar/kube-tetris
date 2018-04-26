package com.vmware.borathon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pod extends Capacity{

    private Logger log = LoggerFactory.getLogger(Pod.class);

    private Node node;
    private String podName;

    public Pod(int memoryGB, int cpuMillicore) {
        super(memoryGB, cpuMillicore);
    }

    void joinedNode(Node node){
        log.info("{} joins the Node {}", this, node);
        this.node = node;
    }
}
