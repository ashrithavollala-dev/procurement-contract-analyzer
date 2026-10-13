package com.company.contractanalyzer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.contractanalyzer.enums.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final PdfInspector inspector = new PdfInspector();

    @Test
    void extractsTextPerPageInOrder() throws IOException {
        byte[] pdf = pdf(List.of(
                List.of("ARTICLE 1 SCOPE", "1.1 The Supplier provides fictional services."),
                List.of("ARTICLE 2 FEES", "2.1 Invoices are payable within 30 days.")));

        List<PageText> pages = extractor.extract(pdf);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).page()).isEqualTo(1);
        assertThat(pages.get(0).text()).contains("ARTICLE 1 SCOPE").doesNotContain("ARTICLE 2");
        assertThat(pages.get(1).text()).contains("2.1 Invoices are payable within 30 days.");
        assertThat(inspector.countPages(pdf)).isEqualTo(2);
    }

    @Test
    void pageWithoutTextYieldsEmptyStringAndZeroExtractableCharacters() throws IOException {
        byte[] pdf = pdf(List.of(List.of()));

        List<PageText> pages = extractor.extract(pdf);

        assertThat(pages).singleElement().extracting(PageText::text).isEqualTo("");
        assertThat(PdfTextExtractor.extractableCharacters(pages)).isZero();
    }

    @Test
    void inspectorRejectsNonPdfAndCorruptContent() {
        assertThat(PdfInspector.hasPdfMagic("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(PdfInspector.hasPdfMagic("PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1))).isFalse();
        assertThat(PdfInspector.hasPdfMagic(new byte[0])).isFalse();

        assertThatThrownBy(() -> inspector.countPages("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IngestionException.class)
                .extracting(e -> ((IngestionException) e).getCode()).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        assertThatThrownBy(() -> inspector.countPages("%PDF-1.7 truncated garbage".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IngestionException.class);
    }

    static byte[] pdf(List<List<String>> pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                if (lines.isEmpty()) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 11);
                    content.setLeading(15);
                    content.newLineAtOffset(60, 780);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
