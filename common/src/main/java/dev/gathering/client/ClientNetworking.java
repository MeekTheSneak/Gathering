package dev.gathering.client;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How client code sends a payload without knowing which loader is underneath.
 * <p>One method, bound once at client init. Sending is the only network verb the client
 * has: it never asks for card identity it has not been offered, because the server decides
 * what it is entitled to and pushes exactly that.
 */
public final class ClientNetworking {

    private static volatile Consumer<CustomPacketPayload> sender;

    private ClientNetworking() {
    }

    public static void bindSender(Consumer<CustomPacketPayload> newSender) {
        sender = Objects.requireNonNull(newSender, "sender");
    }

    public static void send(CustomPacketPayload payload) {
        Consumer<CustomPacketPayload> current = sender;
        if (current == null) {
            throw new IllegalStateException(
                    "No client payload sender is bound; the loader's client init must call bindSender");
        }
        current.accept(payload);
    }
}
