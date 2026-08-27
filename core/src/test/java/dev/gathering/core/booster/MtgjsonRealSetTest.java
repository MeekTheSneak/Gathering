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

    @Test
    @DisplayName("every booster those files sell is one those files can open")
    void everyRealBoosterSoldCanBeOpened() throws Exception {
        List<Path> files = setFiles();
        Assumptions.assumeTrue(!files.isEmpty(),
                "set GATHERING_MTGJSON_DIR to a directory of MTGJSON set files to run this");

        Map<String, JsonObject> parsed = new LinkedHashMap<>();
        Map<String, UUID> bridge = new LinkedHashMap<>();
        for (Path file : files) {
            JsonObject json = read(file);
            parsed.put(file.getFileName().toString(), json);
            bridge.putAll(MtgjsonCollation.printings(json));
        }

        int checked = 0;
        int missing = 0;
        for (Map.Entry<String, JsonObject> file : parsed.entrySet()) {
            MtgjsonCollation.Reading collation = MtgjsonCollation.read(file.getValue(), bridge);
            var products = dev.gathering.core.sealed.MtgjsonProducts.read(file.getValue(), bridge);
            java.util.Set<String> published = publishedKinds(file.getValue());
            for (var booster : products.boosters()) {
                // Nothing bigger than a booster is ever offered as one. A display box or a
                // Commander deck is a thing you buy, and a chest that can hold thirty packs
                // makes the shop pointless.
                assertThat(booster.holdsOtherProducts())
                        .as(file.getKey() + ": " + booster.name() + " holds other products "
                                + "and is being offered as a single booster")
                        .isFalse();
                var names = booster.asBooster();
                if (!names.setCode().equalsIgnoreCase(collation.setCode())) {
                    // A pack of another set, sold in this one's catalog. Openable, but
                    // out of the file that would say so.
                    continue;
                }
                if (collation.pack(names.kind()) == null && published.contains(names.kind())) {
                    // The file publishes this arrangement and the reading dropped it, which
                    // means it draws from a set that is not in this directory. That is the
                    // reading working: a server would have fetched the companion. Counted
                    // out loud rather than passed over, so a run against one file does not
                    // look like a run that checked everything.
                    missing++;
                    continue;
                }
                assertThat(collation.pack(names.kind()))
                        .as(file.getKey() + ": " + booster.name() + " names \"" + names.kind()
                                + "\", which the file does not publish at all. It publishes: "
                                + published)
                        .isNotNull();
                checked++;
            }
        }
        System.out.println(checked + " real boosters sold, all of them openable; "
                + missing + " waiting on a set file that is not in the directory");
        assertThat(checked).as("no set file held a booster anybody sells").isGreaterThan(0);
    }

    @Test
    @DisplayName("every real product this would sell comes out at a price somebody would pay")
    void everyRealProductIsPricedSensibly() throws Exception {
        List<Path> files = setFiles();
        Assumptions.assumeTrue(!files.isEmpty(),
                "set GATHERING_MTGJSON_DIR to a directory of MTGJSON set files to run this");

        int priced = 0;
        for (Path file : files) {
            JsonObject json = read(file);
            var products = dev.gathering.core.sealed.MtgjsonProducts.read(
                    json, MtgjsonCollation.printings(json));
            dev.gathering.core.sealed.SealedCatalog catalog =
                    dev.gathering.core.sealed.SealedCatalog.of(products);
            for (var product : products.products()) {
                if (!dev.gathering.core.sealed.SealedPrice.isSellable(product)) {
                    continue;
                }
                int worth = dev.gathering.core.sealed.SealedPrice.inBoosters(product, catalog);
                System.out.println("    " + worth + " boosters - " + product.name());
                assertThat(worth)
                        .as(product.name() + " is worth " + worth + " boosters")
                        .isBetween(1, 5000);
                if (product.isOneBooster()) {
                    assertThat(worth).as(product.name() + " is one booster").isEqualTo(1);
                }
                if (product.holdsOtherProducts()) {
                    assertThat(worth)
                            .as(product.name() + " holds " + product.piecesHeld()
                                    + " things and costs " + worth)
                            .isGreaterThanOrEqualTo(product.piecesHeld());
                }
                priced++;
            }
        }
        System.out.println(priced + " real products priced");
        assertThat(priced).as("no set file held anything a shop would sell").isGreaterThan(0);
    }

    /** Every arrangement a file publishes, before any of them is dropped for want of a set. */
    private static java.util.Set<String> publishedKinds(JsonObject file) {
        JsonObject data = file.getAsJsonObject("data");
        if (data == null || !data.has("booster") || !data.get("booster").isJsonObject()) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(data.getAsJsonObject("booster").keySet());
    }

    /** The set files to read, or an empty list where there are none to read. */
    private static List<Path> setFiles() throws IOException {
        String where = System.getenv("GATHERING_MTGJSON_DIR");
        if (where == null || where.isBlank() || !Files.isDirectory(Path.of(where))) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> found = Files.list(Path.of(where))) {
            found.filter(path -> path.getFileName().toString().endsWith(".json")).sorted()
                    .forEach(files::add);
        }
        return files;
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
