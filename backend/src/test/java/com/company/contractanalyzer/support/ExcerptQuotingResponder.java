package com.company.contractanalyzer.support;

import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A deterministic "judge" for integration tests. It reads the rule code and excerpt E1 from the
 * actual prompt. For rules listed as missing it answers NOT_FOUND. Otherwise it answers
 * COMPLIANT, citing E1 with a quote copied verbatim from E1, so grounding really verifies.
 */
public final class ExcerptQuotingResponder implements Function<Prompt, String> {

    private static final Pattern RULE = Pattern.compile("RULE ([A-Z][A-Z0-9_]+):");
    private static final Pattern FIRST_EXCERPT = Pattern.compile("<<<EXCERPT E1 \\| page \\d+[^>]*>>>\\n(.+?)\\n<<<END E1>>>",
            Pattern.DOTALL);

    private final Set<String> missingRules;

    public ExcerptQuotingResponder(Set<String> missingRules) {
        this.missingRules = missingRules;
    }

    @Override
    public String apply(Prompt prompt) {
        String user = userText(prompt);
        Matcher rule = RULE.matcher(user);
        String code = rule.find() ? rule.group(1) : "UNKNOWN";
        Matcher excerpt = FIRST_EXCERPT.matcher(user);
        if (missingRules.contains(code) || !excerpt.find()) {
            return "{\"status\":\"NOT_FOUND\",\"confidence\":0.8,\"explanation\":\"No excerpt addresses " + code
                    + ".\",\"citedExcerptIds\":[],\"evidenceQuote\":\"\",\"recommendation\":\"Add a clause.\"}";
        }
        String text = excerpt.group(1).replace("\"", "'").replace("\\", " ");
        String quote = text.length() > 60 ? text.substring(0, 60) : text;
        return "{\"status\":\"COMPLIANT\",\"confidence\":0.9,\"explanation\":\"E1 satisfies " + code
                + ".\",\"citedExcerptIds\":[\"E1\"],\"evidenceQuote\":\"" + quote.strip()
                + "\",\"recommendation\":\"No action required.\"}";
    }

    static String userText(Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        for (Message message : prompt.getInstructions()) {
            if (message.getMessageType() == MessageType.USER) {
                sb.append(message.getText());
            }
        }
        return sb.toString();
    }
}
