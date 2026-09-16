package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DeckItem;
import dev.gathering.network.DeckMadePayload;
import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.DeckEdits;
import dev.gathering.server.DeckVault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A deck made by putting two cards together in the creative menu keeps its cards.
 *
 * <p>The owner reported this three times. Two cards right-clicked together in the creative menu made
 * a deck whose every row said it was still loading, and taking a card out of it gave back nothing.
 *
 * <p>The cause was not in any of the places it was looked for. The creative menu never replays the
 * click on the server - it sends the resulting <em>stack</em> - and a deck component has one wire
 * format, which replaces every card in it with a stand-in so that carrying a deck past somebody does
 * not hand them your list. That format is symmetric, so it redacted the deck on the way <em>to</em>
 * the server as well. What arrived was a deck of stand-ins under a handle nothing had ever seen, and
 * the real list existed nowhere: not on the item, not in the vault, not on the wire. The repair that
 * already existed for a redacted deck had nothing to repair it from.
 *
 * <p>So the client says what it made, before the stack follows it. What is checked here is that the
 * vault ends up holding the real cards, that a redacted copy arriving afterwards is put back
 * together from them, and that this is believed only from a player who could have conjured the cards
 * anyway.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckMadeInCreativeGameTest {

    private static final UUID BOLT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BEAR = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static DeckComponent twoCards() {
        return new DeckComponent("", "", Optional.empty(),
                List.of(new CardComponent(Optional.of(BOLT), false, Optional.empty(), false),
                        new CardComponent(Optional.of(BEAR), false, Optional.empty(), false)),
                List.of(), List.of());
    }

    /** The same deck as every client that can see the item is sent it: thickness, and no names. */
    private static DeckComponent asItCrossesTheWire(DeckComponent deck) {
        return new DeckComponent(deck.name(), deck.description(), deck.owner(),
                java.util.Collections.nCopies(deck.entries().size(), CardComponent.HIDDEN),
                deck.commanders(), deck.sideboard(), deck.color(), deck.sleeve());
    }

    @GameTest(template = "empty")
    public static void adeckMadeInCreativeArrivesWithItsCards(GameTestHelper helper) {
        ServerPlayer maker = helper.makeMockServerPlayerInLevel();
        maker.setGameMode(GameType.CREATIVE);
        UUID handle = UUID.randomUUID();
        DeckComponent real = twoCards();

        // What the client says during the click, ahead of the stack the menu sends after it.
        DeckEdits.made(maker, new DeckMadePayload(handle, real));

        // And then the stack itself, with its cards gone, which is all the menu can send.
        DeckComponent redacted = asItCrossesTheWire(real);
        if (!redacted.isRedacted()) {
            helper.fail("the stand-in copy this test is built on is not actually redacted");
            return;
        }
        DeckComponent recovered = DeckVault.real(handle, redacted).orElse(null);
        if (recovered == null) {
            helper.fail("a deck made in the creative menu could not be put back together at all");
            return;
        }
        if (recovered.isRedacted()) {
            helper.fail("a deck made in the creative menu came back still hidden");
            return;
        }
        if (recovered.entries().size() != 2) {
            helper.fail("a deck of two came back holding " + recovered.entries().size());
            return;
        }
        // The identities, not just the count: a deck of the right number of wrong cards would
        // list two names and hand out somebody else's card.
        if (!recovered.entries().equals(real.entries())) {
            helper.fail("a deck made in the creative menu came back holding other cards: "
                    + recovered.entries());
            return;
        }
        helper.succeed();
    }

    /**
     * A player who is not in creative is not believed.
     * <p>They do not need to be: the server runs their click itself and builds the deck out of its
     * own cards. Believing them would be a second way to make a deck, and the only one where the
     * cards in it are whatever a client said they were.
     */
    @GameTest(template = "empty")
    public static void aplayerNotInCreativeIsNotBelieved(GameTestHelper helper) {
        ServerPlayer maker = helper.makeMockServerPlayerInLevel();
        maker.setGameMode(GameType.SURVIVAL);
        UUID handle = UUID.randomUUID();

        DeckEdits.made(maker, new DeckMadePayload(handle, twoCards()));

        if (DeckVault.real(handle, asItCrossesTheWire(twoCards())).isPresent()) {
            helper.fail("a survival player minted a deck's contents by saying what was in it");
            return;
        }
        helper.succeed();
    }

    /**
     * A copy that is already hidden is not remembered.
     * <p>That is a client passing on what it was given rather than something it made, and writing
     * stand-ins into the vault would overwrite the truth with them.
     */
    @GameTest(template = "empty")
    public static void ahiddenCopyIsNotRemembered(GameTestHelper helper) {
        ServerPlayer maker = helper.makeMockServerPlayerInLevel();
        maker.setGameMode(GameType.CREATIVE);
        UUID handle = UUID.randomUUID();
        DeckComponent real = twoCards();

        DeckEdits.made(maker, new DeckMadePayload(handle, real));
        // And now the same handle again, this time with the cards already gone.
        DeckEdits.made(maker, new DeckMadePayload(handle, asItCrossesTheWire(real)));

        DeckComponent recovered = DeckVault.real(handle, asItCrossesTheWire(real)).orElse(null);
        if (recovered == null || !recovered.entries().equals(real.entries())) {
            helper.fail("a hidden copy overwrote what the vault knew: " + recovered);
            return;
        }
        helper.succeed();
    }

    /** And the whole trip on a real item, which is the shape the owner actually met it in. */
    @GameTest(template = "empty")
    public static void theItemItselfComesBackWhole(GameTestHelper helper) {
        ServerPlayer maker = helper.makeMockServerPlayerInLevel();
        maker.setGameMode(GameType.CREATIVE);
        DeckComponent real = twoCards();

        ItemStack made = DeckItem.of(real);
        UUID handle = DeckItem.handleOf(made).orElse(null);
        if (handle == null) {
            helper.fail("a deck built from two cards carries no handle to remember it by");
            return;
        }
        DeckEdits.made(maker, new DeckMadePayload(handle, real));

        // The stack as the creative menu delivers it: same handle, cards gone.
        ItemStack arrived = made.copy();
        arrived.set(GatheringComponents.DECK.get(), asItCrossesTheWire(real));
        maker.getInventory().setItem(0, arrived);
        arrived.getItem().inventoryTick(arrived, helper.getLevel(), maker, 0, false);

        DeckComponent now = DeckItem.deckOf(maker.getInventory().getItem(0)).orElse(null);
        if (now == null) {
            helper.fail("the deck item was thrown away on its way through the creative menu");
            return;
        }
        if (now.isRedacted() || !now.entries().equals(real.entries())) {
            helper.fail("the deck item still holds stand-ins after a tick: " + now.entries());
            return;
        }
        helper.succeed();
    }
}
