package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The cards somebody is chasing. */
class WantsListTest {

    private static UUID card(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static final UUID BOLT = card("bolt");
    private static final UUID BEARS = card("bears");
    private static final UUID FOREST = card("forest");

    @Test
    @DisplayName("wanting a card puts it on the list")
    void wanting() {
        WantsList wants = WantsList.EMPTY.with(BOLT);

        assertThat(wants.wants(BOLT)).isTrue();
        assertThat(wants.wants(BEARS)).isFalse();
        assertThat(wants.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("wanting one twice does not list it twice, or move it")
    void wantingTwice() {
        WantsList wants = WantsList.EMPTY.with(BOLT).with(BEARS).with(BOLT);

        assertThat(wants.size()).isEqualTo(2);
        // The order is when each was first wanted, so a second press must not reorder it.
        assertThat(wants.printings()).containsExactly(BOLT, BEARS);
    }

    @Test
    @DisplayName("no longer wanting one takes it off and leaves the rest alone")
    void notWanting() {
        WantsList wants = WantsList.EMPTY.with(BOLT).with(BEARS).with(FOREST).without(BEARS);

        assertThat(wants.printings()).containsExactly(BOLT, FOREST);
    }

    @Test
    @DisplayName("taking off one that was never on changes nothing")
    void notWantingSomethingElse() {
        WantsList wants = WantsList.EMPTY.with(BOLT);

        assertThat(wants.without(BEARS)).isEqualTo(wants);
        assertThat(WantsList.EMPTY.without(BOLT)).isEqualTo(WantsList.EMPTY);
    }

    @Test
    @DisplayName("one button, both ways")
    void toggling() {
        WantsList on = WantsList.EMPTY.toggled(BOLT, true);
        WantsList off = on.toggled(BOLT, false);

        assertThat(on.wants(BOLT)).isTrue();
        assertThat(off.wants(BOLT)).isFalse();
    }

    @Test
    @DisplayName("a list fills up rather than growing forever")
    void bounded() {
        WantsList wants = WantsList.EMPTY;
        for (int index = 0; index < WantsList.MOST + 50; index++) {
            wants = wants.with(card("card-" + index));
        }

        assertThat(wants.size()).isEqualTo(WantsList.MOST);
        assertThat(wants.isFull()).isTrue();
        // And a full list takes nothing more rather than dropping what is on it.
        assertThat(wants.with(BOLT).wants(BOLT)).isFalse();
    }

    @Test
    @DisplayName("nothing and nulls are a list of nothing")
    void nothing() {
        assertThat(new WantsList(null)).isEqualTo(WantsList.EMPTY);
        assertThat(WantsList.EMPTY.isEmpty()).isTrue();
        assertThat(WantsList.EMPTY.with(null)).isEqualTo(WantsList.EMPTY);
        assertThat(WantsList.EMPTY.wants(null)).isFalse();
    }

    @Test
    @DisplayName("what is wanted and here, in the order it was wanted")
    void foundAmong() {
        WantsList wants = WantsList.EMPTY.with(BOLT).with(BEARS).with(FOREST);

        assertThat(wants.foundAmong(List.of(FOREST, BOLT))).containsExactly(BOLT, FOREST);
        assertThat(wants.foundAmong(List.of())).isEmpty();
        assertThat(wants.foundAmong(null)).isEmpty();
        assertThat(WantsList.EMPTY.foundAmong(List.of(BOLT))).isEmpty();
    }

    @Test
    @DisplayName("it survives a trip through the file it is kept in")
    void throughTheFile() {
        WantsList wants = WantsList.EMPTY.with(BOLT).with(BEARS);

        assertThat(WantsList.read(wants.lines())).isEqualTo(wants);
    }

    @Test
    @DisplayName("a file somebody edited by hand loses the bad lines and keeps the rest")
    void aFileEditedByHand() {
        List<String> lines = new ArrayList<>();
        lines.add("# cards I am after");
        lines.add("");
        lines.add("   " + BOLT + "   ");
        lines.add("not a card at all");
        lines.add(BEARS.toString());
        lines.add(null);

        assertThat(WantsList.read(lines).printings()).containsExactly(BOLT, BEARS);
        assertThat(WantsList.read(null)).isEqualTo(WantsList.EMPTY);
    }
}
