package com.company.contractanalyzer.dto.request;

import com.company.contractanalyzer.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of a rule's editable fields; the code is immutable. */
public record UpdateRuleRequest(
        @NotBlank @Size(min = 3, max = 200) String title,
        @NotBlank @Size(min = 10, max = 2000) String question,
        @NotBlank @Size(min = 10, max = 4000) String guidance,
        @NotNull Severity severity,
        @NotNull Boolean mandatory,
        @NotNull Boolean active) {
}
