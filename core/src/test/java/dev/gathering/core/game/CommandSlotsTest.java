package dev.gathering.core.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.visibility.SeatView;
import dev.gathering.core.game.visibility.ZoneView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandSlotsTest {

    private static final SeatId ME = SeatId.of(0);

    /**
     * A commander goes home to the slot it left, without anybody being asked.
     *
     * <p>A partner pair means one is usually out while the other is at home, so the empty
     * slot is the one the card came from. Sending it to the first slot regardless would put
     * two cards in one box and leave the other empty - which is the stack of two under a
     * single number that having two slots exists to prevent.
     */
    @Test
    void aCommanderGoesHomeToTheEmptySlot() {
        SeatView seat = seatWith(Map.of(Zone.COMMAND, 1, Zone.COMMAND_TWO, 0));
        assertThat(CommandSlots.homeFor(seat)).isEqualTo(Zone.COMMAND_TWO);

        SeatView other = seatWith(Map.of(Zone.COMMAND, 0, Zone.COMMAND_TWO, 1));
        assertThat(CommandSlots.homeFor(other)).isEqualTo(Zone.COMMAND);
    }

    @Test
    void withBothSlotsEmptyItTakesTheFirst() {
        assertThat(CommandSlots.homeFor(seatWith(Map.of(Zone.COMMAND, 0, Zone.COMMAND_TWO, 0))))
                .isEqualTo(Zone.COMMAND);
    }

    /**
     * A card has to land somewhere.
     *
     * <p>Both slots full is a third card being sent to the command zone, which is not a thing
     * the rules produce - and refusing to move it would be the mod inventing a rule, which it
     * does not do.
     */
    @Test
    void withBothSlotsFullItStillHasAnAnswer() {
        assertThat(CommandSlots.homeFor(seatWith(Map.of(Zone.COMMAND, 1, Zone.COMMAND_TWO, 1))))
                .isEqualTo(Zone.COMMAND);
    }

    @Test
    void aSeatThatIsNotThereStillHasAnAnswer() {
        assertThat(CommandSlots.homeFor(null)).isEqualTo(Zone.COMMAND);
    }

    /** A table with no command zone sends nothing there, but must not fall over if asked. */
    @Test
    void aSeatWithNoCommandSlotsAtAllTakesTheFirst() {
        assertThat(CommandSlots.homeFor(seatWith(Map.of()))).isEqualTo(Zone.COMMAND);
    }

    private static SeatView seatWith(Map<Zone, Integer> counts) {
        Map<Zone, ZoneView> zones = new LinkedHashMap<>();
        counts.forEach((zone, count) ->
                zones.put(zone, new ZoneView(ZoneRef.of(ME, zone), count, List.of())));
        return new SeatView(ME, null, null, 40, Map.of(), Map.of(), Map.of(), false, zones);
    }
}
