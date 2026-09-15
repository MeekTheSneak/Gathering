package dev.gathering.neoforge;

import dev.gathering.Gathering;
import dev.gathering.network.GatheringProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration, adapted to NeoForge from the list both loaders share.
 * <p>What is sent, which way, and what the server does with it is {@link GatheringProtocol}'s.
 * What is left here is NeoForge's own: the protocol version, the handler thread, and turning a
 * payload context into a server player. The client handlers live in the client package, wired in
 * from there, so nothing on a dedicated server ever names a client class.
 */
// The mod bus said out loud. NeoForge from about 21.1.100 works the bus out from the event, and this mod was
// built on one of those; on the earlier 21.1 releases its range admits, a mod-bus event on the game bus stopped
// the mod loading at all ("IModBusEvent events are not allowed on the common NeoForge bus").
@SuppressWarnings("removal")
@EventBusSubscriber(modid = Gathering.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GatheringNetwork {

    /** The shared protocol number, which NeoForge compares itself: see GatheringProtocol.VERSION. */
    private static final String PROTOCOL_VERSION = String.valueOf(GatheringProtocol.VERSION);

    private GatheringNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Said out loud rather than inherited. NeoForge 21.1.248's PayloadRegistrar starts at
        // HandlerThread.MAIN and wraps every handler in MainThreadPayloadHandler, so a handler
        // is already on the server thread - or the render thread, going the other way - before
        // its first line runs. Half of these used to hop again with enqueueWork and half did
        // not, which read as the unwrapped half being unsafe. Neither is: enqueueWork runs the
        // task straight through when it is already on the main thread, so the hops were inert.
        // One statement here, and no per-handler ceremony - and nothing depending on a default
        // that a later NeoForge is free to change.
        var registrar = event.registrar(PROTOCOL_VERSION)
                .executesOn(net.neoforged.neoforge.network.registration.HandlerThread.MAIN);

        for (GatheringProtocol.ToServer<?> route : GatheringProtocol.TO_SERVER) {
            toServer(registrar, route);
        }
        // Registered here so both sides agree on the protocol; the handlers are supplied by
        // the client bootstrap, which is the only place allowed to name a client class.
        for (GatheringProtocol.ToClient<?> route : GatheringProtocol.TO_CLIENT) {
            toClient(registrar, route);
        }
    }

    /**
     * One serverbound route. Generic so the type, codec and handler are checked against each
     * other by the compiler rather than cast into agreement.
     */
    private static <T extends CustomPacketPayload> void toServer(
            PayloadRegistrar registrar, GatheringProtocol.ToServer<T> route) {
        registrar.playToServer(route.type(), route.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                route.handler().accept(player, payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void toClient(
            PayloadRegistrar registrar, GatheringProtocol.ToClient<T> route) {
        registrar.playToClient(route.type(), route.codec(),
                (payload, context) -> GatheringClientPayloadHandlers.handle(payload, context));
    }
}
