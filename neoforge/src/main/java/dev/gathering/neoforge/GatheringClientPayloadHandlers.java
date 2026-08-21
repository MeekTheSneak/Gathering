package dev.gathering.neoforge;

import java.util.function.BiConsumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The seam between payload registration and the client code that handles clientbound
 * payloads.
 *
 * <p>Both sides must register the same payload types or the protocol will not match, so a
 * dedicated server registers the clientbound ones too - with handlers that can never fire
 * there. Routing those through this holder means the registration code names no client
 * class at all, rather than relying on a lambda body never being resolved.
 *
 * <p>Referencing a client-only class from common or server code throws
 * {@code NoClassDefFoundError} on a dedicated server, and single-player testing will never
 * reveal it, because single-player runs an integrated server inside the client.
 */
public final class GatheringClientPayloadHandlers {

    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> handler = (payload, context) -> { };

    private GatheringClientPayloadHandlers() {
    }

    /** Called by the client bootstrap only. */
    public static void bind(BiConsumer<CustomPacketPayload, IPayloadContext> newHandler) {
        handler = java.util.Objects.requireNonNull(newHandler, "handler");
    }

    static void handle(CustomPacketPayload payload, IPayloadContext context) {
        handler.accept(payload, context);
    }
}
