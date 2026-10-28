package dev.gathering.core.collection;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What somebody typed into a collection's search box, as something that can be asked of a card.
 * <p>The box used to match a few words against the name, the type line and the set, which
 * answers "where is that thing called something-Bolt" and nothing else. It could not answer
 * "which of my green creatures cost three or less", which is the question somebody building a
 * deck actually has, so the box takes a query language now.
 * <p><b>It is Scryfall's syntax, on purpose.</b> Everybody who plays this game has typed
 * {@code t:creature c:rg mv<=3} into a search box before, and inventing a second dialect for
 * the same job would mean every player of the mod learning something they already know in a
 * shape nobody else uses. Only the parts a collection can answer are implemented - there is no
 * price data here and no format legality in the box - and a term this does not recognize is
 * matched against the name rather than thrown away, so a query typed hopefully still finds
 * something rather than nothing.
 * <p>Every term is "and", and a term may be negated with a leading minus. Quotes hold a phrase
 * together. Bare words go to the name, the type line and the set, exactly as they always did.
 * <p>Pure. Nothing here fetches anything.
 */
public final class CardSearch {

    private CardSearch() {
    }

    /** Which part of a card a term asks about. */
    public enum Field {
        /** Bare words: the name, the type line, or the set. What the box always did. */
        ANY,
        NAME,
        ORACLE,
        TYPE,
        COLOR,
        IDENTITY,
        MANA_COST,
        MANA_VALUE,
        POWER,
        TOUGHNESS,
        RARITY,
        SET,
        COUNT,
        /** {@code is:foil}, {@code is:known} - the yes-or-no questions. */
        IS
    }

    /** How a term compares. Most fields only ever contain; the numeric ones use the rest. */
    public enum Op {
        CONTAINS, EQUALS, NOT_EQUALS, GREATER, LESS, AT_LEAST, AT_MOST
    }

    /** One thing somebody asked for. */
    public record Term(Field field, Op op, String value, boolean negated) {

        public Term {
            value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The words a field can be called, so a player can type what comes to mind.
     * <p>Scryfall's own abbreviations plus the whole word, because somebody who half-remembers
     * the syntax types "type:" and somebody who uses it daily types "t:", and both of them are
     * asking the same question.
     */
    private static final Map<String, Field> FIELDS = Map.ofEntries(
            Map.entry("name", Field.NAME), Map.entry("n", Field.NAME),
            Map.entry("oracle", Field.ORACLE), Map.entry("o", Field.ORACLE),
            Map.entry("text", Field.ORACLE),
            Map.entry("type", Field.TYPE), Map.entry("t", Field.TYPE),
            Map.entry("color", Field.COLOR), Map.entry("c", Field.COLOR),
            Map.entry("identity", Field.IDENTITY), Map.entry("id", Field.IDENTITY),
            Map.entry("ci", Field.IDENTITY), Map.entry("commander", Field.IDENTITY),
            Map.entry("mana", Field.MANA_COST), Map.entry("m", Field.MANA_COST),
            Map.entry("mv", Field.MANA_VALUE), Map.entry("cmc", Field.MANA_VALUE),
            Map.entry("power", Field.POWER), Map.entry("pow", Field.POWER),
            Map.entry("p", Field.POWER),
            Map.entry("toughness", Field.TOUGHNESS), Map.entry("tou", Field.TOUGHNESS),
            Map.entry("rarity", Field.RARITY), Map.entry("r", Field.RARITY),
            Map.entry("set", Field.SET), Map.entry("s", Field.SET), Map.entry("e", Field.SET),
            Map.entry("edition", Field.SET),
            Map.entry("count", Field.COUNT), Map.entry("have", Field.COUNT),
            Map.entry("is", Field.IS), Map.entry("has", Field.IS));

    /**
     * Colors by every name somebody might reach for.
     * <p>The letters, the words, and the two- and three-color guild and shard names, because
     * "id:gruul" is how people actually talk about a color pair and spelling it "id:rg" is a
     * translation they should not have to do.
     */
    private static final Map<String, String> COLOR_WORDS = Map.ofEntries(
            Map.entry("white", "W"), Map.entry("blue", "U"), Map.entry("black", "B"),
            Map.entry("red", "R"), Map.entry("green", "G"), Map.entry("colorless", "C"),
            Map.entry("azorius", "WU"), Map.entry("dimir", "UB"), Map.entry("rakdos", "BR"),
            Map.entry("gruul", "RG"), Map.entry("selesnya", "GW"), Map.entry("orzhov", "WB"),
            Map.entry("izzet", "UR"), Map.entry("golgari", "BG"), Map.entry("boros", "RW"),
            Map.entry("simic", "GU"),
            Map.entry("bant", "GWU"), Map.entry("esper", "WUB"), Map.entry("grixis", "UBR"),
            Map.entry("jund", "BRG"), Map.entry("naya", "RGW"), Map.entry("abzan", "WBG"),
            Map.entry("jeskai", "URW"), Map.entry("sultai", "BGU"), Map.entry("mardu", "RWB"),
            Map.entry("temur", "GUR"), Map.entry("wubrg", "WUBRG"), Map.entry("rainbow", "WUBRG"));

    // ------------------------------------------------------------------ parsing

    /**
     * Splits what somebody typed into terms.
     * <p>Never throws and never refuses. A search box that answers a typo with an error has
     * turned a question into a form to fill in correctly; anything this cannot make sense of
     * becomes a plain word to look for, which is what the box did before there was a syntax at
     * all and is the behavior somebody typing hopefully expects.
     */
    public static List<Term> parse(String typed) {
        List<Term> terms = new ArrayList<>();
        if (typed == null || typed.isBlank()) {
            return List.of();
        }
        for (String piece : split(typed)) {
            if (piece.isBlank()) {
                continue;
            }
            boolean negated = piece.startsWith("-") && piece.length() > 1;
            String rest = negated ? piece.substring(1) : piece;

            int at = firstOperator(rest);
            if (at < 0) {
                terms.add(new Term(Field.ANY, Op.CONTAINS, unquote(rest), negated));
                continue;
            }
            Field field = FIELDS.get(rest.substring(0, at).toLowerCase(Locale.ROOT));
            if (field == null) {
                // Not a field anybody named. The whole thing is a word to look for, colon and
                // all, rather than a silent no-op - "sol:ring" should find Sol Ring.
                terms.add(new Term(Field.ANY, Op.CONTAINS, unquote(rest), negated));
                continue;
            }
            Op op = operatorAt(rest, at);
            String value = unquote(rest.substring(at + lengthOf(op)));
            terms.add(new Term(field, op, value, negated));
        }
        return List.copyOf(terms);
    }

    /** Splits on spaces, but never inside quotes: {@code o:"draw a card"} is one term. */
    private static List<String> split(String typed) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        boolean quoted = false;
        for (char letter : typed.toCharArray()) {
            if (letter == '"') {
                quoted = !quoted;
                piece.append(letter);
            } else if (letter == ' ' && !quoted) {
                pieces.add(piece.toString());
                piece.setLength(0);
            } else {
                piece.append(letter);
            }
        }
        pieces.add(piece.toString());
        return pieces;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\"", "");
    }

    /** Where the operator starts, or -1 for a bare word. */
    private static int firstOperator(String piece) {
        for (int at = 0; at < piece.length(); at++) {
            char letter = piece.charAt(at);
            if (letter == ':' || letter == '=' || letter == '>' || letter == '<'
                    || (letter == '!' && at + 1 < piece.length() && piece.charAt(at + 1) == '=')) {
                return at;
            }
        }
        return -1;
    }

    private static Op operatorAt(String piece, int at) {
        char letter = piece.charAt(at);
        boolean equalsNext = at + 1 < piece.length() && piece.charAt(at + 1) == '=';
        return switch (letter) {
            case '>' -> equalsNext ? Op.AT_LEAST : Op.GREATER;
            case '<' -> equalsNext ? Op.AT_MOST : Op.LESS;
            case '!' -> Op.NOT_EQUALS;
            case '=' -> Op.EQUALS;
            default -> Op.CONTAINS;
        };
    }

    private static int lengthOf(Op op) {
        return switch (op) {
            case AT_LEAST, AT_MOST, NOT_EQUALS -> 2;
            default -> 1;
        };
    }

    // ---------------------------------------------------------------- matching

    /**
     * Whether a card answers everything that was asked.
     *
     * @param count how many copies the collection holds, for {@code count>=4}
     */
    public static boolean matches(CardMetadata about, int count, List<Term> terms) {
        if (terms == null || terms.isEmpty()) {
            return true;
        }
        if (about == null) {
            // Nothing is known about it, so nothing can be said to be true of it - and
            // nothing can be said to be false either. It answers no term rather than every
            // one, which keeps a card still being fetched out of a filtered list instead of
            // claiming it is a green creature.
            return false;
        }
        for (Term term : terms) {
            if (answers(about, count, term) == term.negated()) {
                return false;
            }
        }
        return true;
    }

    private static boolean answers(CardMetadata about, int count, Term term) {
        return switch (term.field()) {
            case ANY -> lower(about.name()).contains(term.value())
                    || lower(about.typeLine()).contains(term.value())
                    || lower(about.setCode()).contains(term.value())
                    || lower(about.setName()).contains(term.value());
            case NAME -> lower(about.name()).contains(term.value());
            case ORACLE -> everyFaceText(about).contains(term.value());
            case TYPE -> everyFaceTypeLine(about).contains(term.value());
            case MANA_COST -> lower(about.manaCost()).contains(term.value());
            case SET -> lower(about.setCode()).equals(term.value())
                    || lower(about.setName()).contains(term.value());
            case COLOR -> colorsAnswer(about.colors(), term);
            case IDENTITY -> colorsAnswer(about.colorIdentity(), term);
            case MANA_VALUE -> numberAnswers(about.cmc(), term);
            case POWER -> firstFace(about).flatMap(face -> number(face.power()))
                    .map(power -> numberAnswers(power, term)).orElse(false);
            case TOUGHNESS -> firstFace(about).flatMap(face -> number(face.toughness()))
                    .map(toughness -> numberAnswers(toughness, term)).orElse(false);
            case COUNT -> numberAnswers(count, term);
            case RARITY -> rarityAnswers(about.rarity(), term);
            case IS -> isAnswers(about, term);
        };
    }

    /**
     * Color terms, which are the only ones where the operator changes the question.
     * <p>The three readings people actually want: <em>at least</em> these colors (the plain
     * colon, and how somebody asks for "my Gruul cards"), <em>exactly</em> these, and
     * <em>within</em> these, which is the one a commander deck is built on - every card whose
     * identity fits inside the commander's.
     */
    private static boolean colorsAnswer(Set<String> has, Term term) {
        Set<String> wanted = colorsIn(term.value());
        Set<String> mine = upperCased(has);
        if (wanted.contains("C")) {
            // Colorless is the absence of color rather than one of them, so it is its own
            // question: nothing else in the term matters and having any color is a no.
            return mine.isEmpty();
        }
        if (wanted.isEmpty()) {
            return true;
        }
        return switch (term.op()) {
            case EQUALS -> mine.equals(wanted);
            case NOT_EQUALS -> !mine.equals(wanted);
            case AT_MOST, LESS -> wanted.containsAll(mine);
            default -> mine.containsAll(wanted);
        };
    }

    /** The colors in a term: a guild name, a color word, or a run of letters. */
    private static Set<String> colorsIn(String value) {
        String word = value.trim().toLowerCase(Locale.ROOT);
        String letters = COLOR_WORDS.getOrDefault(word, word.toUpperCase(Locale.ROOT));
        Set<String> found = new LinkedHashSet<>();
        for (char letter : letters.toCharArray()) {
            if ("WUBRGC".indexOf(letter) >= 0) {
                found.add(String.valueOf(letter));
            }
        }
        return found;
    }

    /**
     * Rarity, which is ordered as well as named.
     * <p>So {@code r>=rare} means rare and mythic, which is how anybody thinks about it, and
     * {@code r:rare} means that one. Unknown sorts below common: a card whose rarity did not
     * survive the trip is not a mythic.
     */
    private static boolean rarityAnswers(Rarity has, Term term) {
        Rarity wanted = rarityNamed(term.value());
        if (wanted == null) {
            return false;
        }
        int mine = has == null ? -1 : has.ordinal();
        int asked = wanted.ordinal();
        return switch (term.op()) {
            case GREATER -> mine > asked;
            case LESS -> mine < asked;
            case AT_LEAST -> mine >= asked;
            case AT_MOST -> mine <= asked;
            case NOT_EQUALS -> mine != asked;
            default -> mine == asked;
        };
    }

    private static Rarity rarityNamed(String value) {
        for (Rarity rarity : Rarity.values()) {
            String name = rarity.name().toLowerCase(Locale.ROOT);
            if (name.equals(value) || name.startsWith(value) && !value.isEmpty()) {
                return rarity;
            }
        }
        return null;
    }

    private static boolean isAnswers(CardMetadata about, Term term) {
        return switch (term.value()) {
            case "foil" -> false;
            case "creature", "land", "instant", "sorcery", "artifact", "enchantment",
                 "planeswalker", "battle", "legendary", "basic", "snow" ->
                    everyFaceTypeLine(about).contains(term.value());
            case "split", "transform", "dfc", "doublefaced" -> about.faces().size() > 1;
            case "permanent" -> {
                String types = everyFaceTypeLine(about);
                yield types.contains("creature") || types.contains("land")
                        || types.contains("artifact") || types.contains("enchantment")
                        || types.contains("planeswalker") || types.contains("battle");
            }
            case "spell" -> {
                String types = everyFaceTypeLine(about);
                yield types.contains("instant") || types.contains("sorcery");
            }
            case "vanilla" -> everyFaceText(about).isBlank();
            default -> false;
        };
    }

    private static boolean numberAnswers(double has, Term term) {
        Optional<Double> wanted = number(term.value());
        if (wanted.isEmpty()) {
            return false;
        }
        double asked = wanted.get();
        return switch (term.op()) {
            case GREATER -> has > asked;
            case LESS -> has < asked;
            case AT_LEAST -> has >= asked;
            case AT_MOST -> has <= asked;
            case NOT_EQUALS -> has != asked;
            default -> has == asked;
        };
    }

    /**
     * A number, or empty for the things printed where a number goes.
     * <p>A power of "*" or "1+*" is not a number and no comparison against it is true, which
     * is the honest answer: the card's power depends on the board, and a search cannot know
     * the board.
     */
    private static Optional<Double> number(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<CardFace> firstFace(CardMetadata about) {
        return about.faces().isEmpty() ? Optional.empty() : Optional.of(about.faces().get(0));
    }

    /**
     * Every face's rules text, run together.
     * <p>Both halves, because a search for a word on the back of a transform card is a search
     * that should find it - the card in the box is the whole card.
     */
    private static String everyFaceText(CardMetadata about) {
        StringBuilder text = new StringBuilder(lower(about.oracleText()));
        for (CardFace face : about.faces()) {
            text.append(' ').append(lower(face.oracleText()));
        }
        return text.toString();
    }

    private static String everyFaceTypeLine(CardMetadata about) {
        StringBuilder types = new StringBuilder(lower(about.typeLine()));
        for (CardFace face : about.faces()) {
            types.append(' ').append(lower(face.typeLine()));
        }
        return types.toString();
    }

    private static Set<String> upperCased(Set<String> colors) {
        Set<String> upper = new LinkedHashSet<>();
        for (String color : colors) {
            upper.add(color.trim().toUpperCase(Locale.ROOT));
        }
        return upper;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
