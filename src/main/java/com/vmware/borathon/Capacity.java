package com.vmware.borathon;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Capacity {
    long memoryGB;
    long cpuMillicore;

    public Capacity(long memoryGB, long cpuMillicore) {
        this.memoryGB = memoryGB;
        this.cpuMillicore = cpuMillicore;
    }
}
