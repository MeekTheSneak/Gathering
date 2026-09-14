package dev.gathering.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.PodLobby.PackRef;
import dev.gathering.core.draft.PodSettings.CardsGo;
import dev.gathering.core.draft.PodSettings.Kind;
import dev.gathering.core.draft.PodSettings.SetRule;
import dev.gathering.core.draft.PodSettings.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/**
 * A draft or sealed event before its packs open: who owes what, which packs count, and who
 * gets the cards afterwards.
 */
class PodLobbyTest {

    private static final UUID HOST = new UUID(1L, 0L);

    private static List<UUID> players(int count) {
        List<UUID> seated = new ArrayList<>();
        seated.add(HOST);
        for (int index = 1; index < count; index++) {
            seated.add(new UUID(1L, index));
        }
        return List.copyOf(seated);
    }

    private static PackRef pack(String set) {
        return new PackRef(set, "draft", "");
    }

    private static PodSettings settings(Kind kind, Source source, SetRule sets, int packs, CardsGo go) {
        return new PodSettings(kind, source, sets, packs, 0, go);
    }

    // ---------------------------------------------------------------- settings

    @Test
    void theUsualEventsNeedNothingChanged() {
        assertThat(PodSettings.usual(Kind.DRAFT).problem()).isEmpty();
        assertThat(PodSettings.usual(Kind.SEALED).problem()).isEmpty();
        assertThat(PodSettings.usual(Kind.DRAFT).packsEach()).isEqualTo(3);
        assertThat(PodSettings.usual(Kind.SEALED).packsEach()).isEqualTo(6);
    }

    @Test
    void aPickClockIsForADraftAndHasALimit() {
        assertThat(new PodSettings(Kind.DRAFT, Source.EACH_BRINGS, SetRule.ANY, 3, 0, CardsGo.PLAYERS_KEEP, 45).problem())
                .isEmpty();
        assertThat(new PodSettings(Kind.DRAFT, Source.EACH_BRINGS, SetRule.ANY, 3, 0, CardsGo.PLAYERS_KEEP, 301).problem())
                .contains("message.gathering.pod.pick_clock");
        assertThat(new PodSettings(Kind.DRAFT, Source.EACH_BRINGS, SetRule.ANY, 3, 0, CardsGo.PLAYERS_KEEP, -1).problem())
                .contains("message.gathering.pod.pick_clock");
        assertThat(new PodSettings(Kind.SEALED, Source.EACH_BRINGS, SetRule.ANY, 6, 0, CardsGo.PLAYERS_KEEP, 45).problem())
                .contains("message.gathering.pod.pick_clock");
    }

    /** A promise nobody can keep is refused where it is made. */
    @Test
    void settingsThatCannotBeKeptAreRefused() {
        assertThat(settings(Kind.DRAFT, Source.EACH_BRINGS, SetRule.ANY, 3, CardsGo.TO_SPONSOR).problem())
                .contains("message.gathering.pod.no_sponsor");
        assertThat(settings(Kind.DRAFT, Source.GENERATED, SetRule.oneSet("m21"), 3, CardsGo.TO_CONTRIBUTORS).problem())
                .contains("message.gathering.pod.generated_has_no_owner");
        assertThat(settings(Kind.DRAFT, Source.GENERATED, SetRule.ANY, 3, CardsGo.PLAYERS_KEEP).problem())
                .contains("message.gathering.pod.generated_needs_a_set");
        assertThat(settings(Kind.DRAFT, Source.EACH_BRINGS, SetRule.perPack(List.of("a", "b")), 3, CardsGo.PLAYERS_KEEP).problem())
                .contains("message.gathering.pod.set_per_pack");
        assertThat(new PodSettings(Kind.SEALED, Source.EACH_BRINGS, SetRule.ANY, 6, 2, CardsGo.PLAYERS_KEEP).problem())
                .contains("message.gathering.pod.sealed_has_no_picks");
        assertThat(settings(Kind.DRAFT, Source.EACH_BRINGS, SetRule.ANY, 0, CardsGo.PLAYERS_KEEP).problem())
                .contains("message.gathering.pod.packs_each");
    }

    // ---------------------------------------------------------------- putting packs in

    @Test
    void eachPlayerOwesTheirOwnPacksAndNoMore() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, PodSettings.usual(Kind.DRAFT));
        UUID bob = seated.get(1);

        assertThat(lobby.stillOwedBy(bob, seated)).isEqualTo(3);
        for (int index = 0; index < 3; index++) {
            lobby = lobby.with(bob, pack("m21"), seated);
        }
        assertThat(lobby.stillOwedBy(bob, seated)).isZero();
        assertThat(lobby.refusal(bob, pack("m21"), seated)).contains("message.gathering.pod.enough_packs");
        assertThat(lobby.refusal(new UUID(9L, 9L), pack("m21"), seated))
                .contains("message.gathering.pod.sit_down_first");
    }

    /** A set per pack takes pack one first, then pack two, and refuses them out of order. */
    @Test
    void aSetPerPackTakesEachPlayersPacksInOrder() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.DRAFT, Source.EACH_BRINGS,
                SetRule.perPack(List.of("znr", "znr", "khm")), 3, CardsGo.PLAYERS_KEEP));

        assertThat(lobby.refusal(HOST, pack("khm"), seated)).contains("message.gathering.pod.wrong_set");
        lobby = lobby.with(HOST, pack("znr"), seated);
        lobby = lobby.with(HOST, pack("ZNR"), seated);
        assertThat(lobby.refusal(HOST, pack("znr"), seated)).contains("message.gathering.pod.wrong_set");
        assertThat(lobby.refusal(HOST, pack("khm"), seated)).isEmpty();
    }

    /**
     * A sponsor fills every round of a set named twice.
     * <p>Counted against one round, the fifth pack of a set named for rounds one and two was
     * refused with four drafters, and the event could never start.
     */
    @Test
    void aSponsorCanFillBothRoundsOfASetNamedTwice() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.DRAFT, Source.SPONSORED,
                SetRule.perPack(List.of("znr", "znr", "khm")), 3, CardsGo.TO_SPONSOR));
        for (int index = 0; index < 8; index++) {
            assertThat(lobby.refusal(HOST, pack("znr"), seated)).as("znr pack " + index).isEmpty();
            lobby = lobby.with(HOST, pack("znr"), seated);
        }
        assertThat(lobby.refusal(HOST, pack("znr"), seated)).contains("message.gathering.pod.wrong_set");
        for (int index = 0; index < 4; index++) {
            lobby = lobby.with(HOST, pack("khm"), seated);
        }
        assertThat(lobby.notReady(seated)).isEmpty();

        PodLobby.Plan plan = lobby.plan(seated).orElseThrow();
        for (List<PodLobby.Entry> packs : plan.bySeat()) {
            assertThat(packs).extracting(entry -> entry.pack().setCode()).containsExactly("znr", "znr", "khm");
        }
        assertThat(plan.unused()).isEmpty();
    }

    @Test
    void onlyTheHostPutsInASponsoredEventsPacks() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.DRAFT, Source.SPONSORED, SetRule.ANY, 3, CardsGo.PLAYERS_KEEP));
        assertThat(lobby.refusal(seated.get(2), pack("m21"), seated))
                .contains("message.gathering.pod.host_brings_the_packs");
        assertThat(lobby.stillOwedBy(HOST, seated)).isEqualTo(12);
    }

    @Test
    void aGeneratedEventTakesNoPacks() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.DRAFT, Source.GENERATED,
                SetRule.oneSet("m21"), 3, CardsGo.PLAYERS_KEEP));
        assertThat(lobby.refusal(HOST, pack("m21"), seated)).contains("message.gathering.pod.no_packs_wanted");
        assertThat(lobby.notReady(seated)).isEmpty();
        assertThat(lobby.plan(seated).orElseThrow().bySeat().get(3))
                .extracting(entry -> entry.pack().setCode()).containsExactly("m21", "m21", "m21");
    }

    @Test
    void refusedPacksAreRefusedByWithToo() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.DRAFT, Source.SPONSORED, SetRule.ANY, 3, CardsGo.PLAYERS_KEEP));
        assertThatThrownBy(() -> lobby.with(seated.get(1), pack("m21"), seated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- starting

    @Test
    void aDraftNeedsARingAndSealedDoesNot() {
        PodLobby draft = PodLobby.open(HOST, settings(Kind.DRAFT, Source.GENERATED, SetRule.oneSet("m21"), 3, CardsGo.PLAYERS_KEEP));
        PodLobby sealed = PodLobby.open(HOST, settings(Kind.SEALED, Source.GENERATED, SetRule.oneSet("m21"), 6, CardsGo.PLAYERS_KEEP));
        assertThat(draft.notReady(players(3))).contains("message.gathering.pod.too_few");
        assertThat(sealed.notReady(players(1))).isEmpty();
        assertThat(draft.notReady(players(9))).contains("message.gathering.pod.too_many");
    }

    /**
     * Somebody who put packs in and then stood up gets them back unopened.
     * <p>The plan opens the packs of the people seated when the host starts, and nobody
     * else's - so what is left over is exactly what goes back.
     */
    @Test
    void packsFromSomebodyWhoStoodUpAreLeftOver() {
        List<UUID> five = players(5);
        PodLobby lobby = PodLobby.open(HOST, PodSettings.usual(Kind.DRAFT));
        for (UUID player : five) {
            for (int index = 0; index < 3; index++) {
                lobby = lobby.with(player, pack("m21"), five);
            }
        }
        List<UUID> four = five.subList(0, 4);
        PodLobby.Plan plan = lobby.plan(four).orElseThrow();
        assertThat(plan.bySeat()).hasSize(4);
        assertThat(plan.unused()).hasSize(3).allMatch(entry -> entry.contributor().equals(five.get(4)));
    }

    @Test
    void notEnoughPacksMeansNoPlan() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, PodSettings.usual(Kind.DRAFT)).with(HOST, pack("m21"), seated);
        assertThat(lobby.plan(seated)).isEmpty();
        assertThat(lobby.notReady(seated)).contains("message.gathering.pod.waiting_for_packs");
    }

    @Test
    void leavingTakesOnlyYourOwnPacksOut() {
        List<UUID> seated = players(4);
        PodLobby lobby = PodLobby.open(HOST, PodSettings.usual(Kind.DRAFT))
                .with(HOST, pack("m21"), seated)
                .with(seated.get(1), pack("m21"), seated);
        assertThat(lobby.without(HOST).entries()).singleElement()
                .extracting(PodLobby.Entry::contributor).isEqualTo(seated.get(1));
    }

    // ---------------------------------------------------------------- who gets the cards

    /**
     * Every card opened goes to exactly one person, whichever way the host chose.
     * <p>The property the whole event rests on: nothing is duplicated and nothing is lost,
     * however many players, packs and cards.
     */
    @Property(tries = 300)
    void everyOpenedCardGoesToExactlyOnePerson(
            @ForAll @IntRange(min = 4, max = 8) int playerCount,
            @ForAll @IntRange(min = 1, max = 4) int packs,
            @ForAll @IntRange(min = 0, max = 2) int policy,
            @ForAll boolean sponsored) {
        List<UUID> seated = players(playerCount);
        CardsGo go = CardsGo.values()[policy];
        Source source = sponsored ? Source.SPONSORED : Source.EACH_BRINGS;
        if (go == CardsGo.TO_SPONSOR && !sponsored) {
            go = CardsGo.PLAYERS_KEEP;
        }
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.SEALED, source, SetRule.ANY, packs, go));
        for (UUID player : seated) {
            UUID giving = sponsored ? HOST : player;
            for (int index = 0; index < packs; index++) {
                lobby = lobby.with(giving, pack("m21"), seated);
            }
        }
        PodLobby.Plan plan = lobby.plan(seated).orElseThrow();

        List<CardIdentity> everything = new ArrayList<>();
        List<List<List<CardIdentity>>> opened = new ArrayList<>();
        List<List<CardIdentity>> pools = new ArrayList<>();
        for (int seat = 0; seat < seated.size(); seat++) {
            List<List<CardIdentity>> seatPacks = new ArrayList<>();
            List<CardIdentity> pool = new ArrayList<>();
            for (int index = 0; index < packs; index++) {
                List<CardIdentity> cards = new ArrayList<>();
                for (int card = 0; card < 3; card++) {
                    cards.add(CardIdentity.ofPrinting(new UUID(seat * 100L + index, card)));
                }
                seatPacks.add(cards);
                pool.addAll(cards);
                everything.addAll(cards);
            }
            opened.add(seatPacks);
            pools.add(pool);
        }

        Map<UUID, List<CardIdentity>> owed = PodShares.owed(lobby, seated, plan, opened, pools);
        List<CardIdentity> handedOut = new ArrayList<>();
        owed.values().forEach(handedOut::addAll);
        assertThat(handedOut).containsExactlyInAnyOrderElementsOf(everything);
        assertThat(owed.values()).allMatch(cards -> !cards.isEmpty());
    }

    @Test
    void theCardsGoWhereTheHostSaid() {
        List<UUID> seated = players(4);
        UUID bob = seated.get(1);
        CardIdentity a = CardIdentity.ofPrinting(new UUID(5L, 1L));
        CardIdentity b = CardIdentity.ofPrinting(new UUID(5L, 2L));

        PodLobby keep = eachBringsOne(seated, CardsGo.PLAYERS_KEEP);
        PodLobby back = eachBringsOne(seated, CardsGo.TO_CONTRIBUTORS);
        PodLobby.Plan plan = keep.plan(seated).orElseThrow();
        // Bob opened a; Alice drafted it. Bob drafted b, which the host opened.
        List<List<List<CardIdentity>>> opened = List.of(
                List.of(List.of(b)), List.of(List.of(a)), List.of(List.<CardIdentity>of()), List.of(List.<CardIdentity>of()));
        List<List<CardIdentity>> pools = List.of(List.of(a), List.of(b), List.of(), List.of());

        assertThat(PodShares.owed(keep, seated, plan, opened, pools))
                .containsEntry(HOST, List.of(a)).containsEntry(bob, List.of(b));
        assertThat(PodShares.owed(back, seated, plan, opened, pools))
                .containsEntry(HOST, List.of(b)).containsEntry(bob, List.of(a));
    }

    private static PodLobby eachBringsOne(List<UUID> seated, CardsGo go) {
        PodLobby lobby = PodLobby.open(HOST, settings(Kind.SEALED, Source.EACH_BRINGS, SetRule.ANY, 1, go));
        for (UUID player : seated) {
            lobby = lobby.with(player, pack("m21"), seated);
        }
        return lobby;
    }
}
