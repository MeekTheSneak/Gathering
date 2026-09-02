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
        if (restored.from().isPresent()) {
            helper.fail("An import out of nothing arrived naming a collection");
        }

        // And the same list built out of a collection, which is the same request with the
        // cards having to come from somewhere.
        net.minecraft.core.BlockPos box = new net.minecraft.core.BlockPos(-2048, 71, 4096);
        ImportDecklistPayload fromBox = roundTrip(
                helper,
                new ImportDecklistPayload(decklist, "Burn", "", java.util.Optional.of(box)),
                ImportDecklistPayload.STREAM_CODEC);
        if (!fromBox.from().map(box::equals).orElse(false)) {
            helper.fail("The collection to build from changed on the wire: " + fromBox.from());
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
                SOL_RING,
                new CardFaceSummary("Sol Ring", "{1}", "Artifact", "{T}: Add {C}{C}.", "", "small", "normal", ""),
                Optional.empty(),
                dev.gathering.core.card.Rarity.UNCOMMON,
                1.0,
                java.util.Set.of());
        CardSummary doubleFaced = new CardSummary(
                UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a"),
                UUID.fromString("11bf83bb-c95b-4b4f-9a56-ce7a1816307a"),
                new CardFaceSummary("Delver of Secrets", "{U}", "Creature", "At the beginning...", "1/1", "s1", "n1", ""),
                Optional.of(new CardFaceSummary("Insectile Aberration", "", "Creature", "Flying", "3/2", "s2", "n2", "")),
                dev.gathering.core.card.Rarity.MYTHIC,
                1.0,
                java.util.Set.of("U"));

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
        for (dev.gathering.core.card.BasicLand land
                : dev.gathering.core.card.BasicLand.values()) {
            dev.gathering.network.AddBasicsPayload asked =
                    new dev.gathering.network.AddBasicsPayload(true, land, 3);
            dev.gathering.network.AddBasicsPayload restored = roundTrip(
                    helper, asked, dev.gathering.network.AddBasicsPayload.STREAM_CODEC);
            if (!restored.equals(asked)) {
                helper.fail("A request for " + land + " changed on the wire: " + restored);
                return;
            }
            if (!restored.land().printedName().equals(land.printedName())) {
                helper.fail("A basic land lost its name on the wire: " + restored.land());
                return;
            }
        }

        int most = dev.gathering.network.AddBasicsPayload.MOST_AT_ONCE;
        dev.gathering.network.AddBasicsPayload huge = new dev.gathering.network.AddBasicsPayload(
                false, dev.gathering.core.card.BasicLand.FOREST, Integer.MAX_VALUE);
        if (huge.howMany() != most) {
            helper.fail("A request for two billion Forests came through as " + huge.howMany());
            return;
        }
        dev.gathering.network.AddBasicsPayload none = new dev.gathering.network.AddBasicsPayload(
                false, dev.gathering.core.card.BasicLand.ISLAND, -5);
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
                            java.nio.charset.StandardCharsets.UTF_8)), UUID.nameUUIDFromBytes(("printing-" + index).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)),
                    new CardFaceSummary("Card " + index, "{1}", "Artifact", "", "", "s", "n", ""),
                    Optional.empty(),
                    dev.gathering.core.card.Rarity.COMMON, 1.0, java.util.Set.of()));
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

    /**
     * A cascade, on the wire.
     *
     * <p>Worth a test of its own because this one carries an enum, and an enum on the wire is
     * an ordinal: the two questions this payload can ask are one number apart, and a codec
     * that read them the wrong way round would turn somebody's library over looking for the
     * wrong thing. The clamping is checked here too - the numbers arrive from a client.
     */
    @GameTest(template = "empty")
    public static void aRevealUntilSurvivesTheWire(GameTestHelper helper) {
        var asked = roundTrip(
                helper,
                new dev.gathering.network.RevealUntilPayload(
                        net.minecraft.core.BlockPos.ZERO,
                        dev.gathering.network.RevealUntilPayload.Until.OF_TYPE, 0, "Creature"),
                dev.gathering.network.RevealUntilPayload.STREAM_CODEC);

        if (asked.until() != dev.gathering.network.RevealUntilPayload.Until.OF_TYPE) {
            helper.fail("which question a reveal was asking changed on the wire: " + asked.until());
        }
        if (!"Creature".equals(asked.wanted())) {
            helper.fail("the type asked for changed on the wire: " + asked.wanted());
        }

        var cascade = roundTrip(
                helper,
                new dev.gathering.network.RevealUntilPayload(
                        net.minecraft.core.BlockPos.ZERO,
                        dev.gathering.network.RevealUntilPayload.Until.CHEAPER_THAN, 4, ""),
                dev.gathering.network.RevealUntilPayload.STREAM_CODEC);
        if (cascade.until() != dev.gathering.network.RevealUntilPayload.Until.CHEAPER_THAN
                || cascade.manaValue() != 4) {
            helper.fail("a cascade changed on the wire: " + cascade.until()
                    + " at " + cascade.manaValue());
        }

        // A client is where these numbers come from, so the ceiling is part of the payload
        // rather than part of whatever reads it.
        var silly = new dev.gathering.network.RevealUntilPayload(
                net.minecraft.core.BlockPos.ZERO,
                dev.gathering.network.RevealUntilPayload.Until.CHEAPER_THAN,
                Integer.MAX_VALUE, "x".repeat(200));
        if (silly.manaValue() != dev.gathering.network.RevealUntilPayload.MOST_MANA) {
            helper.fail("a mana value of two billion was not brought back down: "
                    + silly.manaValue());
        }
        if (silly.wanted().length() != dev.gathering.network.RevealUntilPayload.LONGEST_TYPE) {
            helper.fail("a two hundred character type was not cut down: "
                    + silly.wanted().length());
        }
        helper.succeed();
    }

    /**
     * A random discard, on the wire.
     *
     * <p>The count is the whole payload, so the clamping is the whole test. This is the one
     * number a client sends that the server acts on without the player naming any cards, and
     * a hand does not survive a request for two billion of them.
     */
    @GameTest(template = "empty")
    public static void aRandomDiscardSurvivesTheWire(GameTestHelper helper) {
        var asked = roundTrip(
                helper,
                new dev.gathering.network.DiscardAtRandomPayload(
                        net.minecraft.core.BlockPos.ZERO, 3),
                dev.gathering.network.DiscardAtRandomPayload.STREAM_CODEC);
        if (asked.howMany() != 3) {
            helper.fail("how many to discard changed on the wire: " + asked.howMany());
        }

        var silly = new dev.gathering.network.DiscardAtRandomPayload(
                net.minecraft.core.BlockPos.ZERO, Integer.MAX_VALUE);
        if (silly.howMany() != dev.gathering.core.game.RandomPick.MOST_AT_ONCE) {
            helper.fail("a discard of two billion was not brought back down: "
                    + silly.howMany());
        }
        var none = new dev.gathering.network.DiscardAtRandomPayload(
                net.minecraft.core.BlockPos.ZERO, -5);
        if (none.howMany() != 1) {
            helper.fail("a discard of minus five was not brought back up: " + none.howMany());
        }
        helper.succeed();
    }

    /**
     * Cards going back under a library, on the wire.
     *
     * <p>The list is what a box-drag produced, so its ceiling is the guard that stops a
     * misdrag putting every permanent on the table under somebody's deck.
     */
    @GameTest(template = "empty")
    public static void aRandomReturnSurvivesTheWire(GameTestHelper helper) {
        var asked = roundTrip(
                helper,
                new dev.gathering.network.ToBottomAtRandomPayload(
                        net.minecraft.core.BlockPos.ZERO,
                        java.util.List.of(
                                new dev.gathering.core.game.CardInstanceId(4),
                                new dev.gathering.core.game.CardInstanceId(9))),
                dev.gathering.network.ToBottomAtRandomPayload.STREAM_CODEC);
        if (asked.cards().size() != 2
                || asked.cards().get(0).value() != 4
                || asked.cards().get(1).value() != 9) {
            helper.fail("the cards going under a library changed on the wire: " + asked.cards());
        }

        java.util.List<dev.gathering.core.game.CardInstanceId> lots = new java.util.ArrayList<>();
        for (int card = 0; card < dev.gathering.network.ToBottomAtRandomPayload.MOST + 40; card++) {
            lots.add(new dev.gathering.core.game.CardInstanceId(card));
        }
        var trimmed = new dev.gathering.network.ToBottomAtRandomPayload(
                net.minecraft.core.BlockPos.ZERO, lots);
        if (trimmed.cards().size() != dev.gathering.network.ToBottomAtRandomPayload.MOST) {
            helper.fail("a hundred cards were not cut down to the ceiling: "
                    + trimmed.cards().size());
        }
        helper.succeed();
    }

    /**
     * A basic land, on the wire.
     *
     * <p>The land is an enum and an enum on the wire is an ordinal, so the six values are one
     * number apart and a codec that read them wrong would put an Island on the table for
     * somebody who pressed Plains. The unknown-id refusal matters more than usual here: this
     * enum is the only thing stopping the payload becoming a general card lookup.
     */
    @GameTest(template = "empty")
    public static void aFetchedBasicSurvivesTheWire(GameTestHelper helper) {
        for (var land : dev.gathering.core.card.BasicLand.values()) {
            var asked = roundTrip(
                    helper,
                    new dev.gathering.network.FetchBasicPayload(
                            net.minecraft.core.BlockPos.ZERO, land, 3),
                    dev.gathering.network.FetchBasicPayload.STREAM_CODEC);
            if (asked.land() != land) {
                helper.fail("a " + land + " came back as a " + asked.land());
            }
            if (asked.count() != 3) {
                helper.fail("how many lands changed on the wire: " + asked.count());
            }
        }

        var silly = new dev.gathering.network.FetchBasicPayload(
                net.minecraft.core.BlockPos.ZERO,
                dev.gathering.core.card.BasicLand.FOREST, Integer.MAX_VALUE);
        if (silly.count() != dev.gathering.network.FetchBasicPayload.MOST) {
            helper.fail("two billion Forests were not brought back down: " + silly.count());
        }
        helper.succeed();
    }

    /** A die and a coin, on the wire, and a die nobody printed brought back down. */
    @GameTest(template = "empty")
    public static void aRollSurvivesTheWire(GameTestHelper helper) {
        for (int sides : new int[] {1, 2, 4, 6, 8, 10, 12, 20}) {
            var asked = roundTrip(
                    helper,
                    new dev.gathering.network.RollDicePayload(
                            net.minecraft.core.BlockPos.ZERO, sides),
                    dev.gathering.network.RollDicePayload.STREAM_CODEC);
            if (asked.sides() != sides) {
                helper.fail("a d" + sides + " came back as a d" + asked.sides());
            }
        }

        var silly = new dev.gathering.network.RollDicePayload(
                net.minecraft.core.BlockPos.ZERO, Integer.MAX_VALUE);
        if (silly.sides() != dev.gathering.core.game.event.GameEvent.DiceRolled.MOST_SIDES) {
            helper.fail("a two billion sided die was not brought down: " + silly.sides());
        }

        var coin = roundTrip(
                helper,
                new dev.gathering.network.FlipCoinPayload(new net.minecraft.core.BlockPos(3, 4, 5)),
                dev.gathering.network.FlipCoinPayload.STREAM_CODEC);
        if (!coin.table().equals(new net.minecraft.core.BlockPos(3, 4, 5))) {
            helper.fail("a coin flip lost its table on the wire: " + coin.table());
        }
        helper.succeed();
    }

    /** Talking to a table, and a dungeon coming in from outside the game, on the wire. */
    @GameTest(template = "empty")
    public static void talkAndDungeonsSurviveTheWire(GameTestHelper helper) {
        var said = roundTrip(
                helper,
                new dev.gathering.network.TableChatPayload(
                        new net.minecraft.core.BlockPos(1, 2, 3), "attacking you with everything"),
                dev.gathering.network.TableChatPayload.STREAM_CODEC);
        if (!said.text().equals("attacking you with everything")) {
            helper.fail("a line lost its words on the wire: " + said.text());
        }

        var heard = roundTrip(
                helper,
                new dev.gathering.network.TableSaidPayload(
                        new net.minecraft.core.BlockPos(1, 2, 3), "Dev", "in response"),
                dev.gathering.network.TableSaidPayload.STREAM_CODEC);
        if (!heard.who().equals("Dev") || !heard.text().equals("in response")) {
            helper.fail("a line came back as " + heard.who() + ": " + heard.text());
        }

        for (dev.gathering.core.card.Dungeon dungeon : dev.gathering.core.card.Dungeon.values()) {
            var asked = roundTrip(
                    helper,
                    new dev.gathering.network.BringInDungeonPayload(
                            net.minecraft.core.BlockPos.ZERO, dungeon.ordinal()),
                    dev.gathering.network.BringInDungeonPayload.STREAM_CODEC);
            if (asked.dungeon() != dungeon) {
                helper.fail(dungeon + " came back as " + asked.dungeon());
            }
        }
        // A dungeon nobody printed is the first one rather than a crash, which is the whole
        // reason the wire carries a position instead of a name.
        var nonsense = new dev.gathering.network.BringInDungeonPayload(
                net.minecraft.core.BlockPos.ZERO, Integer.MAX_VALUE);
        if (nonsense.dungeon() != dev.gathering.core.card.Dungeon.UNDERCITY) {
            helper.fail("a dungeon index off the end was not brought back in: " + nonsense.dungeon());
        }
        helper.succeed();
    }

    /** How much of each set is in a collection, on the wire. */
    @GameTest(template = "empty")
    public static void setProgressSurvivesTheWire(GameTestHelper helper) {
        var asked = roundTrip(
                helper,
                new dev.gathering.network.AskSetProgressPayload(new net.minecraft.core.BlockPos(4, 5, 6)),
                dev.gathering.network.AskSetProgressPayload.STREAM_CODEC);
        if (!asked.collection().equals(new net.minecraft.core.BlockPos(4, 5, 6))) {
            helper.fail("a set-progress question lost its collection: " + asked.collection());
        }

        var answer = roundTrip(
                helper,
                new dev.gathering.network.SetProgressPayload(
                        net.minecraft.core.BlockPos.ZERO,
                        java.util.List.of(
                                new dev.gathering.network.SetProgressPayload.Row(
                                        "tst", "The Test Set", 281, 281, 12),
                                new dev.gathering.network.SetProgressPayload.Row(
                                        "dom", "Dominaria", 61, 269, 0)),
                        7),
                dev.gathering.network.SetProgressPayload.STREAM_CODEC);
        if (answer.sets().size() != 2 || answer.stillLooking() != 7) {
            helper.fail("a set-progress answer came back as " + answer.sets().size()
                    + " sets, " + answer.stillLooking() + " still looking");
        }
        var first = answer.sets().get(0).asProgress();
        if (!first.isComplete() || first.extras() != 12 || first.missing() != 0) {
            helper.fail("a finished set came back as " + first);
        }
        var second = answer.sets().get(1).asProgress();
        if (second.missing() != 208) {
            helper.fail("a part-finished set came back missing " + second.missing());
        }
        helper.succeed();
    }

    /** A deck's new name, on the wire. */
    @GameTest(template = "empty")
    public static void aRenameSurvivesTheWire(GameTestHelper helper) {
        var restored = roundTrip(
                helper,
                dev.gathering.network.RenameDeckPayload.of(
                        net.minecraft.world.InteractionHand.OFF_HAND, "Bear Tribal, Actually"),
                dev.gathering.network.RenameDeckPayload.STREAM_CODEC);

        if (!restored.name().equals("Bear Tribal, Actually")) {
            helper.fail("A deck name changed on the wire: " + restored.name());
        }
        if (restored.hand() != net.minecraft.world.InteractionHand.OFF_HAND) {
            helper.fail("Which hand holds the deck changed on the wire");
        }
        helper.succeed();
    }

    /**
     * A deck too big to encode is refused rather than handed over.
     *
     * <p>Each of a deck's sections crosses the wire as a bounded list, and a bounded list
     * throws when it is <em>written</em> past its bound, not only when it is read. So an
     * oversized deck is not one that arrives short - it is an item stack that cannot be
     * encoded at all, sitting in somebody's inventory and saved with their player data. Every
     * inventory sync after that throws, which is a player who cannot log in again until an
     * administrator edits their file by hand.
     *
     * <p>Building a deck a card at a time could never get here - withAdded has always refused
     * past the bound, and the collection builder stops and says what it left in the box. An
     * import builds the whole list at once, and that was the one way past it.
     */
    @GameTest(template = "empty")
    public static void aDeckTooBigToSendIsRecognisedAsSuch(GameTestHelper helper) {
        List<dev.gathering.item.CardComponent> far = new java.util.ArrayList<>();
        for (int copy = 0; copy < DeckComponent.MAX_CARDS + 200; copy++) {
            far.add(dev.gathering.item.CardComponent.of(
                    CardIdentity.ofPrinting(SOL_RING, false)));
        }
        DeckComponent huge = new DeckComponent(
                "Far too much", "", Optional.empty(), List.copyOf(far), List.of(), List.of());

        if (huge.fitsInAnItem()) {
            helper.fail("a deck of " + huge.totalCards() + " says it fits in an item, and the"
                    + " wire bound is " + DeckComponent.MAX_CARDS);
            return;
        }
        // And this is what would happen if anything handed it over anyway.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DeckComponent.STREAM_CODEC.encode(buffer, huge);
            helper.fail("a deck past the wire bound encoded, so the bound is not what stops"
                    + " it and this test is checking nothing");
            return;
        } catch (RuntimeException expected) {
            // Exactly the throw that would reach a player's inventory sync.
        }

        // An ordinary deck is untouched by any of it.
        DeckComponent ordinary = new DeckComponent(
                "Ordinary", "", Optional.empty(),
                List.of(dev.gathering.item.CardComponent.of(
                        CardIdentity.ofPrinting(SOL_RING, false))),
                List.of(), List.of());
        if (!ordinary.fitsInAnItem()) {
            helper.fail("a one-card deck was refused");
            return;
        }
        helper.succeed();
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
