package dev.gathering.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gathering.core.card.CardIdentity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The single data component a card item carries: {@code {scryfall_id, foil, custom_id?}}.
 *
 * <p>Nothing else. Name, oracle text, mana cost and art are derived data, fetched and cached
 * from Scryfall by whoever needs them. A card item is a pointer, which is what keeps a
 * hundred-card deck cheap to store, cheap to sync, and impossible to desync.
 *
 * <p>On 1.21.1 this is a registered {@code DataComponentType} with a codec, not raw NBT -
 * {@code getOrCreateTag()} has not existed since 1.20.5.
 */
public record CardComponent(Optional<UUID> scryfallId, boolean foil, Optional<String> customId) {

    public static final Codec<CardComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.optionalFieldOf("scryfall_id").forGetter(CardComponent::scryfallId),
            Codec.BOOL.optionalFieldOf("foil", false).forGetter(CardComponent::foil),
            Codec.STRING.optionalFieldOf("custom_id").forGetter(CardComponent::customId))
            .apply(instance, CardComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CardComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CardComponent::scryfallId,
            ByteBufCodecs.BOOL, CardComponent::foil,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), CardComponent::customId,
            CardComponent::new);

    public CardComponent {
        if (scryfallId.isPresent() == customId.isPresent()) {
            throw new IllegalArgumentException(
                    "A card component is either a Scryfall printing or a custom card, never both and never neither");
        }
    }

    public static CardComponent of(CardIdentity identity) {
        return new CardComponent(identity.printing(), identity.foil(), identity.custom());
    }

    /** Back to the pure-core type, where every rule about identity actually lives. */
    public CardIdentity toIdentity() {
        return scryfallId
                .map(id -> CardIdentity.ofPrinting(id, foil))
                .orElseGet(() -> CardIdentity.ofCustom(customId.orElseThrow(), foil));
    }
}
