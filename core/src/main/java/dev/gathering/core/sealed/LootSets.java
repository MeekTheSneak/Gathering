package dev.gathering.core.sealed;

import dev.gathering.core.config.GatheringConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which sets a server's packs are found from.
 *
 * <p>Three things can go in the config and they combine rather than compete: the sets named
 * outright, whatever is current, and the last few releases. A seasonal server names one set;
 * an era server names a block; a server that says nothing gets the current one; and a server
 * that wants its own set plus whatever is new says both.
 *
 * <p>Named sets come first and nothing appears twice. Order matters because a set is chosen
 * by walking this list, and a list that reshuffled between restarts would be a server whose
 * loot changed for no reason anybody could see.
 *
 * <p>Pure. Working out what "current" and "recent" actually are is somebody else's job; this
 * is what to do with the answers.
 */
public final class LootSets {

    private LootSets() {
    }

    /**
     * The sets to draw from.
     *
     * @param named   what the config listed, already checked - set codes and the two words
     * @param current whatever this server's current set turned out to be, if it has one
     * @param recent  the last few releases, newest first, if the config asked for them
     */
    public static List<String> wanted(
            List<String> named, Optional<String> current, List<String> recent) {
        List<String> wanted = new ArrayList<>();
        if (named != null) {
            for (String one : named) {
                if (one != null && !isAWord(one) && !wanted.contains(one)) {
                    wanted.add(one);
                }
            }
            if (named.contains(GatheringConfig.LOOT_SETS_CURRENT)) {
                current.filter(code -> !wanted.contains(code)).ifPresent(wanted::add);
            }
            if (named.contains(GatheringConfig.LOOT_SETS_RECENT) && recent != null) {
                for (String code : recent) {
                    if (code != null && !wanted.contains(code)) {
                        wanted.add(code);
                    }
                }
            }
        }
        return List.copyOf(wanted);
    }

    /** Whether the config needs somebody to go and find out what is out. */
    public static boolean needsTheReleaseList(List<String> named) {
        return named != null
                && (named.contains(GatheringConfig.LOOT_SETS_CURRENT)
                        || named.contains(GatheringConfig.LOOT_SETS_RECENT));
    }

    /** Whether the config asked for more than the newest release. */
    public static boolean needsMoreThanTheNewest(List<String> named) {
        return named != null && named.contains(GatheringConfig.LOOT_SETS_RECENT);
    }

    private static boolean isAWord(String one) {
        return one.equals(GatheringConfig.LOOT_SETS_CURRENT)
                || one.equals(GatheringConfig.LOOT_SETS_RECENT);
    }
}
