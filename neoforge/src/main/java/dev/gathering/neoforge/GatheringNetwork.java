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
@EventBusSubscriber(modid = Gathering.MOD_ID)
public final class GatheringNetwork {

    /**
     * Bumped when a payload's shape changes in a way an older client cannot read.
     * <p>Two, because several did at once and version one stayed put through all of them: the
     * deck component was split into a public copy and an owner's copy, the owner's push grew
     * a number, a trade action and a trade view grew the trade's own identity, a build request
     * and its result grew the press they belong to, and closing a table grew the table it is
     * about. A mixed old-and-new pair does not fail gracefully on any of those - it fails
     * while decoding a payload, which disconnects whoever is on the wrong side of it with a
     * message about a byte count. Refusing to connect at all is the honest answer, and it is
     * what a different number here buys.
     * <p>Three, for the batched table action: a client that sends a selection's verbs as one
     * payload cannot play at a server that does not know the payload.
     * <p>Four, for tokens by printing: a card summary now carries the printing of each token a
     * card makes, and a name that matches several tokens is answered with a choice.
     * <p>Five, for card lookups that end without a name: a server now says whether a printing
     * does not exist or could not be looked up, in a payload an older client cannot read.
     * <p>Six, for draft and sealed signups: creating one, acting at one, and being shown one.
     * <p>Seven, for playing a long table apart.
     * <p>Eight, for tournaments: creating, acting in and being shown one, and being pointed to a seat.
     */
    private static final String PROTOCOL_VERSION = "8";

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
