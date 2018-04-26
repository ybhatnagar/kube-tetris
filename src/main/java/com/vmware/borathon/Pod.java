package com.vmware.borathon;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Pod {

    private static final Logger log = LoggerFactory.getLogger(Pod.class);

    private Capacity capacity;

    private Node parentNode;

    private String name;

    public Pod(String name, long memoryMB, long cpuMillicore) {
        this.capacity = new Capacity(memoryMB, cpuMillicore);
        this.name = name;
    }

    void joinedNode(Node parentNode){
        this.parentNode = parentNode;
        log.info("Pod {} joins the Node {}", this, parentNode);

    }

    @Override
    public String toString() {
        return "Pod{" +
                "capacity=" + capacity +
                ", parentNode=" + parentNode.getName() +
                ", name='" + name + '\'' +
                '}';
    }
}
