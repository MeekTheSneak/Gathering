package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageUris;
import dev.gathering.core.card.Rarity;
import dev.gathering.network.CardSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * How many pictures a card is.
 * <p>"Two faces" and "two sides" are not the same thing, and treating them as the same draws
 * a split card as two copies of one picture side by side. A split, flip, adventure or
 * aftermath card is two lots of rules text printed on one piece of card; a transform card is
 * two of everything.
 * <p>Scryfall says which is which by where it puts the art - on the card for the first kind,
 * on each face for the second - so that is what this checks, rather than a layout string
 * that would need a new case for every set.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CardSummaryGameTest {

    private static final UUID FIRE_ICE = UUID.fromString("cf16f6ab-31c9-4beb-b6c5-e63e0e5b3b91");
    private static final UUID DELVER = UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a");

    @GameTest(template = "empty")
    public static void aSplitCardIsOnePictureAndTwoLotsOfText(GameTestHelper helper) {
        CardSummary summary = CardSummary.of(splitCard());

        if (summary.faces().size() != 2) {
            helper.fail("A split card has two faces of text, got " + summary.faces().size());
        }
        if (summary.printedSides().size() != 1) {
            helper.fail("A split card is one picture, but " + summary.printedSides().size()
                    + " would be drawn");
        }
        String drawn = summary.printedSides().get(0).readableImage().orElse("");
        if (!drawn.equals("https://example.invalid/fire-ice-normal.jpg")) {
            helper.fail("The card's own art was not used for the side that gets drawn: " + drawn);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aTransformCardIsTwoPictures(GameTestHelper helper) {
        CardSummary summary = CardSummary.of(transformCard());

        if (summary.printedSides().size() != 2) {
            helper.fail("A transform card is two pictures, got " + summary.printedSides().size());
        }
        if (summary.printedSides().get(0).readableImage().equals(
                summary.printedSides().get(1).readableImage())) {
            helper.fail("Both sides of a transform card would draw the same picture");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anOrdinaryCardIsOnePicture(GameTestHelper helper) {
        CardSummary summary = CardSummary.of(ordinaryCard());

        if (summary.printedSides().size() != 1) {
            helper.fail("An ordinary card is one picture, got " + summary.printedSides().size());
        }
        if (summary.isDoubleFaced()) {
            helper.fail("An ordinary card claims a back face");
        }
        helper.succeed();
    }

    /**
     * Fire // Ice, as the codec produces it: one printed image, given to the front face,
     * with the second face carrying text and no art of its own.
     */
    private static CardMetadata splitCard() {
        return card(
                FIRE_ICE,
                "Fire // Ice",
                "split",
                List.of(
                        face("Fire", "{1}{R}", new ImageUris(
                                "https://example.invalid/fire-ice-small.jpg",
                                "https://example.invalid/fire-ice-normal.jpg",
                                null, null, null, null)),
                        face("Ice", "{1}{U}", ImageUris.EMPTY)));
    }

    /** Delver of Secrets: two physical sides, so the art is on each face. */
    private static CardMetadata transformCard() {
        return card(
                DELVER,
                "Delver of Secrets // Insectile Aberration",
                "transform",
                List.of(
                        face("Delver of Secrets", "{U}", new ImageUris(
                                "https://example.invalid/delver-small.jpg",
                                "https://example.invalid/delver-normal.jpg",
                                null, null, null, null)),
                        face("Insectile Aberration", "", new ImageUris(
                                "https://example.invalid/aberration-small.jpg",
                                "https://example.invalid/aberration-normal.jpg",
                                null, null, null, null))));
    }

    private static CardMetadata ordinaryCard() {
        return card(
                UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba"),
                "Sol Ring",
                "normal",
                List.of(face("Sol Ring", "{1}", new ImageUris(
                        "https://example.invalid/sol-ring-small.jpg",
                        "https://example.invalid/sol-ring-normal.jpg",
                        null, null, null, null))));
    }

    private static CardFace face(String name, String manaCost, ImageUris images) {
        return new CardFace(name, manaCost, "Instant", "Does a thing.", "", "", "", "", "", images);
    }

    private static CardMetadata card(UUID id, String name, String layout, List<CardFace> faces) {
        return new CardMetadata(
                id, id, name, "{1}{R}", 2.0, "Instant", "Does a thing.",
                Set.of(), Set.of(), faces, layout, "apc", "Apocalypse", "128",
                Rarity.UNCOMMON, false, true, true, false, false,
                List.of("paper"), Map.of(), Map.of(), "https://scryfall.com/");
    }
}
