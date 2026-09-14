package dev.gathering.fabric.mixin;

import dev.gathering.server.CreativeDecks;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps a deck's cards when a creative player moves it. See {@link CreativeDecks}: the creative
 * inventory hands the server the client's copy of an item, and a deck's client copy has its
 * cards hidden.
 * <p>The only server-side hook in the mod, for the same reason the camera hook exists: there is
 * no event for this packet. It changes only the stack the handler is about to store, only for a
 * hidden copy of a deck, and only to the real deck of the same handle.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotMixin {

    static {
        org.slf4j.LoggerFactory.getLogger("Gathering").info("Gathering creative deck hook installed");
    }

    @Shadow
    public ServerPlayer player;

    @ModifyVariable(method = "handleSetCreativeModeSlot", at = @At(value = "STORE", ordinal = 0))
    private ItemStack gathering$keepTheDecksCards(ItemStack incoming, ServerboundSetCreativeModeSlotPacket packet) {
        return CreativeDecks.incoming(player, packet.slotNum(), incoming);
    }
}
