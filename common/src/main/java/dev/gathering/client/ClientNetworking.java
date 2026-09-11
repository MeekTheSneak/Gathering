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
        // The guided first game is not at a table, and nothing done to it may leave this
        // machine. Its board is filed at a position no table can occupy, so a payload that
        // names that position is one some screen addressed to the demonstration - and the
        // demonstration has no server end. Dropped here rather than in the screens because
        // there are seventeen files that send one of these and only one that puts them on
        // the wire. See AtATable.
        //
        // Nothing reaches the server to be refused: a reach check would have refused these
        // anyway, since no player can be within reach of a position below the world, but
        // "refused at the other end" is not the same promise as "never sent" and it is the
        // second one this is for.
        if (payload instanceof dev.gathering.network.AtATable addressed
                && TutorialDemo.at(addressed.table())) {
            return;
        }
        Consumer<CustomPacketPayload> current = sender;
        if (current == null) {
            throw new IllegalStateException(
                    "No client payload sender is bound; the loader's client init must call bindSender");
        }
        current.accept(payload);
    }
}
