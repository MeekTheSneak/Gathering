package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.sealed.MtgjsonProducts;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.server.PackGrant;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Which packs a set can actually be asked for.
 * <p>The rule this exists to hold: a pack can only be granted for a booster the set really
 * sold. Bloomburrow Commander came as precons and its cards turn up in Bloomburrow collector
 * boosters, and there has never been a booster of it - so asking for one has to come back
 * with nothing rather than with a product nobody printed.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackGrantGameTest {

    @GameTest(template = "empty")
    public static void theKindAskedForIsTheProductGranted(GameTestHelper helper) {
        MtgjsonProducts.Reading sold = soldAs("play", "collector");

        SealedProduct chosen = PackGrant.boosterOf(sold, "collector");
        if (chosen == null || !"collector".equals(chosen.asBooster().kind())) {
            helper.fail("Asking for a collector booster gave "
                    + (chosen == null ? "nothing" : chosen.name()));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void namingNoKindTakesTheFirstTheSetSold(GameTestHelper helper) {
        SealedProduct chosen = PackGrant.boosterOf(soldAs("play", "collector"), "");

        if (chosen == null || !"play".equals(chosen.asBooster().kind())) {
            helper.fail("A set asked for a pack with no product named gave "
                    + (chosen == null ? "nothing" : chosen.name()));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBoosterTheSetNeverSoldIsRefused(GameTestHelper helper) {
        MtgjsonProducts.Reading sold = soldAs("play", "collector");

        if (PackGrant.boosterOf(sold, "draft") != null) {
            helper.fail("A set that never sold a draft booster handed one over");
            return;
        }
        // And the refusal says what it does sell, so nobody has to guess twice.
        String kinds = PackGrant.describe(sold);
        if (!kinds.contains("play") || !kinds.contains("collector")) {
            helper.fail("The refusal listed " + kinds);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSetNeverSoldInPacksHandsOverNothing(GameTestHelper helper) {
        // Bloomburrow Commander: precons only, and no wrapper has ever existed.
        MtgjsonProducts.Reading precons = new MtgjsonProducts.Reading("tst", List.of(
                new SealedProduct("deck-1", "Test Commander Deck", "tst", "deck", "commander",
                        100, null)), List.of());

        if (PackGrant.boosterOf(precons, "") != null) {
            helper.fail("A set sold only as precons offered a booster");
            return;
        }
        if (!"-".equals(PackGrant.describe(precons))) {
            helper.fail("A set with no boosters listed " + PackGrant.describe(precons));
            return;
        }
        if (PackGrant.boosterOf(null, "") != null) {
            helper.fail("A set nothing is known about offered a booster");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------- bits

    private static MtgjsonProducts.Reading soldAs(String... kinds) {
        List<SealedProduct> products = new java.util.ArrayList<>();
        for (String kind : kinds) {
            products.add(new SealedProduct(
                    "pack-" + kind, "Test " + kind + " Booster Pack", "tst", "booster_pack",
                    kind, 14,
                    new SealedProduct.Contents(
                            List.of(new SealedProduct.Booster("tst", kind)),
                            List.of(), List.of(), List.of(), List.of())));
        }
        return new MtgjsonProducts.Reading("tst", products, List.of());
    }
}
