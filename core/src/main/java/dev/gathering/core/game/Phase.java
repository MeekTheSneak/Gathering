package dev.gathering.core.game;

import java.util.Locale;

/**
 * The turn structure, as a shared marker and nothing more.
 *
 * <p>The active player advances this by hand. The mod never advances it, never checks that
 * an action suits the current phase, and never stops anyone doing anything in any phase.
 * It exists so that four people can agree on where they are without saying it out loud
 * every thirty seconds - maximum shared clarity, zero enforcement.
 */
public enum Phase {
    UNTAP,
    UPKEEP,
    DRAW,
    PRECOMBAT_MAIN,
    BEGIN_COMBAT,
    DECLARE_ATTACKERS,
    DECLARE_BLOCKERS,
    COMBAT_DAMAGE,
    END_COMBAT,
    POSTCOMBAT_MAIN,
    END_STEP,
    CLEANUP;

    /** The next phase in the turn, wrapping from cleanup back to untap. */
    public Phase next() {
        Phase[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public boolean isLastOfTurn() {
        return this == CLEANUP;
    }

    public String displayName() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
