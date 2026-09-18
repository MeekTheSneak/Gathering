package dev.gathering.item;

import dev.gathering.registry.GatheringComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * What a tournament leaves behind.
 * <p>Handed to whoever wins an event big enough to be worth remembering, engraved with the event,
 * the day and their name - and cast in a color of its own, so a shelf of them is a shelf of separate
 * afternoons rather than a row of the same cup.
 * <p>It does nothing. It is not a block, it does not go in a deck, and no rule anywhere reads it.
 * That is deliberate: a trophy that did something would be a reward, and what this is meant to be is
 * a souvenir.
 * <p>One of the few items that still explains itself in the hand, because what it says is the whole
 * of it and there is nowhere else to read it.
 */
public final class TrophyItem extends Item {

    public TrophyItem(Properties properties) {
        super(properties);
    }

    /** A trophy for this event, cast in a color the world's own randomness chose. */
    public static ItemStack of(String event, String day, String winner, RandomSource random) {
        return of(new TrophyComponent(event, day, winner, tintFrom(random)));
    }

    public static ItemStack of(TrophyComponent engraved) {
        ItemStack stack = new ItemStack(GatheringContent.TROPHY.get());
        stack.set(GatheringComponents.TROPHY.get(), engraved);
        return stack;
    }

    public static Optional<TrophyComponent> trophyOf(ItemStack stack) {
        return Optional.ofNullable(stack.get(GatheringComponents.TROPHY.get()));
    }

    /**
     * A color for a new trophy.
     * <p>Kept bright and away from the grays: a cup is metal or it is nothing, and a random number
     * straight out of the generator is mud about half the time. So the hue is the free part and the
     * rest is fixed - which is also why two trophies are always told apart at a glance.
     * <p>The world's own randomness, like every other roll the server makes.
     */
    public static int tintFrom(RandomSource random) {
        float hue = random == null ? 0.12f : random.nextFloat();
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.55f, 0.92f) & 0xFFFFFF;
    }

    /**
     * What color to draw this trophy, for whichever loader is asking.
     * <p>The color is the object's own and travels on it, so the same trophy is the same color in
     * every hand. One rule here rather than one in each loader, because two of those would be two
     * that can drift - which is how the sweep mixin ended up registered on neither.
     */
    public static int tintOf(ItemStack stack, int layer) {
        return trophyOf(stack).map(TrophyComponent::tint).orElse(TrophyComponent.BLANK.tint())
                | 0xFF000000;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> lines, TooltipFlag flag) {
        trophyOf(stack).filter(TrophyComponent::isEngraved).ifPresent(won -> {
            if (!won.event().isEmpty()) {
                lines.add(Component.literal(won.event()).withStyle(ChatFormatting.GOLD));
            }
            if (!won.winner().isEmpty()) {
                lines.add(Component.translatable("tooltip.gathering.trophy_won_by", won.winner())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!won.day().isEmpty()) {
                lines.add(Component.literal(won.day()).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }
}
