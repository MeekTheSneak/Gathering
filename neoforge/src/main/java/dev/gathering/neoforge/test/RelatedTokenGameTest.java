package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageUris;
import dev.gathering.core.card.Rarity;
import dev.gathering.core.card.RelatedCard;
import dev.gathering.network.CardSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The tokens a card makes, on their way to the client that draws the menu row.
 * <p>The names come off Scryfall on the server and the row that offers them is on the client,
 * so the only thing between the two is this codec. It grew an eighth component to carry them,
 * past the point where {@code StreamCodec.composite} can be used, which means the two halves
 * are written by hand and can disagree - so the round trip is checked byte for byte.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RelatedTokenGameTest {

    @GameTest(template = "empty")
    public static void theTokensACardMakesReachTheClient(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        CardSummary summary = CardSummary.of(cardMaking(id,
                new RelatedCard(UUID.randomUUID(), "Thrull", "Token Creature - Thrull", "token"),
                new RelatedCard(id, "Tevesh Szat", "Legendary Planeswalker - Szat",
                        "combo_piece")));

        if (!summary.makes().equals(List.of("Thrull"))) {
            helper.fail("the summary offers " + summary.makes() + " rather than just the Thrull");
            return;
        }
        roundTrip(helper, summary);
    }

    @GameTest(template = "empty")
    public static void aCardThatMakesNothingSaysSo(GameTestHelper helper) {
        CardSummary summary = CardSummary.of(cardMaking(UUID.randomUUID()));

        if (!summary.makes().isEmpty()) {
            helper.fail("a card with no related printings offers " + summary.makes());
            return;
        }
        roundTrip(helper, summary);
    }

    /**
     * A printing claiming more tokens than the wire allows is trimmed, not refused.
     * <p>The count is read off a socket before the names are, so the reader caps it. If the
     * writer did not cap it too, the two would disagree by exactly the overflow and every
     * card after this one in the same packet would be read as garbage.
     */
    @GameTest(template = "empty")
    public static void aRidiculousCardIsTrimmedOnBothSides(GameTestHelper helper) {
        List<RelatedCard> tooMany = new ArrayList<>();
        for (int index = 0; index < CardSummary.MOST_TOKENS + 5; index++) {
            tooMany.add(new RelatedCard(
                    UUID.randomUUID(), "Token " + index, "Token Creature - Thing", "token"));
        }
        CardSummary summary = CardSummary.of(
                cardMaking(UUID.randomUUID(), tooMany.toArray(new RelatedCard[0])));

        if (summary.makes().size() != CardSummary.MOST_TOKENS) {
            helper.fail("the wire cap is " + CardSummary.MOST_TOKENS + " and the summary carries "
                    + summary.makes().size());
            return;
        }
        roundTrip(helper, summary);
    }

    private static void roundTrip(GameTestHelper helper, CardSummary summary) {
        net.minecraft.network.RegistryFriendlyByteBuf buffer =
                new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(),
                        helper.getLevel().registryAccess());
        try {
            CardSummary.STREAM_CODEC.encode(buffer, summary);
            CardSummary back = CardSummary.STREAM_CODEC.decode(buffer);
            if (!back.makes().equals(summary.makes())) {
                helper.fail("the tokens came back off the wire as " + back.makes()
                        + " rather than " + summary.makes());
                return;
            }
            if (!back.equals(summary)) {
                helper.fail("a card came back off the wire different: " + back.name());
                return;
            }
            if (buffer.readableBytes() != 0) {
                helper.fail(buffer.readableBytes() + " byte(s) of a card were written and never "
                        + "read, so the two halves of the codec disagree");
                return;
            }
            helper.succeed();
        } finally {
            buffer.release();
        }
    }

    private static CardMetadata cardMaking(UUID id, RelatedCard... parts) {
        return new CardMetadata(
                id, id, "Tevesh Szat, Doom of Fools", "{4}{B}", 5.0,
                "Legendary Planeswalker - Szat", "+2: Create two 1/1 black Thrull tokens.",
                Set.of("B"), Set.of("B"),
                List.of(new CardFace("Tevesh Szat, Doom of Fools", "{4}{B}",
                        "Legendary Planeswalker - Szat", "+2: Create two Thrulls.", "", "", "5",
                        "", "", ImageUris.EMPTY)),
                "normal", "cmr", "Commander Legends", "290", Rarity.MYTHIC,
                false, true, true, false, false, List.of("paper"), Map.of(), Map.of(),
                "https://scryfall.com/", List.of(parts));
    }
}
