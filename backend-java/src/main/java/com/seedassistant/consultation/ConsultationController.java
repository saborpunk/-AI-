package com.seedassistant.consultation;

import com.seedassistant.draft.DraftModels;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/consultations")
public class ConsultationController {
    private final ConsultationService service;
    public ConsultationController(ConsultationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Consultation.View> create(@Valid @RequestBody DraftModels.Request request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/consultations/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public Consultation.View get(@PathVariable UUID id) { return service.get(id.toString()); }

    @GetMapping
    public Consultation.Page history(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.history(page, size);
    }

    @PostMapping("/{id}/draft")
    public Consultation.View generate(@PathVariable UUID id, @Valid @RequestBody Consultation.Version request,
                                      HttpServletRequest httpRequest) {
        return service.generate(id.toString(), request.version(), (String) httpRequest.getAttribute("requestId"));
    }

    @PatchMapping("/{id}/review")
    public Consultation.View review(@PathVariable UUID id, @Valid @RequestBody Consultation.Review request) {
        return service.review(id.toString(), request);
    }
}
