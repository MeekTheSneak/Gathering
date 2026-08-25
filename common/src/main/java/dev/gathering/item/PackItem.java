package dev.gathering.item;

import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.PackOpening;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A sealed booster, and the two ways of opening one.
 *
 * <p>Right-click opens it. Everything about what comes out is the server's: the collation, the
 * seed, and the cards. A pack is a promise rather than a container, so there is nothing on the
 * stack for a client to read early and nothing for one to lie about.
 */
public class PackItem extends Item {

    public PackItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(PackComponent pack) {
        ItemStack stack = new ItemStack(dev.gathering.item.GatheringContent.PACK.get());
        stack.set(GatheringComponents.PACK.get(), pack);
        return stack;
    }

    public static Optional<PackComponent> packOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(GatheringComponents.PACK.get()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PackComponent pack = packOf(stack).orElse(null);
        if (pack == null || !pack.isReal()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer opener) {
            // The stack goes first. Opening reaches a network and comes back later, and a
            // pack still in the hand when it does is a pack that can be opened twice.
            stack.shrink(1);
            PackOpening.openFor(opener, pack.setCode(), pack.kind());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            java.util.List<Component> lines, TooltipFlag flag) {
        packOf(stack).filter(PackComponent::isReal).ifPresent(pack -> {
            lines.add(Component.translatable("tooltip.gathering.pack_set",
                    pack.setCode().toUpperCase(java.util.Locale.ROOT)));
            if (!pack.kind().isEmpty()) {
                lines.add(Component.translatable("tooltip.gathering.pack_kind", pack.kind()));
            }
        });
    }
}
