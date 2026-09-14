package dev.gathering.network;

import dev.gathering.core.draft.PodSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Event settings on the wire, both ways.
 * <p>Read defensively on the server: a client can send any numbers at all, so every enum is
 * looked up by position with a bound, the set list is capped, and settings that do not add up
 * are left for {@link PodSettings#problem()} to refuse rather than trusted.
 */
public final class PodWire {

    /** More sets than any event has packs. */
    public static final int MOST_SETS = PodSettings.MOST_PACKS_EACH;

    /** A set code, with room to spare. */
    public static final int LONGEST_SET = 16;

    private PodWire() {
    }

    public static final StreamCodec<FriendlyByteBuf, PodSettings> SETTINGS = StreamCodec.of(
            (buffer, settings) -> {
                buffer.writeVarInt(settings.kind().ordinal());
                buffer.writeVarInt(settings.source().ordinal());
                buffer.writeVarInt(settings.sets().mode().ordinal());
                buffer.writeVarInt(settings.sets().sets().size());
                for (String set : settings.sets().sets()) {
                    buffer.writeUtf(set, LONGEST_SET);
                }
                buffer.writeVarInt(settings.packsEach());
                buffer.writeVarInt(settings.picksPerTurn());
                buffer.writeVarInt(settings.cardsGo().ordinal());
            },
            buffer -> {
                PodSettings.Kind kind = pick(PodSettings.Kind.values(), buffer.readVarInt());
                PodSettings.Source source = pick(PodSettings.Source.values(), buffer.readVarInt());
                PodSettings.SetRule.Mode mode = pick(PodSettings.SetRule.Mode.values(), buffer.readVarInt());
                int count = buffer.readVarInt();
                if (count < 0 || count > MOST_SETS) {
                    throw new IllegalArgumentException("Too many sets: " + count);
                }
                List<String> sets = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    sets.add(buffer.readUtf(LONGEST_SET));
                }
                int packsEach = buffer.readVarInt();
                int picks = buffer.readVarInt();
                PodSettings.CardsGo cardsGo = pick(PodSettings.CardsGo.values(), buffer.readVarInt());
                return new PodSettings(kind, source, ruleOrAny(mode, sets), packsEach, picks, cardsGo);
            });

    /**
     * A set rule from what arrived, or any set when what arrived is not one.
     * <p>Not a refusal: the host is shown what the server understood, and a set rule that did
     * not add up is a host who has not finished typing.
     */
    private static PodSettings.SetRule ruleOrAny(PodSettings.SetRule.Mode mode, List<String> sets) {
        try {
            return new PodSettings.SetRule(mode, mode == PodSettings.SetRule.Mode.ANY ? List.of() : sets);
        } catch (IllegalArgumentException incomplete) {
            return PodSettings.SetRule.ANY;
        }
    }

    private static <E> E pick(E[] values, int index) {
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("No such choice: " + index);
        }
        return values[index];
    }
}
