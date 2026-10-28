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

    /**
     * How far a player may have moved with a screen open and still be given their view back, squared.
     * <p>They cannot walk with one open, so anything much is something else moving them - and the one
     * that matters is being teleported, where putting the old view back overrides a rotation the
     * server chose and then sends it up as though the player had turned. Sixteen blocks is past
     * anything a boat or a minecart carries somebody in the time a screen is open, and well inside
     * where a teleport lands.
     */
    private static final double MOVED_AWAY = 16.0 * 16.0;

    private Facing kept;

    private double x;
    private double y;
    private double z;

    /** Called when one of the mod's screens opens straight from the world. */
    public void opened(Facing looking, double atX, double atY, double atZ) {
        if (kept == null && looking != null) {
            kept = looking;
            x = atX;
            y = atY;
            z = atZ;
        }
    }

    /** Whether there is a view waiting to be put back. */
    public boolean isKeeping() {
        return kept != null;
    }

    /** What to look at again from where the player now is, and it is not kept twice. */
    public Optional<Facing> closed(double atX, double atY, double atZ) {
        Facing was = kept;
        kept = null;
        double moved = (atX - x) * (atX - x) + (atY - y) * (atY - y) + (atZ - z) * (atZ - z);
        return moved > MOVED_AWAY ? Optional.empty() : Optional.ofNullable(was);
    }

    /** Drops it unused: the window went somewhere this is not answerable for. */
    public void forget() {
        kept = null;
    }
}
