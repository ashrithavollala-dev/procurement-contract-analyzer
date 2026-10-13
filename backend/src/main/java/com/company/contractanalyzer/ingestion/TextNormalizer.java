package com.company.contractanalyzer.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure text clean-up applied to every extracted PDF page before chunking. Line breaks are
 * preserved, because clause headings are detected line by line.
 *
 * <ol>
 *   <li>Normalises line endings to {@code \n} and odd spaces (NBSP, tabs, form feeds) to a space.</li>
 *   <li>Removes soft hyphens and joins words hyphenated across a line break ("liabil-\nity").</li>
 *   <li>Collapses runs of spaces and trims every line.</li>
 *   <li>Drops page furniture lines such as "Page 3 of 7" or a bare page number.</li>
 *   <li>Collapses three or more blank lines into one blank line.</li>
 * </ol>
 */
public final class TextNormalizer {

    private static final Pattern LINE_ENDINGS = Pattern.compile("\\r\\n?");
    private static final Pattern ODD_SPACES = Pattern.compile("[\\u00A0\\u2007\\u202F\\t\\x0B\\f]");
    private static final Pattern SOFT_HYPHEN = Pattern.compile("\\u00AD");
    /** A lowercase continuation after "letter-\n" means a word was hyphenated by line wrapping. */
    private static final Pattern HYPHENATED_BREAK = Pattern.compile("(\\p{L})-\\n[ ]*(\\p{Ll})");
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");
    private static final Pattern PAGE_FURNITURE =
            Pattern.compile("^(?i:page\\s+\\d+(\\s+of\\s+\\d+)?|-?\\s*\\d{1,4}\\s*-?)$");
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");

    private TextNormalizer() {
    }

    public static String normalizePage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = LINE_ENDINGS.matcher(raw).replaceAll("\n");
        text = ODD_SPACES.matcher(text).replaceAll(" ");
        text = SOFT_HYPHEN.matcher(text).replaceAll("");
        text = HYPHENATED_BREAK.matcher(text).replaceAll("$1$2");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");

        List<String> kept = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && PAGE_FURNITURE.matcher(trimmed).matches()) {
                continue;
            }
            kept.add(trimmed);
        }
        String joined = String.join("\n", kept);
        return MANY_BLANK_LINES.matcher(joined).replaceAll("\n\n").strip();
    }

    /** Whitespace- and case-insensitive canonical form, used for grounding substring checks. */
    public static String canonical(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ' ') {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (c == '­') {
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.append(Character.toLowerCase(normalizeQuote(c)));
        }
        return out.toString();
    }

    private static char normalizeQuote(char c) {
        return switch (c) {
            case '‘', '’', '‛' -> '\'';
            case '“', '”', '‟' -> '"';
            case '–', '—' -> '-';
            default -> c;
        };
    }
}
