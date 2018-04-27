package com.vmware.borathon;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Pod extends Resource {

    private static final Logger log = LoggerFactory.getLogger(Pod.class);

    private Capacity request;

    private Node parentNode;

    private String name;

    public Pod(int id, String name, long memoryMB, long cpuMillicore) {
        super(id, name);
        this.request = new Capacity(memoryMB, cpuMillicore);
    }

    public void joinedNode(Node parentNode){
        this.parentNode = parentNode;
        log.debug("Pod {} joins the Node {}", this, parentNode);
    }

    public void leftNode(){
        log.debug("Pod {} left the Node {}", this, parentNode);
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
