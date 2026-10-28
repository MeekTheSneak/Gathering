package dev.gathering.core.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Telling somebody where to get a card, only where it is true. */
class AcquisitionHintsTest {

    private static AcquisitionHints.Sources everything() {
        return new AcquisitionHints.Sources(true, true, true, true, true);
    }

    private static AcquisitionHints.Sources nothing() {
        return new AcquisitionHints.Sources(true, false, false, false, false);
    }

    @Test
    @DisplayName("never sends somebody to a shop that is turned off")
    void noShopHintWithoutAShop() {
        // The failure this prevents: advice that sends a player looking for something that is
        // not there, after which the mod looks broken rather than the advice looking wrong.
        List<String> hints = AcquisitionHints.forSources(nothing());
        assertThat(hints).noneMatch(hint -> AcquisitionHints.isAbout(hint, "shop"));
        assertThat(hints).noneMatch(hint -> AcquisitionHints.isAbout(hint, "loot"));
        assertThat(hints).noneMatch(hint -> AcquisitionHints.isAbout(hint, "village"));
        assertThat(hints).noneMatch(hint -> AcquisitionHints.isAbout(hint, "import"));
    }

    @Test
    @DisplayName("says every way that is actually open")
    void everySourceThatIsOn() {
        List<String> hints = AcquisitionHints.forSources(everything());
        assertThat(hints).anyMatch(hint -> AcquisitionHints.isAbout(hint, "shop"));
        assertThat(hints).anyMatch(hint -> AcquisitionHints.isAbout(hint, "loot"));
        assertThat(hints).anyMatch(hint -> AcquisitionHints.isAbout(hint, "village"));
        assertThat(hints).anyMatch(hint -> AcquisitionHints.isAbout(hint, "import"));
    }

    @Test
    @DisplayName("always answers, even when there is nothing to point at")
    void neverSilent() {
        // "No answer" and "no hint row at all" look identical to somebody wondering whether
        // the feature works.
        assertThat(AcquisitionHints.forSources(nothing())).isNotEmpty();
        assertThat(AcquisitionHints.forSources(null)).containsExactly(AcquisitionHints.NOTHING_KNOWN);
    }

    @Test
    @DisplayName("says collecting is off rather than listing shops that cannot help")
    void collectingOffShortCircuits() {
        AcquisitionHints.Sources off = new AcquisitionHints.Sources(false, true, true, false, true);
        List<String> hints = AcquisitionHints.forSources(off);
        assertThat(hints).contains(AcquisitionHints.COLLECTING_OFF);
        assertThat(hints).noneMatch(hint -> AcquisitionHints.isAbout(hint, "shop"));
        assertThat(hints).noneMatch(hint -> AcquisitionHints.isAbout(hint, "trade"));
    }

    @Test
    @DisplayName("still offers importing on a server with collecting off")
    void importSurvivesCollectingOff() {
        // The 'bring your own decks' server: no economy, but a decklist is exactly how you get
        // cards there, so it is the one hint that still means something.
        AcquisitionHints.Sources off = new AcquisitionHints.Sources(false, false, false, true, false);
        assertThat(AcquisitionHints.forSources(off))
                .anyMatch(hint -> AcquisitionHints.isAbout(hint, "import"));
    }

    @Test
    @DisplayName("offers trading whenever there are cards to trade")
    void tradingNeedsNoSetting() {
        assertThat(AcquisitionHints.forSources(nothing()))
                .anyMatch(hint -> AcquisitionHints.isAbout(hint, "trade"));
    }
}
