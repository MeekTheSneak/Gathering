package dev.gathering.core.ui;

import java.util.Optional;

/**
 * The view a player had before a screen took the window, given back when it closes.
 * <p>Reported as "where you were looking before you open a pack, open a menu, use a block
 * needs to be kept the same after you exit that menu". Opening something should not cost you
 * the table you were standing at, and coming out of a screen facing somewhere else is the
 * kind of small wrongness that makes a room feel like it is sliding around.
 * <p>The <em>outermost</em> view, deliberately. Every screen here is a detour and most of them
 * open further detours - a table, a graveyard, a card, back out - and what the player wants
 * back is where they were looking when they stopped looking at the world, not where they were
 * when they opened the third box. So the first open is the one that is kept and every one
 * after it is nothing at all.
 * <p>Nothing is kept across anything that is not this mod's own doing: a view kept while the
 * game handed the window to something else would be put back over whatever that left behind.
 */
public final class KeptView {

    private Facing kept;

    /** Called when one of the mod's screens opens straight from the world. */
    public void opened(Facing looking) {
        if (kept == null && looking != null) {
            kept = looking;
        }
    }

    /** Whether there is a view waiting to be put back. */
    public boolean isKeeping() {
        return kept != null;
    }

    /** What to look at again, and it is not kept twice. */
    public Optional<Facing> closed() {
        Facing was = kept;
        kept = null;
        return Optional.ofNullable(was);
    }

    /** Drops it unused: the window went somewhere this is not answerable for. */
    public void forget() {
        kept = null;
    }
}
