package dev.gathering.core.collection;

import java.util.ArrayList;
import java.util.List;

/**
 * Where cards come from on <em>this</em> server, said only when it is true.
 * <p>A player short of four cards wants to know what to do about it, and the honest answer
 * depends entirely on how the server is configured. Telling somebody to buy packs on a server
 * with the shop turned off is worse than telling them nothing: it sends them to look for a
 * thing that is not there, and then the mod looks broken rather than the advice looking wrong.
 *
 * <p>So every hint is read off the settings that are actually in force, and a server that
 * offers none of them says so rather than making something up. "I do not know where cards come
 * from here, ask whoever runs it" is a useful sentence; an invented one is not.
 *
 * <p><b>Nothing here reads anybody's cards.</b> It takes what the server allows and returns
 * sentences about it. What a particular player owns, what is in anybody else's collection and
 * what happens to be in a loot table are all absent by construction - a hint is about the
 * world, not about a person, so there is nothing here to leak.
 *
 * <p>Pure.
 */
public final class AcquisitionHints {

    /**
     * What this server actually offers, as the config has it.
     *
     * @param collecting    whether collecting is on at all
     * @param shop          whether sealed product can be bought
     * @param loot          whether packs turn up as loot
     * @param importing     whether this player may import a decklist
     * @param villageShop   whether wandering traders and villages carry packs
     */
    public record Sources(
            boolean collecting, boolean shop, boolean loot,
            boolean importing, boolean villageShop) {
    }

    /** Where a hint's line is written. */
    private static final String PREFIX = "hint.gathering.";

    /** Said when the server offers nothing this can point at. */
    public static final String NOTHING_KNOWN = PREFIX + "unknown";

    /** Said when collecting is off entirely, which makes every other hint irrelevant. */
    public static final String COLLECTING_OFF = PREFIX + "collecting_off";

    private AcquisitionHints() {
    }

    /**
     * The truthful hints for this server, in the order they are worth reading.
     * <p>Never empty: a server with nothing on still gets an answer, because "no answer" and
     * "no hint row at all" look identical to somebody wondering whether the feature works.
     */
    public static List<String> forSources(Sources sources) {
        if (sources == null) {
            return List.of(NOTHING_KNOWN);
        }
        if (!sources.collecting()) {
            // The one that makes the rest moot. A server with collecting off is not a server
            // where the shop being on means anything, so saying both would be confusing even
            // though both are true of the file.
            return List.of(COLLECTING_OFF,
                    sources.importing() ? PREFIX + "import" : NOTHING_KNOWN);
        }
        List<String> hints = new ArrayList<>();
        if (sources.shop()) {
            hints.add(PREFIX + "shop");
        }
        if (sources.villageShop()) {
            hints.add(PREFIX + "village");
        }
        if (sources.loot()) {
            hints.add(PREFIX + "loot");
        }
        if (sources.importing()) {
            hints.add(PREFIX + "import");
        }
        // Trading is between players and needs nothing configured, so it is true whenever
        // there are cards at all - which, collecting being on, there are.
        hints.add(PREFIX + "trade");
        return List.copyOf(hints);
    }

    /**
     * Whether a hint names a way of getting cards that this server has switched off.
     * <p>For a test rather than for a caller: what it guards is that no hint is ever produced
     * for something unavailable, which is the one way this could mislead somebody.
     */
    public static boolean isAbout(String hint, String what) {
        return hint != null && hint.equals(PREFIX + what);
    }
}
