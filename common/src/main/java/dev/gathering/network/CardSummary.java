package dev.gathering.network;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageTier;
import dev.gathering.core.card.Rarity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The display metadata for one printing, as it travels to a client entitled to see it.
 *
 * <p>Card identity crosses the network as a UUID and this. A client is sent one of these
 * only for cards the visibility rules entitle it to; the payload set a client receives is
 * exactly the set it is allowed to know, which is why a modified client learns nothing.
 *
 * <p>Rarity travels with it because rarity is printed on the card. It is not hidden
 * information about a card a client already holds - a client that can read the name and the
 * type line can read the little symbol too - and two things need it: a collection sorted by
 * rarity, and the light that comes out of a booster being torn open.
 */
public record CardSummary(
        UUID scryfallId, CardFaceSummary front, Optional<CardFaceSummary> back, Rarity rarity) {

    /**
     * How a rarity crosses the wire, written once.
     *
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

    public static final StreamCodec<RegistryFriendlyByteBuf, CardSummary> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardSummary::scryfallId,
            CardFaceSummary.STREAM_CODEC, CardSummary::front,
            ByteBufCodecs.optional(CardFaceSummary.STREAM_CODEC), CardSummary::back,
            RARITY_STREAM_CODEC, CardSummary::rarity,
            CardSummary::new);

    public CardSummary {
        rarity = rarity == null ? Rarity.UNKNOWN : rarity;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, List<CardSummary>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    public static CardSummary of(CardMetadata card) {
        List<CardFace> faces = card.faces();
        if (faces.isEmpty()) {
            // A printing with no face data at all still needs a name to show.
            return new CardSummary(
                    card.scryfallId(),
                    new CardFaceSummary(
                            card.name(),
                            card.manaCost(),
                            card.typeLine(),
                            card.oracleText(),
                            "",
                            card.images().bestFor(ImageTier.SMALL).orElse(""),
                            card.images().bestFor(ImageTier.NORMAL).orElse("")),
                    Optional.empty(),
                    card.rarity());
        }
        return new CardSummary(
                card.scryfallId(),
                CardFaceSummary.of(faces.get(0)),
                faces.size() > 1 ? Optional.of(CardFaceSummary.of(faces.get(1))) : Optional.empty(),
                card.rarity());
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
     *
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
     *
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
