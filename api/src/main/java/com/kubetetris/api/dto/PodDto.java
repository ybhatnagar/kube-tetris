package com.kubetetris.api.dto;

public record PodDto(
        String uid,
        String name,
        String namespace,
        String kind,
        String nodeName,
        long cpuReq,
        long memReq,
        String qos,
        OwnerRefDto ownerRef,
        boolean reversible,
        boolean pdbOk,
        PinDto pins
) {}
