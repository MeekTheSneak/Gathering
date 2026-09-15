package dev.gathering.core.game.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gathering.core.game.SeatId;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Every reason somebody goes first has a sentence in the language file.
 * <p>The key is built from the reason's name, so the language check cannot see it: renaming
 * "lost the last game" left every second game's log line reading as its raw key.
 */
class StartingPlayerTextTest {

    @Test
    void everyReasonHasItsSentence() throws Exception {
        Path lang = Path.of("..", "common", "src", "main", "resources", "assets", "gathering", "lang", "en_us.json");
        if (!Files.exists(lang)) {
            lang = Path.of("common", "src", "main", "resources", "assets", "gathering", "lang", "en_us.json");
        }
        String text = Files.readString(lang);
        for (GameEvent.StartingPlayerChosen.Why why : GameEvent.StartingPlayerChosen.Why.values()) {
            String key = new GameEvent.StartingPlayerChosen(SeatId.of(0), why).describe(null).key();
            assertThat(text).as("the language file's entry for " + key).contains("\"" + key + "\":");
        }
    }
}
