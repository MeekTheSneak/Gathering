package dev.gathering.core.booster;

import java.util.Set;
import java.util.UUID;

/**
 * Something a card can come out of.
 * <p>A pack, a fixed bundle, a reward, a loot pool - the auditor does not care which, only
 * what each of them can reach. Kept this narrow so a server adding an acquisition path the
 * mod has never heard of is still audited: whoever adds it says what it reaches, and
 * completeness goes on being a computation rather than a claim.
 * <p>"Reaches" means <em>could produce, ever</em>, not "is likely to". A card on a sheet
 * weighted one in ten thousand is reachable; whether that is a sensible weight is a different
 * report, and conflating the two would make an auditor that says a card is unobtainable when
 * it is merely rare.
 */
public interface Faucet {

    /** What this path is called, so a report can say which one covers what. */
    String name();

    /** Every printing this path could ever produce. */
    Set<UUID> reaches();
}
