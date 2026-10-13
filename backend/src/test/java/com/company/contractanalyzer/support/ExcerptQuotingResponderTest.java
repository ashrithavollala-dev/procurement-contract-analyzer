package com.company.contractanalyzer.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.ai.Excerpt;
import com.company.contractanalyzer.ai.PromptLoader;
import com.company.contractanalyzer.repository.RetrievedChunk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/** Guards the test double itself against the real judge prompt template. */
class ExcerptQuotingResponderTest {

    @Test
    void quotesExcerptOneFromTheRenderedJudgePrompt() {
        RetrievedChunk chunk = new RetrievedChunk("x", UUID.randomUUID(), 3, 4, "ARTICLE 8 LIMITATION OF LIABILITY / 8.1",
                "ARTICLE 8 LIMITATION OF LIABILITY 8.1 The aggregate liability of each party shall not exceed the fees.", 0.8);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("ruleCode", "LIABILITY_CAP");
        variables.put("ruleTitle", "Limitation of liability cap");
        variables.put("question", "Is liability capped?");
        variables.put("guidance", "Cap with carve-outs.");
        variables.put("excerpts", Excerpt.renderAll(Excerpt.number(List.of(chunk))));
        variables.put("retryNote", "");
        variables.put("format", "Reply with JSON.");
        String user = new PromptLoader().render(PromptLoader.COMPLIANCE_JUDGE, variables);

        String reply = new ExcerptQuotingResponder(Set.of()).apply(new Prompt(new UserMessage(user)));

        assertThat(reply).as(user).contains("\"COMPLIANT\"").contains("ARTICLE 8 LIMITATION OF LIABILITY 8.1 The aggregate");
    }
}
