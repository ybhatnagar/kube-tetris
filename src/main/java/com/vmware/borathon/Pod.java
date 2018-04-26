package com.vmware.borathon;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
@ToString
public class Pod extends Capacity{

    private static final Logger log = LoggerFactory.getLogger(Pod.class);

    private Node parentNode;

    private String name;

    public Pod(String name, long memoryGB, long cpuMillicore) {
        super(memoryGB, cpuMillicore);
        this.name = name;
    }

    void joinedNode(Node parentNode){
        log.info("Pod {} joins the Node {}", this, parentNode);
        this.parentNode = parentNode;

    }
}
