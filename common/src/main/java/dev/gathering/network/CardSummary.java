package dev.gathering.network;

import dev.gathering.core.card.CardFace;
import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.ImageTier;
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
 */
public record CardSummary(UUID scryfallId, CardFaceSummary front, Optional<CardFaceSummary> back) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CardSummary> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardSummary::scryfallId,
            CardFaceSummary.STREAM_CODEC, CardSummary::front,
            ByteBufCodecs.optional(CardFaceSummary.STREAM_CODEC), CardSummary::back,
            CardSummary::new);

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
                            card.images().bestFor(ImageTier.SMALL).orElse(""),
                            card.images().bestFor(ImageTier.NORMAL).orElse("")),
                    Optional.empty());
        }
        return new CardSummary(
                card.scryfallId(),
                CardFaceSummary.of(faces.get(0)),
                faces.size() > 1 ? Optional.of(CardFaceSummary.of(faces.get(1))) : Optional.empty());
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
}
