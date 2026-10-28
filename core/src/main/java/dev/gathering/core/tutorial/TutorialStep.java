package dev.gathering.core.tutorial;

import java.util.Locale;

/**
 * The six things the guided first game teaches, in order.
 * <p>Six, and no more. This teaches Gathering's controls, not Magic: somebody who has never
 * played Magic will not learn the game from it, and somebody who has played Magic for twenty
 * years still does not know that this table draws on the 2 key. The second of those is the
 * problem worth solving.
 * <p>Each step names the catalogue verb it is about, so the prompt can say which key that verb
 * is on <em>right now</em> rather than which key it shipped on. A player who rebound draw to Z
 * is told Z.
 * <p>Five of the six are watched for in confirmed game events - the server did it, and this
 * player was the one who did it. The sixth is reading a card, which moves nothing and so has
 * nothing to watch for on the server; it is satisfied by this client's own inspect panel
 * actually being open on a card the view already allowed. Those two kinds of evidence are kept
 * apart on purpose. See {@link TutorialProgress}.
 */
public enum TutorialStep {

    /** Take a card off the top of your library. */
    DRAW("draw", Evidence.THE_SERVER_SAID_SO),

    /** Put one of them on the table. */
    PLAY("play", Evidence.THE_SERVER_SAID_SO),

    /** Turn it sideways, which is what using a card looks like. */
    TAP("tap", Evidence.THE_SERVER_SAID_SO),

    /** Put a counter on it. */
    COUNT("add_counter", Evidence.THE_SERVER_SAID_SO),

    /**
     * Read a card that is already face up. Nothing moves.
     * <p>The one step with no catalogue verb. Reading is a key held rather than an action
     * asked for - it changes nothing, sends nothing and is answered entirely by this client -
     * so there is no id to name and the prompt says the read key instead.
     */
    READ(null, Evidence.THIS_CLIENT_SAW_IT),

    /** Say you are done, which is the one thing this table will never do for you. */
    PASS("pass_turn", Evidence.THE_SERVER_SAID_SO);

    /** What counts as having done a step. */
    public enum Evidence {

        /**
         * A confirmed game event, attributed to the learner, in the practice session.
         * <p>The only kind that may advance a step about the game. A packet having been sent
         * is not evidence of anything, and a step that advanced on the press would teach a
         * player that the press is what matters rather than the answer.
         */
        THE_SERVER_SAID_SO,

        /**
         * This client actually showed the player something.
         * <p>For reading a card, which mutates nothing: there is no event to wait for because
         * nothing happened to the game. What is checked is that the panel is open on a card
         * this player's own view already contained, which is why it cannot be a way to learn
         * anything hidden - the card was already theirs to look at.
         */
        THIS_CLIENT_SAW_IT
    }

    private final String action;
    private final Evidence evidence;
    private final String key;

    TutorialStep(String action, Evidence evidence) {
        this.action = action;
        this.evidence = evidence;
        this.key = "tutorial.gathering." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Which catalogue verb this step is about, so the prompt can name its current key.
     * <p>Null for {@link #READ}, which is a held key rather than a verb - the prompt names the
     * read binding directly. A null here also means no confirmed action can ever complete this
     * step, which is right: nothing about it reaches the server at all.
     */
    public String action() {
        return action;
    }

    /** What counts as having done it. */
    public Evidence evidence() {
        return evidence;
    }

    /** The key its instruction is written under. */
    public String key() {
        return key;
    }

    /** The key its one-line "why this matters" is written under. */
    public String whyKey() {
        return key + ".why";
    }

    /** The step after this one, or null for the last. */
    public TutorialStep next() {
        int at = ordinal() + 1;
        return at < values().length ? values()[at] : null;
    }

    /** The step before this one, or null for the first. */
    public TutorialStep previous() {
        int at = ordinal() - 1;
        return at >= 0 ? values()[at] : null;
    }

    /** How many steps there are, for "3 of 6". */
    public static int count() {
        return values().length;
    }
}
