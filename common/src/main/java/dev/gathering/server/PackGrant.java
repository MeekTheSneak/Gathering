package dev.gathering.server;

import dev.gathering.core.sealed.MtgjsonProducts;
import dev.gathering.core.sealed.SealedProduct;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.service.CollationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts a sealed pack in somebody's hands, and says what a set really sold.
 * <p>Only products that existed. A set is asked what it was sold as, and a pack is granted
 * for one of the boosters on that list - so there is no way to be handed a Bloomburrow
 * Commander booster, because there is no such thing. That check is the whole point of reading
 * the product list at all, and doing it here means the loot and the shop inherit it rather
 * than each remembering to do it.
 * <p>The pack is a promise, so this hands over a wrapper and nothing else. What is inside is
 * decided when it is torn open.
 */
public final class PackGrant {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gathering");

    private PackGrant() {
    }

    /**
     * Gives one sealed pack of a set's product.
     *
     * @param kind which booster, or blank for the first the set publishes
     */
    public static void give(ServerPlayer player, String setCode, String kind) {
        // The same question the opening asks, asked before handing anybody a pack rather
        // than after: a booster granted on a server that is not collecting is a booster that
        // will refuse to open, and finding that out afterwards is worse than being told now.
        String refusal = PackOpening.whyNot();
        if (refusal != null) {
            player.sendSystemMessage(Component.translatable(refusal));
            return;
        }
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.pipeline_unavailable"));
            return;
        }
        String set = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        String wanted = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        // The archive is not a set and has no published product to read, so it is handed over
        // here rather than looked up. It is the one pack an admin cannot get any other way -
        // it is never sold, and finding one is the point.
        if (dev.gathering.item.PackComponent.ARCHIVE.equals(set)) {
            ItemStack archive = Archive.pack();
            dev.gathering.server.Handing.give(player, archive);
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.archive_given", Archive.size()));
            return;
        }

        collation.productsFor(set).whenComplete((reading, failure) -> player.server.execute(() -> {
            if (player.hasDisconnected()) {
                return;
            }
            if (failure != null) {
                LOGGER.warn("Reading what {} sold failed", set, failure);
                player.sendSystemMessage(Component.translatable(
                        "message.gathering.pack_failed", Failures.rootMessage(failure)));
                return;
            }
            SealedProduct product = boosterOf(reading, wanted);
            if (product == null) {
                player.sendSystemMessage(Component.translatable(
                        "message.gathering.sealed_no_such_pack", set,
                        wanted.isEmpty() ? "-" : wanted, describe(reading)));
                return;
            }
            ItemStack stack = PackItem.of(new PackComponent(
                    product.asBooster().setCode(), product.asBooster().kind()));
            dev.gathering.server.Handing.give(player, stack);
            player.sendSystemMessage(Component.translatable(
                    "message.gathering.sealed_given", product.name()));
        }));
    }

    /** Tells the player what a set was really sold as, product by product. */
    public static void list(ServerPlayer player, String setCode) {
        CollationService collation = CollationService.active().orElse(null);
        if (collation == null) {
            player.sendSystemMessage(Component.translatable("message.gathering.pipeline_unavailable"));
            return;
        }
        String set = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        player.sendSystemMessage(Component.translatable("message.gathering.sealed_reading", set));

        collation.productsFor(set).whenComplete((reading, failure) -> player.server.execute(() -> {
            if (player.hasDisconnected()) {
                return;
            }
            if (failure != null) {
                player.sendSystemMessage(Component.translatable(
                        "message.gathering.pack_failed", Failures.rootMessage(failure)));
                return;
            }
            if (reading.isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                        "message.gathering.sealed_none", set));
                return;
            }
            for (SealedProduct product : reading.products()) {
                player.sendSystemMessage(Component.translatable(
                        "message.gathering.sealed_product", product.name(), whatItIs(product)));
            }
        }));
    }

    /**
     * The booster product of a set matching this kind, or null.
     * <p>Blank takes the first the set publishes, which is what a player means by "a pack of
     * this set" for the many sets that only ever sold one.
     */
    public static SealedProduct boosterOf(MtgjsonProducts.Reading reading, String kind) {
        if (reading == null) {
            return null;
        }
        List<SealedProduct> boosters = reading.boosters();
        if (boosters.isEmpty()) {
            return null;
        }
        if (kind == null || kind.isBlank()) {
            return boosters.get(0);
        }
        String wanted = kind.trim().toLowerCase(Locale.ROOT);
        for (SealedProduct booster : boosters) {
            if (booster.asBooster().kind().equals(wanted)) {
                return booster;
            }
        }
        return null;
    }

    /** What kinds of booster a set does sell, for a refusal that helps. */
    public static String describe(MtgjsonProducts.Reading reading) {
        List<String> kinds = new ArrayList<>();
        if (reading != null) {
            for (SealedProduct booster : reading.boosters()) {
                kinds.add(booster.asBooster().kind());
            }
        }
        return kinds.isEmpty() ? "-" : String.join(", ", kinds);
    }

    /** One phrase saying what a product is, for a list somebody is reading in chat. */
    static String whatItIs(SealedProduct product) {
        if (product.isOneBooster()) {
            return product.asBooster().kind() + " booster";
        }
        if (product.holdsOtherProducts()) {
            return product.piecesHeld() + " inside";
        }
        return product.category().replace('_', ' ');
    }

}
