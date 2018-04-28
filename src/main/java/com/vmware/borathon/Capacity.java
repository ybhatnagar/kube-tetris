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

    public double getCpuMemoryRatio(){
        if(Double.compare(this.memoryMB, 0.0) == 0)
            return 1000000;
        if(Double.compare(this.cpuMillicore, 0.0) == 0)
            return 0.0;
        else
            return ((double)this.cpuMillicore/this.memoryMB);
    }
}
