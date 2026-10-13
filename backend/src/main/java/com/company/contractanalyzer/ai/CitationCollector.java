package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side record of the chunks retrieved while answering one question. Citations shown to
 * the user come from here, never from text the model wrote, so a model cannot invent a page
 * reference. One instance per request, passed to tools through the ToolContext.
 */
public final class CitationCollector {

    private final Map<String, RetrievedChunk> chunks = new LinkedHashMap<>();

    public synchronized void addAll(List<RetrievedChunk> retrieved) {
        for (RetrievedChunk chunk : retrieved) {
            chunks.merge(chunk.id(), chunk, (a, b) -> a.score() >= b.score() ? a : b);
        }
    }

    public synchronized List<RetrievedChunk> chunks() {
        return new ArrayList<>(chunks.values());
    }

    public synchronized boolean isEmpty() {
        return chunks.isEmpty();
    }
}
