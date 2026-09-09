package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.item.DraftedPool;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a deck tells the room, and what it keeps to itself.
 * <p>An item component is synchronized to every client that can see the item, so carrying a
 * deck to a table handed the whole list to everybody there - and hiding the tooltip protects
 * nothing at all from a client that reads what it was sent. What crosses now is the box: the
 * name, the note on it, the colour, the sleeves, the commanders, and how thick each part is.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeckPrivacyGameTest {

    /** The deck everybody else sees has the counts and none of the cards. */
    @GameTest(template = "empty")
    public static void adeckOnTheWireNamesNoCards(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        DeckComponent deck = new DeckComponent(
                "Ana's Deck", "the good one", Optional.of(owner),
                cards(60), cards(1), cards(15),
                Optional.of(0xFF884422), dev.gathering.core.card.Sleeve.DEFAULT);

        DeckComponent seen = roundTrip(helper, deck, DeckComponent.PUBLIC_STREAM_CODEC);

        for (CardComponent card : seen.entries()) {
            if (card.scryfallId().isPresent()) {
                helper.fail("A main-deck card crossed the wire by name: " + card.scryfallId().get());
                return;
            }
        }
        for (CardComponent card : seen.sideboard()) {
            if (card.scryfallId().isPresent()) {
                helper.fail("A sideboard card crossed the wire by name: " + card.scryfallId().get());
                return;
            }
        }
        // And what is public is still there, or the deck stops being a deck on the shelf.
        if (!seen.name().equals("Ana's Deck") || !seen.description().equals("the good one")) {
            helper.fail("The deck lost its name or its note: " + seen.name());
            return;
        }
        if (seen.entries().size() != 60 || seen.sideboard().size() != 15) {
            helper.fail("The deck's thickness changed on the wire: " + seen.entries().size()
                    + " and " + seen.sideboard().size());
            return;
        }
        if (seen.commanders().size() != 1 || seen.commanders().get(0).scryfallId().isEmpty()) {
            helper.fail("A commander is public and did not cross");
            return;
        }
        if (!seen.color().equals(deck.color()) || !seen.owner().equals(deck.owner())) {
            helper.fail("The deck box changed colour or owner on the wire");
            return;
        }
        if (!seen.isRedacted()) {
            helper.fail("The public copy does not know it is one");
            return;
        }
        helper.succeed();
    }

    /** The owner's own copy, which travels addressed to them, is the whole deck. */
    @GameTest(template = "empty")
    public static void theOwnersCopyIsTheWholeDeck(GameTestHelper helper) {
        DeckComponent deck = new DeckComponent(
                "Ana's Deck", "", Optional.empty(), cards(4), List.of(), cards(2),
                Optional.empty(), dev.gathering.core.card.Sleeve.DEFAULT);

        DeckComponent mine = roundTrip(helper, deck, DeckComponent.STREAM_CODEC);

        if (!mine.equals(deck)) {
            helper.fail("The owner's own copy of a deck changed on the wire");
            return;
        }
        if (mine.isRedacted()) {
            helper.fail("The owner's own copy reads as the public one");
            return;
        }
        helper.succeed();
    }

    /** A drafted pool is the one thing a drafter knows that the table does not. */
    @GameTest(template = "empty")
    public static void apoolOnTheWireIsACount(GameTestHelper helper) {
        DraftedPool pool = new DraftedPool(cards(45), "pod-1");

        DraftedPool seen = roundTrip(helper, pool, DraftedPool.PUBLIC_STREAM_CODEC);

        if (seen.size() != 45 || !seen.fromPod().equals("pod-1")) {
            helper.fail("A pool lost its size or its pod: " + seen.size() + " from " + seen.fromPod());
            return;
        }
        for (CardComponent card : seen.cards()) {
            if (card.scryfallId().isPresent()) {
                helper.fail("A drafted card crossed the wire by name");
                return;
            }
        }
        helper.succeed();
    }

    private static List<CardComponent> cards(int howMany) {
        List<CardComponent> made = new ArrayList<>(howMany);
        for (int index = 0; index < howMany; index++) {
            made.add(new CardComponent(
                    Optional.of(UUID.randomUUID()), false, Optional.empty(), false));
        }
        return made;
    }

    private static <T> T roundTrip(
            GameTestHelper helper, T value,
            net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        codec.encode(buffer, value);
        T back = codec.decode(buffer);
        if (buffer.readableBytes() != 0) {
            helper.fail(value.getClass().getSimpleName() + " left "
                    + buffer.readableBytes() + " bytes unread");
        }
        return back;
    }
}
