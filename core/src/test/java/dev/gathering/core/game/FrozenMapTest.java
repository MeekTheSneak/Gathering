package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gathering.core.game.event.GameEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A state shares its maps with the next state instead of copying them, and is still a value.
 * <p>What sharing must not cost: an earlier state changing when a later one is made, a caller's
 * map reaching into a state, or anything handed out by a state being writable.
 */
class FrozenMapTest {

    @Test
    @DisplayName("an earlier state is unchanged by every state made after it")
    void earlierStatesStandStill() {
        GameSession session = GameFixtures.twoPlayerTable(20);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 3));
        GameState before = session.state();
        Map<CardInstanceId, CardInstance> cardsBefore = new LinkedHashMap<>(before.cards());
        Map<ZoneRef, List<CardInstanceId>> zonesBefore = new LinkedHashMap<>(before.zones());
        Map<SeatId, SeatState> seatsBefore = new LinkedHashMap<>(before.seatStates());

        List<CardInstanceId> hand = new ArrayList<>(before.contents(GameFixtures.ALICE, Zone.HAND));
        for (CardInstanceId card : hand) {
            session.submit(new GameEvent.CardMoved(GameFixtures.ALICE, card,
                    ZoneRef.of(GameFixtures.ALICE, Zone.BATTLEFIELD), Placement.at(4000, 4000)));
            session.submit(new GameEvent.CardTapSet(GameFixtures.ALICE, card, true));
        }
        session.submit(new GameEvent.LifeChanged(GameFixtures.ALICE, GameFixtures.ALICE, -3));

        assertThat(before.cards()).isEqualTo(cardsBefore);
        assertThat(before.zones()).isEqualTo(zonesBefore);
        assertThat(before.seatStates()).isEqualTo(seatsBefore);
        assertThat(session.state().cards()).isNotEqualTo(cardsBefore);
    }

    @Test
    @DisplayName("nothing a state hands out can be written to")
    void nothingIsWritable() {
        GameSession session = GameFixtures.twoPlayerTable(5);
        session.submit(new GameEvent.CardsDrawn(GameFixtures.ALICE, GameFixtures.ALICE, 1));
        GameState state = session.state();
        CardInstanceId any = state.cards().keySet().iterator().next();

        assertThatThrownBy(() -> state.cards().put(any, state.requireCard(any)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.cards().remove(any))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.cards().keySet().remove(any))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.cards().values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.cards().entrySet().iterator().next().setValue(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.zones().values().iterator().next().add(any))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.seatStates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a map a caller built is copied, so changing it afterwards changes no state")
    void callersMapsAreCopied() {
        GameState empty = GameState.empty(List.of(GameFixtures.ALICE), 20);
        LinkedHashMap<SeatId, SeatState> mine = new LinkedHashMap<>(empty.seatStates());
        LinkedHashMap<ZoneRef, List<CardInstanceId>> zones = new LinkedHashMap<>(empty.zones());
        GameState built = new GameState(empty.seats(), Map.of(), zones, mine, null, null,
                empty.turn(), 0, 0, 0, false);

        mine.clear();
        zones.clear();

        assertThat(built.seatStates()).hasSize(1);
        assertThat(built.zones()).isNotEmpty();
    }

    @Test
    @DisplayName("keeps the order maps were built in, which is the order states are written out")
    void orderIsKept() {
        LinkedHashMap<String, Integer> built = new LinkedHashMap<>();
        for (String key : List.of("z", "a", "m", "b")) {
            built.put(key, key.length());
        }
        assertThat(FrozenMap.adopt(built).keySet()).containsExactly("z", "a", "m", "b");
        assertThat(FrozenMap.of(built).keySet()).containsExactly("z", "a", "m", "b");
        Map<String, Integer> frozen = FrozenMap.of(built);
        assertThat(FrozenMap.of(frozen)).isSameAs(frozen);
        assertThat(frozen).isEqualTo(built).hasSameHashCodeAs(built);
    }
}
