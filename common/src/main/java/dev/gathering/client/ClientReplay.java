package dev.gathering.client;

import dev.gathering.core.game.visibility.GameView;
import dev.gathering.network.ReplayFramePayload;
import dev.gathering.network.WatchReplayPayload;
import java.util.Optional;

/**
 * The finished game this client is currently watching, if any.
 *
 * <p>One at a time, because there is one screen. What is held is a single frame - the board at
 * one step - and nothing else: scrubbing does not fold anything here, it asks the server for a
 * different picture. That is the whole reason a replay can show hands and libraries without
 * handing a modified client the means to do the same to a live game.
 *
 * <p>Client-only.
 */
public final class ClientReplay {

    /**
     * How many client ticks one step takes while playing.
     *
     * <p>Four a second. A game's log is mostly small moves, so anything slower is a scrubber
     * somebody drags instead of watching, and anything faster is a blur.
     */
    private static final int TICKS_PER_STEP = 5;

    private static volatile String id;
    private static volatile GameView frame;
    private static volatile int step;
    private static volatile int steps;

    /** Whether it is running by itself, and how far through the current step it is. */
    private static boolean playing;
    private static int sinceStep;

    /**
     * The step this client has asked for and not yet been sent.
     *
     * <p>So a dragged scrubber sends one request per position rather than one per frame, and
     * so an answer that arrives out of order does not drag the scrubber backwards.
     */
    private static volatile int asked = -1;

    /**
     * How long a frame that was asked for has to arrive before the scrubber gives up on it.
     *
     * <p>Five seconds. The server refuses a frame with a chat line rather than a packet - a
     * replay that has been deleted, a server that switched replays off mid-session - and
     * without this the scrubber sat there having asked for a step that was never coming,
     * refusing to ask for it again and refusing to play, with the bar frozen and nothing
     * saying why.
     */
    private static final int WAITED_TOO_LONG = 100;

    private static int waiting;

    private ClientReplay() {
    }

    /** Begins watching. The screen opens when the first frame lands, not before. */
    public static void watch(String id) {
        ClientReplay.id = id;
        ClientReplay.frame = null;
        ClientReplay.step = 0;
        ClientReplay.steps = 0;
        ClientReplay.playing = false;
        ClientReplay.sinceStep = 0;
        ClientReplay.waiting = 0;
        // Cleared before asking, not after. What is outstanding is a step of the game that
        // was being watched a moment ago, and ask() refuses to send a request for the step it
        // is already waiting on - so picking a second game from the list before the first
        // one's opening frame arrived used to send nothing at all, and sit on the list screen
        // for ever with no way to tell that anything had gone wrong.
        ClientReplay.asked = -1;
        ask(0);
    }

    /** Takes a frame off the wire. Anything about a game we are no longer watching is dropped. */
    public static void accept(ReplayFramePayload payload) {
        if (id == null || !id.equals(payload.id())) {
            return;
        }
        GameView board;
        try {
            board = dev.gathering.core.game.persistence.ViewCodec.read(payload.view());
        } catch (java.io.IOException unreadable) {
            // A frame this client cannot read is one it must not draw a guess at.
            return;
        }
        // A step forward is a move somebody made, so it is drawn as one - the same cards
        // crossing the same felt they crossed on the night. A scrub is not: dragging from
        // turn two to turn nine would fling the entire board at once, which is a light show
        // rather than a game, so it simply cuts.
        boolean stepped = frame != null && payload.step() == step + 1;
        frame = board;
        step = payload.step();
        steps = payload.steps();
        if (stepped) {
            long now = ClientCardFlights.now();
            ClientCardFlights.arrived(TableScreen.replayTable(), board, now);
            ClientTableNews.arrived(TableScreen.replayTable(), board, now);
        } else {
            ClientCardFlights.forget(TableScreen.replayTable());
            ClientTableNews.forget(TableScreen.replayTable());
        }
        if (asked == payload.step()) {
            asked = -1;
            waiting = 0;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (!(client.screen instanceof TableScreen table && table.isReplay())) {
            client.setScreen(TableScreen.watching());
        }
    }

    public static Optional<GameView> frame() {
        return Optional.ofNullable(frame);
    }

    public static boolean watching() {
        return id != null;
    }

    public static int step() {
        return step;
    }

    public static int steps() {
        return steps;
    }

    public static boolean playing() {
        return playing;
    }

    public static void playPause() {
        // Pressing play at the end starts it again from the top, which is what the button
        // being there at all is offering to do.
        if (!playing && step >= steps && steps > 0) {
            ask(0);
        }
        playing = !playing;
        sinceStep = 0;
    }

    /** One client tick of a replay: the wait on the last frame, then playback if it is on. */
    public static void tick() {
        if (id == null) {
            return;
        }
        // Before the playing check, because a frame that is never coming has to be given up
        // on whether or not it was playback that asked for it. Scrubbing pauses, so a scrub
        // whose answer went missing would otherwise leave the bar refusing that step for
        // ever - and refusing it silently, since it is the same step the player is dragging
        // back to.
        if (asked >= 0 && ++waiting >= WAITED_TOO_LONG) {
            asked = -1;
            waiting = 0;
            playing = false;
        }
        if (!playing) {
            return;
        }
        if (step >= steps) {
            playing = false;
            return;
        }
        if (asked >= 0) {
            // Still waiting. Playing faster than the server answers would queue up requests
            // nobody is going to see.
            return;
        }
        if (++sinceStep < TICKS_PER_STEP) {
            return;
        }
        sinceStep = 0;
        ask(step + 1);
    }

    /** Moves to a step, clamped, and asks for it. Pauses, because scrubbing is a decision. */
    public static void scrubTo(int wanted) {
        playing = false;
        ask(Math.clamp(wanted, 0, Math.max(0, steps)));
    }

    public static void nudge(int by) {
        scrubTo(step + by);
    }

    /** Stops watching. Called when the screen goes, so a later frame is not acted on. */
    public static void stop() {
        ClientCardFlights.forget(TableScreen.replayTable());
        ClientTableNews.forget(TableScreen.replayTable());
        id = null;
        frame = null;
        step = 0;
        steps = 0;
        playing = false;
        asked = -1;
        waiting = 0;
    }

    private static void ask(int which) {
        if (id == null || which == asked) {
            return;
        }
        asked = which;
        waiting = 0;
        ClientNetworking.send(new WatchReplayPayload(id, which));
    }
}
