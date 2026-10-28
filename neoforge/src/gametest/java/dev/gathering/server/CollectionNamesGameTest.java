package dev.gathering.server;

import com.mojang.authlib.GameProfile;
import dev.gathering.Gathering;
import dev.gathering.block.CollectionBlockEntity;
import dev.gathering.core.collection.CollectionRights;
import dev.gathering.item.GatheringContent;
import dev.gathering.network.CollectionKeyPayload;
import dev.gathering.network.CollectionOwnerPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A name typed into a collection's keys is looked up off the server thread, and not without end.
 * <p>From the 2026-09-25 sweep. A name that is not online and not remembered is a question for
 * Mojang, and vanilla's profile cache asks it there and then: an HTTPS request, a pause between
 * pages, three tries a second apart when it is refused, and a file written when it is answered -
 * all on whatever thread asked. Letting somebody in and handing a collection over asked on the
 * server thread, for every press, with nothing limiting how often, so one owner could stall every
 * tick by typing names.
 * <p>A game-test server has no profile cache, which is why this never ran in a test: these stand
 * one in, over a profile service that answers at once and writes down where it was asked from.
 * One test rather than three, because the stand-in is one static and tests in a batch run
 * alongside each other.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionNamesGameTest {

    private static final UUID FRIEND = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID HEIR = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MARKER = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PAL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID LAST = UUID.fromString("66666666-6666-4666-8666-666666666666");

    /** How many names one player may have looked up at once: the budget's burst. */
    private static final int NAME_LOOKUPS_AT_ONCE = 4;

    /** One question put to the profile service: the name, and whether the server thread asked it. */
    private record Asked(String name, boolean onTheServerThread) {
    }

    @GameTest(template = "tables", timeoutTicks = 200)
    public static void namesarelookedupofftheserverthreadandnotwithoutend(GameTestHelper helper) throws Exception {
        List<Asked> asked = new CopyOnWriteArrayList<>();
        Path file = Files.createTempFile("gathering-usercache-", ".json");
        file.toFile().deleteOnExit();
        var server = helper.getLevel().getServer();
        GameProfileCache cache = new GameProfileCache((names, answer) -> {
            for (String name : names) {
                asked.add(new Asked(name, server.isSameThread()));
                UUID id = switch (name.toLowerCase(java.util.Locale.ROOT)) {
                    case "friend" -> FRIEND;
                    case "heir" -> HEIR;
                    case "marker" -> MARKER;
                    case "pal" -> PAL;
                    case "last" -> LAST;
                    default -> UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                };
                answer.onProfileLookupSucceeded(new GameProfile(id, name));
            }
        }, file.toFile());
        CollectionKeys.profilesForTesting(cache);

        // Letting somebody in by a name the server has to go and ask about.
        CollectionBlockEntity letting = place(helper, new BlockPos(1, 1, 1));
        ServerPlayer owner = owning(helper, letting, new BlockPos(1, 1, 1));
        CollectionKeys.set(owner, new CollectionKeyPayload(
                helper.absolutePos(new BlockPos(1, 1, 1)), "Friend", true, false, false));
        if (asked.stream().anyMatch(Asked::onTheServerThread)) {
            CollectionKeys.profilesForTesting(null);
            helper.fail("a name was looked up on the server thread: " + asked);
            return;
        }
        // Two presses on one row for a name the server has to ask about, let in and then shut out
        // again: the answers land in the order the presses were made, so the second one stands.
        BlockPos letAt = helper.absolutePos(new BlockPos(1, 1, 1));
        CollectionKeys.set(owner, new CollectionKeyPayload(letAt, "Pal", true, false, false));
        CollectionKeys.set(owner, new CollectionKeyPayload(letAt, "Pal", false, false, false));
        CollectionKeys.set(owner, new CollectionKeyPayload(letAt, "Last", true, false, false));

        // Handing one over, pressed twice before the question could have been put - somebody
        // pressing again because nothing seemed to happen. That is not a confirmation.
        CollectionBlockEntity handing = place(helper, new BlockPos(5, 1, 1));
        ServerPlayer giver = owning(helper, handing, new BlockPos(5, 1, 1));
        BlockPos handed = helper.absolutePos(new BlockPos(5, 1, 1));
        CollectionKeys.handOver(giver, new CollectionOwnerPayload(handed, "Heir"));
        CollectionKeys.handOver(giver, new CollectionOwnerPayload(handed, "Heir"));
        // And a name of the same player's behind them on the one lookup thread, so that when it has
        // landed back on the server thread, both of theirs have too.
        CollectionKeys.set(giver, new CollectionKeyPayload(handed, "Marker", true, false, false));

        // And one owner typing name after name.
        CollectionBlockEntity flooded = place(helper, new BlockPos(9, 1, 1));
        ServerPlayer typist = owning(helper, flooded, new BlockPos(9, 1, 1));
        BlockPos typedAt = helper.absolutePos(new BlockPos(9, 1, 1));
        for (int name = 0; name < 20; name++) {
            CollectionKeys.set(typist, new CollectionKeyPayload(typedAt, "Flood" + name, true, false, false));
        }

        helper.startSequence()
                .thenWaitUntil(() -> {
                    // First, so that it is what a timed-out wait reports: once it has changed hands,
                    // the giver's own name behind the two presses is refused and never lands.
                    if (handing.rights().isOwner(HEIR)) {
                        throw new GameTestAssertException("two presses sent before the question was put handed "
                                + "the collection over without anybody reading whose name it was");
                    }
                    if (!letIn(letting, FRIEND)) {
                        throw new GameTestAssertException("a name looked up off the server thread was never let in");
                    }
                    if (!letIn(handing, MARKER)) {
                        throw new GameTestAssertException("still waiting on the names behind the hand-over");
                    }
                    if (!letIn(letting, LAST)) {
                        throw new GameTestAssertException("still waiting on the names behind the two presses");
                    }
                    if (floods(asked) < NAME_LOOKUPS_AT_ONCE) {
                        throw new GameTestAssertException("still waiting on the typed names: " + floods(asked));
                    }
                })
                .thenExecute(() -> {
                    if (letIn(letting, PAL)) {
                        throw new GameTestAssertException("let in and then shut out, and the answers landed the "
                                + "wrong way round: the player is let in");
                    }
                    if (handing.rights().isOwner(HEIR)) {
                        throw new GameTestAssertException("two presses sent before the question was put handed "
                                + "the collection over without anybody reading whose name it was");
                    }
                    if (!CollectionKeys.waitingToHandOver(handed, HEIR)) {
                        throw new GameTestAssertException("the hand-over was not asked about at all");
                    }
                    if (floods(asked) != NAME_LOOKUPS_AT_ONCE) {
                        throw new GameTestAssertException("twenty names typed at once put " + floods(asked)
                                + " questions to the profile service, not " + NAME_LOOKUPS_AT_ONCE);
                    }
                    if (asked.stream().anyMatch(Asked::onTheServerThread)) {
                        throw new GameTestAssertException("a name was looked up on the server thread: " + asked);
                    }
                })
                .thenIdle(1)
                // The press after the question was put, which is the confirmation.
                .thenExecute(() -> CollectionKeys.handOver(giver, new CollectionOwnerPayload(handed, "Heir")))
                .thenWaitUntil(() -> {
                    if (!handing.rights().isOwner(HEIR)) {
                        throw new GameTestAssertException("a press after the question was put did not hand it over");
                    }
                })
                .thenExecute(() -> CollectionKeys.profilesForTesting(null))
                .thenSucceed();
    }

    /**
     * Whether somebody is on a collection's lists. Not {@code mayLook}: an open collection may be looked
     * in by anybody, which is how a first draft of this test passed without waiting for anything.
     */
    private static boolean letIn(CollectionBlockEntity collection, UUID who) {
        return collection.rights().everybodyLetIn().contains(who);
    }

    private static long floods(List<Asked> asked) {
        return asked.stream().filter(question -> question.name().startsWith("flood")).count();
    }

    private static ServerPlayer owning(GameTestHelper helper, CollectionBlockEntity collection, BlockPos at) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        BlockPos where = helper.absolutePos(at);
        owner.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5);
        collection.setRights(CollectionRights.ownedBy(owner.getUUID()));
        return owner;
    }

    private static CollectionBlockEntity place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, GatheringContent.COLLECTION.get().defaultBlockState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof CollectionBlockEntity collection) {
            return collection;
        }
        throw new IllegalStateException("A collection block was placed without its block entity");
    }
}
