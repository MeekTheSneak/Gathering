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

    /** Two commanders are only allowed when both of them say so. */
    public boolean allowsPairing(List<CardMetadata> commanders) {
        return switch (this) {
            case NONE -> commanders.isEmpty();
            case COMMANDER -> commanders.size() <= 1 || commanders.stream().allMatch(CommanderRules::hasPartner);
            case OATHBREAKER -> commanders.size() == 2;
        };
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

    private static boolean hasPartner(CardMetadata card) {
        String text = oracleTextOf(card);
        // "Partner" and "Partner with <name>" both pair; "Partner" appearing inside another
        // word would be a false positive, so the check is on the keyword as written.
        return text.contains("Partner with") || text.contains("Partner (")
                || text.lines().anyMatch(line -> line.strip().equals("Partner"));
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
