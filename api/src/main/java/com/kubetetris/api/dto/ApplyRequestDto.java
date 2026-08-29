package com.kubetetris.api.dto;

public record ApplyRequestDto(
        boolean ack,
        String pendingPodUid,
        String swapRef,
        ApplyOptsDto opts
) {}
