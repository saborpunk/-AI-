package com.seedassistant.controller;

import com.seedassistant.dto.command.*;
import com.seedassistant.dto.response.PageResponse;
import com.seedassistant.entity.ArticleCategory;
import com.seedassistant.service.ArticleCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/article-categories")
public class ArticleCategoryController {
    private final ArticleCategoryService service;
    public ArticleCategoryController(ArticleCategoryService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<ArticleCategory> create(@Valid @RequestBody CategoryCreateRequest request) {
        var row=service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/article-categories/"+row.getId())).body(row);
    }
    @GetMapping("/{id}")
    public ArticleCategory get(@PathVariable UUID id) { return service.get(id.toString()); }
    @GetMapping
    public PageResponse<ArticleCategory> search(@RequestParam(defaultValue="") @Size(max=80) String keyword,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.search(keyword,status,page,size); }
    @PutMapping("/{id}")
    public ArticleCategory update(@PathVariable UUID id,@Valid @RequestBody CategoryUpdateRequest request) { return service.update(id.toString(),request); }
    @PatchMapping("/{id}/status")
    public ArticleCategory status(@PathVariable UUID id,@Valid @RequestBody StatusRequest request) { return service.status(id.toString(),request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,@RequestParam @PositiveOrZero long version) {
        service.delete(id.toString(),version); return ResponseEntity.noContent().build();
    }
}
