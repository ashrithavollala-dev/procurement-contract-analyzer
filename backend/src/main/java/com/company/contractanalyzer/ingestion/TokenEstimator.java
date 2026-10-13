package com.company.contractanalyzer.ingestion;

/**
 * Cheap, deterministic token-count approximation, so the chunker and the prompt budget need no
 * tokenizer. For English text, BPE tokenizers (Llama, GPT, WordPiece) produce roughly one token
 * per 4 characters, or about 4 tokens per 3 words. The estimate takes the larger of the two, so
 * it errs on the side of overestimating, and a chunk or prompt sized with it will not overflow
 * the real limit. Runs in O(n) with one pass and no allocation.
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(CharSequence text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chars = 0;
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else {
                chars++;
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            }
        }
        int byChars = (chars + 3) / 4;
        int byWords = (words * 4 + 2) / 3;
        return Math.max(byChars, byWords);
    }
}
