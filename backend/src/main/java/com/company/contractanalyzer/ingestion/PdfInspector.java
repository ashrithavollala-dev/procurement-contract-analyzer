package com.company.contractanalyzer.ingestion;

import com.company.contractanalyzer.enums.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

/**
 * Cheap upload-time checks that run on the request thread before anything is persisted:
 * the PDF magic bytes, whether PDFBox can open the file, and its page count. It reads no
 * text; full extraction happens asynchronously.
 */
@Component
public class PdfInspector {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public static boolean hasPdfMagic(byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the number of pages
     * @throws IngestionException with UNSUPPORTED_FILE_TYPE if the bytes are not a readable PDF
     */
    public int countPages(byte[] content) {
        if (!hasPdfMagic(content)) {
            throw new IngestionException(ErrorCode.UNSUPPORTED_FILE_TYPE, "The file is not a PDF document");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            return document.getNumberOfPages();
        } catch (InvalidPasswordException e) {
            throw new IngestionException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Password-protected PDFs are not supported", e);
        } catch (IOException e) {
            throw new IngestionException(ErrorCode.UNSUPPORTED_FILE_TYPE, "The PDF file is damaged or unreadable", e);
        }
    }
}
