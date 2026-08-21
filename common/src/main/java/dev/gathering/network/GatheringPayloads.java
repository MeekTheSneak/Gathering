package dev.gathering.network;

import dev.gathering.Gathering;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Payload ids in one place, so the two loaders cannot register them under different names. */
public final class GatheringPayloads {

    private GatheringPayloads() {
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        ResourceLocation id = Gathering.id(path);
        return new CustomPacketPayload.Type<>(id);
    }
}
