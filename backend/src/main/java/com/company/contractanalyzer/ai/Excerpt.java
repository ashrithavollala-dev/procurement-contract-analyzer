package com.company.contractanalyzer.ai;

import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;

/**
 * A retrieved chunk as shown to the model, with a short id ({@code E1..En}) the model cites.
 * The ids exist only for this prompt, so grounding can check that every citation refers to
 * text the server actually sent.
 */
public record Excerpt(String id, RetrievedChunk chunk) {

    public static List<Excerpt> number(List<RetrievedChunk> chunks) {
        List<Excerpt> excerpts = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            excerpts.add(new Excerpt("E" + (i + 1), chunks.get(i)));
        }
        return excerpts;
    }

    /** Delimited rendering: the markers tell the model where untrusted contract data starts and ends. */
    public String render() {
        String heading = chunk.heading() == null ? "" : " | " + sanitizeMarker(chunk.heading());
        return "<<<EXCERPT " + id + " | page " + chunk.page() + heading + ">>>\n"
                + sanitizeMarker(chunk.text()) + "\n<<<END " + id + ">>>";
    }

    public static String renderAll(List<Excerpt> excerpts) {
        StringBuilder sb = new StringBuilder();
        for (Excerpt excerpt : excerpts) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(excerpt.render());
        }
        return sb.toString();
    }

    /** Contract text cannot forge excerpt boundaries: marker sequences inside it are neutralised. */
    static String sanitizeMarker(String text) {
        return text.replace("<<<", "< < <").replace(">>>", "> > >");
    }
}
