package com.seedassistant.controller;

import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.KnowledgeArticle;
import com.seedassistant.service.KnowledgeArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/articles")
public class KnowledgeArticleController {
    private final KnowledgeArticleService service;
    public KnowledgeArticleController(KnowledgeArticleService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<KnowledgeArticle> create(@Valid @RequestBody KnowledgeArticleCreateRequest request) {
        var row=service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/articles/"+row.getId())).body(row);
    }
    @GetMapping("/{id}")
    public KnowledgeArticle get(@PathVariable UUID id) { return service.get(id.toString()); }
    @GetMapping
    public PageResponse<KnowledgeArticle> search(@RequestParam(defaultValue="") @Size(max=200) String keyword,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.search(keyword,status,page,size); }
    @PutMapping("/{id}")
    public KnowledgeArticle update(@PathVariable UUID id,@Valid @RequestBody KnowledgeArticleUpdateRequest request) { return service.update(id.toString(),request); }
    @PatchMapping("/{id}/status")
    public KnowledgeArticle status(@PathVariable UUID id,@Valid @RequestBody StatusRequest request) { return service.status(id.toString(),request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,@RequestParam @PositiveOrZero long version) {
        service.delete(id.toString(),version); return ResponseEntity.noContent().build();
    }
}
