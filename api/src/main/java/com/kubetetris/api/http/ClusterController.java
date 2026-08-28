package com.kubetetris.api.http;

import com.kubetetris.api.dto.ClusterCreateRequestDto;
import com.kubetetris.api.dto.ClusterDto;
import com.kubetetris.api.dto.ClusterListDto;
import com.kubetetris.api.store.ClusterRecord;
import com.kubetetris.api.store.ClusterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clusters")
public class ClusterController {

    private final ClusterRegistry registry;

    public ClusterController(ClusterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ClusterListDto list() {
        List<ClusterDto> dtos = registry.list().stream().map(ClusterRecord::toDto).toList();
        return new ClusterListDto(dtos);
    }

    @PostMapping
    public ResponseEntity<ClusterDto> create(@RequestBody ClusterCreateRequestDto body) {
        ClusterRecord rec = registry.create(body.name(), body.apiUrl(), body.authMethod(), body.credentialRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(rec.toDto());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return registry.remove(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
