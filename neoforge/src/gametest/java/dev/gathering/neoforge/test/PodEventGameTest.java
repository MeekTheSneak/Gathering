package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.DraftPods;
import dev.gathering.block.PodSignup;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableBlockEntity;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.draft.DraftPod;
import dev.gathering.core.draft.DrafterId;
import dev.gathering.core.draft.PodLobby;
import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.table.Side;
import dev.gathering.core.table.TableCell;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.item.GatheringContent;
import dev.gathering.item.PackComponent;
import dev.gathering.item.PackItem;
import dev.gathering.server.DraftActions;
import dev.gathering.server.PodEvents;
import dev.gathering.server.PodSignups;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A draft or sealed event from its packs to its cards.
 * <p>The test server has no card pipeline to open a real booster with, so these hand the
 * event what each pack held and go through everything after that on the real path: the packs
 * used up, the pod or the sealed pools, the handing out, the table breaking halfway. What is
 * checked each time is where every card ended up, by identity.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PodEventGameTest {

    /** Sealed: each player is handed exactly what their own packs held, as a pool to build. */
    @GameTest(template = "tables")
    public static void sealedHandsEachPlayerTheirOwnPacks(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        ServerPlayer guest = seated(helper, origin, new TableCell(0, 0), Side.SOUTH);
        PodSettings settings = new PodSettings(PodSettings.Kind.SEALED, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.ANY, 2, 0, PodSettings.CardsGo.PLAYERS_KEEP);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), settings);
        PodSignups.putIn(host, origin, packs("m21", 2));
        PodSignups.putIn(guest, origin, packs("m21", 2));

        Started started = begin(helper, origin, 5);
        if (!started.began()) {
            helper.fail("a ready sealed event did not begin");
            return;
        }
        if (tableAt(helper, origin).hasSignup() || tableAt(helper, origin).hasPod()) {
            helper.fail("a sealed event left a signup or a pod behind");
            return;
        }
        List<CardIdentity> hostGot = cardsInDecks(host);
        List<CardIdentity> guestGot = cardsInDecks(guest);
        if (!sameCards(hostGot, started.opened().get(0)) || !sameCards(guestGot, started.opened().get(1))) {
            helper.fail("sealed pools went to the wrong players: host " + hostGot.size()
                    + ", guest " + guestGot.size());
            return;
        }
        ItemStack pool = firstDeck(host);
        if (pool.get(dev.gathering.registry.GatheringComponents.POOL.get()) == null) {
            helper.fail("a sealed pool came without the pool it may be built from");
            return;
        }
        helper.succeed();
    }

    /** Every card to the sponsor, and the spare pack of a player who left goes back unopened. */
    @GameTest(template = "tables")
    public static void asponsoredEventGivesTheSponsorEveryCardAndItsSpares(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        ServerPlayer guest = seated(helper, origin, new TableCell(0, 0), Side.SOUTH);
        PodSettings settings = new PodSettings(PodSettings.Kind.SEALED, PodSettings.Source.SPONSORED,
                PodSettings.SetRule.ANY, 1, 0, PodSettings.CardsGo.TO_SPONSOR);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), settings);
        PodSignups.putIn(host, origin, packs("m21", 2));
        // The guest leaves, so only one of the sponsor's two packs is opened.
        TableSeats.leave(helper.getLevel(), origin, guest.getUUID());
        int packsBefore = packsIn(host);

        Started started = begin(helper, origin, 4);
        if (!started.began()) {
            helper.fail("a sponsored event with a pack to spare did not begin");
            return;
        }
        if (!sameCards(cardsInDecks(host), started.opened().get(0)) || !cardsInDecks(guest).isEmpty()) {
            helper.fail("the sponsor was not handed every card: " + cardsInDecks(host).size());
            return;
        }
        if (packsIn(host) != packsBefore + 1) {
            helper.fail("the spare pack went " + (packsIn(host) - packsBefore) + " packs back, not 1");
            return;
        }
        helper.succeed();
    }

    /**
     * A draft of real packs whose cards go back: every contributor gets exactly what their own
     * packs held, whoever drafted those cards.
     */
    @GameTest(template = "tables", timeoutTicks = 200)
    public static void adraftedEventReturnsEachContributorsOwnCards(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        List<ServerPlayer> four = sitFour(helper, origin);
        PodSettings settings = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.ANY, 1, 1, PodSettings.CardsGo.TO_CONTRIBUTORS);
        PodSignups.create(helper.getLevel(), origin, four.get(0).getUUID(), settings);
        for (ServerPlayer player : four) {
            PodSignups.putIn(player, origin, packs("m21", 1));
        }
        Started started = begin(helper, origin, 4);
        if (!started.began() || DraftPods.podAt(helper.getLevel(), origin).isEmpty()) {
            helper.fail("a ready draft did not begin a pod");
            return;
        }
        // Everybody takes the first card in front of them until the packs are empty.
        for (int turn = 0; turn < 4 && DraftPods.podAt(helper.getLevel(), origin).isPresent(); turn++) {
            for (ServerPlayer player : four) {
                DraftActions.handle(player, origin, List.of(0));
            }
        }
        if (DraftPods.podAt(helper.getLevel(), origin).isPresent()) {
            helper.fail("the draft did not finish");
            return;
        }
        for (int seat = 0; seat < 4; seat++) {
            if (!sameCards(cardsInDecks(four.get(seat)), started.opened().get(seat))) {
                helper.fail("seat " + seat + " was handed " + cardsInDecks(four.get(seat)).size()
                        + " cards, not the 4 their pack held");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The pick clock: nothing is taken before the time is up, and once it is, whoever has not
     * picked takes the first cards in their pack - so a draft nobody touches still finishes.
     */
    @GameTest(template = "tables", timeoutTicks = 300)
    public static void thePickClockPicksForWhoeverIsLate(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        List<ServerPlayer> four = sitFour(helper, origin);
        PodSettings settings = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.ANY, 1, 1, PodSettings.CardsGo.PLAYERS_KEEP, 1);
        PodSignups.create(helper.getLevel(), origin, four.get(0).getUUID(), settings);
        for (ServerPlayer player : four) {
            PodSignups.putIn(player, origin, packs("m21", 1));
        }
        Started started = begin(helper, origin, 3);
        DraftPod opened = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
        if (!started.began() || opened == null) {
            helper.fail("a ready draft with a clock did not begin a pod");
            return;
        }
        DrafterId second = opened.placeOf(four.get(1).getUUID()).orElseThrow();
        CardIdentity secondsFirst = opened.state().packHeldBy(second).cards().get(0);
        DraftActions.handle(four.get(0), origin, List.of(2));
        helper.runAfterDelay(10, () -> {
            DraftPod early = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
            if (early == null || !early.state().poolOf(second).isEmpty()) {
                helper.fail("the clock picked before its time was up");
            }
        });
        helper.runAfterDelay(30, () -> {
            DraftPod late = DraftPods.podAt(helper.getLevel(), origin).orElse(null);
            if (late == null || !late.state().poolOf(second).equals(List.of(secondsFirst))) {
                helper.fail("a drafter out of time was not given the first card in their pack");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(DraftPods.podAt(helper.getLevel(), origin).isEmpty(), "the clock did not finish the draft");
            for (int seat = 0; seat < 4; seat++) {
                helper.assertTrue(cardsInDecks(four.get(seat)).size() == 3, "seat " + seat + " was not handed a pool of 3");
            }
        });
    }

    /** A table broken mid-draft gives each contributor back what their packs held. */
    @GameTest(template = "tables")
    public static void breakingTheTableMidDraftGivesThePacksCardsBack(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        List<ServerPlayer> four = sitFour(helper, origin);
        PodSettings settings = new PodSettings(PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.ANY, 1, 0, PodSettings.CardsGo.PLAYERS_KEEP);
        PodSignups.create(helper.getLevel(), origin, four.get(0).getUUID(), settings);
        for (ServerPlayer player : four) {
            PodSignups.putIn(player, origin, packs("m21", 1));
        }
        Started started = begin(helper, origin, 6);
        DraftActions.handle(four.get(0), origin, List.of(0, 1));

        for (ServerPlayer player : four) {
            TableSeats.leave(helper.getLevel(), origin, player.getUUID());
        }
        helper.getLevel().destroyBlock(origin, false);

        for (int seat = 0; seat < 4; seat++) {
            if (!sameCards(cardsInDecks(four.get(seat)), started.opened().get(seat))) {
                helper.fail("seat " + seat + " got " + cardsInDecks(four.get(seat)).size()
                        + " cards back, not the 6 their pack held");
                return;
            }
        }
        helper.succeed();
    }

    /** A pack put in while the others were opening means nothing is used. */
    @GameTest(template = "tables")
    public static void achangeWhileOpeningUsesNothing(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        ServerPlayer host = seated(helper, origin, new TableCell(0, 0), Side.NORTH);
        PodSettings settings = new PodSettings(PodSettings.Kind.SEALED, PodSettings.Source.EACH_BRINGS,
                PodSettings.SetRule.ANY, 2, 0, PodSettings.CardsGo.PLAYERS_KEEP);
        PodSignups.create(helper.getLevel(), origin, host.getUUID(), settings);
        PodSignups.putIn(host, origin, packs("m21", 1));
        TableBlockEntity table = tableAt(helper, origin);
        PodSignup asStarted = table.signup().orElseThrow();
        List<UUID> seated = PodSignups.seatedAt(helper.getLevel(), origin);
        // Starting a second pack's worth of plan from the one pack in, then a pack goes in.
        PodSignups.putIn(host, origin, packs("m21", 1));
        PodLobby.Plan plan = table.signup().orElseThrow().lobby().plan(seated).orElseThrow();

        boolean began = PodEvents.begin(helper.getLevel(), origin, asStarted, seated, plan,
                List.of(List.of(cards("x", 3), cards("y", 3))));
        if (began || table.signup().orElseThrow().held().size() != 2 || !cardsInDecks(host).isEmpty()) {
            helper.fail("an event began from a signup that changed while it was opening");
            return;
        }
        helper.succeed();
    }

    /** The record of what the packs held survives a restart in the middle of a draft. */
    @GameTest(template = "tables")
    public static void anEventsPacksAreRememberedThroughARestart(GameTestHelper helper) {
        BlockPos origin = twoTables(helper);
        List<ServerPlayer> four = sitFour(helper, origin);
        PodSignups.create(helper.getLevel(), origin, four.get(0).getUUID(), new PodSettings(
                PodSettings.Kind.DRAFT, PodSettings.Source.EACH_BRINGS, PodSettings.SetRule.ANY, 1, 0,
                PodSettings.CardsGo.TO_CONTRIBUTORS));
        for (ServerPlayer player : four) {
            PodSignups.putIn(player, origin, packs("m21", 1));
        }
        begin(helper, origin, 5);
        var registries = helper.getLevel().registryAccess();
        TableBlockEntity table = tableAt(helper, origin);
        TableBlockEntity reloaded = new TableBlockEntity(origin, helper.getLevel().getBlockState(origin));
        reloaded.loadWithComponents(table.saveWithoutMetadata(registries), registries);
        if (reloaded.podRecord().isEmpty() || !reloaded.podRecord().get().equals(table.podRecord().orElse(null))) {
            helper.fail("the record of the event's packs did not survive a save");
            return;
        }
        DraftPod pod = reloaded.pod().orElse(null);
        if (pod == null || pod.poolsAreKept() || pod.state().picksDueFrom(DrafterId.of(0)) != 2) {
            helper.fail("the pod came back as " + pod);
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ fixtures

    private record Started(boolean began, List<List<CardIdentity>> opened) {
    }

    /** Begins the event at this table with made-up cards, this many per pack. */
    private static Started begin(GameTestHelper helper, BlockPos origin, int perPack) {
        TableBlockEntity table = tableAt(helper, origin);
        PodSignup signup = table.signup().orElseThrow();
        List<UUID> seated = PodSignups.seatedAt(helper.getLevel(), origin);
        PodLobby.Plan plan = signup.lobby().plan(seated).orElseThrow();
        List<List<List<CardIdentity>>> opened = new ArrayList<>();
        List<List<CardIdentity>> bySeat = new ArrayList<>();
        for (int seat = 0; seat < plan.bySeat().size(); seat++) {
            List<List<CardIdentity>> packs = new ArrayList<>();
            List<CardIdentity> all = new ArrayList<>();
            for (int pack = 0; pack < plan.bySeat().get(seat).size(); pack++) {
                List<CardIdentity> cards = cards("s" + seat + "p" + pack + "-" + helper.getTick(), perPack);
                packs.add(cards);
                all.addAll(cards);
            }
            opened.add(packs);
            bySeat.add(all);
        }
        boolean began = PodEvents.begin(helper.getLevel(), origin, signup, seated, plan, opened);
        return new Started(began, bySeat);
    }

    private static List<CardIdentity> cards(String tag, int count) {
        List<CardIdentity> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(CardIdentity.ofPrinting(UUID.nameUUIDFromBytes((tag + "/" + index)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return cards;
    }

    private static boolean sameCards(List<CardIdentity> got, List<CardIdentity> expected) {
        List<CardIdentity> left = new ArrayList<>(got);
        for (CardIdentity card : expected) {
            if (!left.remove(card)) {
                return false;
            }
        }
        return left.isEmpty();
    }

    private static List<CardIdentity> cardsInDecks(ServerPlayer player) {
        List<CardIdentity> cards = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            DeckItem.deckOf(player.getInventory().getItem(slot)).ifPresent(deck -> {
                for (CardComponent card : deck.entries()) {
                    cards.add(card.toIdentity());
                }
                for (CardComponent card : deck.sideboard()) {
                    cards.add(card.toIdentity());
                }
            });
        }
        return cards;
    }

    private static ItemStack firstDeck(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (DeckItem.deckOf(stack).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int packsIn(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.has(dev.gathering.registry.GatheringComponents.PACK.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack packs(String set, int count) {
        ItemStack stack = PackItem.of(new PackComponent(set, "draft"));
        stack.setCount(count);
        return stack;
    }

    private static ServerPlayer seated(GameTestHelper helper, BlockPos origin, TableCell cell, Side side) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(origin.getX() + 1.0, origin.getY(), origin.getZ() + 1.0);
        TableSeats.take(helper.getLevel(), origin, cell, side, player.getUUID());
        return player;
    }

    private static List<ServerPlayer> sitFour(GameTestHelper helper, BlockPos origin) {
        return List.of(
                seated(helper, origin, new TableCell(0, 0), Side.NORTH),
                seated(helper, origin, new TableCell(0, 0), Side.SOUTH),
                seated(helper, origin, new TableCell(1, 0), Side.NORTH),
                seated(helper, origin, new TableCell(1, 0), Side.SOUTH));
    }

    private static TableBlockEntity tableAt(GameTestHelper helper, BlockPos origin) {
        return TableBlock.entityAt(helper.getLevel(), origin).orElseThrow();
    }

    private static BlockPos twoTables(GameTestHelper helper) {
        BlockPos origin = place(helper, 1, 2, 1);
        place(helper, 3, 2, 1);
        return origin;
    }

    private static BlockPos place(GameTestHelper helper, int x, int y, int z) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, y, z));
        var table = GatheringContent.TABLE.get().defaultBlockState();
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin), table.setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }
}
