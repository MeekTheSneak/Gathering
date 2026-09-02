package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.booster.BoosterConfig;
import dev.gathering.core.booster.BoosterSheet;
import dev.gathering.core.booster.BoosterVariant;
import dev.gathering.core.booster.MtgjsonCollation;
import dev.gathering.core.booster.OpenedPack;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.platform.Platform;
import dev.gathering.server.PackOpening;
import dev.gathering.service.ServerSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Which pack gets opened, and whether one may be at all.
 * <p>The drawing itself is checked where it is pure. What is here is the part that needs a
 * server: the config gate, and the choosing between the several kinds of pack a real set
 * publishes - which is the only place a set could end up opening the wrong product.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackOpeningGameTest {

    private static final String FILE_NAME = "gathering-server.toml";

    @GameTest(template = "empty")
    public static void noPackOpensOnAPlayOnlyServer(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = false\n", () -> {
            String refusal = PackOpening.whyNot();
            if (refusal == null) {
                return "A server with collection off was willing to open a booster";
            }
            if (!"message.gathering.pack_collection_off".equals(refusal)) {
                return "The refusal was " + refusal;
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void packsOpenOnceCollectionIsOn(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = true\n", () -> {
            String refusal = PackOpening.whyNot();
            return refusal == null ? null : "Collection was on and a booster was still refused: "
                    + refusal;
        });
    }

    @GameTest(template = "empty")
    public static void theKindAskedForIsTheKindOpened(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = true\n", () -> {
            MtgjsonCollation.Reading reading = reading("draft", "collector");
            BoosterConfig chosen = PackOpening.pick(reading, "collector");
            return "tst:collector".equals(chosen == null ? null : chosen.id())
                    ? null
                    : "Asked for a collector pack and got " + (chosen == null ? "none" : chosen.id());
        });
    }

    @GameTest(template = "empty")
    public static void theServersOwnBoosterModelWinsWhenNobodySaid(GameTestHelper helper) {
        withConfig(helper, """
                [modes]
                collection_enabled = true
                [collection]
                booster_model = "collector"
                """, () -> {
            BoosterConfig chosen = PackOpening.pick(reading("draft", "collector"), "");
            return "tst:collector".equals(chosen == null ? null : chosen.id())
                    ? null
                    : "The configured booster model was ignored: "
                            + (chosen == null ? "none" : chosen.id());
        });
    }

    @GameTest(template = "empty")
    public static void aSetWithoutTheUsualPacksStillOpensSomething(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = true\n", () -> {
            // No play, draft or set booster anywhere - a real thing for older and stranger
            // products, and the case where a chain of preferences quietly gives up.
            BoosterConfig chosen = PackOpening.pick(reading("jumpstart"), "");
            if (chosen == null) {
                return "A set that publishes only a jumpstart pack opened nothing at all";
            }
            if (!"tst:jumpstart".equals(chosen.id())) {
                return "Opened " + chosen.id() + " from a set that only has a jumpstart pack";
            }
            return null;
        });
    }

    @GameTest(template = "empty")
    public static void aSetWithNoCollationOpensNothing(GameTestHelper helper) {
        withConfig(helper, "[modes]\ncollection_enabled = true\n", () -> {
            MtgjsonCollation.Reading nothing =
                    new MtgjsonCollation.Reading("tst", Map.of(), List.of(), List.of());
            return PackOpening.pick(nothing, "") == null
                    ? null
                    : "A set with no collation still produced a pack";
        });
    }

    @GameTest(template = "empty")
    public static void everyCardTheOpenerNamedIsHandedOver(GameTestHelper helper) {
        UUID first = UUID.nameUUIDFromBytes("first".getBytes(StandardCharsets.UTF_8));
        UUID second = UUID.nameUUIDFromBytes("second".getBytes(StandardCharsets.UTF_8));
        OpenedPack pack = new OpenedPack("tst:draft", List.of(
                CardIdentity.ofPrinting(first, false),
                CardIdentity.ofPrinting(first, false),
                CardIdentity.ofPrinting(second, true)));

        PackOpening.Delivery delivery =
                PackOpening.whatToGive(pack, List.of(metadata(first), metadata(second)));

        if (delivery.unnameable() != 0) {
            helper.fail(delivery.unnameable() + " cards went missing from a pack nothing was "
                    + "wrong with");
            return;
        }
        if (delivery.giving().size() != 3) {
            helper.fail("A three-card pack handed over " + delivery.giving().size());
            return;
        }
        // The same printing twice is two cards, and the foil is still the foil.
        if (!delivery.giving().get(0).equals(delivery.giving().get(1))) {
            helper.fail("A pack with the same card twice handed over two different ones");
            return;
        }
        if (!delivery.giving().get(2).foil()) {
            helper.fail("A foil came out of the pack without being foil");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCardNothingCanNameIsLeftOutAndCounted(GameTestHelper helper) {
        UUID known = UUID.nameUUIDFromBytes("known".getBytes(StandardCharsets.UTF_8));
        UUID nameless = UUID.nameUUIDFromBytes("nameless".getBytes(StandardCharsets.UTF_8));
        OpenedPack pack = new OpenedPack("tst:draft", List.of(
                CardIdentity.ofPrinting(known, false),
                CardIdentity.ofPrinting(nameless, false)));

        PackOpening.Delivery delivery = PackOpening.whatToGive(pack, List.of(metadata(known)));

        if (delivery.giving().size() != 1 || delivery.unnameable() != 1) {
            helper.fail("A pack with one unlookupable card handed over " + delivery.giving().size()
                    + " and counted " + delivery.unnameable() + " missing");
            return;
        }
        if (!delivery.giving().get(0).scryfallId().equals(known)) {
            helper.fail("The card handed over was not the one that could be named");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void askingForAKindASetDoesNotHaveSaysSoRatherThanBlamingTheSet(
            GameTestHelper helper) {
        MtgjsonCollation.Reading has = reading("draft");
        MtgjsonCollation.Reading hasNothing =
                new MtgjsonCollation.Reading("tst", Map.of(), List.of(), List.of());

        String wrongKind = PackOpening.whyNothingOpened(has, "collector");
        if (!"message.gathering.pack_no_such_kind".equals(wrongKind)) {
            helper.fail("Asking a set with boosters for a kind it lacks said " + wrongKind);
            return;
        }
        String noBoosters = PackOpening.whyNothingOpened(hasNothing, "collector");
        if (!"message.gathering.pack_no_collation".equals(noBoosters)) {
            helper.fail("A set with no boosters at all said " + noBoosters);
            return;
        }
        // Nobody named a kind, so there is no kind to blame.
        if (!"message.gathering.pack_no_collation".equals(PackOpening.whyNothingOpened(has, ""))) {
            helper.fail("Naming no kind still blamed the kind");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------- bits

    private static CardMetadata metadata(UUID id) {
        return new CardMetadata(
                id, id, "Something", "{1}", 1.0, "Artifact", "",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal",
                "tst", "Test Set", "1", Rarity.COMMON,
                false, true, true, false, false, List.of("paper"),
                Map.of(), Map.of(), "https://scryfall.com/card/tst/1");
    }


    private static MtgjsonCollation.Reading reading(String... kinds) {
        Map<String, BoosterConfig> packs = new LinkedHashMap<>();
        for (String kind : kinds) {
            packs.put(kind, config(kind));
        }
        return new MtgjsonCollation.Reading("tst", packs, List.of(), List.of());
    }

    private static BoosterConfig config(String kind) {
        Map<String, BoosterSheet> sheets = new LinkedHashMap<>();
        sheets.put("common", new BoosterSheet("common", false, false, false,
                Map.of(UUID.nameUUIDFromBytes(kind.getBytes(StandardCharsets.UTF_8)), 1)));
        return new BoosterConfig("tst", kind, sheets,
                List.of(new BoosterVariant("plain", 1, Map.of("common", 1))));
    }

    /** What a check found wrong, or null if it found nothing. */
    @FunctionalInterface
    private interface Check {
        String run();
    }

    private static void withConfig(GameTestHelper helper, String text, Check check) {
        Path file = Platform.get().configDirectory().resolve(FILE_NAME);
        String before = null;
        try {
            if (Files.isRegularFile(file)) {
                before = Files.readString(file, StandardCharsets.UTF_8);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
            ServerSettings.load(Platform.get());

            String wrong = check.run();
            if (wrong != null) {
                helper.fail(wrong);
                return;
            }
            helper.succeed();
        } catch (IOException couldNotWrite) {
            helper.fail("The config file could not be written: " + couldNotWrite);
        } finally {
            try {
                if (before == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.writeString(file, before, StandardCharsets.UTF_8);
                }
            } catch (IOException couldNotRestore) {
                // Nothing useful to do here; the next start writes a fresh one.
            }
            ServerSettings.load(Platform.get());
        }
    }
}
