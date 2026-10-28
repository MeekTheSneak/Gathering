package dev.gathering.network;

import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.tournament.EventSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Tournament settings on the wire, read defensively on the server like {@link PodWire}. */
public final class EventWire {

    private EventWire() {
    }

    public static final StreamCodec<FriendlyByteBuf, EventSettings> SETTINGS = StreamCodec.of(
            (buffer, settings) -> {
                buffer.writeVarInt(settings.kind().ordinal());
                buffer.writeUtf(settings.formatId(), 64);
                buffer.writeBoolean(settings.pod() != null);
                if (settings.pod() != null) {
                    PodWire.SETTINGS.encode(buffer, settings.pod());
                }
                buffer.writeVarInt(settings.bestOf());
                buffer.writeVarInt(settings.roundMinutes());
                buffer.writeVarInt(settings.buildMinutes());
                buffer.writeVarInt(settings.extraTurns());
                buffer.writeVarInt(settings.rounds());
                buffer.writeVarInt(settings.topCut());
                buffer.writeVarInt(settings.decks().ordinal());
                buffer.writeBoolean(settings.largeEvent());
            },
            buffer -> {
                EventSettings.Kind kind = pick(EventSettings.Kind.values(), buffer.readVarInt());
                String format = buffer.readUtf(64);
                PodSettings pod = buffer.readBoolean() ? PodWire.SETTINGS.decode(buffer) : null;
                return new EventSettings(kind, format, pod, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        pick(EventSettings.DeckRegistration.values(), buffer.readVarInt()), buffer.readBoolean());
            });

    static <E> E pick(E[] values, int index) {
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("No such choice: " + index);
        }
        return values[index];
    }
}
