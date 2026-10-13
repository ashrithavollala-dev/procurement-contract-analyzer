package com.company.contractanalyzer.support;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Generates the three fictional sample contracts in {@code docs/samples}. All names, places and
 * terms are invented for this demo.
 *
 * <ol>
 *   <li>{@code contract-wellformed.pdf}: a master services agreement covering all 12 seeded rules.</li>
 *   <li>{@code contract-weak.pdf}: omits the liability cap, audit rights and data protection,
 *       and has weaker payment and renewal terms.</li>
 *   <li>{@code contract-scanned-image-only.pdf}: a page rendered as an image only, with no
 *       extractable text, for the NO_EXTRACTABLE_TEXT failure path.</li>
 * </ol>
 *
 * Run from {@code backend/}: {@code mvn -q test-compile} and then
 * {@code java -cp "target/test-classes;target/classes;<deps>" com.company.contractanalyzer.support.SamplePdfGenerator ../docs/samples}
 * (backend/README.md has the exact command). Integration tests call the same methods in memory.
 */
public final class SamplePdfGenerator {

    private static final float MARGIN = 60f;
    private static final float FONT_SIZE = 10.5f;
    private static final float HEADING_SIZE = 11.5f;
    private static final float LEADING = 14.5f;

    private SamplePdfGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "../docs/samples");
        Files.createDirectories(dir);
        Files.write(dir.resolve("contract-wellformed.pdf"), wellFormedContract());
        Files.write(dir.resolve("contract-weak.pdf"), weakContract());
        Files.write(dir.resolve("contract-scanned-image-only.pdf"), imageOnlyContract());
        System.out.println("Sample PDFs written to " + dir.toAbsolutePath().normalize());
    }

    // ------------------------------------------------------------------ content

    public static byte[] wellFormedContract() throws IOException {
        List<Block> b = new ArrayList<>();
        title(b, "MASTER SERVICES AGREEMENT");
        para(b, "This Master Services Agreement is made between Halcyon Retail Group Ltd (fictional), the Customer, "
                + "and Brightwater Logistics Ltd (fictional), the Supplier, for warehousing and distribution services. "
                + "All names in this document are fictional and used for demonstration only.");
        heading(b, "ARTICLE 1 DEFINITIONS AND SCOPE");
        para(b, "1.1 Services means the warehousing, inventory management and last-mile distribution services described in Schedule A.");
        para(b, "1.2 The Supplier shall perform the Services with reasonable skill and care and in accordance with good industry practice.");
        heading(b, "ARTICLE 2 FEES AND PAYMENT");
        para(b, "2.1 The Customer shall pay each valid invoice within forty-five (45) days of receipt. All invoices are issued in euro (EUR).");
        para(b, "2.2 If the Customer disputes any part of an invoice in good faith, it shall notify the Supplier within ten days and "
                + "pay the undisputed portion by the due date. The parties shall resolve the disputed amount within thirty days.");
        heading(b, "ARTICLE 3 SERVICE LEVELS");
        para(b, "3.1 The Supplier shall achieve order dispatch accuracy of at least 99.5 percent and warehouse system availability "
                + "of at least 99.9 percent, each measured monthly.");
        para(b, "3.2 If a service level is missed in any month, the Supplier shall credit five percent of the monthly fees for each "
                + "missed service level. Repeated failure in three consecutive months entitles the Customer to terminate this Agreement for cause.");
        heading(b, "ARTICLE 4 CONFIDENTIALITY");
        para(b, "4.1 Each party shall keep the other party's Confidential Information secret and use it only to perform this Agreement.");
        para(b, "4.2 The obligations in this Article do not apply to information that is public, independently developed or "
                + "required to be disclosed by law, and they survive for five (5) years after termination of this Agreement.");
        heading(b, "ARTICLE 5 DATA PROTECTION");
        para(b, "5.1 The Supplier shall process personal data only on the documented instructions of the Customer and shall "
                + "implement appropriate technical and organisational security measures.");
        para(b, "5.2 The Supplier shall notify the Customer of any personal data breach without undue delay and in any event within "
                + "forty-eight (48) hours of becoming aware of it.");
        para(b, "5.3 The Supplier shall not engage any sub-processor without the Customer's prior written approval and shall delete "
                + "or return all personal data at the end of this Agreement.");
        heading(b, "ARTICLE 6 INTELLECTUAL PROPERTY");
        para(b, "6.1 All intellectual property rights in deliverables created specifically for the Customer vest in the Customer on creation.");
        para(b, "6.2 The Supplier grants the Customer a perpetual, royalty-free licence to use any Supplier background intellectual "
                + "property embedded in the deliverables.");
        heading(b, "ARTICLE 7 INDEMNIFICATION");
        para(b, "7.1 The Supplier shall defend, indemnify and hold harmless the Customer against all third-party claims alleging that "
                + "the Services infringe intellectual property rights, or arising from the Supplier's negligence or breach of applicable law.");
        para(b, "7.2 The Customer shall promptly notify the Supplier of any such claim and allow the Supplier to control its defence.");
        heading(b, "ARTICLE 8 LIMITATION OF LIABILITY");
        para(b, "8.1 The aggregate liability of each party under this Agreement shall not exceed the total fees paid or payable by "
                + "the Customer in the twelve (12) months preceding the event giving rise to the claim.");
        para(b, "8.2 The limitation in clause 8.1 does not apply to fraud, wilful misconduct, gross negligence, breach of Article 4 "
                + "(Confidentiality) or breach of Article 5 (Data Protection), or to the indemnities in Article 7.");
        heading(b, "ARTICLE 9 AUDIT RIGHTS");
        para(b, "9.1 The Customer, or an independent auditor appointed by it, may audit the Supplier's records, security controls and "
                + "compliance with this Agreement once per year on thirty days written notice.");
        para(b, "9.2 The Supplier shall cooperate fully with any audit and shall remediate any material finding within sixty days.");
        heading(b, "ARTICLE 10 TERM, RENEWAL AND TERMINATION");
        para(b, "10.1 This Agreement starts on the Effective Date and continues for an initial term of two (2) years.");
        para(b, "10.2 This Agreement renews automatically for successive periods of twelve (12) months unless either party gives "
                + "notice of non-renewal at least sixty (60) days before expiry. Fees may increase on renewal by no more than three percent.");
        para(b, "10.3 The Customer may terminate this Agreement for convenience on sixty (60) days written notice, paying only for "
                + "Services delivered up to the termination date. No termination fee applies.");
        heading(b, "ARTICLE 11 FORCE MAJEURE");
        para(b, "11.1 Neither party is liable for delay caused by events beyond its reasonable control, including flood, fire, "
                + "epidemic or war, provided that it notifies the other party promptly and uses reasonable efforts to mitigate the effects.");
        para(b, "11.2 If a force majeure event continues for more than sixty (60) days, either party may terminate this Agreement "
                + "on written notice. Force majeure does not excuse payment obligations.");
        heading(b, "ARTICLE 12 GOVERNING LAW AND JURISDICTION");
        para(b, "12.1 This Agreement is governed by the laws of the fictional State of Westmarch.");
        para(b, "12.2 The courts of Westmarch City have exclusive jurisdiction over any dispute arising out of this Agreement.");
        para(b, "Signed for and on behalf of the Customer and the Supplier by their authorised representatives.");
        return render(b);
    }

    public static byte[] weakContract() throws IOException {
        List<Block> b = new ArrayList<>();
        title(b, "SERVICES AGREEMENT");
        para(b, "This Services Agreement is made between Halcyon Retail Group Ltd (fictional), the Customer, and Copperleaf "
                + "Facilities Ltd (fictional), the Supplier, for office cleaning and facilities services. All names are fictional.");
        heading(b, "ARTICLE 1 SERVICES");
        para(b, "1.1 The Supplier shall provide cleaning and facilities services at the Customer's sites as described in the order form.");
        para(b, "1.2 The Supplier will use best efforts to perform the services in a timely manner.");
        heading(b, "ARTICLE 2 PAYMENT");
        para(b, "2.1 The Customer shall pay each invoice within ten (10) days of the invoice date. Late payments accrue interest at "
                + "two percent (2%) per month.");
        heading(b, "ARTICLE 3 CONFIDENTIALITY");
        para(b, "3.1 Each party shall keep confidential any business information received from the other party during the term of this Agreement.");
        heading(b, "ARTICLE 4 INTELLECTUAL PROPERTY");
        para(b, "4.1 The Supplier retains all intellectual property rights in any materials, reports or deliverables it produces. "
                + "The Customer's right to use them ends on termination of this Agreement.");
        heading(b, "ARTICLE 5 INDEMNITY");
        para(b, "5.1 The Customer shall indemnify the Supplier against all claims arising from the condition of the Customer's premises.");
        heading(b, "ARTICLE 6 TERM AND RENEWAL");
        para(b, "6.1 This Agreement has an initial term of three (3) years and renews automatically for successive three-year terms "
                + "unless the Customer gives notice of non-renewal at least one hundred and eighty (180) days before expiry.");
        para(b, "6.2 The Supplier may increase its fees on each renewal at its discretion.");
        para(b, "6.3 The Customer may terminate this Agreement only for material breach by the Supplier that remains uncured after sixty days.");
        heading(b, "ARTICLE 7 FORCE MAJEURE");
        para(b, "7.1 The Supplier is not liable for any failure or delay caused by circumstances beyond its control.");
        heading(b, "ARTICLE 8 GOVERNING LAW");
        para(b, "8.1 This Agreement is governed by the laws of the fictional State of Westmarch.");
        heading(b, "ARTICLE 9 NOTICES");
        para(b, "9.1 Notices under this Agreement must be in writing and sent by courier or by email to the addresses in the order form.");
        para(b, "9.2 A notice sent by email is deemed received on the next business day after it is sent.");
        heading(b, "ARTICLE 10 ASSIGNMENT AND SUBCONTRACTING");
        para(b, "10.1 The Supplier may assign this Agreement or subcontract any of the services to any third party without the "
                + "Customer's consent.");
        para(b, "10.2 The Customer may not assign this Agreement without the Supplier's prior written consent.");
        heading(b, "ARTICLE 11 INSURANCE");
        para(b, "11.1 The Supplier shall maintain public liability insurance with a reputable insurer during the term of this Agreement.");
        para(b, "11.2 The Supplier shall provide a certificate of insurance on request, no more than once in each calendar year.");
        heading(b, "SCHEDULE A SERVICE DESCRIPTION");
        para(b, "A.1 Daily cleaning of office areas, kitchens and washrooms on business days between 18:00 and 22:00.");
        para(b, "A.2 Weekly cleaning of windows, meeting rooms and reception areas, and monthly deep cleaning of carpets.");
        para(b, "A.3 Replenishment of consumables such as soap, paper towels and waste bags, charged at the Supplier's list prices.");
        para(b, "A.4 Reactive cleaning requests raised through the Supplier's helpdesk, attended within one business day where possible.");
        para(b, "A.5 Waste segregation and removal in line with the site's recycling scheme, with collection twice per week.");
        para(b, "A.6 A named site supervisor who visits each site at least once per month and reports issues to the Customer.");
        heading(b, "SCHEDULE B FEES");
        para(b, "B.1 The monthly fee for each site is set out in the order form and is invoiced monthly in advance.");
        para(b, "B.2 Additional services requested by the Customer are charged at the Supplier's hourly rates in force at the time.");
        para(b, "B.3 Consumables are invoiced monthly in arrears together with any additional services.");
        para(b, "Signed for and on behalf of the Customer and the Supplier by their authorised representatives.");
        return render(b);
    }

    /** Two pages whose content is a rendered image of text: PDFBox extracts nothing from them. */
    public static byte[] imageOnlyContract() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addImagePage(document, "SCANNED SUPPLY AGREEMENT (FICTIONAL)", new String[] {
                "1. The Supplier delivers packaging materials monthly.",
                "2. Invoices are payable within thirty days.",
                "3. This page is an image: it has no extractable text.",
                "   Optical character recognition (OCR) is out of scope."});
            addImagePage(document, "SCANNED SUPPLY AGREEMENT - PAGE 2", new String[] {
                "4. Either party may terminate on ninety days notice.",
                "5. This agreement is governed by the laws of Westmarch.",
                "   Signed by the authorised representatives."});
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void addImagePage(PDDocument document, String title, String[] lines) throws IOException {
        BufferedImage image = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 40));
            g.drawString(title, 120, 200);
            g.setFont(new Font(Font.SERIF, Font.PLAIN, 28));
            int y = 320;
            for (String line : lines) {
                g.drawString(line, 120, y);
                y += 60;
            }
        } finally {
            g.dispose();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            PDRectangle box = page.getMediaBox();
            content.drawImage(pdImage, 0, 0, box.getWidth(), box.getHeight());
        }
    }

    // ------------------------------------------------------------------ layout

    private record Block(String text, boolean heading, boolean title) {
    }

    private static void title(List<Block> blocks, String text) {
        blocks.add(new Block(text, false, true));
    }

    private static void heading(List<Block> blocks, String text) {
        blocks.add(new Block(text, true, false));
    }

    private static void para(List<Block> blocks, String text) {
        blocks.add(new Block(text, false, false));
    }

    /** Simple word-wrapping layout with automatic page breaks and page-number footers. */
    private static byte[] render(List<Block> blocks) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
            Layout layout = new Layout(document);
            for (Block block : blocks) {
                PDType1Font font = block.heading() || block.title() ? bold : regular;
                float size = block.title() ? 15f : block.heading() ? HEADING_SIZE : FONT_SIZE;
                if (block.heading()) {
                    layout.space(LEADING * 0.8f);
                    // Keep a heading together with the first lines of its clause.
                    layout.ensureRoom(LEADING * 4);
                }
                for (String line : wrap(block.text(), font, size, width)) {
                    layout.line(line, font, size);
                }
                layout.space(block.title() ? LEADING : LEADING * 0.4f);
            }
            layout.finish(regular);
            document.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000f * size > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static final class Layout {
        private final PDDocument document;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        void ensureRoom(float height) throws IOException {
            if (y - height < MARGIN + 20) {
                newPage();
            }
        }

        void space(float height) {
            y -= height;
        }

        void line(String text, PDType1Font font, float size) throws IOException {
            ensureRoom(LEADING);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= LEADING;
        }

        void finish(PDType1Font footerFont) throws IOException {
            stream.close();
            for (int i = 0; i < pages.size(); i++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, pages.get(i),
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    footer.beginText();
                    footer.setFont(footerFont, 8.5f);
                    footer.newLineAtOffset(MARGIN, MARGIN / 2);
                    footer.showText("Page " + (i + 1) + " of " + pages.size());
                    footer.endText();
                }
            }
        }
    }
}
