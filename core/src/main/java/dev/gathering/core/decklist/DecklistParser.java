package dev.gathering.core.decklist;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns pasted decklist text into structured entries.
 *
 * <p>This is the front door of the whole mod, so it is deliberately permissive about input
 * and strict about output: it accepts the shapes the common exporters actually emit, it
 * never throws, and every line it cannot understand comes back as a {@link ParseProblem}
 * carrying its line number and original text rather than silently vanishing.
 *
 * <p>Formats handled:
 * <ul>
 *   <li>Moxfield / Archidekt exports - {@code 1 Sol Ring (C21) 263 *F* [Artifact] ^Have^}</li>
 *   <li>MTGA / Arena exports - {@code About} / {@code Name} / {@code Deck} / {@code Sideboard} headers</li>
 *   <li>MTGO style - two blocks separated by a blank line, second block is the sideboard</li>
 *   <li>Deckstats style - {@code 1 [C21#263] Sol Ring}</li>
 *   <li>Plain lists - {@code 1x Sol Ring}, {@code 1 Sol Ring}, or bare {@code Sol Ring}</li>
 * </ul>
 *
 * <p>Set codes and collector numbers are recorded as hints only. Choosing an actual
 * printing is resolution's job, not parsing's.
 */
public final class DecklistParser {

    /** Above this, a line is far likelier to be a typo than an intent. */
    public static final int MAX_QUANTITY = 1000;

    private static final Pattern TRAILING_ARCHIDEKT_TAGS = Pattern.compile("\\s*\\^[^^]*\\^\\s*$");
    private static final Pattern TRAILING_CATEGORY = Pattern.compile("\\s*\\[[^\\]]*\\]\\s*$");
    private static final Pattern FINISH_MARKER =
            Pattern.compile("\\s*\\*(?:F|E|FOIL|ETCHED)\\*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_FINISH_WORD =
            Pattern.compile("\\s*\\((?:foil|etched)\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY = Pattern.compile("^(\\d{1,6})\\s*[xX]?\\s+");
    private static final Pattern QUANTITY_X_FIRST = Pattern.compile("^[xX](\\d{1,6})\\s+");
    private static final Pattern LEADING_SET =
            Pattern.compile("^\\[([A-Za-z0-9]{2,6})(?:#([A-Za-z0-9★†\\-]{1,12}))?\\]\\s*");
    private static final Pattern TRAILING_SET_PAREN =
            Pattern.compile("\\s*\\(([A-Za-z0-9]{2,6})\\)(?:\\s+([A-Za-z0-9★†\\-]{1,12}))?\\s*$");
    /** Uppercase only: {@code [C21]} is a set, {@code [Land]} is an Archidekt category. */
    private static final Pattern TRAILING_SET_BRACKET =
            Pattern.compile("\\s*\\[([A-Z0-9]{2,6})\\](?:\\s+([A-Za-z0-9★†\\-]{1,12}))?\\s*$");
    private static final Pattern HEADER_CANDIDATE =
            Pattern.compile("^([A-Za-z][A-Za-z ()]{0,20}?)\\s*:?\\s*(?:\\(\\d+\\))?$");
    private static final Pattern ARENA_NAME = Pattern.compile("^Name\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /**
     * A bare web address on its own line.
     *
     * <p>Pasting the link to a deck instead of the deck is the single most natural mistake to
     * make here - it is what you have in your clipboard after looking at your list, and every
     * deck site's share button hands you one. Without this it parses as a card named
     * "https://archidekt.com/decks/1234567", resolves to nothing, and the player is told no
     * such card exists, which is true and no help at all.
     *
     * <p>Requires a dotted domain followed by a slash and no spaces, so it cannot swallow a
     * real card name: "Fire // Ice" has no dotted domain before its slashes.
     */
    private static final Pattern LOOKS_LIKE_A_LINK = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+/\\S*$", Pattern.CASE_INSENSITIVE);

    private DecklistParser() {
    }

    public static ParsedDecklist parse(String text) {
        if (text == null || text.isBlank()) {
            return ParsedDecklist.EMPTY;
        }

        List<PositionedEntry> parsed = new ArrayList<>();
        List<ParseProblem> problems = new ArrayList<>();

        DeckSection current = DeckSection.MAINBOARD;
        boolean sawExplicitHeader = false;
        boolean inAboutBlock = false;
        String deckName = null;

        int blockIndex = 0;
        boolean previousLineWasBlank = true;
        int lineNumber = 0;

        for (String rawLine : text.split("\r\n|\r|\n", -1)) {
            lineNumber++;
            String line = rawLine.strip();

            if (line.isEmpty()) {
                previousLineWasBlank = true;
                continue;
            }
            if (previousLineWasBlank && !parsed.isEmpty()) {
                blockIndex++;
            }
            previousLineWasBlank = false;

            if (isComment(line)) {
                continue;
            }

            if (inAboutBlock) {
                Matcher nameMatcher = ARENA_NAME.matcher(line);
                if (nameMatcher.matches()) {
                    deckName = nameMatcher.group(1).strip();
                    continue;
                }
            }

            Optional<String> header = headerText(line);
            if (header.isPresent()) {
                String headerKey = header.get().toLowerCase(Locale.ROOT);
                if (headerKey.equals("about")) {
                    inAboutBlock = true;
                    continue;
                }
                Optional<DeckSection> section = DeckSection.fromHeader(header.get());
                if (section.isPresent()) {
                    inAboutBlock = false;
                    current = section.get();
                    sawExplicitHeader = true;
                    continue;
                }
            }

            inAboutBlock = false;
            if (LOOKS_LIKE_A_LINK.matcher(line).matches()) {
                problems.add(new ParseProblem(lineNumber, line, linkAdviceFor(line)));
                continue;
            }
            LineResult result = parseCardLine(line, current, lineNumber, rawLine);
            if (result.problem() != null) {
                problems.add(result.problem());
            } else {
                parsed.add(new PositionedEntry(result.entry(), blockIndex));
            }
        }

        List<DecklistEntry> entries = applyBlockSideboardConvention(parsed, sawExplicitHeader);
        return new ParsedDecklist(deckName, entries, problems);
    }

    /**
     * MTGO and several plain exporters mark the sideboard with nothing but a blank line.
     * Applied only when the list carried no explicit headers and split into exactly two
     * blocks, so an ordinary list with a stray blank line is left alone.
     */
    private static List<DecklistEntry> applyBlockSideboardConvention(
            List<PositionedEntry> parsed, boolean sawExplicitHeader) {
        int blockCount = parsed.stream().mapToInt(PositionedEntry::block).max().orElse(0) + 1;
        boolean applies = !sawExplicitHeader && blockCount == 2;
        List<DecklistEntry> entries = new ArrayList<>(parsed.size());
        for (PositionedEntry positioned : parsed) {
            DecklistEntry entry = positioned.entry();
            entries.add(applies && positioned.block() == 1 ? entry.withSection(DeckSection.SIDEBOARD) : entry);
        }
        return entries;
    }

    /**
     * What to do about a pasted link, named per site because "export it" is useless without
     * saying where the button is.
     */
    private static String linkAdviceFor(String link) {
        String host = link.toLowerCase(Locale.ROOT);
        if (host.contains("archidekt.com")) {
            return "that is a deck link - open the deck on Archidekt, choose Export, pick Text, and paste that";
        }
        if (host.contains("moxfield.com")) {
            return "that is a deck link - open the deck on Moxfield, choose More, Export, Text, and paste that";
        }
        if (host.contains("tappedout.net") || host.contains("deckstats.net")
                || host.contains("mtggoldfish.com") || host.contains("scryfall.com")) {
            return "that is a deck link - use the site's export or download option and paste the card list itself";
        }
        return "that looks like a web address rather than a card - paste the decklist text itself";
    }

    private static boolean isComment(String line) {
        // Only at the start of a line: "Fire // Ice" is a card, not a comment.
        return line.startsWith("//") || line.startsWith("#");
    }

    private static Optional<String> headerText(String line) {
        Matcher matcher = HEADER_CANDIDATE.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String candidate = matcher.group(1).strip();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        String key = candidate.toLowerCase(Locale.ROOT);
        if (key.equals("about") || DeckSection.fromHeader(candidate).isPresent()) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static LineResult parseCardLine(String line, DeckSection section, int lineNumber, String sourceLine) {
        String working = line;

        working = stripRepeatedly(working, TRAILING_ARCHIDEKT_TAGS);

        boolean foil = false;
        Matcher finish = FINISH_MARKER.matcher(working);
        if (finish.find()) {
            foil = true;
            working = finish.replaceAll("");
        }
        Matcher finishWord = TRAILING_FINISH_WORD.matcher(working);
        if (finishWord.find()) {
            foil = true;
            working = finishWord.replaceAll("");
        }

        int quantity = 1;
        Matcher quantityMatcher = QUANTITY.matcher(working);
        Matcher quantityXFirst = QUANTITY_X_FIRST.matcher(working);
        if (quantityMatcher.find()) {
            quantity = parseQuantity(quantityMatcher.group(1));
            working = working.substring(quantityMatcher.end());
        } else if (quantityXFirst.find()) {
            quantity = parseQuantity(quantityXFirst.group(1));
            working = working.substring(quantityXFirst.end());
        }

        if (quantity <= 0) {
            return LineResult.problem(lineNumber, sourceLine, "quantity must be at least 1");
        }
        if (quantity > MAX_QUANTITY) {
            return LineResult.problem(lineNumber, sourceLine,
                    "quantity " + quantity + " exceeds the maximum of " + MAX_QUANTITY);
        }

        String setCode = null;
        String collectorNumber = null;

        Matcher leadingSet = LEADING_SET.matcher(working);
        if (leadingSet.find()) {
            setCode = leadingSet.group(1);
            collectorNumber = leadingSet.group(2);
            working = working.substring(leadingSet.end());
        }

        if (setCode == null) {
            Matcher bracketSet = TRAILING_SET_BRACKET.matcher(working);
            if (bracketSet.find()) {
                setCode = bracketSet.group(1);
                collectorNumber = bracketSet.group(2);
                working = working.substring(0, bracketSet.start());
            }
        }

        working = stripRepeatedly(working, TRAILING_CATEGORY);

        if (setCode == null) {
            Matcher parenSet = TRAILING_SET_PAREN.matcher(working);
            if (parenSet.find()) {
                setCode = parenSet.group(1);
                collectorNumber = parenSet.group(2);
                working = working.substring(0, parenSet.start());
            }
        }

        String name = normaliseName(working);
        if (name.isEmpty()) {
            return LineResult.problem(lineNumber, sourceLine, "no card name found");
        }

        return LineResult.entry(new DecklistEntry(
                quantity,
                name,
                setCode == null ? null : setCode.toUpperCase(Locale.ROOT),
                collectorNumber,
                foil,
                section,
                lineNumber,
                sourceLine));
    }

    private static int parseQuantity(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            // The pattern caps the digit count, so this is unreachable in practice; treat
            // an overflow as "absurdly large" rather than crashing an import.
            return MAX_QUANTITY + 1;
        }
    }

    private static String stripRepeatedly(String input, Pattern pattern) {
        String previous;
        String working = input;
        do {
            previous = working;
            working = pattern.matcher(working).replaceAll("").strip();
        } while (!working.equals(previous));
        return working;
    }

    private static String normaliseName(String raw) {
        String name = WHITESPACE_RUN.matcher(raw.strip()).replaceAll(" ").strip();
        while (name.endsWith(",") || name.endsWith("-")) {
            name = name.substring(0, name.length() - 1).strip();
        }
        return name;
    }

    private record PositionedEntry(DecklistEntry entry, int block) {
    }

    private record LineResult(DecklistEntry entry, ParseProblem problem) {
        static LineResult entry(DecklistEntry entry) {
            return new LineResult(entry, null);
        }

        static LineResult problem(int lineNumber, String sourceLine, String reason) {
            return new LineResult(null, new ParseProblem(lineNumber, sourceLine.strip(), reason));
        }
    }
}
