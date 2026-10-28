package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.block.TableBlock;
import dev.gathering.block.TableClusters;
import dev.gathering.block.TablePart;
import dev.gathering.block.TableSeats;
import dev.gathering.block.TableSessions;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageUris;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.game.GameSession;
import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.Zone;
import dev.gathering.core.match.MatchRules;
import dev.gathering.item.GatheringContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A token name that means several tokens is a question, and a picked token is exactly that one.
 * <p>Asked about Cats: a 1/1 white Cat and a 2/2 green Cat share a name, and the table used to
 * make whichever was printed most recently without asking. Both halves are checked here without
 * Scryfall: what is done with what a search found, and what is done with a printing somebody
 * picked.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TokenChoiceGameTest {

    private static BlockPos table(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        for (TablePart part : TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(TableBlock.PART, part), 3);
        }
        return origin;
    }

    private static SeatId seated(GameTestHelper helper, ServerPlayer player, BlockPos origin) {
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        var seat = TableClusters.at(helper.getLevel(), origin).seats().getFirst();
        TableSeats.take(helper.getLevel(), origin, seat.cell(), seat.side(), player.getUUID());
        TableSessions.start(helper.getLevel(), origin, new MatchRules(FormatPresets.COMMANDER, 1));
        return TableSessions.seatIdOf(helper.getLevel(), origin, player.getUUID()).orElseThrow();
    }

    private static CardMetadata card(String name, String typeLine, String power, String toughness,
            Set<String> colors, String text, String layout) {
        UUID id = UUID.randomUUID();
        return new CardMetadata(id, UUID.randomUUID(), name, "", 0, typeLine, text, colors, colors,
                List.of(new CardFace(name, "", typeLine, text, power, toughness, "", "", "",
                        ImageUris.EMPTY)),
                layout, "tst", "Test", "1", Rarity.COMMON, false, false, true, false, false,
                List.of("paper"), Map.of(), Map.of(), "", List.of());
    }

    private static int onTheBattlefield(GameTestHelper helper, BlockPos origin, SeatId seat) {
        GameSession session = TableSessions.sessionAt(helper.getLevel(), origin).orElseThrow();
        return session.state().contents(seat, Zone.BATTLEFIELD).size();
    }

    /** Two Cats that play differently: nothing is made, and the player is asked. */
    @GameTest(template = "empty")
    public static void twodifferentcatsareaquestion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId seat = seated(helper, player, origin);
        List<CardMetadata> found = List.of(
                card("Cat", "Token Creature - Cat", "1", "1", Set.of("W"), "Lifelink", "token"),
                card("Cat", "Token Creature - Cat", "2", "2", Set.of("G"), "", "token"));

        int offered = dev.gathering.server.TokenCreation.offer(
                player, helper.getLevel(), origin, seat, found, 1, "Cat");

        if (offered != 2) {
            helper.fail("two different Cats were offered as " + offered + " choice(s)");
            return;
        }
        if (onTheBattlefield(helper, origin, seat) != 0) {
            helper.fail("a Cat was made before anybody said which");
            return;
        }
        helper.succeed();
    }

    /** One Cat printed twice is one Cat: made at once, with no question. */
    @GameTest(template = "empty")
    public static void onecatreprintedismadeatonce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId seat = seated(helper, player, origin);
        List<CardMetadata> found = List.of(
                card("Cat", "Token Creature - Cat", "1", "1", Set.of("W"), "", "token"),
                card("Cat", "Token Creature - Cat", "1", "1", Set.of("W"), "", "token"));

        int offered = dev.gathering.server.TokenCreation.offer(
                player, helper.getLevel(), origin, seat, found, 2, "Cat");

        if (offered != 1) {
            helper.fail("one Cat printed twice was treated as " + offered + " different tokens");
            return;
        }
        if (onTheBattlefield(helper, origin, seat) != 2) {
            helper.fail("asking for two of the only Cat made " + onTheBattlefield(helper, origin, seat));
            return;
        }
        helper.succeed();
    }

    /** A picked printing is made - and a real card's printing is refused, whatever the client says. */
    @GameTest(template = "empty")
    public static void apickedtokenismadeandarealcardisnot(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = table(helper);
        SeatId seat = seated(helper, player, origin);

        boolean realCard = dev.gathering.server.TokenCreation.makeChosen(player, helper.getLevel(),
                origin, seat, Optional.of(card("Grizzly Bears", "Creature - Bear", "2", "2",
                        Set.of("G"), "", "normal")), 1);
        if (realCard || onTheBattlefield(helper, origin, seat) != 0) {
            helper.fail("a real card's printing was made as a token because a client named it");
            return;
        }

        boolean token = dev.gathering.server.TokenCreation.makeChosen(player, helper.getLevel(),
                origin, seat, Optional.of(card("Cat", "Token Creature - Cat", "2", "2",
                        Set.of("G"), "", "token")), 1);
        if (!token || onTheBattlefield(helper, origin, seat) != 1) {
            helper.fail("a picked Cat token was not made");
            return;
        }
        helper.succeed();
    }
}
