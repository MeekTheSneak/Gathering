package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Legality;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.format.ValidationResult;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.server.DeckCheck;
import dev.gathering.service.CardDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The one referee this mod permits, checked where it is actually called from.
 *
 * <p>Whether a thirty-two card deck is legal for Modern is settled in the pure module, over
 * every rule the format has. What these check is the thing that was wrong for months: the
 * validator was written, tested, and wired to nothing, so a deck that broke every rule in the
 * format started a game without a word.
 *
 * <p>And the other half, which matters more: a check that cannot be made must not refuse.
 * Turning "I do not know what this card is" into "your deck is illegal" would be the mod
 * inventing a rules violation, which is the one thing it promises never to do.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckCheckGameTest {

    @GameTest(template = "empty")
    public static void aDeckTooSmallForItsFormatIsCaught(GameTestHelper helper) {
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("No card service running, so the deck check cannot be exercised");
            return;
        }
        CardMetadata forest = cache(cards, "Forest", "Basic Land - Forest");

        // Thirty cards where Modern wants sixty.
        DeckComponent deck = new DeckComponent("Too small", "", Optional.empty(),
                copies(forest.scryfallId(), 30), List.of(), List.of());

        ValidationResult result = DeckCheck.of(deck, FormatPresets.MODERN).orElse(null);
        if (result == null) {
            helper.fail("The check gave no opinion on a deck it had every card for");
            return;
        }
        if (result.isLegal()) {
            helper.fail("A thirty-card deck passed a sixty-card format");
            return;
        }
        if (result.errors().stream().noneMatch(issue -> issue.code().equals("deck_too_small"))) {
            helper.fail("The deck was refused for the wrong reason: " + result.errors());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDeckTheCheckCannotJudgeIsNotRefused(GameTestHelper helper) {
        // A card this server has never looked up. There is nothing to check it against, so
        // there is nothing to say about the deck it is in - and a game has to be able to start.
        DeckComponent deck = new DeckComponent("Unknown", "", Optional.empty(),
                copies(UUID.fromString("00000000-0000-4000-8000-0000000000ff"), 30),
                List.of(), List.of());

        if (DeckCheck.of(deck, FormatPresets.MODERN).isPresent()) {
            helper.fail("The check claimed an opinion on a card it had never seen");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTableWithNoFormatIsNeverRefused(GameTestHelper helper) {
        // Free play skips the check entirely, which is the fence the design draws round it.
        DeckComponent deck = new DeckComponent("Anything", "", Optional.empty(),
                copies(UUID.fromString("00000000-0000-4000-8000-0000000000fe"), 1),
                List.of(), List.of());

        if (DeckCheck.of(deck, null).isPresent()) {
            helper.fail("A table with no format still checked a deck");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTableRefusesADeckThatFailsTheCheck(GameTestHelper helper) {
        // The two lines that were missing. Everything above tests the check; this tests that
        // anybody consults it before a deck goes down.
        CardDataService cards = CardDataService.active().orElse(null);
        if (cards == null) {
            helper.fail("No card service running, so the deck check cannot be exercised");
            return;
        }
        CardMetadata forest = cache(cards, "Forest", "Basic Land - Forest");
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(origin, dev.gathering.item.GatheringContent.TABLE.get()
                .defaultBlockState(), 3);
        for (dev.gathering.block.TablePart part : dev.gathering.block.TablePart.values()) {
            helper.getLevel().setBlock(part.offsetFrom(origin),
                    dev.gathering.item.GatheringContent.TABLE.get().defaultBlockState()
                            .setValue(dev.gathering.block.TableBlock.PART, part), 3);
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        dev.gathering.block.TableSeats.take(helper.getLevel(), origin,
                new dev.gathering.core.table.TableCell(0, 0),
                dev.gathering.core.table.Side.NORTH, player.getUUID());
        dev.gathering.block.TableSessions.start(helper.getLevel(), origin,
                dev.gathering.core.match.MatchRules.single(FormatPresets.MODERN));

        DeckComponent tooSmall = new DeckComponent("Too small", "", Optional.empty(),
                copies(forest.scryfallId(), 30), List.of(), List.of());

        // Nobody named this format yet, so the check is a note rather than a door.
        if (!dev.gathering.block.TableBlock.deckMayGoDown(
                helper.getLevel(), origin, tooSmall, player)) {
            helper.fail("A table nobody chose a format for refused a deck");
            return;
        }

        dev.gathering.block.TableBlock.entityAt(helper.getLevel(), origin)
                .orElseThrow()
                .formatWasChosen(true);
        if (dev.gathering.block.TableBlock.deckMayGoDown(
                helper.getLevel(), origin, tooSmall, player)) {
            helper.fail("A thirty-card deck was allowed onto a table somebody set to Modern");
            return;
        }

        DeckComponent bigEnough = new DeckComponent("Big enough", "", Optional.empty(),
                copies(forest.scryfallId(), 60), List.of(), List.of());
        if (!dev.gathering.block.TableBlock.deckMayGoDown(
                helper.getLevel(), origin, bigEnough, player)) {
            helper.fail("A legal deck was refused");
        }
        helper.succeed();
    }

    /** Puts a card in the running server's cache, in memory only, and hands it back. */
    private static CardMetadata cache(CardDataService cards, String name, String typeLine) {
        CardMetadata card = new CardMetadata(
                UUID.nameUUIDFromBytes(("gathering-test:" + name).getBytes()),
                UUID.nameUUIDFromBytes(("gathering-test-oracle:" + name).getBytes()),
                name, "", 0, typeLine, "", Set.of(), Set.of("G"), List.of(),
                "normal", "tst", "Test", "1", Rarity.COMMON,
                false, false, true, false, false, List.of("paper"),
                everyFormatLegal(), Map.of(), "");
        cards.store().store(card, null);
        return card;
    }

    /** Legal everywhere, so a test about deck size is not also a test about ban lists. */
    private static Map<String, Legality> everyFormatLegal() {
        Map<String, Legality> legalities = new java.util.LinkedHashMap<>();
        for (var preset : FormatPresets.all()) {
            legalities.put(preset.legalitiesKey(), Legality.LEGAL);
        }
        return legalities;
    }

    private static List<CardComponent> copies(UUID printing, int howMany) {
        List<CardComponent> cards = new ArrayList<>(howMany);
        for (int index = 0; index < howMany; index++) {
            cards.add(new CardComponent(Optional.of(printing), false, Optional.empty(), false));
        }
        return cards;
    }
}
