package dev.gathering.core.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The order the formats come out in, which a player learns and should not have to relearn. */
class FormatPresetsTest {

    @Test
    @DisplayName("the formats come out in the order they are written, not in a hash order")
    void theOrderIsTheOneWeChose() {
        // They were indexed into a Map.copyOf, whose iteration order is unspecified and comes
        // out of a hash salted once per launch - so the buttons on the setup screen were in a
        // different order every time the game started and nobody could learn where Commander
        // was. The order is a decision: the ones most tables play, first.
        assertThat(FormatPresets.all())
                .extracting(FormatPreset::id)
                .containsExactly("commander", "oathbreaker", "standard", "pioneer",
                        "modern", "legacy", "vintage", "pauper", "limited");
    }

    @Test
    @DisplayName("asking twice gives the same order")
    void askingTwiceIsTheSame() {
        List<String> once = FormatPresets.all().stream().map(FormatPreset::id).toList();
        List<String> twice = FormatPresets.all().stream().map(FormatPreset::id).toList();
        assertThat(twice).isEqualTo(once);
    }
}
