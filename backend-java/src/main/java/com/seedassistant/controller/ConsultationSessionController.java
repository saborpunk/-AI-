package com.seedassistant.controller;

import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.ConsultationSession;
import com.seedassistant.service.SessionAccessService;
import jakarta.validation.Valid;
import com.seedassistant.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
public class ConsultationSessionController {
    private final SessionAccessService service;
    public ConsultationSessionController(SessionAccessService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<ConsultationSession> create(@Valid @RequestBody ConsultationSessionCreateRequest request, @AuthenticationPrincipal CurrentUser user) {
        var row=service.create(request,user);
        return ResponseEntity.created(URI.create("/api/v1/sessions/"+row.getId())).body(row);
    }
    @GetMapping("/{id}")
    public ConsultationSession get(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser user) { return service.get(id.toString(),user); }
    @GetMapping
    public PageResponse<ConsultationSession> search(@RequestParam(defaultValue="") @Size(max=200) String keyword,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, @AuthenticationPrincipal CurrentUser user) { return service.search(keyword,status,page,size,user); }
    @PutMapping("/{id}")
    public ConsultationSession update(@PathVariable UUID id,@Valid @RequestBody ConsultationSessionUpdateRequest request, @AuthenticationPrincipal CurrentUser user) { return service.update(id.toString(),request,user); }
    @PatchMapping("/{id}/status")
    public ConsultationSession status(@PathVariable UUID id,@Valid @RequestBody StatusRequest request, @AuthenticationPrincipal CurrentUser user) { return service.status(id.toString(),request,user); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,@RequestParam @PositiveOrZero long version, @AuthenticationPrincipal CurrentUser user) {
        service.delete(id.toString(),version,user); return ResponseEntity.noContent().build();
    }
}
