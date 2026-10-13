package com.company.contractanalyzer.controller;

import com.company.contractanalyzer.dto.response.SystemStatusResponse;
import com.company.contractanalyzer.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
public class SystemController {

    private final SystemStatusService systemStatusService;

    public SystemController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @Operation(summary = "LLM provider and reachability, embedding model and dimension")
    @GetMapping("/status")
    public SystemStatusResponse status() {
        return systemStatusService.status();
    }
}
