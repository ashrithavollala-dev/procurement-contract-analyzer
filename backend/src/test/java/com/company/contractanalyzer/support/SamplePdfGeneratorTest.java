package com.company.contractanalyzer.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.contractanalyzer.ingestion.Chunk;
import com.company.contractanalyzer.ingestion.ChunkingOptions;
import com.company.contractanalyzer.ingestion.ClauseAwareChunker;
import com.company.contractanalyzer.ingestion.PageText;
import com.company.contractanalyzer.ingestion.PdfInspector;
import com.company.contractanalyzer.ingestion.PdfTextExtractor;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Pins the properties the demo walkthrough and the integration tests rely on. */
class SamplePdfGeneratorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final ClauseAwareChunker chunker = new ClauseAwareChunker(ChunkingOptions.DEFAULTS);

    @Test
    void wellFormedContractIsMultiPageAndCoversEveryArticle() throws Exception {
        byte[] pdf = SamplePdfGenerator.wellFormedContract();
        List<PageText> pages = extractor.extract(pdf);

        assertThat(new PdfInspector().countPages(pdf)).isGreaterThanOrEqualTo(2);
        String all = String.join("\n", pages.stream().map(PageText::text).toList());
        for (int article = 1; article <= 12; article++) {
            assertThat(all).contains("ARTICLE " + article + " ");
        }
        assertThat(all).doesNotContain("Page 1 of");

        List<Chunk> chunks = chunker.chunk(pages.stream().map(PageText::text).toList());
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(12);
        assertThat(chunks).anySatisfy(c -> assertThat(c.heading()).startsWith("ARTICLE 8 LIMITATION OF LIABILITY"));
        assertThat(chunks).anySatisfy(c -> assertThat(c.heading()).startsWith("ARTICLE 12 GOVERNING LAW"));
        assertThat(chunks).extracting(Chunk::page).contains(1, pages.size());
    }

    @Test
    void weakContractOmitsLiabilityCapAuditRightsAndDataProtection() throws Exception {
        byte[] pdf = SamplePdfGenerator.weakContract();
        List<PageText> pages = extractor.extract(pdf);
        assertThat(new PdfInspector().countPages(pdf)).isGreaterThanOrEqualTo(2);
        String all = String.join("\n", pages.stream().map(PageText::text).toList()).toLowerCase(Locale.ROOT);

        assertThat(all).doesNotContain("liability shall not exceed", "limitation of liability", "audit", "personal data");
        assertThat(all).contains("governed by the laws of the fictional state of westmarch");
        assertThat(PdfTextExtractor.extractableCharacters(pages)).isGreaterThan(200);
    }

    @Test
    void imageOnlyContractHasNoExtractableText() throws Exception {
        List<PageText> pages = extractor.extract(SamplePdfGenerator.imageOnlyContract());

        assertThat(pages).hasSize(2);
        assertThat(PdfTextExtractor.extractableCharacters(pages)).isZero();
    }
}
