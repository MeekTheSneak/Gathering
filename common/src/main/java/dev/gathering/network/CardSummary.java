package dev.gathering.network;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageTier;
import dev.gathering.core.card.Rarity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The display metadata for one printing, as it travels to a client entitled to see it.
 * <p>Card identity crosses the network as a UUID and this. A client is sent one of these
 * only for cards the visibility rules entitle it to; the payload set a client receives is
 * exactly the set it is allowed to know, which is why a modified client learns nothing.
 * <p>Rarity travels with it because rarity is printed on the card. It is not hidden
 * information about a card a client already holds - a client that can read the name and the
 * type line can read the little symbol too - and two things need it: a collection sorted by
 * rarity, and the light that comes out of a booster being torn open.
 */
public record CardSummary(
        UUID scryfallId, UUID oracleId, CardFaceSummary front, Optional<CardFaceSummary> back,
        Rarity rarity, double manaValue, Set<String> colorIdentity, List<CardSummary.MadeToken> makes,
        boolean special) {

    /** The same card, not one of a set's special versions - or read before that was kept. */
    public CardSummary(
            UUID scryfallId, UUID oracleId, CardFaceSummary front, Optional<CardFaceSummary> back,
            Rarity rarity, double manaValue, Set<String> colorIdentity, List<CardSummary.MadeToken> makes) {
        this(scryfallId, oracleId, front, back, rarity, manaValue, colorIdentity, makes, false);
    }

    /**
     * One token or emblem a card makes: what it is called, and exactly which printing it is.
     * <p>The printing as well as the name, because a name does not pick a token. A card that
     * makes a 2/2 green Cat has to put down that Cat and not the most recent token of the same
     * name, which is what looking the name up again did.
     */
    public record MadeToken(String name, UUID printing) {
        public MadeToken {
            name = name == null ? "" : name;
        }
    }

    /**
     * The same card with nothing listed under it.
     * <p>Most printings make no token, and every test that builds a summary by hand predates
     * the field, so the shorter form stays the one most callers write.
     */
    public CardSummary(
            UUID scryfallId, UUID oracleId, CardFaceSummary front, Optional<CardFaceSummary> back,
            Rarity rarity, double manaValue, Set<String> colorIdentity) {
        this(scryfallId, oracleId, front, back, rarity, manaValue, colorIdentity, List.of());
    }

    /**
     * How a rarity crosses the wire, written once.
     * <p>An ordinal, because {@link Rarity} is in the pure core and the pure core has no
     * Minecraft on its classpath to carry a codec of its own. A number out of range reads as
     * {@link Rarity#UNKNOWN} rather than throwing: this comes off a socket, and a card whose
     * rarity did not survive the trip is a card drawn without a colored ring, not a
     * disconnect.
     */
    public static final StreamCodec<io.netty.buffer.ByteBuf, Rarity> RARITY_STREAM_CODEC =
            ByteBufCodecs.idMapper(
                    id -> id >= 0 && id < Rarity.values().length
                            ? Rarity.values()[id]
                            : Rarity.UNKNOWN,
                    Rarity::ordinal);

    /** Five colors, and a little room for whatever Scryfall decides a color is next. */
    private static final int MOST_COLORS = 8;

    /** A color is one letter. Bounded anyway, because the length comes off the wire. */
    private static final int LONGEST_COLOR = 8;

    /**
     * How many tokens one card is allowed to say it makes.
     * <p>The real maximum on a printed card is small; this is the wire's limit, not Magic's,
     * and it exists because the count is read off a socket before anything is allocated.
     */
    public static final int MOST_TOKENS = 16;

    /** A card name's ceiling, matched to what the token search will accept. */
    private static final int LONGEST_TOKEN_NAME = 128;

    /**
     * Written out by hand rather than composed, for the reason {@link CardFaceSummary}'s is.
     * <p>Nine components and {@link StreamCodec#composite} stops at six. The order below is
     * the record's own, top to bottom, which is the only thing to keep right.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CardSummary> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, card) -> {
                        UUIDUtil.STREAM_CODEC.encode(buffer, card.scryfallId());
                        UUIDUtil.STREAM_CODEC.encode(buffer, card.oracleId());
                        CardFaceSummary.STREAM_CODEC.encode(buffer, card.front());
                        buffer.writeBoolean(card.back().isPresent());
                        card.back().ifPresent(back -> CardFaceSummary.STREAM_CODEC.encode(buffer, back));
                        RARITY_STREAM_CODEC.encode(buffer, card.rarity());
                        buffer.writeDouble(card.manaValue());
                        buffer.writeVarInt(card.colorIdentity().size());
                        card.colorIdentity().forEach(color -> buffer.writeUtf(color, LONGEST_COLOR));
                        buffer.writeVarInt(card.makes().size());
                        card.makes().forEach(made -> {
                            buffer.writeUtf(made.name(), LONGEST_TOKEN_NAME);
                            UUIDUtil.STREAM_CODEC.encode(buffer, made.printing());
                        });
                        buffer.writeBoolean(card.special());
                    },
                    buffer -> {
                        UUID printing = UUIDUtil.STREAM_CODEC.decode(buffer);
                        UUID oracle = UUIDUtil.STREAM_CODEC.decode(buffer);
                        CardFaceSummary front = CardFaceSummary.STREAM_CODEC.decode(buffer);
                        Optional<CardFaceSummary> back = buffer.readBoolean()
                                ? Optional.of(CardFaceSummary.STREAM_CODEC.decode(buffer))
                                : Optional.empty();
                        Rarity rarity = RARITY_STREAM_CODEC.decode(buffer);
                        double manaValue = buffer.readDouble();
                        // Bounded, because this comes off a socket: five colors exist and a
                        // length read from the wire is a length somebody could have written.
                        int colors = Math.min(buffer.readVarInt(), MOST_COLORS);
                        Set<String> identity = new java.util.LinkedHashSet<>();
                        for (int index = 0; index < colors; index++) {
                            identity.add(buffer.readUtf(LONGEST_COLOR));
                        }
                        int tokens = Math.min(buffer.readVarInt(), MOST_TOKENS);
                        List<MadeToken> makes = new java.util.ArrayList<>();
                        for (int index = 0; index < tokens; index++) {
                            String name = buffer.readUtf(LONGEST_TOKEN_NAME);
                            makes.add(new MadeToken(name, UUIDUtil.STREAM_CODEC.decode(buffer)));
                        }
                        return new CardSummary(
                                printing, oracle, front, back, rarity, manaValue, identity, makes,
                                buffer.readBoolean());
                    });

    public CardSummary {
        rarity = rarity == null ? Rarity.UNKNOWN : rarity;
        oracleId = oracleId == null ? scryfallId : oracleId;
        // Kept in order rather than sorted into a hash order salted once per launch, so two
        // clients encode the same card the same way.
        // Trimmed on the way in for the same reason makes is, and it was not: the reader takes at
        // most MOST_COLORS of at most LONGEST_COLOR characters, so a printing carrying more than
        // that wrote a packet the far side could not read back - and a mis-set length is not one
        // broken field, it is every field after it in the packet. Scryfall has never sent more than
        // five single letters; this is so that a day it does is a trimmed color and not a
        // disconnect for every client the card is sent to.
        java.util.Set<String> colors = new java.util.LinkedHashSet<>();
        if (colorIdentity != null) {
            for (String color : colorIdentity) {
                if (color == null || color.isEmpty() || colors.size() >= MOST_COLORS) {
                    continue;
                }
                colors.add(color.length() > LONGEST_COLOR ? color.substring(0, LONGEST_COLOR) : color);
            }
        }
        colorIdentity = java.util.Collections.unmodifiableSet(colors);
        // Trimmed here rather than at the menu, so a printing that lists thirty tokens cannot
        // encode a packet the far side then refuses to read back.
        makes = makes == null
                ? List.of()
                : List.copyOf(makes.subList(0, Math.min(makes.size(), MOST_TOKENS)));
    }

    /** What this card makes, by printing. See {@link CardMetadata#tokenParts}. */
    private static List<MadeToken> madeBy(CardMetadata card) {
        List<MadeToken> made = new java.util.ArrayList<>();
        for (dev.gathering.core.card.RelatedCard part : card.tokenParts()) {
            made.add(new MadeToken(part.name(), part.id()));
        }
        return made;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, List<CardSummary>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list(
                    dev.gathering.network.CardMetadataPayload.MOST_PER_PACKET));

    public static CardSummary of(CardMetadata card) {
        List<CardFace> faces = card.faces();
        if (faces.isEmpty()) {
            // A printing with no face data at all still needs a name to show.
            return new CardSummary(
                    card.scryfallId(),
                    card.oracleId(),
                    new CardFaceSummary(
                            card.name(),
                            card.manaCost(),
                            card.typeLine(),
                            card.oracleText(),
                            "",
                            card.images().bestFor(ImageTier.SMALL).orElse(""),
                            card.images().bestFor(ImageTier.NORMAL).orElse(""),
                            card.images().bestFor(ImageTier.PNG).orElse("")),
                    Optional.empty(),
                    card.rarity(),
                    card.cmc(),
                    card.colorIdentity(),
                    madeBy(card),
                    card.specialTreatment());
        }
        return new CardSummary(
                card.scryfallId(),
                card.oracleId(),
                CardFaceSummary.of(faces.get(0)),
                faces.size() > 1 ? Optional.of(CardFaceSummary.of(faces.get(1))) : Optional.empty(),
                card.rarity(),
                card.cmc(),
                card.colorIdentity(),
                madeBy(card),
                card.specialTreatment());
    }

    public String name() {
        return front.name();
    }

    public boolean isDoubleFaced() {
        return back.isPresent();
    }

    /** Front then back, so callers can walk the faces without asking how many there are. */
    public List<CardFaceSummary> faces() {
        return back.map(b -> List.of(front, b)).orElseGet(() -> List.of(front));
    }

    /**
     * The one side to draw, given which way up the card is sitting.
     * <p>A card lies on a table with one side up. A transform card has two printed sides and
     * shows whichever is up; a split or flip card has two faces of rules text on one piece of
     * card and shows that one piece whichever way it is read. Drawing every printed side at
     * once - which is what asking for {@link #printedSides()} and laying them out in a row
     * amounts to - turns a transform card into two half-size cards side by side, which is
     * neither of those things and is not how the card exists.
     *
     * @param flipped whether this card is showing its other side
     */
    public CardFaceSummary sideShown(boolean flipped) {
        List<CardFaceSummary> printed = printedSides();
        if (printed.size() < 2) {
            return printed.get(0);
        }
        return flipped ? printed.get(1) : printed.get(0);
    }

    /** Whether this card has a second printed side to turn over to. */
    public boolean hasAnotherSide() {
        return printedSides().size() > 1;
    }

    /**
     * The sides that are actually printed, which is what gets drawn.
     * <p>Not the same as {@link #faces()}. A split or flip card has two faces of rules text
     * on one piece of card, and drawing one image per face shows the same picture twice; a
     * transform card has two of everything. The difference is whether the faces carry their
     * own art, which {@link #of} has already sorted out.
     */
    public List<CardFaceSummary> printedSides() {
        List<CardFaceSummary> printed = faces().stream()
                .filter(face -> face.readableImage().isPresent())
                .toList();
        return printed.isEmpty() ? List.of(front) : printed;
    }
}
