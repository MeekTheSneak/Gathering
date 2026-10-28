package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.SetRelease;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.AskSetMissingPayload;
import dev.gathering.network.AskSetProgressPayload;
import dev.gathering.network.GatheringProtocol;
import dev.gathering.network.SetMissingPayload;
import dev.gathering.server.ActionBudget;
import dev.gathering.server.CollectionSets;
import dev.gathering.server.Notices;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A set clicked on the set list is answered, whatever the answer is.
 * <p>The whole-set budget - two at once, one more every ten seconds - was spent on the packet, before
 * anything else was asked: a set already in memory was charged as though it had been fetched, and a
 * request past the budget was dropped with nothing sent back. A third set clicked inside ten seconds
 * played its click and opened nothing. Through the real packet route, because that is where the
 * budget was.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionSetsGameTest {

    @GameTest(template = "tables")
    public static void aSetAskedForPastTheBudgetIsStillAnswered(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("no card service, so no set can be asked about");
            return;
        }
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        BlockPos where = helper.absolutePos(at);
        if (!(helper.getLevel().getBlockEntity(where) instanceof CollectionBlockEntity collection)) {
            helper.fail("a collection block was placed without its block entity");
            return;
        }
        ServerPlayer fetching = helper.makeMockServerPlayerInLevel();
        ServerPlayer inMemory = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(fetching.getUUID()));
        fetching.setPos(net.minecraft.world.phys.Vec3.atCenterOf(where));
        inMemory.setPos(net.minecraft.world.phys.Vec3.atCenterOf(where));

        // Two sets this server knows: one it would have to read from Scryfall, one already read.
        String unread = "zz" + Integer.toString(Math.floorMod(where.hashCode(), 1296), 36);
        String read = unread + "r";
        cards.holdSetForTesting(read, List.of(printing(read, "1"), printing(read, "2")));
        Map<String, SetRelease> ours = new LinkedHashMap<>();
        ours.put(unread, new SetRelease(unread, "Unread Set", "expansion", "2020-01-01", false, 2, 2));
        ours.put(read, new SetRelease(read, "Read Set", "expansion", "2020-01-01", false, 2, 2));

        // Both players have just looked at two other sets, which is the whole burst.
        ActionBudget.WHOLE_SETS.spend(fetching.getUUID(), 2);
        ActionBudget.WHOLE_SETS.spend(inMemory.getUUID(), 2);

        List<String> fetchingShown = Collections.synchronizedList(new ArrayList<>());
        List<SetMissingPayload> inMemoryAnswered = Collections.synchronizedList(new ArrayList<>());
        UUID fetchingId = fetching.getUUID();
        UUID inMemoryId = inMemory.getUUID();
        Notices.watchForTesting((to, line) -> {
            if (fetchingId.equals(to) && line.getContents() instanceof TranslatableContents said) {
                fetchingShown.add(said.getKey());
            }
        });
        CollectionSets.watchForTesting((to, answer) -> {
            if (inMemoryId.equals(to)) {
                inMemoryAnswered.add(answer);
            }
        });

        // The server's list of sets, with these two in it, for exactly as long as the two asks take:
        // both are answered from it inside this call, and the list goes back as it was before any
        // other test can read it.
        Map<String, SetRelease> was = cards.setsForTesting(Map.copyOf(ours));
        if (was != null) {
            Map<String, SetRelease> withOurs = new LinkedHashMap<>(was);
            withOurs.putAll(ours);
            cards.setsForTesting(Map.copyOf(withOurs));
        }
        try {
            route(fetching, new AskSetMissingPayload(where, unread));
            route(inMemory, new AskSetMissingPayload(where, read));
        } finally {
            cards.setsForTesting(was);
        }

        if (!fetchingShown.contains("message.gathering.sets.busy")) {
            helper.fail("a set asked for past the whole-set budget opened nothing and said nothing: " + fetchingShown);
            return;
        }
        helper.succeedWhen(() -> {
            if (inMemoryAnswered.isEmpty()) {
                throw new GameTestAssertException("a set already read this run was charged as a fetch and"
                        + " refused, so its missing cards never came back");
            }
            if (inMemoryAnswered.stream().noneMatch(answer -> answer.code().equals(read))) {
                throw new GameTestAssertException("the answer was about another set: " + inMemoryAnswered);
            }
        });
    }

    /**
     * A set clicked as the list arrives is answered, and clicked twice it is not told it came too soon.
     * <p>The list and a set clicked on it shared one wait of a second, and a click inside it was dropped
     * without a word, so the first set clicked as the list came up played its click and opened nothing.
     * A click on no set at all - a code the server does not know - started the wait too, and refused the
     * real click after it. And a double-click is one request: the second click is not to be told "Too
     * many sets at once" over the set the first one opened.
     */
    @GameTest(template = "tables")
    public static void aSetClickedAsTheListArrivesIsAnswered(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        ServerPlayer player = playerAtACollection(helper);
        if (cards == null || player == null) {
            return;
        }
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        String read = "zy" + Integer.toString(Math.floorMod(where.hashCode(), 1296), 36);
        cards.holdSetForTesting(read, List.of(printing(read, "1"), printing(read, "2")));
        UUID id = player.getUUID();
        List<String> shown = Collections.synchronizedList(new ArrayList<>());
        List<SetMissingPayload> answered = Collections.synchronizedList(new ArrayList<>());
        watch(id, shown, answered);
        int afterTheFirst;
        int afterTheSecond;
        Map<String, SetRelease> was = lendTheSetList(cards, Map.of(
                read, new SetRelease(read, "Read Set", "expansion", "2020-01-01", false, 2, 2)));
        try {
            routeAny(player, new AskSetProgressPayload(where));
            routeAny(player, new AskSetMissingPayload(where, read + "-not-a-set"));
            routeAny(player, new AskSetMissingPayload(where, read));
            afterTheFirst = shown.size();
            routeAny(player, new AskSetMissingPayload(where, read));
            afterTheSecond = shown.size();
        } finally {
            cards.setsForTesting(was);
        }
        if (afterTheFirst > 0) {
            helper.fail("a set clicked as the list arrived, after a click on no set, was refused as too soon: " + shown);
            return;
        }
        if (afterTheSecond > afterTheFirst) {
            helper.fail("a double-click on one set was told it came too soon, over the set it opened: " + shown);
            return;
        }
        helper.succeedWhen(() -> {
            if (answered.stream().noneMatch(answer -> answer.code().equals(read))) {
                throw new GameTestAssertException("a set clicked as the list arrived opened nothing: " + answered);
            }
        });
    }

    /**
     * A second set clicked inside a second of the first is told so.
     * <p>Dropped without a word, it played the click's sound and opened nothing.
     */
    @GameTest(template = "tables")
    public static void aSecondSetClickedInsideASecondIsToldSo(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        ServerPlayer player = playerAtACollection(helper);
        if (cards == null || player == null) {
            return;
        }
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        String first = "zw" + Integer.toString(Math.floorMod(where.hashCode(), 1296), 36);
        String second = first + "b";
        cards.holdSetForTesting(first, List.of(printing(first, "1")));
        cards.holdSetForTesting(second, List.of(printing(second, "1")));
        UUID id = player.getUUID();
        List<String> shown = Collections.synchronizedList(new ArrayList<>());
        List<SetMissingPayload> answered = Collections.synchronizedList(new ArrayList<>());
        watch(id, shown, answered);
        Map<String, SetRelease> was = lendTheSetList(cards, Map.of(
                first, new SetRelease(first, "First Set", "expansion", "2020-01-01", false, 1, 1),
                second, new SetRelease(second, "Second Set", "expansion", "2020-01-01", false, 1, 1)));
        try {
            routeAny(player, new AskSetMissingPayload(where, first));
            routeAny(player, new AskSetMissingPayload(where, second));
        } finally {
            cards.setsForTesting(was);
        }
        if (!shown.contains("message.gathering.sets.busy")) {
            helper.fail("a second set clicked inside a second of the first opened nothing and said nothing: " + shown);
            return;
        }
        helper.succeed();
    }

    /** Listens for what one player is told and which sets are sent back to them. */
    private static void watch(UUID id, List<String> shown, List<SetMissingPayload> answered) {
        Notices.watchForTesting((to, line) -> {
            if (id.equals(to) && line.getContents() instanceof TranslatableContents said) {
                shown.add(said.getKey());
            }
        });
        CollectionSets.watchForTesting((to, answer) -> {
            if (id.equals(to)) {
                answered.add(answer);
            }
        });
    }

    /**
     * A set already being read costs nothing more to ask for again.
     * <p>A second ask joins the read already going rather than starting another, so the whole-set
     * budget has nothing to keep off the card worker - and charging for it refused a set the player
     * had clicked once already, waiting on its first answer.
     */
    @GameTest(template = "tables")
    public static void aSetBeingReadCostsNothingMore(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        ServerPlayer player = playerAtACollection(helper);
        if (cards == null || player == null) {
            return;
        }
        BlockPos where = helper.absolutePos(new BlockPos(1, 1, 1));
        String reading = "zx" + Integer.toString(Math.floorMod(where.hashCode(), 1296), 36);
        java.util.concurrent.CompletableFuture<List<CardMetadata>> read = cards.readingSetForTesting(reading);
        ActionBudget.WHOLE_SETS.spend(player.getUUID(), 2);
        UUID id = player.getUUID();
        List<String> shown = Collections.synchronizedList(new ArrayList<>());
        List<SetMissingPayload> answered = Collections.synchronizedList(new ArrayList<>());
        Notices.watchForTesting((to, line) -> {
            if (id.equals(to) && line.getContents() instanceof TranslatableContents said) {
                shown.add(said.getKey());
            }
        });
        CollectionSets.watchForTesting((to, answer) -> {
            if (id.equals(to)) {
                answered.add(answer);
            }
        });
        Map<String, SetRelease> was = lendTheSetList(cards, Map.of(
                reading, new SetRelease(reading, "Reading Set", "expansion", "2020-01-01", false, 2, 2)));
        try {
            routeAny(player, new AskSetMissingPayload(where, reading));
        } finally {
            cards.setsForTesting(was);
            read.complete(List.of(printing(reading, "1"), printing(reading, "2")));
        }
        if (shown.contains("message.gathering.sets.busy")) {
            helper.fail("a set already being read was charged as a new fetch and refused: " + shown);
            return;
        }
        helper.succeedWhen(() -> {
            if (answered.stream().noneMatch(answer -> answer.code().equals(reading))) {
                throw new GameTestAssertException("a set asked for while it was being read never came back: "
                        + answered);
            }
        });
    }

    /** A collection at the test's corner and a player standing at it, or null with the test failed. */
    private static ServerPlayer playerAtACollection(GameTestHelper helper) {
        if (CardDataService.active().isEmpty()) {
            helper.fail("no card service, so no set can be asked about");
            return null;
        }
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        BlockPos where = helper.absolutePos(at);
        if (!(helper.getLevel().getBlockEntity(where) instanceof CollectionBlockEntity collection)) {
            helper.fail("a collection block was placed without its block entity");
            return null;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(player.getUUID()));
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(where));
        return player;
    }

    /**
     * Puts these sets into the server's list of every set, and hands back the list it replaced, to be
     * put back in the same call: the asks are answered from it inside that call, and no other test
     * reads it before it goes back.
     */
    private static Map<String, SetRelease> lendTheSetList(CardDataService cards, Map<String, SetRelease> ours) {
        Map<String, SetRelease> was = cards.setsForTesting(Map.copyOf(ours));
        if (was != null) {
            Map<String, SetRelease> withOurs = new LinkedHashMap<>(was);
            withOurs.putAll(ours);
            cards.setsForTesting(Map.copyOf(withOurs));
        }
        return was;
    }

    /** Sends a payload down the route a client's would take. */
    private static void route(ServerPlayer player, AskSetMissingPayload payload) {
        routeAny(player, payload);
    }

    /** Sends any payload down the route a client's would take. */
    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void routeAny(
            ServerPlayer player, T payload) {
        for (GatheringProtocol.ToServer<?> route : GatheringProtocol.TO_SERVER) {
            if (route.type().equals(payload.type())) {
                ((GatheringProtocol.ToServer<T>) route).handler().accept(player, payload);
                return;
            }
        }
        throw new GameTestAssertException("nothing on the server takes " + payload.type().id());
    }

    private static CardMetadata printing(String setCode, String number) {
        String name = "Card " + setCode + " " + number;
        return new CardMetadata(
                UUID.nameUUIDFromBytes((setCode + number).getBytes()), UUID.nameUUIDFromBytes(name.getBytes()),
                name, "", 0, "Artifact", "", Set.of(), Set.of(),
                List.of(), "normal", setCode, "Read Set", number, Rarity.COMMON,
                false, false, true, false, false, List.of("paper"),
                Map.of(), Map.of(), "");
    }
}
