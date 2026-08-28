package com.kubetetris.api.http;

import com.kubetetris.api.dto.HealthDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public HealthDto healthz() { return HealthDto.OK; }
}
