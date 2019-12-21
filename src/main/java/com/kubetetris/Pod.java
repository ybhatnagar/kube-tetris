package com.kubetetris;

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

    boolean systemPod;

    public Pod(String id, String name, long memoryMB, long cpuMillicore,boolean isSystem) {
        super(id, name);
        this.request = new Capacity(memoryMB, cpuMillicore);
        systemPod=isSystem;
    }

    public void joinedNode(Node parentNode){
        this.parentNode = parentNode;
        log.debug("Pod {} joins the Node {}", this, parentNode);
    }

    public void leftNode(){
        this.parentNode = null;
        log.debug("Pod {} left the Node {}", this, parentNode);
    }

    @Override
    public String toString() {
        return "Pod{" +
                "id=" + getId() +
                ", request=" + request +
                ", parentNode=" + (parentNode == null ? null:parentNode.getName()) +
                ", name='" + this.getName() + '\'' +
                '}';
    }

    public Pod clone() {
        return new Pod(this.getId(), this.getName(), this.getRequest().getMemoryMB(), this.getRequest().getCpuMillicore(),this.systemPod);
    }
}
