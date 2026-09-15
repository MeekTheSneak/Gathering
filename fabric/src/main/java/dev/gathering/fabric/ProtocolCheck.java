package dev.gathering.fabric;

import dev.gathering.Gathering;
import dev.gathering.network.GatheringProtocol;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;

/**
 * A joining client's protocol number, asked for while its connection is configured, and a client
 * on another number turned away before it plays.
 * <p>NeoForge does this itself: payloads registered under a version refuse a client with a
 * different one. Fabric registers the same payloads with no version, so a client a release behind
 * connected, and failed on the first payload it could not decode - a disconnect with a message about
 * a byte count, mid-game, rather than one saying which version to install. Here the server sends its
 * number, the client answers with its own, and the server lets the connection go on only if they
 * match.
 * <p>A client without this mod never gets asked - it cannot receive the question - and is left to
 * Fabric's own registry check, which already refuses it by name. So is a client with a build of
 * this mod from before the check, which has no channel to be asked on: the check came in the same
 * release as the Scorekeeper's Desk, whose block, item and block entity such a client does not have,
 * and registry sync turns it away for those.
 */
public record ProtocolCheck(int version) implements CustomPacketPayload {

    public static final Type<ProtocolCheck> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Gathering.MOD_ID, "protocol"));
    public static final StreamCodec<FriendlyByteBuf, ProtocolCheck> CODEC =
            ByteBufCodecs.VAR_INT.map(ProtocolCheck::new, ProtocolCheck::version).cast();

    /** What the server holds the connection for: the answer. */
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(Gathering.MOD_ID + ":protocol");

    @Override
    public Type<ProtocolCheck> type() {
        return TYPE;
    }

    /** Whether a client answering with this number may play here. */
    public static boolean accepts(int theirs) {
        return theirs == GatheringProtocol.VERSION;
    }

    /** The server's half, and the payload's registration both sides need. Called once, from the common entry point. */
    static void bootstrap() {
        PayloadTypeRegistry.configurationS2C().register(TYPE, CODEC);
        PayloadTypeRegistry.configurationC2S().register(TYPE, CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, TYPE)) {
                handler.addTask(new Ask());
            }
        });
        ServerConfigurationNetworking.registerGlobalReceiver(TYPE, (answer, context) -> {
            if (accepts(answer.version())) {
                context.networkHandler().completeTask(TASK);
            } else {
                context.networkHandler().disconnect(Component.translatableWithFallback("disconnect.gathering.protocol",
                        "This server runs Gathering's protocol %s and your game has %s. Install the version of Gathering the server has.",
                        GatheringProtocol.VERSION, answer.version()));
            }
        });
    }

    private record Ask() implements ConfigurationTask {

        @Override
        public void start(Consumer<Packet<?>> send) {
            send.accept(ServerConfigurationNetworking.createS2CPacket(new ProtocolCheck(GatheringProtocol.VERSION)));
        }

        @Override
        public Type type() {
            return TASK;
        }
    }
}
