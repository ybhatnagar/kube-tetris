package com.vmware.borathon;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Pod {

    private static final Logger log = LoggerFactory.getLogger(Pod.class);

    private Capacity request;

    private Node parentNode;

    private String name;

    public Pod(String name, long memoryMB, long cpuMillicore) {
        this.name = name;
        this.request = new Capacity(memoryMB, cpuMillicore);
    }

    public void joinedNode(Node parentNode){
        this.parentNode = parentNode;
        log.info("Pod {} joins the Node {}", this, parentNode);

    }

    @Override
    public String toString() {
        return "Pod{" +
                "request=" + request +
                ", parentNode=" + parentNode.getName() +
                ", name='" + name + '\'' +
                '}';
    }
}
