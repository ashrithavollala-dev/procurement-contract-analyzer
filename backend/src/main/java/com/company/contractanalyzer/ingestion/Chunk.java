package com.company.contractanalyzer.ingestion;

/**
 * One retrievable unit of contract text.
 *
 * @param page       1-based page on which the chunk's text starts
 * @param chunkIndex 0-based position in document order
 * @param heading    clause heading the chunk belongs to (null for preamble text)
 * @param text       chunk text (heading line first, then clause body)
 */
public record Chunk(int page, int chunkIndex, String heading, String text) {
}
