package com.company.contractanalyzer.ingestion;

/** Normalised text of one PDF page (1-based page number). */
public record PageText(int page, String text) {
}
