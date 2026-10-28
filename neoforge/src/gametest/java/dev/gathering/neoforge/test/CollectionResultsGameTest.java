package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.item.CardComponent;
import dev.gathering.item.CardItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.CollectionPagePayload;
import dev.gathering.network.CollectionQuery;
import dev.gathering.server.CollectionView;
import dev.gathering.service.CardDataService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Turning a page of a collection reads the answer back rather than searching again - and
 * never reads back an answer something has since changed.
 * <p>The saving is the easy half: one search where there were three. Every other test here is
 * about the half that makes it safe, one test per thing a search is built from: the box's
 * counts, the player's pockets, what the card cache knows, the question, and who is asking. A
 * cache that got any of those wrong would show a card that had gone, hide one that had arrived,
 * or show one player's pockets to another.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionResultsGameTest {

    /** Rows per page, small enough that a few dozen cards make several pages. */
    private static final int PER_PAGE = 10;

    private static final int CARDS = 30;

    private static BlockPos box(GameTestHelper helper) {
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(where, GatheringContent.COLLECTION.get().defaultBlockState(), 3);
        return where;
    }

    private static CollectionBlockEntity entity(GameTestHelper helper, BlockPos where) {
        if (helper.getLevel().getBlockEntity(where) instanceof CollectionBlockEntity box) {
            return box;
        }
        throw new AssertionError("no collection at " + where);
    }

    /** A box holding this many different cards nobody has looked up. */
    private static List<CardIdentity> filled(GameTestHelper helper, BlockPos where, int howMany) {
        CollectionBlockEntity box = entity(helper, where);
        List<CardIdentity> cards = new java.util.ArrayList<>();
        for (int one = 0; one < howMany; one++) {
            CardIdentity card = CardIdentity.ofPrinting(UUID.randomUUID());
            box.put(card, 1);
            cards.add(card);
        }
        return cards;
    }

    private static ServerPlayer standingAt(GameTestHelper helper, BlockPos where) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setPos(where.getX() + 0.5, where.getY(), where.getZ() + 0.5);
        CollectionView.forget(player.getUUID());
        return player;
    }

    private static CollectionPagePayload page(ServerPlayer player, BlockPos where,
            CollectionQuery query, int page, boolean pockets) {
        CollectionPagePayload answer = CollectionView.pageFor(
                player, where, query, false, page, PER_PAGE, pockets, 0);
        if (answer == null) {
            throw new AssertionError("the player was not at the collection");
        }
        return answer;
    }

    private static CollectionPagePayload page(ServerPlayer player, BlockPos where, int page) {
        return page(player, where, CollectionQuery.EVERYTHING, page, false);
    }

    /** Every page of an answer from one search, and together they are the whole answer. */
    @GameTest(template = "empty")
    public static void turningapagedoesnotsearchagain(GameTestHelper helper) {
        BlockPos where = box(helper);
        filled(helper, where, CARDS);
        ServerPlayer player = standingAt(helper, where);

        CollectionView.forgetTheCount();
        Set<CardComponent> seen = new HashSet<>();
        int pages = page(player, where, 0).pages();
        for (int showing = 0; showing < pages; showing++) {
            for (CollectionPagePayload.Row row : page(player, where, showing).rows()) {
                if (!seen.add(row.card())) {
                    helper.fail("page " + showing + " repeated a card an earlier page showed");
                    return;
                }
            }
        }
        if (seen.size() != CARDS) {
            helper.fail("the pages together showed " + seen.size() + " cards of " + CARDS);
            return;
        }
        if (CollectionView.orderingsBuilt() != 1) {
            helper.fail("paging through " + pages + " pages searched "
                    + CollectionView.orderingsBuilt() + " times; one search answers all of them");
            return;
        }
        helper.succeed();
    }

    /** A card taken out through the real take path is gone from the very next page. */
    @GameTest(template = "empty")
    public static void acardtakenisgonefromthenextpage(GameTestHelper helper) {
        BlockPos where = box(helper);
        List<CardIdentity> cards = filled(helper, where, CARDS);
        ServerPlayer player = standingAt(helper, where);
        entity(helper, where).claimFor(player.getUUID());

        int before = page(player, where, 0).counts().total();
        int took = CollectionView.take(player, where, CardComponent.of(cards.get(0)), 1);
        if (took != 1) {
            helper.fail("the fixture could not take a card: took " + took);
            return;
        }
        CollectionPagePayload after = page(player, where, 1);
        if (after.counts().total() != before - 1 || after.counts().matched() != CARDS - 1) {
            helper.fail("after taking a card the next page still counted "
                    + after.counts().total() + " cards and " + after.counts().matched()
                    + " matches; expected " + (before - 1) + " and " + (CARDS - 1));
            return;
        }
        helper.succeed();
    }

    /** A card put in shows on the very next page. */
    @GameTest(template = "empty")
    public static void acardputinshowsonthenextpage(GameTestHelper helper) {
        BlockPos where = box(helper);
        filled(helper, where, CARDS);
        ServerPlayer player = standingAt(helper, where);

        page(player, where, 0);
        entity(helper, where).put(CardIdentity.ofPrinting(UUID.randomUUID()), 1);
        CollectionPagePayload after = page(player, where, 1);
        if (after.counts().matched() != CARDS + 1) {
            helper.fail("a card put in was missing from the next page: "
                    + after.counts().matched() + " matches, expected " + (CARDS + 1));
            return;
        }
        helper.succeed();
    }

    /** A card picked up into the player's pockets shows on the builder's next page. */
    @GameTest(template = "empty")
    public static void acardpickedupshowsonthenextpage(GameTestHelper helper) {
        BlockPos where = box(helper);
        filled(helper, where, CARDS);
        ServerPlayer player = standingAt(helper, where);

        page(player, where, CollectionQuery.EVERYTHING, 0, true);
        player.getInventory().add(CardItem.of(
                CardComponent.of(CardIdentity.ofPrinting(UUID.randomUUID()))));
        CollectionPagePayload after = page(player, where, CollectionQuery.EVERYTHING, 1, true);
        if (after.counts().matched() != CARDS + 1) {
            helper.fail("a card in the player's pockets was missing from the next page: "
                    + after.counts().matched() + " matches, expected " + (CARDS + 1));
            return;
        }
        helper.succeed();
    }

    /**
     * Two people at one box are never shown each other's answer.
     * <p>Interleaved, which is the order that would catch a cache keyed on the box rather than
     * on the person: one of them counts their pockets in and the other does not.
     */
    @GameTest(template = "empty")
    public static void twoplayersaretoldtheirownanswers(GameTestHelper helper) {
        BlockPos where = box(helper);
        filled(helper, where, CARDS);
        ServerPlayer carrying = standingAt(helper, where);
        ServerPlayer emptyHanded = standingAt(helper, where);
        carrying.getInventory().add(CardItem.of(
                CardComponent.of(CardIdentity.ofPrinting(UUID.randomUUID()))));

        int mine = page(carrying, where, CollectionQuery.EVERYTHING, 0, true).counts().matched();
        int theirs = page(emptyHanded, where, CollectionQuery.EVERYTHING, 0, true).counts().matched();
        int mineAgain = page(carrying, where, CollectionQuery.EVERYTHING, 1, true).counts().matched();
        if (mine != CARDS + 1 || mineAgain != CARDS + 1) {
            helper.fail("the carrying player's pages counted " + mine + " then " + mineAgain
                    + "; expected " + (CARDS + 1) + " both times");
            return;
        }
        if (theirs != CARDS) {
            helper.fail("the empty-handed player was shown " + theirs
                    + " matches, which includes somebody else's pockets; expected " + CARDS);
            return;
        }
        helper.succeed();
    }

    /** A different question is a new search, not a slice of the old answer. */
    @GameTest(template = "empty")
    public static void adifferentquestionsearchesagain(GameTestHelper helper) {
        BlockPos where = box(helper);
        filled(helper, where, CARDS);
        ServerPlayer player = standingAt(helper, where);

        page(player, where, 0);
        CollectionPagePayload narrowed =
                page(player, where, CollectionQuery.EVERYTHING.searchingFor("nothingiscalledthis"), 0, false);
        if (narrowed.counts().matched() != 0) {
            helper.fail("a search nothing answers was shown " + narrowed.counts().matched()
                    + " matches from the answer before it");
            return;
        }
        helper.succeed();
    }

    /**
     * A name that arrives from the card cache shows on the next page, and answers a search.
     * <p>The one input that changes off the server thread: a page asks the cache for what it
     * could not name, and the answer lands whenever it lands. A page read back from before it
     * landed would go on showing an unnamed card, and a search for the name would go on
     * finding nothing.
     */
    @GameTest(template = "empty")
    public static void anamethatarrivesshowsonthenextpage(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("no card service, so a name cannot arrive");
            return;
        }
        BlockPos where = box(helper);
        filled(helper, where, CARDS);
        ServerPlayer player = standingAt(helper, where);
        UUID printing = UUID.randomUUID();
        String name = "Arrived" + printing.toString().replace("-", "");
        entity(helper, where).put(CardIdentity.ofPrinting(printing), 1);

        CollectionQuery byName = CollectionQuery.EVERYTHING.searchingFor(name);
        if (page(player, where, byName, 0, false).counts().matched() != 0) {
            helper.fail("the fixture's card was findable by name before anybody named it");
            return;
        }
        cards.store().store(new CardMetadata(
                printing, UUID.nameUUIDFromBytes(name.getBytes()),
                name, "", 0, "Artifact", "", Set.of(), Set.of(),
                List.of(), "normal", "tst", "Test", "1", Rarity.COMMON,
                false, false, true, false, false, List.of("paper"),
                java.util.Map.of(), java.util.Map.of(), ""), null);

        CollectionPagePayload after = page(player, where, byName, 0, false);
        if (after.counts().matched() != 1) {
            helper.fail("a card whose name had arrived was not found by it: "
                    + after.counts().matched() + " matches");
            return;
        }
        if (after.rows().getFirst().about().isEmpty()) {
            helper.fail("the page still showed the card as unnamed");
            return;
        }
        helper.succeed();
    }
}
