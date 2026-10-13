package com.company.contractanalyzer.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clause-aware chunker: pure, deterministic and free of Spring dependencies.
 *
 * <p>The algorithm has three linear passes over the text:
 * <ol>
 *   <li><b>Block building.</b> Every line of every page is tested against the heading patterns
 *       below. A heading starts a new clause block that records the page it starts on and its
 *       article number. Other lines are appended to the current block, together with their
 *       page number.</li>
 *   <li><b>Tiny-block merging.</b> A block below {@code minTokens} is merged into the following
 *       block when both belong to the same article (for example "5.1" and "5.2"). A block that
 *       contains only a heading, such as "ARTICLE 9 LIMITATION OF LIABILITY", always merges
 *       forward, because a heading on its own is useless as a retrieval unit.</li>
 *   <li><b>Oversized-block splitting.</b> A block above {@code maxTokens} is split on sentence
 *       boundaries. Sentences are packed greedily up to {@code maxTokens}. Each new window
 *       starts with the trailing sentences of the previous window, as long as they fit in
 *       {@code overlapTokens}. A single sentence longer than {@code maxTokens} is split on word
 *       boundaries.</li>
 * </ol>
 *
 * <p>Heading patterns, tested in this order on a trimmed line:
 * <ul>
 *   <li>{@code ARTICLE 5}, {@code Article IV - Term}: article heading. Roman numerals are
 *       converted to numbers.</li>
 *   <li>{@code Section 4.2 Payment}, {@code Clause 7}: section heading. The article is the
 *       first number.</li>
 *   <li>{@code 12. Termination}: a number, a dot and a short Title-cased phrase of at most
 *       8 words with no sentence punctuation.</li>
 *   <li>{@code 9.1 The aggregate liability ...}: a numbered sub-clause. It starts a block whose
 *       body begins on the same line. The article is "9".</li>
 *   <li>{@code LIMITATION OF LIABILITY}: an all-caps line of 1 to 10 words with no lowercase.
 *       Directly after a heading-only block, such a line continues that heading instead of
 *       starting a new block.</li>
 * </ul>
 *
 * <p>Complexity: O(n) time and O(n) space in the number of characters. Each character is
 * visited a constant number of times: normalisation, line scanning, sentence splitting,
 * packing. Overlap re-reads at most {@code overlapTokens} per emitted chunk, and that is
 * bounded by a constant fraction of the chunk itself.
 */
public final class ClauseAwareChunker {

    private static final Pattern ARTICLE =
            Pattern.compile("^(?i:article)\\s+(\\d{1,3}|[IVXLCDM]{1,8})\\b[.:\\-\\u2013\\u2014]?(\\s+.{0,100})?$");
    private static final Pattern SECTION =
            Pattern.compile("^(?i:section|clause)\\s+(\\d{1,3})((?:\\.\\d{1,3})*)\\b[.:\\-\\u2013\\u2014]?(\\s+.{0,120})?$");
    private static final Pattern NUMBERED_TITLE =
            Pattern.compile("^(\\d{1,3})\\.\\s+([A-Z][^.;:!?]{1,80})$");
    private static final Pattern NUMBERED_CLAUSE =
            Pattern.compile("^(\\d{1,3})((?:\\.\\d{1,3})+)\\.?\\s+(\\S.*)$");
    private static final Pattern ALL_CAPS =
            Pattern.compile("^[A-Z0-9][A-Z0-9 ,&/'()\\-]{2,80}$");

    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g", "i.e", "no", "nos", "inc", "ltd", "co", "corp", "art", "sec", "cl", "vs", "etc",
            "mr", "ms", "mrs", "dr", "st", "para", "approx", "fig", "u.s", "u.k");

    private static final int MAX_SECTION_TITLE_WORDS = 12;
    private static final int MAX_NUMBERED_TITLE_WORDS = 8;
    private static final int MAX_ALL_CAPS_WORDS = 10;

    private final ChunkingOptions options;

    public ClauseAwareChunker(ChunkingOptions options) {
        this.options = options;
    }

    /**
     * @param pageTexts normalised page texts; element {@code i} is page {@code i + 1}
     * @return chunks in document order with sequential chunk indexes
     */
    public List<Chunk> chunk(List<String> pageTexts) {
        List<Block> blocks = buildBlocks(pageTexts);
        List<Block> merged = mergeTinyBlocks(blocks);
        List<Chunk> chunks = new ArrayList<>();
        for (Block block : merged) {
            emitBlock(block, chunks);
        }
        return chunks;
    }

    // ---------------------------------------------------------------- pass 1: blocks

    private List<Block> buildBlocks(List<String> pageTexts) {
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        String articleTitle = null;
        String articleTitleKey = null;

        for (int pageIndex = 0; pageIndex < pageTexts.size(); pageIndex++) {
            int page = pageIndex + 1;
            String pageText = pageTexts.get(pageIndex);
            if (pageText == null || pageText.isBlank()) {
                continue;
            }
            for (String rawLine : pageText.split("\n")) {
                String line = rawLine.strip();
                if (line.isEmpty()) {
                    continue;
                }
                HeadingMatch heading = detectHeading(line);
                if (heading == null) {
                    if (current == null) {
                        current = new Block(null, null, page, false);
                        blocks.add(current);
                    }
                    current.append(line, page);
                    continue;
                }
                if (heading.kind == HeadingKind.ALL_CAPS && current != null && current.headingOnly) {
                    // "ARTICLE 9" on one line, "LIMITATION OF LIABILITY" on the next.
                    current.extendHeading(line);
                    if (current.articleKey != null && current.articleKey.equals(articleTitleKey)) {
                        articleTitle = current.heading;
                    }
                    continue;
                }
                String blockHeading = heading.label;
                if (heading.kind == HeadingKind.ARTICLE || heading.kind == HeadingKind.NUMBERED_TITLE
                        || heading.kind == HeadingKind.SECTION && heading.isTopLevel) {
                    articleTitle = heading.label;
                    articleTitleKey = heading.articleKey;
                } else if (heading.kind == HeadingKind.NUMBERED_CLAUSE || heading.kind == HeadingKind.SECTION) {
                    if (articleTitle != null && heading.articleKey.equals(articleTitleKey)) {
                        blockHeading = articleTitle + " / " + heading.label;
                    }
                } else if (heading.kind == HeadingKind.ALL_CAPS) {
                    articleTitle = null;
                    articleTitleKey = null;
                }
                boolean headingOnly = heading.kind != HeadingKind.NUMBERED_CLAUSE;
                current = new Block(blockHeading, heading.articleKey, page, headingOnly);
                current.append(line, page);
                blocks.add(current);
            }
        }
        return blocks;
    }

    static HeadingMatch detectHeading(String line) {
        Matcher m = ARTICLE.matcher(line);
        if (m.matches()) {
            return new HeadingMatch(HeadingKind.ARTICLE, line, toArabic(m.group(1)), true);
        }
        m = SECTION.matcher(line);
        if (m.matches() && wordCount(m.group(3)) <= MAX_SECTION_TITLE_WORDS) {
            boolean topLevel = m.group(2) == null || m.group(2).isEmpty();
            return new HeadingMatch(HeadingKind.SECTION, line, m.group(1), topLevel);
        }
        m = NUMBERED_TITLE.matcher(line);
        if (m.matches() && wordCount(m.group(2)) <= MAX_NUMBERED_TITLE_WORDS) {
            return new HeadingMatch(HeadingKind.NUMBERED_TITLE, line, m.group(1), true);
        }
        m = NUMBERED_CLAUSE.matcher(line);
        if (m.matches()) {
            return new HeadingMatch(HeadingKind.NUMBERED_CLAUSE, m.group(1) + m.group(2), m.group(1), false);
        }
        if (ALL_CAPS.matcher(line).matches() && isAllCapsHeading(line)) {
            return new HeadingMatch(HeadingKind.ALL_CAPS, line, null, true);
        }
        return null;
    }

    private static boolean isAllCapsHeading(String line) {
        int letters = 0;
        for (int i = 0; i < line.length(); i++) {
            if (Character.isLetter(line.charAt(i))) {
                letters++;
            }
        }
        int words = wordCount(line);
        return letters >= 4 && words >= 1 && words <= MAX_ALL_CAPS_WORDS;
    }

    // ---------------------------------------------------------------- pass 2: merge

    private List<Block> mergeTinyBlocks(List<Block> blocks) {
        List<Block> result = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            Block current = blocks.get(i);
            i++;
            while (i < blocks.size()) {
                Block next = blocks.get(i);
                boolean tiny = current.tokens() < options.minTokens();
                boolean sameArticle = current.articleKey != null && current.articleKey.equals(next.articleKey);
                if (current.headingOnly || (tiny && sameArticle)) {
                    current = current.mergeWith(next);
                    i++;
                } else {
                    break;
                }
            }
            result.add(current);
        }
        return result;
    }

    // ---------------------------------------------------------------- pass 3: split

    private void emitBlock(Block block, List<Chunk> out) {
        if (block.tokens() <= options.maxTokens()) {
            out.add(new Chunk(block.startPage, out.size(), block.heading, block.text()));
            return;
        }
        List<Sentence> sentences = splitSentences(block);
        int n = sentences.size();
        int i = 0;
        while (i < n) {
            int start = i;
            int tokens = 0;
            StringBuilder window = new StringBuilder();
            while (i < n && (i == start || tokens + sentences.get(i).tokens <= options.maxTokens())) {
                if (window.length() > 0) {
                    window.append(' ');
                }
                window.append(sentences.get(i).text);
                tokens += sentences.get(i).tokens;
                i++;
            }
            if (i - start == 1 && tokens > options.maxTokens()) {
                splitOnWords(sentences.get(start), block.heading, out);
            } else {
                out.add(new Chunk(sentences.get(start).page, out.size(), block.heading, window.toString()));
            }
            if (i < n) {
                // Step back over trailing sentences that fit in the overlap budget (always progresses).
                int back = i;
                int overlap = 0;
                while (back - 1 > start && overlap + sentences.get(back - 1).tokens <= options.overlapTokens()) {
                    back--;
                    overlap += sentences.get(back).tokens;
                }
                i = back;
            }
        }
    }

    /** Fallback for one sentence longer than maxTokens: word windows with word-level overlap. */
    private void splitOnWords(Sentence sentence, String heading, List<Chunk> out) {
        List<String> words = List.of(sentence.text.split("\\s+"));
        int maxWords = Math.max(1, options.maxTokens() * 3 / 4);
        int overlapWords = Math.min(options.overlapTokens() * 3 / 4, maxWords / 2);
        int i = 0;
        while (i < words.size()) {
            int end = Math.min(words.size(), i + maxWords);
            out.add(new Chunk(sentence.page, out.size(), heading, String.join(" ", words.subList(i, end))));
            if (end == words.size()) {
                break;
            }
            i = end - overlapWords;
        }
    }

    /** Splits a block into sentences, each tagged with the page its first character came from. */
    static List<Sentence> splitSentences(Block block) {
        String text = block.text();
        PageCursor pages = block.pageCursor();
        List<Sentence> sentences = new ArrayList<>();
        int sentenceStart = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            boolean terminal = c == '.' || c == ';' || c == '!' || c == '?';
            if (!terminal || i + 2 >= length || text.charAt(i + 1) != ' ') {
                continue;
            }
            char nextStart = text.charAt(i + 2);
            boolean startsNew = Character.isUpperCase(nextStart) || Character.isDigit(nextStart)
                    || nextStart == '(' || nextStart == '"' || nextStart == '“';
            if (!startsNew || (c == '.' && endsWithAbbreviation(text, sentenceStart, i))) {
                continue;
            }
            addSentence(sentences, pages, text, sentenceStart, i + 1);
            sentenceStart = i + 2;
        }
        if (sentenceStart < length) {
            addSentence(sentences, pages, text, sentenceStart, length);
        }
        return sentences;
    }

    private static void addSentence(List<Sentence> out, PageCursor pages, String text, int from, int to) {
        String s = text.substring(from, to).strip();
        if (!s.isEmpty()) {
            out.add(new Sentence(s, pages.pageAt(from), TokenEstimator.estimate(s)));
        }
    }

    private static boolean endsWithAbbreviation(String text, int sentenceStart, int dotIndex) {
        int wordStart = dotIndex;
        while (wordStart > sentenceStart && !Character.isWhitespace(text.charAt(wordStart - 1))
                && text.charAt(wordStart - 1) != '(') {
            wordStart--;
        }
        String word = text.substring(wordStart, dotIndex).toLowerCase(Locale.ROOT);
        return ABBREVIATIONS.contains(word) || (word.length() == 1 && Character.isLetter(word.charAt(0)));
    }

    // ---------------------------------------------------------------- helpers

    private static int wordCount(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        return s.strip().split("\\s+").length;
    }

    static String toArabic(String number) {
        if (Character.isDigit(number.charAt(0))) {
            return String.valueOf(Integer.parseInt(number));
        }
        int total = 0;
        int previous = 0;
        for (int i = number.length() - 1; i >= 0; i--) {
            int value = switch (number.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return String.valueOf(total);
    }

    enum HeadingKind { ARTICLE, SECTION, NUMBERED_TITLE, NUMBERED_CLAUSE, ALL_CAPS }

    record HeadingMatch(HeadingKind kind, String label, String articleKey, boolean isTopLevel) {
    }

    record Sentence(String text, int page, int tokens) {
    }

    /** Amortised O(1) page lookup: sentences are visited in text order, so the cursor only moves forward. */
    static final class PageCursor {
        private final List<int[]> runs;
        private int index;
        private int page;

        PageCursor(List<int[]> runs, int startPage) {
            this.runs = runs;
            this.page = startPage;
        }

        int pageAt(int offset) {
            while (index < runs.size() && runs.get(index)[0] <= offset) {
                page = runs.get(index)[1];
                index++;
            }
            return page;
        }
    }

    /** A clause block under construction: text plus an offset-to-page index for page attribution. */
    static final class Block {
        private String heading;
        private final String articleKey;
        private final int startPage;
        private boolean headingOnly;
        private final StringBuilder text = new StringBuilder();
        /** Page runs as {offset, page}: text from {@code page} begins at {@code offset} (ascending). */
        private final List<int[]> pageRuns = new ArrayList<>();
        private int cachedTokens = -1;

        Block(String heading, String articleKey, int startPage, boolean headingOnly) {
            this.heading = heading;
            this.articleKey = articleKey;
            this.startPage = startPage;
            this.headingOnly = headingOnly;
        }

        void append(String line, int page) {
            if (text.length() > 0) {
                // A second line means the block has body text, not just its heading line.
                text.append(' ');
                headingOnly = false;
            }
            if (pageRuns.isEmpty() || pageRuns.get(pageRuns.size() - 1)[1] != page) {
                pageRuns.add(new int[] {text.length(), page});
            }
            text.append(line);
            cachedTokens = -1;
        }

        void extendHeading(String line) {
            heading = heading == null ? line : heading + " " + line;
            text.append(' ').append(line);
            cachedTokens = -1;
        }

        Block mergeWith(Block next) {
            String mergedHeading = headingOnly && next.heading != null ? next.heading : heading;
            Block merged = new Block(mergedHeading, articleKey != null ? articleKey : next.articleKey,
                    startPage, false);
            merged.copyFrom(this);
            merged.copyFrom(next);
            merged.headingOnly = false;
            return merged;
        }

        private void copyFrom(Block other) {
            int base = text.length() == 0 ? 0 : text.length() + 1;
            if (text.length() > 0) {
                text.append(' ');
            }
            for (int[] run : other.pageRuns) {
                int page = run[1];
                if (pageRuns.isEmpty() || pageRuns.get(pageRuns.size() - 1)[1] != page) {
                    pageRuns.add(new int[] {base + run[0], page});
                }
            }
            text.append(other.text);
            cachedTokens = -1;
        }

        /** Forward-only cursor over the page runs; offsets must be queried in ascending order. */
        PageCursor pageCursor() {
            return new PageCursor(pageRuns, startPage);
        }

        String text() {
            return text.toString();
        }

        int tokens() {
            if (cachedTokens < 0) {
                cachedTokens = TokenEstimator.estimate(text);
            }
            return cachedTokens;
        }
    }
}
