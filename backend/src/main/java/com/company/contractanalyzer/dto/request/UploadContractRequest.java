package com.company.contractanalyzer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/** multipart/form-data upload: the PDF plus optional descriptive fields. */
public record UploadContractRequest(
        @Schema(description = "The contract PDF", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "a PDF file is required")
        MultipartFile file,

        @Schema(description = "Display title; defaults to the file name", maxLength = 255)
        @Size(max = 255)
        String title,

        @Schema(description = "Vendor (supplier) name", maxLength = 255)
        @Size(max = 255)
        String vendorName) {
}
