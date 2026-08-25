package dev.gathering.core.booster;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads whatever real MTGJSON set files are sitting in a directory, if any are.
 *
 * <p>Off by default and skipped when the directory is not there, because the mod
 * redistributes nobody's card data and four megabytes of somebody else's file has no business
 * in this repository. It exists because the adapter next door is a claim about a schema
 * somebody else publishes, and the only honest way to check a claim like that is against the
 * real thing:
 *
 * <pre>
 * curl -O https://mtgjson.com/api/v5/DMU.json
 * GATHERING_MTGJSON_DIR=$PWD ./gradlew :core:test --tests '*MtgjsonRealSetTest'
 * </pre>
 *
 * <p>An environment variable rather than a system property because Gradle hands those to the
 * test JVM without the build having to be told to forward them.
 *
 * <p>What it asserts is what a wrong reading would break: every file reads without throwing,
 * every kind of pack that survives can be opened, and a pack comes out the size its own
 * arrangement says it should. The weight totals inside the files check themselves, loudly,
 * inside the reader.
 */
class MtgjsonRealSetTest {

    @Test
    @DisplayName("every real set file in the directory reads, and its packs open")
    void realSetFilesRead() throws Exception {
        String where = System.getenv("GATHERING_MTGJSON_DIR");
        Assumptions.assumeTrue(where != null && !where.isBlank(),
                "set GATHERING_MTGJSON_DIR to a directory of MTGJSON set files to run this");
        Path directory = Path.of(where);
        Assumptions.assumeTrue(Files.isDirectory(directory), where + " is not a directory");

        List<Path> files = new ArrayList<>();
        try (Stream<Path> found = Files.list(directory)) {
            found.filter(path -> path.getFileName().toString().endsWith(".json")).sorted()
                    .forEach(files::add);
        }
        Assumptions.assumeTrue(!files.isEmpty(), "no .json files in " + where);

        // Every file's printings, so a set whose packs reach into another set finds it here
        // exactly as a server would once it had fetched both.
        Map<String, JsonObject> parsed = new LinkedHashMap<>();
        Map<String, UUID> bridge = new LinkedHashMap<>();
        for (Path file : files) {
            JsonObject json = read(file);
            parsed.put(file.getFileName().toString(), json);
            bridge.putAll(MtgjsonCollation.printings(json));
        }

        int opened = 0;
        for (Map.Entry<String, JsonObject> file : parsed.entrySet()) {
            MtgjsonCollation.Reading reading =
                    MtgjsonCollation.read(file.getValue(), bridge);
            System.out.println(file.getKey() + " -> " + reading.packs().keySet()
                    + (reading.alsoNeeds().isEmpty() ? "" : " also needs " + reading.alsoNeeds()));
            for (String note : reading.notes()) {
                System.out.println("    " + note);
            }
            for (BoosterConfig config : reading.packs().values()) {
                assertThat(config.isUsable())
                        .as(config.id() + " survived the reading but cannot be opened")
                        .isTrue();
                for (int pack = 0; pack < 50; pack++) {
                    OpenedPack out = BoosterOpener.open(
                            config, seed(), config.id() + "#" + pack);
                    assertThat(out.size())
                            .as(config.id() + " opened " + out.size() + " cards")
                            .isBetween(1, 60);
                    opened++;
                }
            }
        }
        assertThat(opened).as("nothing was opened at all").isGreaterThan(0);
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static byte[] seed() {
        return "a seed that is not a secret".getBytes(StandardCharsets.UTF_8);
    }
}
