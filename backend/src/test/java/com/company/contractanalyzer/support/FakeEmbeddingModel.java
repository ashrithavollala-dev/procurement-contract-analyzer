package com.company.contractanalyzer.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic, offline stand-in for the 384-dimensional ONNX embedding model: a hashed
 * bag-of-words vector over crude 5-character stems, L2-normalised. Texts that share words
 * are close in cosine space, which is all the retrieval tests need.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 384;

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "this", "with", "shall", "will", "any", "all", "are", "from",
            "its", "not", "may", "such", "under", "into", "each", "upon", "than", "other", "been", "has",
            "have", "does", "what", "which", "who", "how", "when", "where", "there", "their", "they",
            "party", "parties", "agreement", "contract");

    private final int dimensions;

    public FakeEmbeddingModel() {
        this(DIMENSIONS);
    }

    public FakeEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vectorize(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    public float[] vectorize(String text) {
        float[] vector = new float[dimensions];
        // Small constant component: avoids an all-zero vector (cosine distance would be NaN).
        vector[0] = 0.01f;
        if (text != null) {
            for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.length() < 3 || STOP_WORDS.contains(token)) {
                    continue;
                }
                String stem = token.length() > 5 ? token.substring(0, 5) : token;
                int hash = stem.hashCode() * 0x9E3779B1;
                int index = 1 + Math.floorMod(hash, dimensions - 1);
                vector[index] += 1.0f;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
