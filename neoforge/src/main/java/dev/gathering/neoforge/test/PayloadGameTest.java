package dev.gathering.neoforge.test;

import dev.gathering.Gathering;
import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.decklist.DeckSection;
import dev.gathering.core.decklist.DecklistEntry;
import dev.gathering.core.deck.ResolvedCard;
import dev.gathering.core.deck.ResolvedDeck;
import dev.gathering.item.CardComponent;
import dev.gathering.item.DeckComponent;
import dev.gathering.network.CardFaceSummary;
import dev.gathering.network.CardMetadataPayload;
import dev.gathering.network.CardSummary;
import dev.gathering.network.DeckEditPayload;
import dev.gathering.network.ImportDecklistPayload;
import dev.gathering.network.ImportResultPayload;
import dev.gathering.network.OpenImportScreenPayload;
import dev.gathering.server.DecklistImport;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Every payload, written and read back against the server's real registries.
 *
 * <p>A stream codec that encodes fine and decodes wrong is invisible until two people try
 * to play, so each one gets a round trip here rather than a reading. The buffer is also
 * asserted empty afterwards, which is what catches a codec that writes more than it reads
 * - the failure mode that corrupts every packet after it rather than just its own.
 */
@GameTestHolder(Gathering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PayloadGameTest {

    private static final UUID SOL_RING = UUID.fromString("5805f64c-dd88-4e94-8f0a-a01dae67e3ba");

    @GameTest(template = "empty")
    public static void importRequestRoundTrips(GameTestHelper helper) {
        String decklist = """
                Commander
                1 Halana and Alena, Partners (VOW) 239

                Deck
                1 Sol Ring (LTC) 284 *F*
                """;

        ImportDecklistPayload restored = roundTrip(
                helper,
                new ImportDecklistPayload(decklist, "Halana and Tevesh", "Two commanders, one bad idea"),
                ImportDecklistPayload.STREAM_CODEC);

        if (!restored.decklist().equals(decklist)) {
            helper.fail("A decklist changed on the wire");
        }
        if (!restored.deckName().equals("Halana and Tevesh")) {
            helper.fail("A deck name changed on the wire: " + restored.deckName());
        }
        if (!restored.description().equals("Two commanders, one bad idea")) {
            helper.fail("A deck description changed on the wire: " + restored.description());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void metadataRequestsRoundTrip(GameTestHelper helper) {
        List<UUID> printings = List.of(SOL_RING, UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a"));

        dev.gathering.network.RequestCardMetadataPayload restored = roundTrip(
                helper,
                new dev.gathering.network.RequestCardMetadataPayload(printings),
                dev.gathering.network.RequestCardMetadataPayload.STREAM_CODEC);

        if (!restored.printings().equals(printings)) {
            helper.fail("A metadata request changed on the wire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void importResultRoundTripsIncludingProblems(GameTestHelper helper) {
        ImportResultPayload payload = new ImportResultPayload(
                "Halana and Tevesh", 99, List.of("line 4: no card name found (0 Sol Ring)"));

        ImportResultPayload restored = roundTrip(helper, payload, ImportResultPayload.STREAM_CODEC);

        if (!restored.equals(payload)) {
            helper.fail("An import result changed on the wire: " + restored);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cardMetadataRoundTripsForBothFaces(GameTestHelper helper) {
        CardSummary singleFaced = new CardSummary(
                SOL_RING,
                new CardFaceSummary("Sol Ring", "{1}", "Artifact", "{T}: Add {C}{C}.", "small", "normal"),
                Optional.empty());
        CardSummary doubleFaced = new CardSummary(
                UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a"),
                new CardFaceSummary("Delver of Secrets", "{U}", "Creature", "At the beginning...", "s1", "n1"),
                Optional.of(new CardFaceSummary("Insectile Aberration", "", "Creature", "Flying", "s2", "n2")));

        CardMetadataPayload payload = new CardMetadataPayload(List.of(singleFaced, doubleFaced));
        CardMetadataPayload restored = roundTrip(helper, payload, CardMetadataPayload.STREAM_CODEC);

        if (!restored.equals(payload)) {
            helper.fail("Card metadata changed on the wire");
        }
        if (!restored.cards().get(1).isDoubleFaced()) {
            helper.fail("The back face did not survive the wire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void askingForBasicLandsNamesATypeAndNeverACard(GameTestHelper helper) {
        // The one card that reaches a deck without coming from anywhere, so the one place a
        // client could name any card in Magic and be handed it. It names a type instead, and
        // the count is clamped on the way in rather than trusted: a request for two billion
        // Forests must be refused rather than allocated.
        for (dev.gathering.network.AddBasicsPayload.Basic land
                : dev.gathering.network.AddBasicsPayload.Basic.values()) {
            dev.gathering.network.AddBasicsPayload asked =
                    new dev.gathering.network.AddBasicsPayload(true, land, 3);
            dev.gathering.network.AddBasicsPayload restored = roundTrip(
                    helper, asked, dev.gathering.network.AddBasicsPayload.STREAM_CODEC);
            if (!restored.equals(asked)) {
                helper.fail("A request for " + land + " changed on the wire: " + restored);
                return;
            }
            if (!restored.land().cardName().equals(land.cardName())) {
                helper.fail("A basic land lost its name on the wire: " + restored.land());
                return;
            }
        }

        int most = dev.gathering.network.AddBasicsPayload.MOST_AT_ONCE;
        dev.gathering.network.AddBasicsPayload huge = new dev.gathering.network.AddBasicsPayload(
                false, dev.gathering.network.AddBasicsPayload.Basic.FOREST, Integer.MAX_VALUE);
        if (huge.howMany() != most) {
            helper.fail("A request for two billion Forests came through as " + huge.howMany());
            return;
        }
        dev.gathering.network.AddBasicsPayload none = new dev.gathering.network.AddBasicsPayload(
                false, dev.gathering.network.AddBasicsPayload.Basic.ISLAND, -5);
        if (none.howMany() != 1) {
            helper.fail("A request for minus five Islands came through as " + none.howMany());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aRunTooLongForOnePacketIsSplitAndNothingIsLost(GameTestHelper helper) {
        // A cube import and a table whose four graveyards are full both come to more
        // summaries than the game will write in one custom payload, and a payload it refuses
        // to write disconnects the player it was for. So the senders split, and what a
        // client puts together from the pieces has to be exactly what was handed over: same
        // cards, same order, none dropped at a seam and none sent twice.
        int many = CardMetadataPayload.MOST_PER_PACKET * 2 + 1;
        List<CardSummary> summaries = new java.util.ArrayList<>(many);
        for (int index = 0; index < many; index++) {
            summaries.add(new CardSummary(
                    UUID.nameUUIDFromBytes(("printing-" + index).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)),
                    new CardFaceSummary("Card " + index, "{1}", "Artifact", "", "s", "n"),
                    Optional.empty()));
        }

        List<CardMetadataPayload> packets = CardMetadataPayload.inPackets(summaries);
        if (packets.size() != 3) {
            helper.fail(many + " summaries went out as " + packets.size() + " packets");
        }
        for (CardMetadataPayload packet : packets) {
            if (packet.cards().size() > CardMetadataPayload.MOST_PER_PACKET) {
                helper.fail("a packet carried " + packet.cards().size() + " summaries");
            }
            if (packet.cards().isEmpty()) {
                helper.fail("a packet carried nothing at all");
            }
        }

        List<CardSummary> arrived = new java.util.ArrayList<>(many);
        for (CardMetadataPayload packet : packets) {
            arrived.addAll(roundTrip(helper, packet, CardMetadataPayload.STREAM_CODEC).cards());
        }
        if (!arrived.equals(summaries)) {
            helper.fail("what arrived over " + packets.size() + " packets was not what was sent: "
                    + arrived.size() + " of " + many);
        }

        // And a run that fits still goes as one, so the split is not a cost every send pays.
        if (CardMetadataPayload.inPackets(summaries.subList(0, 1)).size() != 1) {
            helper.fail("a single summary was split");
        }
        if (!CardMetadataPayload.inPackets(List.of()).isEmpty()) {
            helper.fail("nothing at all was sent as a packet");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deckEditsRoundTripForEveryHandActionAndSection(GameTestHelper helper) {
        // Three small enums on the wire: an off-by-one in any of them silently edits the
        // wrong pile, which reads as "the game lost my card" rather than as a protocol bug.
        CardComponent card = CardComponent.of(CardIdentity.ofPrinting(SOL_RING, true));
        for (boolean offHand : new boolean[] {false, true}) {
            for (DeckEditPayload.Action action : DeckEditPayload.Action.values()) {
                for (DeckComponent.Section from : DeckComponent.Section.values()) {
                    for (DeckComponent.Section to : DeckComponent.Section.values()) {
                        DeckEditPayload payload = new DeckEditPayload(offHand, action, from, to, card);
                        DeckEditPayload restored =
                                roundTrip(helper, payload, DeckEditPayload.STREAM_CODEC);
                        if (!restored.equals(payload)) {
                            helper.fail("A deck edit changed on the wire: " + payload
                                    + " became " + restored);
                        }
                        if (restored.hand() != payload.hand()) {
                            helper.fail("A deck edit changed hands on the wire");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void openImportScreenRoundTrips(GameTestHelper helper) {
        roundTrip(helper, OpenImportScreenPayload.INSTANCE, OpenImportScreenPayload.STREAM_CODEC);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void quantitiesBecomeIndividualCards(GameTestHelper helper) {
        // Four copies is four objects on a table, not one entry with a number on it.
        ResolvedDeck deck = new ResolvedDeck(
                "Test",
                List.of(
                        resolved(SOL_RING, 1, DeckSection.COMMANDER),
                        resolved(SOL_RING, 4, DeckSection.MAINBOARD),
                        resolved(SOL_RING, 2, DeckSection.SIDEBOARD)),
                List.of(),
                List.of());

        DeckComponent component = DecklistImport.toComponent(deck, UUID.randomUUID());

        if (component.commanders().size() != 1) {
            helper.fail("Expected one commander, got " + component.commanders().size());
        }
        if (component.entries().size() != 4) {
            helper.fail("Expected four mainboard cards, got " + component.entries().size());
        }
        if (component.sideboard().size() != 2) {
            helper.fail("Expected two sideboard cards, got " + component.sideboard().size());
        }
        if (component.deckSize() != 5) {
            helper.fail("The deck proper should not count the sideboard; got " + component.deckSize());
        }
        helper.succeed();
    }

    private static ResolvedCard resolved(UUID id, int quantity, DeckSection section) {
        DecklistEntry entry = new DecklistEntry(
                quantity, "Sol Ring", "LTC", "284", false, section, 1, quantity + " Sol Ring");
        return new ResolvedCard(
                CardIdentity.ofPrinting(id, false),
                metadataFor(id),
                quantity,
                section,
                entry,
                false);
    }

    private static dev.gathering.core.card.CardMetadata metadataFor(UUID id) {
        return new dev.gathering.core.card.CardMetadata(
                id, id, "Sol Ring", "{1}", 1.0, "Artifact", "{T}: Add {C}{C}.",
                java.util.Set.of(), java.util.Set.of(), List.of(), "normal",
                "ltc", "Commander: The Lord of the Rings", "284",
                dev.gathering.core.card.Rarity.UNCOMMON,
                false, true, true, false, false, List.of("paper"),
                java.util.Map.of(), java.util.Map.of(), "https://scryfall.com/card/ltc/284");
    }

    /** Writes, reads back, and insists the buffer is fully consumed. */
    private static <T> T roundTrip(
            GameTestHelper helper, T payload, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        codec.encode(buffer, payload);
        T restored = codec.decode(buffer);

        if (buffer.readableBytes() != 0) {
            throw new GameTestAssertException(
                    payload.getClass().getSimpleName() + " left " + buffer.readableBytes()
                            + " bytes unread, which would corrupt every packet after it");
        }
        return restored;
    }
}
