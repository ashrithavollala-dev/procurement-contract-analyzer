package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.enums.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Direct, per-page text extraction with Apache PDFBox 3. Each page is stripped on its own
 * (start page = end page), so every chunk can be attributed to the page where its text starts.
 * Pages without extractable text (for example scanned images) yield an empty string. OCR is
 * out of scope.
 */
@Component
public class PdfTextExtractor {

    public List<PageText> extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            stripper.setAddMoreFormatting(false);
            int pages = document.getNumberOfPages();
            List<PageText> result = new ArrayList<>(pages);
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String raw = stripper.getText(document);
                result.add(new PageText(page, TextNormalizer.normalizePage(raw)));
            }
            return result;
        } catch (InvalidPasswordException e) {
            throw new IngestionException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Password-protected PDFs are not supported", e);
        } catch (IOException e) {
            throw new IngestionException(ErrorCode.UNSUPPORTED_FILE_TYPE, "The PDF file could not be parsed", e);
        }
    }

    /** Total non-whitespace characters across pages; used for the NO_EXTRACTABLE_TEXT threshold. */
    public static int extractableCharacters(List<PageText> pages) {
        int count = 0;
        for (PageText page : pages) {
            String text = page.text();
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isWhitespace(text.charAt(i))) {
                    count++;
                }
            }
        }
        return count;
    }
}
