package dev.gathering.core.ui;

/**
 * How far a pack has been torn open, and what the torn edge looks like.
 * <p>A booster is opened by hand, across the top, from one corner to the other - so that is
 * the gesture: take hold of a corner and drag, and the tear goes where the cursor goes. Not a
 * button and not a progress bar. The point of the ceremony is that opening the pack is
 * something you do rather than something you ask for, and a tear that follows your hand is
 * the smallest honest version of that.
 * <p>Two rules make it feel like paper rather than like a slider.
 * <p><b>It starts at a corner.</b> Until the cursor has been over the grip at the near end,
 * nothing tears - so clicking in the middle of the wrapper does not open half the pack at
 * once, any more than poking the middle of a real one would.
 * <p><b>It never goes back.</b> Dragging left does not un-tear what is torn. A wrapper is
 * paper, and a tear that healed when your hand wandered would be the one thing about this
 * that could not happen.
 * <p>Pure, and a value rather than a thing that mutates: each drag hands back the tear as it
 * now stands, which is what makes it checkable without a screen.
 *
 * @param width   how far across the tear runs, in whatever units the screen is drawn in
 * @param seed    which pack this is, so its torn edge is its own and does not change under a
 *                cursor that has stopped moving
 * @param gripped whether a corner has been taken hold of yet
 * @param torn    how much of the way across, from nought to one
 */
public record PackTear(int width, long seed, boolean gripped, float torn) {

    /**
     * How much of the near end counts as the corner you can take hold of.
     * <p>A sixth. Generous, because a player who cannot find where to start is a player who
     * decides the ceremony is broken, and small enough that the middle of the pack is plainly
     * not it.
     */
    public static final float GRIP = 1f / 6f;

    /** How far a tear has to have got before the pack counts as open. */
    public static final float OPEN_AT = 0.92f;

    /** How deep the torn edge wanders, as a fraction of the strip it is torn along. */
    private static final float RAGGEDNESS = 0.7f;

    /**
     * How many times the edge wanders off and back across the whole tear.
     * <p>Twenty rather than the nine this started as. Nine excursions across a pack put each
     * one over a tenth of the width, which reads as a wave rather than as a tear: paper does
     * not undulate, it goes a short way, catches, and turns. The number that matters is how
     * long one excursion is against how deep it is, and at nine it was five times wider than
     * it was deep.
     */
    private static final int WANDERS = 20;

    /** How much finer the second wave is than the first, and the third than the second. */
    private static final int FINER = 3;

    /** How much of the wander each of the three waves is worth. They come to one. */
    private static final float SLOW = 0.46f;
    private static final float QUICK = 0.32f;
    private static final float TEETH = 0.22f;

    /** How far in from each corner the edge takes to reach its full wander. */
    private static final float SETTLES_OVER = 0.07f;

    public PackTear {
        width = Math.max(1, width);
        torn = Math.min(1f, Math.max(0f, torn));
    }

    /** A pack nobody has touched yet. */
    public static PackTear unopened(int width, long seed) {
        return new PackTear(width, seed, false, 0f);
    }

    /**
     * The tear after the cursor has moved to here.
     *
     * @param cursorX where the cursor is, measured from the near end of the tear
     */
    public PackTear followedTo(int cursorX) {
        float along = Math.min(1f, Math.max(0f, cursorX / (float) width));
        if (!gripped) {
            // Nothing until a corner has been taken hold of, and then the tear starts from
            // where the hand is rather than from nought, so taking hold does not itself tear.
            return along <= GRIP ? new PackTear(width, seed, true, along) : this;
        }
        return along <= torn ? this : new PackTear(width, seed, true, along);
    }

    /** Whether the wrapper has been torn far enough to come apart. */
    public boolean isOpen() {
        return torn >= OPEN_AT;
    }

    /** Whether anything has happened to this pack yet. */
    public boolean isUntouched() {
        return !gripped && torn <= 0f;
    }

    /** How far along the tear has got, in the same units as the width. */
    public int tornTo() {
        return Math.round(torn * width);
    }

    /**
     * The torn edge, as a height above the tear line at each step across.
     * <p>Given as offsets rather than as points so a caller can lay them along whatever line
     * it is drawing and in whatever direction, and so this owes nothing to a coordinate space
     * it cannot see.
     * <p>Ragged the same way every frame. The wander comes from the pack's own seed and the
     * step's number, not from a random draw taken while drawing - an edge that reshuffled
     * every frame would be a wrapper boiling rather than a wrapper torn.
     *
     * @param steps how many points to give back, at least two
     * @param depth how far the edge may wander either side of the line
     */
    public float[] edge(int steps, float depth) {
        int points = Math.max(2, steps);
        float[] offsets = new float[points];
        if (depth <= 0f) {
            return offsets;
        }
        for (int step = 1; step < points - 1; step++) {
            float along = step / (float) (points - 1);
            offsets[step] = wander(along) * depth * RAGGEDNESS * settled(along);
        }
        // The two ends are left at nought outright rather than by the curve: a tear starts at
        // the edge of the paper and finishes at the edge of the paper, and sine of pi is not
        // quite nought in floating point, which would leave the far end a hair off the line
        // for no reason anybody could see but a test could.
        return offsets;
    }

    /**
     * How much of its wander the edge has at this point along it.
     * <p>Nought at each corner and full everywhere else, over a short run in from the ends
     * rather than a curve across the whole width. A sine envelope was the obvious thing and
     * was wrong to look at: it put the tear's whole first quarter inside the ramp, so a pack
     * a quarter torn had an edge as straight as a ruler - which is the one thing a tear is
     * not.
     */
    private static float settled(float along) {
        float in = Math.min(1f, along / SETTLES_OVER);
        float out = Math.min(1f, (1f - along) / SETTLES_OVER);
        float edge = Math.min(in, out);
        return edge * edge * (3f - 2f * edge);
    }

    /**
     * How far the edge has wandered off the line at this point along it.
     * <p>Three waves rather than a fresh number per point. A number per point is noise, and
     * noise drawn as a line is a saw blade - which is what this was until somebody looked at
     * a picture of it. Paper tears in long excursions with smaller ones riding on them, and
     * then in small sharp teeth riding on those.
     * <p>The first two are smoothed, because a long excursion in paper is a curve. The third
     * is not: it turns a corner at every point, which is what a tooth is. Smoothing all three
     * was what made this read as a gentle wave instead of a tear - a torn edge is mostly
     * corners, and a curve has none.
     * <p>Measured along the tear rather than by step number, so the same pack tears the same
     * shape whether it is drawn at twenty points or two hundred.
     * <p>Deliberately not the deterministic stream the tables shuffle with. Nothing here
     * decides anything - it is the shape of a torn edge - and a draw that matters is a draw
     * that has to be reproducible from a session seed, which this must never be confused for.
     */
    private float wander(float along) {
        return waveAt(along, WANDERS, 0, true) * SLOW
                + waveAt(along, WANDERS * FINER, 977, true) * QUICK
                + waveAt(along, WANDERS * FINER * FINER, 5501, false) * TEETH;
    }

    /**
     * One wave of this many excursions across the tear.
     *
     * @param smoothed whether the line arrives and leaves each control point level, which is
     *     what a long excursion does, or turns a corner at it, which is what a tooth is
     */
    private float waveAt(float along, int excursions, int salt, boolean smoothed) {
        float at = along * excursions;
        int cell = (int) Math.floor(at);
        float across = at - cell;
        float eased = smoothed ? across * across * (3f - 2f * across) : across;
        float from = corner(cell + salt);
        float to = corner(cell + 1 + salt);
        return from + (to - from) * eased;
    }

    /** A number in {@code [-1, 1]} that is always the same for this pack and this corner. */
    private float corner(int index) {
        long mixed = seed * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 32;
        return ((mixed >>> 40) / (float) (1 << 24)) * 2f - 1f;
    }
}
