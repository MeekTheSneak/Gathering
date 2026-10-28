package dev.gathering.fabric;

import dev.gathering.network.GatheringProtocol;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload registration, adapted to Fabric from the list both loaders share.
 * <p>What is sent, which way, and what the server does with it is {@link GatheringProtocol}'s.
 * Clientbound types are registered here too so both sides agree on the protocol; their
 * handlers are installed by the client entry point, which is the only place allowed to name
 * a client class.
 */
final class GatheringNetwork {

    private GatheringNetwork() {
    }

    static void bootstrap() {
        for (GatheringProtocol.ToServer<?> route : GatheringProtocol.TO_SERVER) {
            registerType(route);
        }
        for (GatheringProtocol.ToClient<?> route : GatheringProtocol.TO_CLIENT) {
            registerType(route);
        }
        // Every receiver runs on the server thread. Fabric's networking-api-v1 states it in
        // ServerPlayNetworking itself - "this handler executes the callback in the server
        // thread, ensuring thread safety" - which is the same guarantee NeoForge gives through
        // HandlerThread.MAIN, so the handlers are free to touch the world directly and neither
        // loader needs a hop.
        for (GatheringProtocol.ToServer<?> route : GatheringProtocol.TO_SERVER) {
            receive(route);
        }
    }

    private static <T extends CustomPacketPayload> void registerType(
            GatheringProtocol.ToServer<T> route) {
        PayloadTypeRegistry.playC2S().register(route.type(), route.codec());
    }

    private static <T extends CustomPacketPayload> void registerType(
            GatheringProtocol.ToClient<T> route) {
        PayloadTypeRegistry.playS2C().register(route.type(), route.codec());
    }

    /**
     * One serverbound route. Generic so the type, codec and handler are checked against each
     * other by the compiler rather than cast into agreement.
     */
    private static <T extends CustomPacketPayload> void receive(
            GatheringProtocol.ToServer<T> route) {
        ServerPlayNetworking.registerGlobalReceiver(route.type(), (payload, context) ->
                route.handler().accept(context.player(), payload));
    }
}
