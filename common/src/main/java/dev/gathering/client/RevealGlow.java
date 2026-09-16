package dev.gathering.client;

import dev.gathering.network.CardSummary;

/**
 * The glow behind a card worth making a fuss about, as a pack is laid out.
 * <p>Asked for by the owner in place of a ring round only the last card: every rare and mythic in the
 * pack glows from behind, pulsing slowly, and a special version of a card - showcase, borderless,
 * extended art, full art - glows purple whatever its rarity, because that is what somebody opening a
 * pack is hoping to see. Gold for a rare and orange for a mythic, the colors the tear already uses.
 * <p>Client-only.
 */
final class RevealGlow {

    static final int RARE_GLOW = 0xFFE8C35A;
    static final int MYTHIC_GLOW = 0xFFF08A3C;
    static final int SPECIAL_GLOW = 0xFFB47AF0;

    /** One slow breath, in milliseconds. */
    private static final double BREATH = 2_400.0;

    private RevealGlow() {
    }

    /** The glow's color for this card, or zero for none. A card not yet named has none yet. */
    static int colorFor(CardSummary card) {
        if (card == null) {
            return 0;
        }
        // Rarity and nothing else. A special version used to be checked first and win, so a showcase
        // mythic glowed the showcase's purple rather than the mythic's orange - the smaller of the two
        // announcements for the bigger of the two cards.
        return switch (card.rarity()) {
            case MYTHIC -> MYTHIC_GLOW;
            case RARE -> RARE_GLOW;
            default -> 0;
        };
    }

    /**
     * How strong the glow is at this moment, between two thirds and full. Each card a little out of step
     * with the one before, so a pack of rares breathes rather than blinks; steady at full for somebody
     * who asked for less motion.
     */
    static float pulse(long now, int index, boolean reducedMotion) {
        if (reducedMotion) {
            return 1f;
        }
        double phase = (now / BREATH + index * 0.13) * Math.PI * 2;
        return (float) (0.66 + 0.34 * (0.5 + 0.5 * Math.sin(phase)));
    }
}
