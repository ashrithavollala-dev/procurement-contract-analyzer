package com.company.contractanalyzer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.ingestion.ClauseAwareChunker.HeadingKind;
import com.company.contractanalyzer.ingestion.ClauseAwareChunker.HeadingMatch;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClauseAwareChunkerTest {

    private final ClauseAwareChunker defaults = new ClauseAwareChunker(ChunkingOptions.DEFAULTS);

    @Nested
    class HeadingDetection {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
            "ARTICLE 5|ARTICLE|5",
            "ARTICLE 9 LIMITATION OF LIABILITY|ARTICLE|9",
            "Article IV - Term and Termination|ARTICLE|4",
            "Section 4.2 Payment Terms|SECTION|4",
            "Clause 7|SECTION|7",
            "12. Termination|NUMBERED_TITLE|12",
            "3. Fees and Payment|NUMBERED_TITLE|3",
            "9.1 The aggregate liability of the Supplier shall not exceed the fees paid.|NUMBERED_CLAUSE|9",
            "11.2.3 Either Party may terminate|NUMBERED_CLAUSE|11"
        })
        void detectsDocumentedHeadingForms(String line, HeadingKind kind, String article) {
            HeadingMatch match = ClauseAwareChunker.detectHeading(line);
            assertThat(match).isNotNull();
            assertThat(match.kind()).isEqualTo(kind);
            assertThat(match.articleKey()).isEqualTo(article);
        }

        @Test
        void detectsAllCapsHeadingWithoutArticle() {
            HeadingMatch match = ClauseAwareChunker.detectHeading("CONFIDENTIALITY AND DATA PROTECTION");
            assertThat(match).isNotNull();
            assertThat(match.kind()).isEqualTo(HeadingKind.ALL_CAPS);
            assertThat(match.articleKey()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "The Supplier shall deliver the Services in accordance with Schedule A.",
            "12. The Customer shall pay all invoices within thirty days.",
            "USA",
            "in accordance with Section 4.2 of this Agreement the Supplier will provide the reports monthly to the Customer",
            "Page 3 of 7"
        })
        void ignoresBodyTextAndNoise(String line) {
            assertThat(ClauseAwareChunker.detectHeading(line)).isNull();
        }

        @Test
        void convertsRomanNumerals() {
            assertThat(ClauseAwareChunker.toArabic("XIV")).isEqualTo("14");
            assertThat(ClauseAwareChunker.toArabic("IX")).isEqualTo("9");
            assertThat(ClauseAwareChunker.toArabic("07")).isEqualTo("7");
        }
    }

    @Test
    void eachClauseBecomesItsOwnChunkWithHeadingMetadata() {
        String page = String.join("\n",
                "ARTICLE 3 PAYMENT",
                "3.1 " + sentences("Invoices are payable within thirty days of receipt", 8),
                "ARTICLE 4 CONFIDENTIALITY",
                "4.1 " + sentences("Each party shall keep confidential information secret", 8));

        List<Chunk> chunks = defaults.chunk(List.of(page));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).heading()).isEqualTo("ARTICLE 3 PAYMENT / 3.1");
        assertThat(chunks.get(0).text()).startsWith("ARTICLE 3 PAYMENT 3.1 Invoices");
        assertThat(chunks.get(1).heading()).isEqualTo("ARTICLE 4 CONFIDENTIALITY / 4.1");
        assertThat(chunks).extracting(Chunk::chunkIndex).containsExactly(0, 1);
    }

    @Test
    void twoLineArticleHeadingIsJoined() {
        String page = String.join("\n",
                "ARTICLE 9",
                "LIMITATION OF LIABILITY",
                "9.1 " + sentences("The aggregate liability of the Supplier is capped at the annual fees", 8));

        List<Chunk> chunks = defaults.chunk(List.of(page));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isEqualTo("ARTICLE 9 LIMITATION OF LIABILITY / 9.1");
    }

    @Test
    void oversizedClauseIsSplitOnSentenceBoundariesWithOverlap() {
        ChunkingOptions options = new ChunkingOptions(60, 20, 5);
        List<String> numbered = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            numbered.add("Sentence number " + i + " describes service level obligation detail " + i + ".");
        }
        String page = "5.1 " + String.join(" ", numbered);

        List<Chunk> chunks = new ClauseAwareChunker(options).chunk(List.of(page));

        assertThat(chunks).hasSizeGreaterThan(1);
        for (Chunk chunk : chunks) {
            assertThat(TokenEstimator.estimate(chunk.text())).isLessThanOrEqualTo(60);
            assertThat(chunk.text()).endsWith(".");
            assertThat(chunk.heading()).isEqualTo("5.1");
        }
        // Overlap: the last sentence of chunk N reappears at the start of chunk N+1.
        for (int i = 0; i + 1 < chunks.size(); i++) {
            String previous = chunks.get(i).text();
            String lastSentence = previous.substring(previous.lastIndexOf("Sentence number"));
            assertThat(chunks.get(i + 1).text()).startsWith(lastSentence);
        }
        // Nothing is lost: every sentence appears in some chunk.
        String all = String.join(" ", chunks.stream().map(Chunk::text).toList());
        for (int i = 1; i <= 12; i++) {
            assertThat(all).contains("detail " + i + ".");
        }
    }

    @Test
    void singleSentenceLongerThanMaxIsSplitOnWords() {
        ChunkingOptions options = new ChunkingOptions(40, 8, 5);
        String longSentence = "7.1 " + "word ".repeat(200).strip();

        List<Chunk> chunks = new ClauseAwareChunker(options).chunk(List.of(longSentence));

        assertThat(chunks).hasSizeGreaterThan(5);
        chunks.forEach(c -> assertThat(TokenEstimator.estimate(c.text())).isLessThanOrEqualTo(40));
    }

    @Test
    void tinyClausesMergeWithFollowingClauseOfSameArticle() {
        String page = String.join("\n",
                "ARTICLE 6 TERM",
                "6.1 This Agreement starts on the Effective Date.",
                "6.2 It continues for three years.",
                "6.3 " + sentences("Renewal requires written agreement of both parties", 6),
                "ARTICLE 7 NOTICES",
                "7.1 " + sentences("Notices must be in writing and delivered by courier", 6));

        List<Chunk> chunks = defaults.chunk(List.of(page));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text())
                .contains("6.1 This Agreement starts")
                .contains("6.2 It continues")
                .contains("6.3 Renewal");
        assertThat(chunks.get(1).text()).startsWith("ARTICLE 7 NOTICES");
    }

    @Test
    void tinyClauseDoesNotMergeAcrossArticles() {
        String page = String.join("\n",
                "6.1 This Agreement starts on the Effective Date.",
                "7.1 " + sentences("Notices must be in writing and delivered by courier", 6));

        List<Chunk> chunks = defaults.chunk(List.of(page));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("6.1 This Agreement starts on the Effective Date.");
    }

    @Test
    void chunksRememberThePageWhereTheirTextStarts() {
        String page1 = "ARTICLE 1 DEFINITIONS\n1.1 " + sentences("Definitions apply throughout", 6);
        String page2 = "ARTICLE 2 SERVICES\n2.1 " + sentences("The Supplier provides logistics services", 6);
        String page3 = "";
        String page4 = "ARTICLE 3 FEES\n3.1 " + sentences("Fees are set out in Schedule B", 6);

        List<Chunk> chunks = defaults.chunk(List.of(page1, page2, page3, page4));

        assertThat(chunks).extracting(Chunk::page).containsExactly(1, 2, 4);
    }

    @Test
    void splitClauseSpanningPagesAttributesContinuationToLaterPage() {
        ChunkingOptions options = new ChunkingOptions(40, 0, 5);
        String page1 = "8.1 " + sentences("The Supplier maintains insurance coverage at all times", 3);
        String page2 = sentences("Certificates of insurance are provided on request annually", 3);

        List<Chunk> chunks = new ClauseAwareChunker(options).chunk(List.of(page1, page2));

        assertThat(chunks.get(0).page()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).page()).isEqualTo(2);
        assertThat(chunks.get(chunks.size() - 1).text()).contains("Certificates");
    }

    @Test
    void emptyInputAndBlankPagesProduceNoChunks() {
        assertThat(defaults.chunk(List.of())).isEmpty();
        assertThat(defaults.chunk(List.of("", "   ", "\n\n"))).isEmpty();
    }

    @Test
    void textWithoutHeadingsBecomesPreambleChunks() {
        String page = sentences("This letter confirms the purchase of office supplies", 4);

        List<Chunk> chunks = defaults.chunk(List.of(page));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).heading()).isNull();
        assertThat(chunks.get(0).page()).isEqualTo(1);
    }

    @Test
    void sameInputAlwaysGivesSameOutput() {
        List<String> pages = List.of(
                "MASTER SERVICES AGREEMENT\nThis Agreement is made between the parties.",
                "ARTICLE 1 SCOPE\n1.1 " + sentences("The Supplier delivers warehousing services", 30),
                "ARTICLE 2 FEES\n2.1 Fees are fixed.\n2.2 " + sentences("Invoices are issued monthly", 5));

        List<Chunk> first = defaults.chunk(pages);
        List<Chunk> second = new ClauseAwareChunker(ChunkingOptions.DEFAULTS).chunk(pages);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void abbreviationsDoNotSplitSentences() {
        String text = "1.1 The Supplier, e.g. a logistics firm, follows Sec. 4 rules. Next sentence starts here.";
        ClauseAwareChunker.Block block = new ClauseAwareChunker.Block("1.1", "1", 1, false);
        block.append(text, 1);

        List<ClauseAwareChunker.Sentence> sentences = ClauseAwareChunker.splitSentences(block);

        assertThat(sentences).hasSize(2);
        assertThat(sentences.get(0).text()).endsWith("rules.");
    }

    private static String sentences(String base, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            sb.append(base).append(" in clause part ").append(i).append(". ");
        }
        return sb.toString().strip();
    }
}
