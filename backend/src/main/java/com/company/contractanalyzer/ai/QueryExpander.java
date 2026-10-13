package com.company.contractanalyzer.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic query expansion for the agent's REFLECT step. When the first retrieval finds
 * nothing convincing, the agent searches again with the vocabulary contracts actually use for
 * that topic, for example "aggregate liability shall not exceed" instead of "cap the
 * supplier's liability".
 *
 * <p>A fixed synonym table was chosen over asking the LLM to write the query. It costs no
 * extra slow LLM call on a CPU-only model, it is reproducible in tests, and it cannot be
 * steered by prompt injection. Unknown (user-created) rules fall back to the rule title plus
 * the question's content words.
 */
@Component
public class QueryExpander {

    private static final Map<String, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry("PAYMENT_TERMS", List.of("invoice payable within days of receipt", "payment terms net days",
                    "late payment interest disputed invoice", "fees currency")),
            Map.entry("TERMINATION_CONVENIENCE", List.of("terminate for convenience written notice",
                    "termination without cause notice period", "termination fee")),
            Map.entry("LIABILITY_CAP", List.of("limitation of liability aggregate liability shall not exceed",
                    "liability cap fees paid twelve months", "exclusion of indirect consequential damages")),
            Map.entry("INDEMNIFICATION", List.of("indemnify defend hold harmless third party claims",
                    "intellectual property infringement claim indemnity")),
            Map.entry("CONFIDENTIALITY", List.of("confidential information non-disclosure",
                    "keep confidential survive termination years")),
            Map.entry("GOVERNING_LAW", List.of("governed by the laws of", "exclusive jurisdiction courts arbitration",
                    "dispute resolution venue")),
            Map.entry("IP_OWNERSHIP", List.of("intellectual property rights deliverables ownership assignment",
                    "licence background IP royalty-free perpetual")),
            Map.entry("DATA_PROTECTION", List.of("personal data processing instructions security measures",
                    "data breach notification hours", "sub-processor data protection")),
            Map.entry("SLA_PENALTIES", List.of("service levels availability uptime service credits",
                    "response time resolution time remedy")),
            Map.entry("FORCE_MAJEURE", List.of("force majeure events beyond reasonable control",
                    "act of god delay suspension notice")),
            Map.entry("AUTO_RENEWAL", List.of("renew automatically successive terms",
                    "notice of non-renewal before expiry", "renewal price increase")),
            Map.entry("AUDIT_RIGHTS", List.of("right to audit records inspection", "audit books accounts controls access",
                    "independent auditor reasonable notice")));

    private static final Set<String> STOP_WORDS = Set.of(
            "does", "the", "a", "an", "and", "or", "of", "to", "for", "in", "on", "by", "with", "is", "are", "be",
            "any", "what", "which", "when", "how", "who", "contract", "agreement", "define", "defined", "there",
            "this", "that", "under", "it", "its", "if", "so", "at", "as", "from", "into", "whether", "do", "can");

    /**
     * @param attempt 1-based index of the expansion (the original query is attempt 0)
     * @return a query not in {@code previous}, or null when expansion is exhausted
     */
    public String expand(RuleSpec rule, List<String> previous, int attempt) {
        List<String> candidates = KEYWORDS.get(rule.code());
        if (candidates != null) {
            for (int i = 0; i < candidates.size(); i++) {
                String candidate = candidates.get((attempt - 1 + i) % candidates.size());
                if (!previous.contains(candidate)) {
                    return candidate;
                }
            }
        }
        String fallback = rule.title() + " " + contentWords(rule.question());
        return previous.contains(fallback) ? null : fallback.strip();
    }

    static String contentWords(String text) {
        Set<String> words = new LinkedHashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+")) {
            if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                words.add(token);
            }
            if (words.size() == 10) {
                break;
            }
        }
        return String.join(" ", words);
    }
}
