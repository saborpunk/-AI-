package com.seedassistant.controller;

import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.ConsultationSession;
import com.seedassistant.service.ConsultationSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
public class ConsultationSessionController {
    private final ConsultationSessionService service;
    public ConsultationSessionController(ConsultationSessionService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<ConsultationSession> create(@Valid @RequestBody ConsultationSessionCreateRequest request) {
        var row=service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/sessions/"+row.getId())).body(row);
    }
    @GetMapping("/{id}")
    public ConsultationSession get(@PathVariable UUID id) { return service.get(id.toString()); }
    @GetMapping
    public PageResponse<ConsultationSession> search(@RequestParam(defaultValue="") @Size(max=200) String keyword,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.search(keyword,status,page,size); }
    @PutMapping("/{id}")
    public ConsultationSession update(@PathVariable UUID id,@Valid @RequestBody ConsultationSessionUpdateRequest request) { return service.update(id.toString(),request); }
    @PatchMapping("/{id}/status")
    public ConsultationSession status(@PathVariable UUID id,@Valid @RequestBody StatusRequest request) { return service.status(id.toString(),request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,@RequestParam @PositiveOrZero long version) {
        service.delete(id.toString(),version); return ResponseEntity.noContent().build();
    }
}
