package dev.gathering.core.ui;

/**
 * The wrapper as a sheet of foil, simulated, so it tears where it is pulled.
 * <p>A scripted tear runs along a line somebody drew. This does not: the wrapper is a grid of points held
 * together by links, the player takes hold of it and pulls, and the links that are stretched too far give
 * way. Where it tears is wherever it was pulled hardest, which is the whole point - two people opening the
 * same pack do not get the same tear, and neither does the same person twice.
 * <p><b>Verlet integration with position constraints.</b> Each point remembers where it was last step, and
 * its velocity is the difference; a link is satisfied by moving its two ends toward each other, a few
 * times per step, until the sheet is roughly consistent. That is the whole solver. It is stable at large
 * timesteps, which a spring-force solver is not, and it needs no forces at all for the interesting part -
 * the tear is a link being removed, not a force being exceeded.
 * <p><b>Deterministic.</b> Fixed timestep, fixed iteration count, and what randomness there is comes from
 * the pack's own seed. The same pack pulled the same way tears the same way on every machine - which is
 * what lets any of this be checked at all, and what keeps a torn wrapper looking the same to two people
 * watching one screen.
 * <p>Pure: no window, no textures, no clock. The screen hands it how much time has passed and where the
 * hand is, and reads the points back out.
 */
public final class PackCloth {

    /**
     * How many points across the sheet and down it.
     * <p>Seventeen down, so the rows land on sixteenths and the crimp's edge is a row rather than a
     * rounding. At nineteen the seam fell at four and a half rows and rounded to five, which put the tear
     * a sixteenth of the way into the artwork - the owner could see it taking a slice of the pack with
     * the crimp.
     */
    public static final int ACROSS = 15;
    public static final int DOWN = 17;

    /** The fixed step the solver runs at, in seconds. Everything below is tuned to it. */
    private static final float STEP = 1f / 60f;

    /** How many steps may be run for one frame, so a stalled window does not run a minute of cloth. */
    private static final int MOST_STEPS = 4;

    /**
     * How many times each link is satisfied per step. More is stiffer.
     * <p>Eight rather than four: the crimp has to hold its shape as it comes away, or it peels like a
     * rubber sheet and the moment it lets go is lost in the wobble.
     */
    private static final int ITERATIONS = 8;

    /** Downward pull, in sheet-heights per second squared. */
    private static final float GRAVITY = 2.4f;

    /** How much speed survives each step: foil is light and does not swing for long. */
    private static final float KEEP = 0.986f;

    /**
     * How far a link may stretch before it gives way, as a multiple of its resting length.
     * <p>The seam gives and nothing else does, because a booster has a notch cut in it and that is what a
     * notch is. The foil itself is far stronger than the crimp: pull a booster anywhere and it opens along
     * the top, it does not come apart in your hands. Letting the sheet tear anywhere made opening a pack
     * feel like shredding paper - the strip came apart in pieces before it ever peeled off.
     * <p>Which <em>part</em> of the seam gives first is still entirely the hand's: pull at one corner and
     * it unzips from there, pull at the middle and it opens outward. The tear is yours; the line it
     * follows is the pack's, as it is on a real one.
     */
    private static final float TEARS_AT = 6f;
    private static final float SEAM_TEARS_AT = 1.42f;

    /**
     * How far the crimped strip reaches down the wrapper, which is where the picture says it does: the
     * wrapper is sixteen rows and the top four of them are the crimp.
     * <p>Taken from {@link PackWrapper} rather than written out again. It was the same two numbers
     * spelled twice, in the one place where the two drifting apart would tear the pack in the wrong
     * row - and nothing would have said so.
     */
    private static final float CRIMP = (float) PackWrapper.crimp();

    /**
     * How far a point may be from the hand and still be taken hold of.
     * <p>A hand takes hold of a piece of wrapper, not of one point of it. Holding a single point and
     * dragging pulls that point out through its own neighbours, which is a hole rather than a tear.
     */
    private static final float GRABS_WITHIN = 0.16f;

    /**
     * How much of the top seam has to be gone before the wrapper counts as open.
     * <p>Not all of it. The last corner of a booster's crimp usually stays attached and is pulled off
     * with the strip; waiting for every last link would be a player dragging at a wrapper that is plainly
     * already open.
     */
    private static final float OPEN_AT = 0.6f;

    private final float[] x;
    private final float[] y;
    private final float[] wasX;
    private final float[] wasY;
    private final boolean[] pinned;

    /** Links, four numbers each: one end, the other end, its resting length, and whether it is alive. */
    private final int[] linkA;
    private final int[] linkB;
    private final float[] linkRest;
    private final boolean[] linkAlive;
    private final int links;

    /**
     * Whether the link to the right of each point, and the one below it, is still holding.
     * <p>Kept beside the links rather than looked up among them: what is drawn asks this of every square
     * of the sheet every frame, and walking a thousand links to answer each of two hundred and fifty
     * squares is a quarter of a million comparisons a frame to draw one wrapper.
     */
    private final boolean[] rightAlive;
    private final boolean[] downAlive;

    /** How many links lay along the seam the strip tears off along, and how many of them are gone. */
    private final int seamLinks;
    private int seamTorn;

    /** The piece of wrapper the hand has hold of, and where each of its points sits within that grip. */
    private final boolean[] grabbed;
    private final float[] grabX;
    private final float[] grabY;
    private boolean holding;
    private float heldX;
    private float heldY;
    private float owed;

    /**
     * A wrapper, flat and whole.
     *
     * @param seed the pack's own, so its tear is its own and is the same every time it is torn
     */
    public PackCloth(long seed) {
        int points = ACROSS * DOWN;
        x = new float[points];
        y = new float[points];
        wasX = new float[points];
        wasY = new float[points];
        pinned = new boolean[points];

        for (int down = 0; down < DOWN; down++) {
            for (int across = 0; across < ACROSS; across++) {
                int at = down * ACROSS + across;
                // A hair of slack, from the pack's own seed, so no two wrappers are stretched identically
                // and a tear does not run dead straight down the middle of every one.
                float slack = wobble(seed, at) * 0.0016f;
                x[at] = across / (float) (ACROSS - 1) + slack;
                y[at] = down / (float) (DOWN - 1);
                wasX[at] = x[at];
                wasY[at] = y[at];
            }
        }
        // Only the crimp moves. Everything below the seam is held exactly where it is, because it is
        // not loose foil - it is a wrapper stretched tight over a block of cards, and a block of cards
        // does not billow. Letting the whole sheet flex made opening a pack feel like tearing up a piece
        // of paper: the body stretched and arched about, the tear wandered into the middle of the
        // artwork, and nothing about it read as a booster. Held, the only thing that can give is the
        // seam - so the strip peels off along the crimp, which is what opening a pack is.
        int seam = seamRow();
        for (int down = 0; down < DOWN; down++) {
            for (int across = 0; across < ACROSS; across++) {
                pinned[down * ACROSS + across] = down > seam;
            }
        }

        grabbed = new boolean[points];
        grabX = new float[points];
        grabY = new float[points];
        rightAlive = new boolean[points];
        downAlive = new boolean[points];
        int most = 2 * points + 2 * points;
        linkA = new int[most];
        linkB = new int[most];
        linkRest = new float[most];
        linkAlive = new boolean[most];
        int count = 0;
        int alongTheSeam = 0;
        int seamRow = seam;
        for (int down = 0; down < DOWN; down++) {
            for (int across = 0; across < ACROSS; across++) {
                int at = down * ACROSS + across;
                if (across + 1 < ACROSS) {
                    count = link(count, at, at + 1);
                    rightAlive[at] = true;
                }
                if (down + 1 < DOWN) {
                    count = link(count, at, at + ACROSS);
                    downAlive[at] = true;
                    // The row of links the strip hangs from. Counting them is how "how far across has it
                    // torn" is answered without anybody drawing a line for it to follow.
                    if (down == seamRow) {
                        alongTheSeam++;
                    }
                }
                // Diagonals, which are what stop a grid of squares folding flat under its own weight.
                if (across + 1 < ACROSS && down + 1 < DOWN) {
                    count = link(count, at, at + ACROSS + 1);
                    count = link(count, at + 1, at + ACROSS);
                }
            }
        }
        links = count;
        seamLinks = alongTheSeam;
    }

    /**
     * The row of links the crimped strip hangs from, which is where a pack is torn.
     * <p>The last row of the crimp, not the first row of the pack. A square of this sheet takes the
     * row of the picture above its lower edge, so the links between point row four and point row
     * five carry the <em>first orange row</em> of the wrapper - and tearing there took that row out
     * of the world along with the crimp. The pack came away a pixel short at the top, which is the
     * tear sitting one pixel too low.
     * <p>One row up, the square that goes is the bottom row of the crimp, which is the row a tear is
     * supposed to consume. The strip still comes off whole and the artwork underneath is untouched.
     */
    private static int seamRow() {
        return Math.max(0, crimpRows() - 1);
    }

    /** How many rows of this sheet the crimp covers. */
    private static int crimpRows() {
        return Math.round(CRIMP * (DOWN - 1));
    }

    private int link(int count, int a, int b) {
        linkA[count] = a;
        linkB[count] = b;
        linkRest[count] = distance(a, b);
        linkAlive[count] = true;
        return count + 1;
    }

    /** A number in {@code [-1, 1]} that is always the same for this pack and this point. */
    private static float wobble(long seed, int at) {
        long mixed = seed * 0x9E3779B97F4A7C15L + at * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 32;
        return (mixed >> 11) / (float) (1L << 52);
    }

    /** Takes hold of the piece of wrapper under here, if any of it is near enough to take hold of. */
    public boolean grab(float atX, float atY) {
        return grab(atX, atY, 1f);
    }

    /**
     * Takes hold of the wrapper here, on a pack this much taller than it is wide.
     * <p>The sheet runs nought to one both ways, and a pack is not square, so a reach measured in the
     * sheet was an ellipse on the screen - further down than across. Scaled by the pack's shape, it is
     * the circle round the cursor it was meant to be.
     *
     * @param tallness the pack's height over its width
     */
    public boolean grab(float atX, float atY, float tallness) {
        java.util.Arrays.fill(grabbed, false);
        holding = false;
        float reach = GRABS_WITHIN * GRABS_WITHIN;
        float down = tallness > 0f && Float.isFinite(tallness) ? tallness : 1f;
        for (int at = 0; at < x.length; at++) {
            if (pinned[at]) {
                continue;
            }
            float dx = x[at] - atX;
            float dy = (y[at] - atY) * down;
            if (dx * dx + dy * dy < reach) {
                grabbed[at] = true;
                grabX[at] = dx;
                grabY[at] = y[at] - atY;
                holding = true;
            }
        }
        if (holding) {
            touched = true;
        }
        heldX = atX;
        heldY = atY;
        return holding;
    }

    /** Drags whatever is held to here. Does nothing when nothing is held. */
    public void dragTo(float atX, float atY) {
        heldX = atX;
        heldY = atY;
    }

    /** Lets go. What was held falls or springs back, depending on what is left holding it. */
    public void letGo() {
        holding = false;
        java.util.Arrays.fill(grabbed, false);
    }

    public boolean isHeld() {
        return holding;
    }

    /**
     * Runs the sheet forward.
     *
     * @param seconds how long since the last time, which is clamped so a stalled window cannot run a
     *     minute of cloth in one frame and fling the wrapper off the screen
     */
    public void advance(float seconds) {
        owed += Math.max(0f, Math.min(seconds, STEP * MOST_STEPS));
        int steps = 0;
        while (owed >= STEP && steps < MOST_STEPS) {
            step();
            owed -= STEP;
            steps++;
        }
    }

    private void step() {
        for (int at = 0; at < x.length; at++) {
            if (pinned[at] || grabbed[at]) {
                continue;
            }
            float nextX = x[at] + (x[at] - wasX[at]) * KEEP;
            float nextY = y[at] + (y[at] - wasY[at]) * KEEP + GRAVITY * STEP * STEP;
            wasX[at] = x[at];
            wasY[at] = y[at];
            x[at] = nextX;
            y[at] = nextY;
        }
        for (int pass = 0; pass < ITERATIONS; pass++) {
            if (holding) {
                // The hand wins outright: the piece it has hold of is where it says it is, each point
                // keeping the place it had within the grip.
                for (int at = 0; at < x.length; at++) {
                    if (grabbed[at]) {
                        x[at] = heldX + grabX[at];
                        y[at] = heldY + grabY[at];
                    }
                }
            }
            satisfy();
        }
        letTheRestGo();
    }

    /** One pass over the links, moving each one's ends toward its resting length, and tearing what is over. */
    private void satisfy() {
        int seam = seamRow();
        for (int at = 0; at < links; at++) {
            if (!linkAlive[at]) {
                continue;
            }
            int a = linkA[at];
            int b = linkB[at];
            float dx = x[b] - x[a];
            float dy = y[b] - y[a];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length <= 1.0e-6f) {
                continue;
            }
            boolean alongTheSeam = b == a + ACROSS && a / ACROSS == seam;
            // Only the straight links of the seam are weak. Weakening the diagonals across it too - so the
            // strip would come away whole rather than hang from them - let one torn link weaken its
            // neighbors in turn, and the gentlest pull unzipped the whole wrapper. Once it is open the
            // screen stops drawing the strip, so what the diagonals hold is never seen.
            if (length > linkRest[at] * (alongTheSeam ? SEAM_TEARS_AT : TEARS_AT)) {
                linkAlive[at] = false;
                if (b == a + 1) {
                    rightAlive[a] = false;
                } else if (b == a + ACROSS) {
                    downAlive[a] = false;
                    // A link from the seam row down to the one below it is a piece of the tear itself.
                    if (alongTheSeam) {
                        seamTorn++;
                    }
                }
                continue;
            }
            float pull = (length - linkRest[at]) / length * 0.5f;
            float moveX = dx * pull;
            float moveY = dy * pull;
            boolean heldA = pinned[a] || grabbed[a];
            boolean heldB = pinned[b] || grabbed[b];
            if (heldA && heldB) {
                continue;
            }
            if (heldA) {
                x[b] -= moveX * 2f;
                y[b] -= moveY * 2f;
            } else if (heldB) {
                x[a] += moveX * 2f;
                y[a] += moveY * 2f;
            } else {
                x[a] += moveX;
                y[a] += moveY;
                x[b] -= moveX;
                y[b] -= moveY;
            }
        }
    }

    /** How much of the seam has given way, from nought to one. */
    public float torn() {
        return seamLinks == 0 ? 0f : Math.min(1f, seamTorn / (float) seamLinks);
    }

    /** Whether enough of it has come away that the pack is open. */
    public boolean isOpen() {
        return torn() >= OPEN_AT;
    }

    /**
     * Once most of the seam has gone, the rest of it goes at once - and the strip comes away whole.
     * <p>A crimp does not come off a booster one centimetre at a time and then hang by a corner: past the
     * point where it is plainly off, it is off. Leaving the last few links to be worried apart is what
     * made the moment it comes away unsatisfying - there was no moment, only a gradual giving up.
     * <p>Every link across the seam, not only the straight ones. The two diagonals of each column cross
     * it too, and leaving them meant the strip hung off the pack by twenty-eight threads: the owner
     * asked for the whole top to come off as one solid piece. Cut here, at the moment it opens, rather
     * than weakened from the start - weak diagonals let the gentlest pull unzip the whole wrapper.
     */
    private void letTheRestGo() {
        if (torn() < OPEN_AT) {
            return;
        }
        int seam = seamRow();
        for (int at = 0; at < links; at++) {
            if (!linkAlive[at]) {
                continue;
            }
            int a = linkA[at];
            int b = linkB[at];
            boolean straight = b == a + ACROSS && a / ACROSS == seam;
            boolean crossing = a / ACROSS == seam && b / ACROSS == seam + 1;
            if (!straight && !crossing) {
                continue;
            }
            linkAlive[at] = false;
            if (b == a + ACROSS) {
                downAlive[a] = false;
                if (straight) {
                    seamTorn++;
                }
            }
        }
    }

    /** How many links still join the torn strip to the pack, which past opening is none. For the tests. */
    public int linksAcrossTheSeam() {
        int seam = seamRow();
        int joined = 0;
        for (int at = 0; at < links; at++) {
            if (linkAlive[at] && linkA[at] / ACROSS == seam && linkB[at] / ACROSS == seam + 1) {
                joined++;
            }
        }
        return joined;
    }

    /** Whether anything has happened to it yet. */
    public boolean isUntouched() {
        // Touched means taken hold of, ever - not only torn or held right now. A wrapper pulled and
        // let go without tearing read as untouched, so a resize at that moment threw its creases
        // away and put the "take hold" prompt back over a pack the player had already been pulling.
        return !touched && seamTorn == 0 && !holding;
    }

    /** Whether this wrapper has ever been taken hold of. */
    private boolean touched;

    public float xOf(int at) {
        return x[at];
    }

    public float yOf(int at) {
        return y[at];
    }

    /** Whether this square of the sheet still has all four of its sides, and so is worth drawing. */
    public boolean stillThere(int across, int down) {
        if (across + 1 >= ACROSS || down + 1 >= DOWN) {
            return false;
        }
        int corner = at(across, down);
        return rightAlive[corner] && downAlive[corner]
                && downAlive[corner + 1] && rightAlive[corner + ACROSS];
    }

    public static int at(int across, int down) {
        return down * ACROSS + across;
    }

    /** How many links are still holding, for a test that wants to watch a sheet come apart. */
    public int linksLeft() {
        int alive = 0;
        for (int at = 0; at < links; at++) {
            if (linkAlive[at]) {
                alive++;
            }
        }
        return alive;
    }

    private float distance(int a, int b) {
        float dx = x[b] - x[a];
        float dy = y[b] - y[a];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
