package com.company.contractanalyzer.dto.response;

import java.util.List;

/** {@code mode} is "rag" or "tools": the mode that actually produced the answer. */
public record AskResponse(String answer, boolean grounded, String mode, List<CitationResponse> citations) {
}
