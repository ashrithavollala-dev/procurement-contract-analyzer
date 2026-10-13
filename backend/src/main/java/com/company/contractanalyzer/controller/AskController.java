package com.company.contractanalyzer.controller;

import com.company.contractanalyzer.dto.request.AskRequest;
import com.company.contractanalyzer.dto.response.AskResponse;
import com.company.contractanalyzer.service.AskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Q&A", description = "Grounded questions about one contract")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @Operation(summary = "Ask a question; the answer cites retrieved pages (may take minutes on a CPU-only model)")
    @PostMapping("/{contractId}/ask")
    public AskResponse ask(@PathVariable UUID contractId, @Valid @RequestBody AskRequest request) {
        return askService.ask(contractId, request);
    }
}
