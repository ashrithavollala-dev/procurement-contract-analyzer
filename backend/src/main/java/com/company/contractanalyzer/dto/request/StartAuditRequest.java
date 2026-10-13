package com.company.contractanalyzer.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Optional subset of rule codes; null or empty means all active rules. */
public record StartAuditRequest(
        @Size(max = 50)
        List<@Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "must be an upper-case rule code") String> ruleCodes) {
}
