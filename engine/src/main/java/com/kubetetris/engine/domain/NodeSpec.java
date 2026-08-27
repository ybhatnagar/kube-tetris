package com.kubetetris.engine.domain;

/** Immutable node identity + allocatable capacity. Node identity = name. */
public record NodeSpec(String name, ResourceReq allocatable) {
}
