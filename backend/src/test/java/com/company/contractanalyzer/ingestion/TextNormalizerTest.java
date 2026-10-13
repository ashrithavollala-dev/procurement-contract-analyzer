package com.company.contractanalyzer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void joinsHyphenatedLineBreaksAndCollapsesWhitespace() {
        String raw = "The Supplier's liabil-\nity is capped.\r\nPayment   is\tdue in 30 days.";

        assertThat(TextNormalizer.normalizePage(raw))
                .isEqualTo("The Supplier's liability is capped.\nPayment is due in 30 days.");
    }

    @Test
    void keepsRealHyphensBeforeUppercaseOrDigits() {
        // A bare number on its own line would be dropped as a page number, so use real text after it.
        assertThat(TextNormalizer.normalizePage("Non-\nCompliance\nSLA-\n99.5% availability"))
                .isEqualTo("Non-\nCompliance\nSLA-\n99.5% availability");
    }

    @Test
    void dropsPageFurnitureAndExtraBlankLines() {
        String raw = "ARTICLE 1\n\n\n\nText here.\nPage 2 of 5\n- 3 -\n12\nEnd.";

        assertThat(TextNormalizer.normalizePage(raw)).isEqualTo("ARTICLE 1\n\nText here.\nEnd.");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(TextNormalizer.normalizePage(null)).isEmpty();
        assertThat(TextNormalizer.normalizePage("  \n ")).isEmpty();
    }

    @Test
    void canonicalFormIgnoresCaseWhitespaceAndQuoteStyle() {
        assertThat(TextNormalizer.canonical("  The “Supplier”\n shall   NOT’s "))
                .isEqualTo("the \"supplier\" shall not's");
    }
}
