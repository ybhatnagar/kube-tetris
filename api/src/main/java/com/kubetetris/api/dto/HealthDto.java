package com.kubetetris.api.dto;

public record HealthDto(String status) {
    public static final HealthDto OK = new HealthDto("ok");
}
