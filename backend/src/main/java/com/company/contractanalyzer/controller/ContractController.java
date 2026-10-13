package com.company.contractanalyzer.controller;

import com.company.contractanalyzer.dto.request.UploadContractRequest;
import com.company.contractanalyzer.dto.response.ContractResponse;
import com.company.contractanalyzer.dto.response.ErrorResponse;
import com.company.contractanalyzer.dto.response.PageResponse;
import com.company.contractanalyzer.enums.ContractStatus;
import com.company.contractanalyzer.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter only: validation annotations, status codes and headers; all logic lives in ContractService. */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Contracts", description = "Upload, list, download, reprocess and delete contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @Operation(summary = "Upload a contract PDF; ingestion runs asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted and queued")
    @ApiResponse(responseCode = "409", description = "DUPLICATE_CONTRACT",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "INGESTION_QUEUE_FULL",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponse> upload(@Valid @ModelAttribute UploadContractRequest request) {
        ContractResponse created = contractService.upload(request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/contracts/" + created.id())).body(created);
    }

    @Operation(summary = "List contracts (newest first) with optional status and text filters")
    @GetMapping
    public PageResponse<ContractResponse> list(
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return contractService.list(status, q, page, size);
    }

    @Operation(summary = "Get one contract")
    @GetMapping("/{id}")
    public ContractResponse get(@PathVariable UUID id) {
        return contractService.get(id);
    }

    @Operation(summary = "Download the original PDF")
    @GetMapping(value = "/{id}/file", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        ContractService.StoredFile file = contractService.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(new FileSystemResource(file.path()));
    }

    @Operation(summary = "Reprocess a FAILED contract (or a READY one with force=true)")
    @ApiResponse(responseCode = "202", description = "Re-queued")
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ContractResponse> reprocess(@PathVariable UUID id,
                                                      @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.accepted().body(contractService.reprocess(id, force));
    }

    @Operation(summary = "Delete a contract, its vectors, its file and its audits")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contractService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
