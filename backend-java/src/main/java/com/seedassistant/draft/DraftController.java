package com.seedassistant.draft;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/germination-drafts")
public class DraftController {
    private final PythonDraftClient client;

    public DraftController(PythonDraftClient client) {
        this.client = client;
    }

    @PostMapping
    public DraftModels.Response generate(@Valid @RequestBody DraftModels.Request request,
                                         HttpServletRequest httpRequest) {
        String requestId = (String) httpRequest.getAttribute("requestId");
        return new DraftModels.Response(requestId, client.generate(requestId, request));
    }
}
