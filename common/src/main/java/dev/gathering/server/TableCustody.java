package dev.gathering.server;

import dev.gathering.block.TableBlockEntity;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which loaded table block entity is the one a table's game and keeping belong to, found by the
 * table's own identity rather than where it stands.
 * <p>For tables that are moved rather than broken. Sable - under Create Aeronautics' ships - carries
 * a table off by writing its block entity down, loading the copy in the ship's own region, and only
 * then clearing the blocks where it stood. Clearing a table ends its game and hands back every deck
 * and staked card it holds, so a carried table handed them all back while the copy on the ship still
 * held them: every one of them twice. A table removed while a loaded copy of it stands somewhere
 * else was carried, not broken, and its keeping went with the copy.
 * <p>Keyed by an identity the table writes into its own saved data, so any mover that carries a
 * block entity's data is recognized, not only Sable - and only a copy loaded from the table's own
 * latest save, in the tick it was made, counts as the table carried. Server thread only.
 */
public final class TableCustody {

    private static final Map<UUID, WeakReference<TableBlockEntity>> LOADED = new HashMap<>();

    private TableCustody() {
    }

    /** A table has been loaded into a server level: the latest copy of an identity is the one that counts. */
    public static void loaded(TableBlockEntity table) {
        LOADED.put(table.custody(), new WeakReference<>(table));
    }

    /** A table has left its level. Forgotten only if it is still the copy that counts. */
    public static void gone(TableBlockEntity table) {
        WeakReference<TableBlockEntity> held = LOADED.get(table.custody());
        if (held != null && held.get() == table) {
            LOADED.remove(table.custody());
        }
    }

    /** Whether another loaded table, somewhere else, carries this one's identity: this one was moved. */
    public static boolean carriedElsewhere(TableBlockEntity table) {
        WeakReference<TableBlockEntity> held = LOADED.get(table.custody());
        TableBlockEntity other = held == null ? null : held.get();
        return other != null && other != table && !other.isRemoved()
                && (other.getLevel() != table.getLevel() || !other.getBlockPos().equals(table.getBlockPos()))
                // And made from this table's own latest save this tick, which is what a move is. A copy
                // that shares the identity any other way - pasted, cloned, picked in creative - is not.
                && table.wasCarriedTo(other);
    }

    /** For a server that is stopping. */
    public static void clear() {
        LOADED.clear();
    }
}
