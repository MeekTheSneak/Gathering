package dev.gathering.core.tournament;

import dev.gathering.core.draft.PodSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * What a host has chosen so far for a tournament that does not exist yet.
 * <p>The create screen used to keep each choice in a field of its own and rebuild its widgets after
 * every press, which meant a choice could only survive as long as that one screen object did - and
 * a subscreen replaces the screen. Every choice lives here instead, so the screen can hand the whole
 * draft to a subscreen, take it back changed, and be rebuilt or reopened around it without losing
 * anything.
 * <p>Pure: no window, no block, no items. The prizes are slots rather than stacks for that reason,
 * and because there is nowhere to keep an item before the event exists. See {@link PrizeOffer}.
 *
 * @param name         what the host typed, before it is cleaned
 * @param kind         draft, sealed or constructed
 * @param formatId     the constructed format; kept while a limited kind is chosen, so going back to
 *                     constructed finds the format the host had picked
 * @param bestOf       games in a match
 * @param roundMinutes how long a round runs
 * @param buildMinutes how long players have to build after a draft or sealed opening
 * @param rounds       Swiss rounds, or 0 for the player count to decide
 * @param topCut       {@link EventSettings#AUTO_CUT}, 0, 4 or 8
 * @param decks        how constructed decks are registered
 * @param largeEvent   whether players check in
 * @param pod          how a limited event gets its packs; kept even while constructed is chosen
 * @param prizes       the prizes put up, in the order they were put up
 */
public record EventDraft(
        String name, EventSettings.Kind kind, String formatId, int bestOf, int roundMinutes, int buildMinutes,
        int rounds, int topCut, EventSettings.DeckRegistration decks, boolean largeEvent, PodSettings pod,
        List<PrizeOffer> prizes) {

    public EventDraft {
        name = name == null ? "" : name;
        kind = kind == null ? EventSettings.Kind.CONSTRUCTED : kind;
        formatId = formatId == null ? "" : formatId;
        decks = decks == null ? EventSettings.DeckRegistration.LOCKED : decks;
        pod = pod == null ? PodSettings.usual(PodSettings.Kind.DRAFT) : pod;
        prizes = prizes == null ? List.of() : List.copyOf(prizes);
    }

    /** A tournament nobody has chosen anything about yet, in this constructed format. */
    public static EventDraft blank(String formatId) {
        return new EventDraft("", EventSettings.Kind.CONSTRUCTED, formatId, 3, EventSettings.USUAL_ROUND_MINUTES,
                EventSettings.usualBuildMinutes(EventSettings.Kind.CONSTRUCTED), 0, EventSettings.AUTO_CUT,
                EventSettings.DeckRegistration.LOCKED, false, PodSettings.usual(PodSettings.Kind.DRAFT), List.of());
    }

    public EventDraft withName(String typed) {
        return new EventDraft(typed, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, topCut, decks,
                largeEvent, pod, prizes);
    }

    /**
     * The kind chosen.
     * <p>Choosing the kind that is already chosen changes nothing at all. It used to put the pack
     * settings back to their defaults, so a host who set up their packs and then pressed Draft again
     * - or pressed it on the way back from any other screen that rebuilt the row - lost every one of
     * those choices without being told.
     * <p>A real change carries what still applies across: the build clock follows the kind while the
     * host has left it where the kind put it, and stays where they put it once they have not, and the
     * pack settings keep the host's source, sets and clock rather than starting again.
     */
    public EventDraft withKind(EventSettings.Kind wanted) {
        if (wanted == null || wanted == kind) {
            return this;
        }
        int build = buildMinutes == EventSettings.usualBuildMinutes(kind)
                ? EventSettings.usualBuildMinutes(wanted) : buildMinutes;
        return new EventDraft(name, wanted, formatId, bestOf, roundMinutes, build, rounds, topCut, decks, largeEvent,
                podFor(wanted, pod), prizes);
    }

    public EventDraft withFormat(String wanted) {
        return new EventDraft(name, kind, wanted, bestOf, roundMinutes, buildMinutes, rounds, topCut, decks,
                largeEvent, pod, prizes);
    }

    public EventDraft withBestOf(int games) {
        return new EventDraft(name, kind, formatId, games, roundMinutes, buildMinutes, rounds, topCut, decks,
                largeEvent, pod, prizes);
    }

    public EventDraft withRoundMinutes(int minutes) {
        return new EventDraft(name, kind, formatId, bestOf, minutes, buildMinutes, rounds, topCut, decks, largeEvent,
                pod, prizes);
    }

    public EventDraft withBuildMinutes(int minutes) {
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, minutes, rounds, topCut, decks, largeEvent,
                pod, prizes);
    }

    public EventDraft withRounds(int swiss) {
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, swiss, topCut, decks,
                largeEvent, pod, prizes);
    }

    public EventDraft withTopCut(int cut) {
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, cut, decks, largeEvent,
                pod, prizes);
    }

    public EventDraft withDecks(EventSettings.DeckRegistration registration) {
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, topCut, registration,
                largeEvent, pod, prizes);
    }

    public EventDraft withLargeEvent(boolean checkIn) {
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, topCut, decks, checkIn,
                pod, prizes);
    }

    /** The pack settings a subscreen handed back, lined up with the kind chosen here. */
    public EventDraft withPod(PodSettings chosen) {
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, topCut, decks,
                largeEvent, podFor(kind, chosen == null ? pod : chosen), prizes);
    }

    /** Another prize put up. An unacceptable one, or a second for a slot already promised, changes nothing. */
    public EventDraft withPrize(PrizeOffer offer) {
        List<PrizeOffer> wanted = new ArrayList<>(prizes);
        wanted.add(offer);
        List<PrizeOffer> kept = PrizeOffer.accepted(wanted);
        return kept.size() == prizes.size() ? this
                : new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, topCut, decks,
                        largeEvent, pod, kept);
    }

    /** One prize taken back down, by where it is in the list. */
    public EventDraft withoutPrize(int index) {
        if (index < 0 || index >= prizes.size()) {
            return this;
        }
        List<PrizeOffer> left = new ArrayList<>(prizes);
        left.remove(index);
        return new EventDraft(name, kind, formatId, bestOf, roundMinutes, buildMinutes, rounds, topCut, decks,
                largeEvent, pod, left);
    }

    /** The settings this draft would create, as the server will read them. */
    public EventSettings settings() {
        return new EventSettings(kind, kind.isLimited() ? "" : formatId, kind.isLimited() ? podFor(kind, pod) : null,
                bestOf, roundMinutes, buildMinutes, EventSettings.USUAL_EXTRA_TURNS, rounds, topCut, decks, largeEvent);
    }

    /**
     * The pack settings, with their draft or sealed matching the event's and every other choice the
     * host made kept.
     * <p>Sealed has no picks to take two at a time, and a sealed pool is a different number of packs
     * from a draft's, so those two are the kind's to say. Where the packs come from, which sets they
     * are and where the cards end up are the host's, and survive the switch.
     */
    private static PodSettings podFor(EventSettings.Kind kind, PodSettings chosen) {
        PodSettings.Kind wanted = kind == EventSettings.Kind.SEALED ? PodSettings.Kind.SEALED : PodSettings.Kind.DRAFT;
        if (chosen.kind() == wanted) {
            return chosen;
        }
        return new PodSettings(wanted, chosen.source(), chosen.sets(),
                wanted == PodSettings.Kind.SEALED ? PodSettings.USUAL_SEALED_PACKS : PodSettings.USUAL_DRAFT_PACKS,
                0, chosen.cardsGo(), wanted == PodSettings.Kind.SEALED ? 0 : chosen.pickSeconds());
    }
}
