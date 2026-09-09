package dev.gathering.core.format;

import dev.gathering.core.card.CardMetadata;
import java.util.List;

/**
 * Whether a format has a command zone, and what is allowed to sit in it.
 * <p>Part of a preset's data rather than a branch in the validator, so adding a
 * commander-shaped format later is a table entry.
 */
public enum CommanderRules {

    /** Sixty-card formats. No command zone, no color identity restriction. */
    NONE(0, 0),

    /** One commander, or two that say Partner. */
    COMMANDER(1, 2),

    /** A planeswalker and a signature instant or sorcery, always exactly two. */
    OATHBREAKER(2, 2);

    private final int minimum;
    private final int maximum;

    CommanderRules(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public boolean inUse() {
        return this != NONE;
    }

    public int minimumCommanders() {
        return minimum;
    }

    public int maximumCommanders() {
        return maximum;
    }

    /**
     * Whether a card may lead a deck under these rules.
     * <p>Read off the type line and the printed text rather than a list of names, so a card
     * printed next year that says it can be your commander works with no code change - which
     * is the same principle as reading the any-number exception off oracle text.
     */
    public boolean isEligible(CardMetadata card, int position) {
        return switch (this) {
            case NONE -> false;
            case COMMANDER -> isLegendaryCreature(card) || saysCanBeYourCommander(card);
            // Oathbreaker's two slots are different from each other, so position matters.
            case OATHBREAKER -> position == 0 ? isPlaneswalker(card) : isInstantOrSorcery(card);
        };
    }

    /**
     * Whether this card may lead the deck <em>given the card beside it</em>.
     * <p>The question a whole deck asks, and a different question from the one above. A
     * Background is a legendary enchantment: it leads nothing by itself and answers no to
     * every test a lone commander is put through. What puts it in the command zone is the
     * other card saying "Choose a Background" - so a deck built exactly as the rules describe
     * one was rejected with {@code commander_ineligible}, while {@link #allowsPairing} was
     * quite happy with the same two cards. Asking each slot in isolation cannot see a
     * mechanic whose whole point is that one card admits another.
     * <p>So: eligible on its own, or admitted by a partner that is itself eligible. The
     * partner has to be eligible or two ineligible cards would let each other in.
     */
    public boolean isEligible(List<CardMetadata> commanders, int position) {
        CardMetadata card = commanders.get(position);
        if (isEligible(card, position)) {
            return true;
        }
        if (this != COMMANDER || commanders.size() != 2) {
            return false;
        }
        CardMetadata other = commanders.get(1 - position);
        return isEligible(other, 1 - position) && pairsOneWay(other, card);
    }

    /**
     * Why two particular cards are not a legal pair, in the words of the mechanic they were
     * reaching for.
     * <p>"Two commanders are only allowed when both have Partner" was the whole message, and
     * it is wrong for four of the five mechanics that put two cards in a command zone. What a
     * player wants to know is which rule they missed.
     */
    public String describePairing(List<CardMetadata> commanders) {
        if (this == OATHBREAKER) {
            return "An Oathbreaker deck needs exactly one planeswalker and one signature spell.";
        }
        if (commanders.size() != 2) {
            return "Only one card may lead this deck.";
        }
        CardMetadata one = commanders.get(0);
        CardMetadata two = commanders.get(1);
        String named = partnerNamedBy(one) != null ? partnerNamedBy(one) : partnerNamedBy(two);
        if (named != null) {
            return "That pairing is a \"Partner with\" clause, which only pairs with "
                    + named + ".";
        }
        if (saysChooseABackground(one) || saysChooseABackground(two)) {
            return "Choose a Background pairs with a Background, and nothing else.";
        }
        if (hasKeyword(one, "Friends forever") || hasKeyword(two, "Friends forever")) {
            return "Friends forever pairs with another Friends forever, and nothing else.";
        }
        if (hasKeyword(one, "Doctor's companion") || hasKeyword(two, "Doctor's companion")) {
            return "Doctor's companion pairs with a Time Lord Doctor, and nothing else.";
        }
        return "Two commanders need a printed pairing: Partner, Partner with, Friends forever,"
                + " Choose a Background or Doctor's companion.";
    }

    /**
     * Whether these two cards may lead a deck together.
     * <p>Four printed mechanics put two cards in a command zone and each pairs differently.
     * Reading them all as "has the word Partner somewhere" accepted pairs no rules enforcement
     * would: two cards whose Partner-with clauses name different people, a Background beside
     * another Background, a Doctor's companion with no Doctor. An audit reproduced the first.
     * <ul>
     *   <li><b>Partner</b> - the bare keyword pairs with any other bare Partner.</li>
     *   <li><b>Partner with N</b> - pairs only with the card it names, and that card names it
     *       back. It does not pair with an ordinary Partner.</li>
     *   <li><b>Friends forever</b> - pairs with another Friends forever, and nothing else.</li>
     *   <li><b>Choose a Background</b> - pairs with a Background, which is an enchantment type
     *       rather than a keyword.</li>
     *   <li><b>Doctor's companion</b> - pairs with a Time Lord Doctor.</li>
     * </ul>
     */
    public boolean allowsPairing(List<CardMetadata> commanders) {
        return switch (this) {
            case NONE -> commanders.isEmpty();
            case COMMANDER -> commanders.size() <= 1
                    || (commanders.size() == 2 && pairs(commanders.get(0), commanders.get(1)));
            case OATHBREAKER -> commanders.size() == 2;
        };
    }

    /** Whether these two, in either order, are a printed pairing. */
    private static boolean pairs(CardMetadata one, CardMetadata two) {
        return pairsOneWay(one, two) || pairsOneWay(two, one);
    }

    private static boolean pairsOneWay(CardMetadata card, CardMetadata with) {
        // Named partners: the name has to match, both ways round. "Partner with Tevesh Szat"
        // beside "Partner with Thrasios" is two cards each waiting for somebody else.
        String named = partnerNamedBy(card);
        if (named != null) {
            String back = partnerNamedBy(with);
            return named.equalsIgnoreCase(nameOf(with))
                    && (back == null || back.equalsIgnoreCase(nameOf(card)));
        }
        if (hasKeyword(card, "Friends forever")) {
            return hasKeyword(with, "Friends forever");
        }
        if (saysChooseABackground(card)) {
            return with.isOfType("Background");
        }
        if (hasKeyword(card, "Doctor's companion")) {
            // Asked a word at a time, because that is how a type line is read here: the
            // creature type is "Time Lord Doctor" and no single word of it is enough.
            return with.isOfType("Time") && with.isOfType("Lord") && with.isOfType("Doctor");
        }
        // The bare keyword, which pairs with another bare keyword and with nothing fussier.
        return hasBarePartner(card) && hasBarePartner(with);
    }

    /** The name a "Partner with" clause names, or null when the card has no such clause. */
    private static String partnerNamedBy(CardMetadata card) {
        for (String line : oracleTextOf(card).lines().toList()) {
            String said = line.strip();
            int at = said.indexOf("Partner with ");
            if (at < 0) {
                continue;
            }
            String rest = said.substring(at + "Partner with ".length()).strip();
            // The clause is followed by its reminder text in brackets, and otherwise runs to
            // the end of the line. Only the bracket ends it: card names have commas and full
            // stops in them - "Hanna, Ship's Navigator" - and cutting at those turned a named
            // partner into a card nobody is called.
            int bracket = rest.indexOf(" (");
            String name = (bracket >= 0 ? rest.substring(0, bracket) : rest).strip();
            if (name.endsWith(".")) {
                name = name.substring(0, name.length() - 1).strip();
            }
            if (!name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    private static boolean hasBarePartner(CardMetadata card) {
        if (partnerNamedBy(card) != null) {
            return false;
        }
        String text = oracleTextOf(card);
        return text.contains("Partner (")
                || text.lines().anyMatch(line -> line.strip().equals("Partner"));
    }

    private static boolean saysChooseABackground(CardMetadata card) {
        return oracleTextOf(card).contains("Choose a Background");
    }

    private static boolean hasKeyword(CardMetadata card, String keyword) {
        return oracleTextOf(card).lines()
                .anyMatch(line -> line.strip().equals(keyword)
                        || line.strip().startsWith(keyword + " ("));
    }

    private static String nameOf(CardMetadata card) {
        return card.name() == null ? "" : card.name();
    }

    public String describeEligibility(int position) {
        return switch (this) {
            case NONE -> "not a commander format";
            case COMMANDER -> "a legendary creature, or a card that says it can be your commander";
            case OATHBREAKER -> position == 0 ? "a planeswalker" : "an instant or sorcery";
        };
    }

    // What a card is, asked of the card rather than worked out here. Scryfall joins a
    // double-faced card's types into one line - "Instant // Land" - and these used to search
    // that whole string, which is the exact mistake CardMetadata's own type reading is
    // written to avoid: a card is its front face everywhere but the battlefield, so a search
    // over the joined line answers for the back as well. Two copies of one rule, and only one
    // of them had the comment explaining it.

    private static boolean isLegendaryCreature(CardMetadata card) {
        return card.isOfType("Legendary") && card.isOfType("Creature");
    }

    private static boolean isPlaneswalker(CardMetadata card) {
        return card.isOfType("Planeswalker");
    }

    private static boolean isInstantOrSorcery(CardMetadata card) {
        return card.isOfType("Instant") || card.isOfType("Sorcery");
    }

    private static boolean saysCanBeYourCommander(CardMetadata card) {
        return oracleTextOf(card).contains("can be your commander");
    }

    /** Card-level text plus every face's, since a double-faced commander says it on one side. */
    private static String oracleTextOf(CardMetadata card) {
        StringBuilder text = new StringBuilder(card.oracleText() == null ? "" : card.oracleText());
        card.faces().forEach(face -> {
            if (face.oracleText() != null) {
                text.append('\n').append(face.oracleText());
            }
            if (face.typeLine() != null) {
                text.append('\n').append(face.typeLine());
            }
        });
        return text.toString();
    }
}
