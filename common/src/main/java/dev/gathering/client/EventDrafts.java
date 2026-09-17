package dev.gathering.client;

import dev.gathering.core.tournament.EventDraft;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

/**
 * A tournament somebody is part way through making, kept against the desk they are making it at.
 * <p>What happens when the create screen is closed and opened again at the same block: it comes back
 * as it was left. A host fills the screen in, walks off to fetch the boosters they are putting up as
 * a prize, and comes back - and typing a name, a format, a pack rule and six clock settings again is
 * the kind of small punishment nobody can see the reason for. Keyed by the desk rather than kept as
 * one, so two desks being set up in the same room do not overwrite each other, and dropped the
 * moment Create is pressed, so the next tournament hosted at that desk starts blank rather than
 * inheriting the last one's name and prizes.
 * <p>Client-side only and nothing to do with the server: this is a screen remembering what somebody
 * typed into it, not state a server told us. It still goes when a server does, because the desk it
 * is keyed to belongs to that world.
 */
public final class EventDrafts {

    private static final Map<BlockPos, EventDraft> DRAFTS = new HashMap<>();

    /** How many desks are remembered at once. A room being set up has a handful, not hundreds. */
    private static final int MOST_DESKS = 8;

    private EventDrafts() {
    }

    /** What was being made at this desk, or a fresh draft. */
    public static EventDraft at(BlockPos desk, Supplier<EventDraft> blank) {
        EventDraft kept = desk == null ? null : DRAFTS.get(desk.immutable());
        return kept == null ? blank.get() : kept;
    }

    public static void keep(BlockPos desk, EventDraft draft) {
        if (desk == null || draft == null) {
            return;
        }
        if (DRAFTS.size() >= MOST_DESKS && !DRAFTS.containsKey(desk.immutable())) {
            // The oldest goes. A bound rather than an ordering: nothing here is worth a second map to
            // keep in order, and a host with eight unfinished tournaments on the go has other problems.
            DRAFTS.remove(DRAFTS.keySet().iterator().next());
        }
        DRAFTS.put(desk.immutable(), draft);
    }

    /** This desk's draft is finished with: it became a tournament, or was thrown away. */
    public static void forget(BlockPos desk) {
        if (desk != null) {
            DRAFTS.remove(desk.immutable());
        }
    }

    public static void clear() {
        DRAFTS.clear();
    }
}
