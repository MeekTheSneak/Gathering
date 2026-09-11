package dev.gathering.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * What this client has asked for and not yet been told the answer to.
 * <p>Four states, and the difference between the last two is the whole reason this exists:
 *
 * <ul>
 *   <li><b>waiting</b> - it was sent. That is all it means. Nothing has moved.
 *   <li><b>confirmed</b> - the server said yes and the board already shows it.
 *   <li><b>refused</b> - the server said no, and said why.
 *   <li><b>unknown</b> - nothing came back in time. <em>This is not a refusal.</em>
 * </ul>
 *
 * <p>A request that times out has not failed; it has gone unanswered, which is a different
 * fact. The screen may say so, and a player may try again where trying again is harmless -
 * but nothing here resends anything by itself, because the one thing worse than a booster
 * that did not arrive is two of them.
 * <p>Keyed by the id the request carried, so an answer to somebody else's press cannot finish
 * this one. That rule already had to be learned twice: a builder waiting for its own Finish
 * accepted an untagged import result meant for a screen since closed, and closed itself as
 * though it had worked.
 * <p>Nothing here is authority. A confirmation is the server's, and this only remembers that
 * it arrived; a "waiting" cue may say that the press was received, and must never draw a card
 * as though it had moved.
 * <p>Client thread only.
 */
public final class PendingWork {

    /** How many outstanding requests to remember. Far past any real screen's worth. */
    private static final int MOST_REMEMBERED = 64;

    /** How a request ended, or that it has not. */
    public enum State {

        /** Sent. Nothing has happened yet, and saying otherwise would be a lie. */
        WAITING,

        /** The server did it. */
        CONFIRMED,

        /** The server refused, and there is a reason to show. */
        REFUSED,

        /**
         * Long enough has passed with no answer.
         * <p>Not a refusal. The request may yet be answered, may have been done and the reply
         * lost, or may never have arrived. A screen says which of those it does not know.
         */
        UNKNOWN
    }

    /** One request: when it went, how it ended, and what to say about it. */
    public record Work(UUID id, State state, long startedAt, Component said) {
    }

    private static final Map<UUID, Work> OUTSTANDING = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Work> eldest) {
            return size() > MOST_REMEMBERED;
        }
    };

    private PendingWork() {
    }

    /**
     * Notes that a request has gone out, and hands back the id it should carry.
     * <p>The id is minted here rather than by the caller so that there is exactly one way for
     * a request to be identified, and so a caller cannot forget.
     */
    public static UUID sent() {
        UUID id = UUID.randomUUID();
        OUTSTANDING.put(id, new Work(id, State.WAITING, now(), null));
        return id;
    }

    /**
     * The server did what this exact request asked.
     * <p>An id nothing is waiting for is ignored rather than recorded: a stale acknowledgment
     * has nothing to finish, and inventing an entry for it would be inventing a request.
     */
    public static void confirmed(UUID id) {
        finish(id, State.CONFIRMED, null);
    }

    /** The server refused this exact request, with a reason worth showing. */
    public static void refused(UUID id, Component why) {
        finish(id, State.REFUSED, why);
    }

    private static void finish(UUID id, State state, Component said) {
        if (id == null) {
            return;
        }
        Work had = OUTSTANDING.get(id);
        if (had == null) {
            return;
        }
        OUTSTANDING.put(id, new Work(id, state, had.startedAt(), said));
    }

    /** How this request stands, or empty if nothing is known about it. */
    public static Optional<Work> of(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        Work work = OUTSTANDING.get(id);
        if (work == null) {
            return Optional.empty();
        }
        if (work.state() == State.WAITING && overdue(work)) {
            // Not refused. Nothing has been decided; this client simply does not know, and a
            // screen that said "failed" here would be making that up.
            Work unknown = new Work(id, State.UNKNOWN, work.startedAt(), null);
            OUTSTANDING.put(id, unknown);
            return Optional.of(unknown);
        }
        return Optional.of(work);
    }

    /**
     * Whether this request has been out long enough that the screen should say so.
     * <p>The threshold is the player's, because how long is too long depends on the server
     * they are on: what is patience on somebody's home machine is a hang over a bad
     * connection.
     */
    public static boolean worthMentioning(UUID id) {
        Work work = id == null ? null : OUTSTANDING.get(id);
        return work != null && work.state() == State.WAITING && overdue(work);
    }

    private static boolean overdue(Work work) {
        return now() - work.startedAt() >= ClientSettings.waitingAfterMillis();
    }

    /**
     * What a screen should tell the player about a request, or nothing while there is nothing
     * to say.
     * <p>The policy, kept here rather than in the screens, because it is the same policy for
     * all of them and because a screen cannot be loaded in a test at all - a dedicated server
     * refuses every client class a screen is built from. Written like this, what a player is
     * told about a slow request is something that can be run.
     * <p>Silence while a request is merely young: a note that appears the instant a button is
     * pressed is a note nobody reads, and most answers arrive inside the threshold. Silence
     * again once it is answered, because the screen then has a real result to show and this
     * would be a second voice saying something vaguer.
     * <p>The one it does speak up about is the one the screens had no answer for: long enough
     * has passed that this client does not know. <b>It does not say failed.</b> The request may
     * be being worked on, may be done with its reply lost, or may never have arrived, and a
     * screen that guessed between those would be inventing the one fact the player needs.
     */
    public static Optional<Component> noteFor(UUID id) {
        Work work = of(id).orElse(null);
        if (work == null) {
            return Optional.empty();
        }
        return switch (work.state()) {
            case WAITING, CONFIRMED -> Optional.empty();
            case REFUSED -> Optional.of(work.said() == null
                    ? Component.translatable("screen.gathering.pending.refused")
                    : work.said());
            case UNKNOWN -> Optional.of(Component.translatable("screen.gathering.pending.no_answer"));
        };
    }

    /**
     * Forgets a request, for a screen that has closed.
     * <p>A screen that is gone has nowhere to show an answer, so keeping the entry would only
     * grow the map. What it does <em>not</em> do is cancel anything: the server is doing
     * whatever it was asked, and a durable piece of work like a booster being opened settles
     * through its own receipt rather than through anything on this client.
     */
    public static void forget(UUID id) {
        if (id != null) {
            OUTSTANDING.remove(id);
        }
    }

    /** Between worlds: one server's requests are not the next one's. */
    public static void clear() {
        OUTSTANDING.clear();
    }

    /** How many requests are remembered right now, which is what a test asks. */
    public static int remembered() {
        return OUTSTANDING.size();
    }

    /**
     * The monotonic clock, not the wall clock.
     * <p>A wall clock can go backwards - a time sync, a laptop waking up - and a request that
     * had been waiting a hundred milliseconds would then have been waiting minus four seconds.
     */
    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
