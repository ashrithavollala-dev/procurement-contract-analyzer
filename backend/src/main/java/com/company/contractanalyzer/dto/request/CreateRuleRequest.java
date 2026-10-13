package com.company.contractanalyzer.dto.request;

import com.company.contractanalyzer.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRuleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$",
                message = "must be 3-64 upper-case letters, digits or underscores, starting with a letter") String code,
        @NotBlank @Size(min = 3, max = 200) String title,
        @NotBlank @Size(min = 10, max = 2000) String question,
        @NotBlank @Size(min = 10, max = 4000) String guidance,
        @NotNull Severity severity,
        @NotNull Boolean mandatory,
        Boolean active) {
}
