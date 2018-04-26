package com.vmware.borathon;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Capacity {
    private long memoryMB;
    private long cpuMillicore;

    public Capacity(long memoryMB, long cpuMillicore) {
        this.memoryMB = memoryMB;
        this.cpuMillicore = cpuMillicore;
    }

    public double getCpuMemoryrRatio(){
        return (this.cpuMillicore/this.memoryMB);
    }
}
