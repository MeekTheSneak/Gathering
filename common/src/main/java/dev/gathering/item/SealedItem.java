package dev.gathering.item;

import dev.gathering.registry.GatheringComponents;
import dev.gathering.server.CardShop;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
 * A sealed box, and what happens when you cut the tape.
 * <p>Everything bigger than a booster: a display box, a bundle, a Commander precon, a case of
 * six boxes. Right-click opens it, and it opens one level down - a case gives six boxes, a box
 * gives thirty-six packs, a precon gives the deck and the sample pack that came in with it.
 * All the way down in one click would turn the best part of the whole feature into a shower of
 * two hundred and sixteen items.
 * <p>Like a pack, this is a pointer rather than a container: what is inside is looked up in the
 * server's catalog when it is opened, so there is nothing on the stack to read early and
 * nothing for a client to lie about.
 */
public class SealedItem extends Item {

    public SealedItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(SealedComponent box) {
        ItemStack stack = new ItemStack(GatheringContent.SEALED.get());
        stack.set(GatheringComponents.SEALED.get(), box);
        return stack;
    }

    public static Optional<SealedComponent> boxOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(GatheringComponents.SEALED.get()));
    }

    @Override
    public Component getName(ItemStack stack) {
        // What it said on the front. A row of unlabeled brown boxes on a shelf is a row
        // nobody can tell apart, and the name is the one thing the client has.
        return boxOf(stack)
                .filter(box -> box.isReal() && !box.name().isEmpty())
                .<Component>map(box -> Component.literal(box.name()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SealedComponent box = boxOf(stack).orElse(null);
        if (box == null || !box.isReal()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer buyer)) {
            return InteractionResultHolder.pass(stack);
        }

        List<ItemStack> inside = CardShop.openingOf(box);
        if (inside.isEmpty()) {
            // A box this server cannot look up: bought here and brought to another world, or
            // written by hand. Nothing is destroyed and nothing is invented.
            buyer.sendSystemMessage(Component.translatable("message.gathering.sealed_unknown",
                    box.setCode().toUpperCase(java.util.Locale.ROOT)));
            return InteractionResultHolder.fail(stack);
        }

        stack.shrink(1);
        for (ItemStack one : inside) {
            dev.gathering.server.Handing.give(buyer, one);
        }
        level.playSound(null, buyer.blockPosition(),
                net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> lines, TooltipFlag flag) {
        boxOf(stack).filter(SealedComponent::isReal).ifPresent(box -> {
            lines.add(Component.translatable("tooltip.gathering.pack_set",
                    box.setCode().toUpperCase(java.util.Locale.ROOT)));
            lines.add(Component.translatable("tooltip.gathering.sealed_open")
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
