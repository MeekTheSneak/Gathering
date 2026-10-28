package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.CardComponent;
import dev.gathering.item.GatheringContent;
import dev.gathering.server.Notices;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A screen that stays open is answered on that screen.
 * <p>The collection, the table's setup screen and the replay list each answered their own buttons
 * in chat, which each of them is drawn over: a refused take kept showing its grid and playing the
 * sound that means it worked, a refused Create opened no lobby and said nothing, and a replay that
 * would not read was a click that did nothing. Chat keeps the line; what this checks is that the
 * player who pressed is also shown it where they are looking. A stand-in player cannot take the
 * notice's payload, so these listen one step before the wire, at {@link Notices}.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScreenAnswersGameTest {

    private static final UUID BOLT = UUID.fromString("77c6fa74-5543-42ac-9ead-0e890b188e99");

    /** Taking out of somebody else's collection, refused, and then out of one walked away from. */
    @GameTest(template = "tables")
    public static void aRefusedTakeIsAnsweredOnTheCollectionScreen(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof CollectionBlockEntity collection)) {
            helper.fail("a collection block was placed without its block entity");
            return;
        }
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer looking = helper.makeMockServerPlayerInLevel();
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));
        collection.put(CardIdentity.ofPrinting(BOLT, false), 4);
        looking.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)));
        List<String> shown = listenFor(looking);

        int took = dev.gathering.server.CollectionView.take(looking, helper.absolutePos(at),
                CardComponent.of(CardIdentity.ofPrinting(BOLT, false)), 1);
        if (took != 0) {
            helper.fail("somebody allowed only to look took " + took + " card(s), so this checks nothing");
            return;
        }
        if (!shown.contains("message.gathering.collection_may_not_take")) {
            helper.fail("a take the collection refused was answered only in chat, under the screen: " + shown);
            return;
        }

        shown.clear();
        looking.setPos(looking.getX() + 20, looking.getY(), looking.getZ());
        dev.gathering.server.CollectionView.take(looking, helper.absolutePos(at),
                CardComponent.of(CardIdentity.ofPrinting(BOLT, false)), 1);
        if (!shown.contains("message.gathering.collection_gone")) {
            helper.fail("a take from a collection out of reach was answered only in chat, under the screen: "
                    + shown);
            return;
        }
        helper.succeed();
    }

    /** Create on the setup screen, pressed by somebody who is standing at the table rather than seated. */
    @GameTest(template = "tables")
    public static void aRefusedEventIsAnsweredOnTheSetupScreen(GameTestHelper helper) {
        BlockPos origin = TestTables.place(helper, 1, 2, 1);
        ServerPlayer standing = helper.makeMockServerPlayerInLevel();
        standing.setPos(origin.getCenter());
        List<String> shown = listenFor(standing);

        dev.gathering.server.PodLobbies.create(standing, new dev.gathering.network.CreatePodPayload(origin,
                dev.gathering.core.draft.PodSettings.usual(dev.gathering.core.draft.PodSettings.Kind.DRAFT)));

        if (!shown.contains("message.gathering.pod.sit_down_first")) {
            helper.fail("Create refused for somebody not seated was answered only in chat, under the screen: "
                    + shown);
            return;
        }
        helper.succeed();
    }

    /** A row of the replay list naming a game that cannot be read. */
    @GameTest(template = "empty")
    public static void aReplayThatWillNotOpenIsAnsweredOnTheList(GameTestHelper helper) {
        ServerPlayer watching = helper.makeMockServerPlayerInLevel();
        List<String> shown = listenFor(watching);

        dev.gathering.server.ReplayWatch.handle(watching,
                new dev.gathering.network.WatchReplayPayload("nothing-kept-by-this-name", 0));

        // Unreadable, or - should another test have switched replays off meanwhile - off. Either is
        // an answer; silence on the list is the fault.
        if (!shown.contains("message.gathering.replay_unreadable") && !shown.contains("message.gathering.replays_off")) {
            helper.fail("a replay that could not be read was answered only in chat, under the list: " + shown);
            return;
        }
        helper.succeed();
    }

    /** Every notice this player is shown, by its translation key. */
    private static List<String> listenFor(ServerPlayer player) {
        List<String> shown = Collections.synchronizedList(new ArrayList<>());
        UUID who = player.getUUID();
        Notices.watchForTesting((to, line) -> {
            if (who.equals(to)) {
                shown.add(keyOf(line));
            }
        });
        return shown;
    }

    private static String keyOf(Component line) {
        return line.getContents() instanceof TranslatableContents said ? said.getKey() : line.getString();
    }
}
