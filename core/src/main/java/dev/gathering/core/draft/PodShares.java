package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who ends up with which cards when a draft or sealed event is over.
 * <p>The one decision in an event that moves property, and so the one worth stating as a rule
 * that can be checked: every card opened goes to exactly one person, and nobody gets a card
 * nobody opened. The three ways the host can choose all satisfy that - they differ only in who
 * the one person is.
 * <p>Pure. What is handed over and how is the server's business; what is owed to whom is
 * decided here, from the plan the packs were opened by and what came out of them.
 */
public final class PodShares {

    private PodShares() {
    }

    /**
     * What each person is owed.
     *
     * @param lobby   the event as it was when the packs were opened
     * @param seated  the players, in the seat order the plan was made with
     * @param plan    which pack each seat opened
     * @param opened  per seat, per pack in the plan, the cards that came out of it
     * @param pools   per seat, what that player ends the event holding: their picks in a
     *                draft, everything they opened in sealed
     * @return per person, in the order they first appear, everything they are owed; nobody
     *         appears who is owed nothing
     */
    public static Map<UUID, List<CardIdentity>> owed(
            PodLobby lobby, List<UUID> seated, PodLobby.Plan plan,
            List<List<List<CardIdentity>>> opened, List<List<CardIdentity>> pools) {
        Map<UUID, List<CardIdentity>> owed = new LinkedHashMap<>();
        switch (lobby.settings().cardsGo()) {
            case PLAYERS_KEEP -> {
                for (int seat = 0; seat < seated.size(); seat++) {
                    add(owed, seated.get(seat), pools.get(seat));
                }
            }
            case TO_SPONSOR -> {
                for (List<CardIdentity> pool : pools) {
                    add(owed, lobby.host(), pool);
                }
            }
            case TO_CONTRIBUTORS -> {
                // By what went in rather than by who drafted what: nobody keeps anything, so
                // all that matters is that each contributor gets back exactly what their own
                // packs held, whoever those cards were passed to.
                for (int seat = 0; seat < plan.bySeat().size(); seat++) {
                    List<PodLobby.Entry> packs = plan.bySeat().get(seat);
                    for (int index = 0; index < packs.size(); index++) {
                        UUID contributor = packs.get(index).contributor();
                        add(owed, contributor == null ? lobby.host() : contributor,
                                opened.get(seat).get(index));
                    }
                }
            }
        }
        Map<UUID, List<CardIdentity>> sealed = new LinkedHashMap<>();
        owed.forEach((who, cards) -> sealed.put(who, List.copyOf(cards)));
        return java.util.Collections.unmodifiableMap(sealed);
    }

    private static void add(Map<UUID, List<CardIdentity>> owed, UUID who, List<CardIdentity> cards) {
        if (who == null || cards == null || cards.isEmpty()) {
            return;
        }
        owed.computeIfAbsent(who, ignored -> new ArrayList<>()).addAll(cards);
    }
}
