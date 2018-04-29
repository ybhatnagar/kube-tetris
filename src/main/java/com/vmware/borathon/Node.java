package com.vmware.borathon;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@Setter
public class Node extends Resource{

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private SystemController systemController;

    private static final long CPU_HEADROOM = 0;
    private static final long MEM_HEADROOM = 0;

    private Map<String,Pod> pods;

    private Capacity totalCapacity;

    private Capacity availableCapacity;

    public Node(String id, String name, long memoryMB, long cpuMillicore) {
        super(id, name);
        this.totalCapacity = new Capacity(memoryMB, cpuMillicore);
        this.availableCapacity = new Capacity(memoryMB, cpuMillicore);
        this.pods = new ConcurrentHashMap<>();
    }

    public boolean addPod(Pod pod) {
        if ((availableCapacity.getMemoryMB() - MEM_HEADROOM) >= pod.getRequest().getMemoryMB()
                && (availableCapacity.getCpuMillicore() - CPU_HEADROOM) >= pod.getRequest().getCpuMillicore()) {
            availableCapacity.setMemoryMB(availableCapacity.getMemoryMB() - pod.getRequest().getMemoryMB());
            availableCapacity.setCpuMillicore(availableCapacity.getCpuMillicore() - pod.getRequest().getCpuMillicore());
            if(!pod.isSystemPod()){
                pods.put(pod.getId(),pod);
            }
            pod.joinedNode(this);
            return true;
        }
        return false;
    }

    public boolean removePod(Pod pod) {
            Pod removedPod = pods.remove(pod.getId());
            if(removedPod != null) {
                availableCapacity.setMemoryMB(availableCapacity.getMemoryMB() + pod.getRequest().getMemoryMB());
                availableCapacity.setCpuMillicore(availableCapacity.getCpuMillicore() + pod.getRequest().getCpuMillicore());
                pod.leftNode();
                return true;
            }
            return false;
    }


    public void joinedMigrationController(SystemController systemController){
        this.systemController = systemController;
        log.debug("{} joins the migration controller", this);
    }

    public void leftMigrationController(){
        log.debug("{} left the migration controller", this);
    }

    @Override
    public String toString() {
        return "Node{" +
                "name='" + this.getName() + '\'' +
//                ", totalCapacity=" + totalCapacity +
                ", availableCapacity=" + availableCapacity +
                '}';
    }

    public List<Pod> getPodsSortedByCpu(){
        return this.getPods().values()
                .stream()
                .sorted((pod1, pod2) -> Long.compare(pod2.getRequest().getCpuMillicore(), pod1.getRequest().getCpuMillicore()))
                .collect(Collectors.toList());
    }

    public List<Pod> getPodsSortedByMem(){
        return this.getPods().values()
                .stream()
                .sorted((pod1, pod2) -> Long.compare(pod2.getRequest().getMemoryMB(), pod1.getRequest().getMemoryMB()))
                .collect(Collectors.toList());

    }

    public Node clone() {
       Node node = new Node(this.getId(), this.getName(),this.totalCapacity.getMemoryMB(), this.totalCapacity.getCpuMillicore());
       this.getPods().forEach((integer, pod) -> {
           node.addPod(pod.clone());
       });
       node.setAvailableCapacity(this.availableCapacity);
       return node;
    }

    public double getDistanceFromPivot(double pivotratio){
        return Math.abs(pivotratio - this.getAvailableCapacity().getCpuMemoryRatio());
    }

}
