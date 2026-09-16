package dev.gathering.client;

import dev.gathering.core.card.Rarity;
import dev.gathering.core.ui.PackReveal;
import dev.gathering.core.ui.Rect;
import dev.gathering.item.CardComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Thumbing through a pack, one card at a time.
 * <p>What a pack is opened for is the turn, and this is the turn: the cards left are a stack, the one in
 * front is the only one you can read, and you take it off the top to see the next. The order is the
 * ritual's - worst first - so the card the pack was opened for is the last one you reach, and the card
 * <em>before</em> it is lit from behind by what is coming.
 * <p>The light is a promise the pack has to keep. It says what is next and nothing about what is after
 * that, so a pack with two good cards in it tells you twice.
 * <p>Its own class rather than another three hundred lines of the screen: the screen owns the wrapper and
 * the way out, and this owns the stack.
 * <p>Client-only. The arithmetic is {@link PackReveal}'s, which has no window and is checked without one.
 */
final class PackTurning {

    /** How far the front card has to be dragged before it comes off the stack, as a share of its width. */
    private static final float TAKES = 0.35f;

    /** How far a card leans as it is dragged, in degrees at a full swipe. */
    private static final float SWEEP = 22f;

    /** How much of the way back to the middle an uncommitted drag springs, per frame. */
    private static final float SPRING = 0.3f;

    /** How long the shimmer waits between chimes while a good card is next, in milliseconds. */
    private static final long SHIMMER_EVERY = 900L;

    /** How long the card that has been taken off takes to leave, in milliseconds. */
    private static final long FLIES_FOR = 240L;

    /** How far it travels on its way out, as a multiple of its own width. */
    private static final float FLIES = 1.6f;

    private final List<CardComponent> cards;
    private final List<PackReveal.Tier> tiers;
    private PackReveal reveal;

    /** How far the front card has been dragged from the middle, in pixels. */
    private float swipe;
    private boolean dragging;
    private long shimmeredAt;

    /** The card that has just been taken off the stack, on its way out, and where it went from. */
    private CardComponent leaving;
    private float leavingWay;
    private float leavingFrom;
    private float leavingLean;
    private long leftAt;

    private PackTurning(List<CardComponent> cards, List<PackReveal.Tier> tiers) {
        this.cards = List.copyOf(cards);
        this.tiers = List.copyOf(tiers);
        this.reveal = new PackReveal(tiers, 0);
    }

    /**
     * A pack about to be gone through, in the order it should be.
     * <p>The tiers are read once, here, from whatever this client has been told about each card. A summary
     * that has not arrived yet reads as plain, which is the honest answer: the light must never promise
     * something on the strength of not knowing.
     */
    static PackTurning of(List<CardComponent> cards) {
        record Held(CardComponent card, PackReveal.Tier tier) { }
        List<Held> held = new ArrayList<>();
        for (CardComponent card : cards) {
            var summary = ClientCardCache.get().summary(card).orElse(null);
            held.add(new Held(card, PackReveal.Tier.of(
                    summary == null ? Rarity.UNKNOWN : summary.rarity())));
        }
        held.sort(java.util.Comparator.comparingInt(one -> one.tier().ordinal()));
        List<CardComponent> order = new ArrayList<>();
        List<PackReveal.Tier> tiers = new ArrayList<>();
        for (Held one : held) {
            order.add(one.card());
            tiers.add(one.tier());
        }
        return new PackTurning(order, tiers);
    }

    boolean finished() {
        return reveal.finished();
    }

    /** Every card, in the order they were turned - what the spread at the end lays out. */
    List<CardComponent> inOrder() {
        return cards;
    }

    /** What the player is looking at, for the scripted client. */
    int shown() {
        return reveal.shown();
    }

    PackReveal.Tier nextUp() {
        return reveal.nextUp();
    }

    /**
     * The stack, the card in front of it, and the light the next card casts through it.
     *
     * @param where where a card is drawn, which the screen works out from the pack it came from
     */
    void draw(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            Rect where, int mouseX, int mouseY, boolean reducedMotion) {
        if (reveal.finished()) {
            return;
        }
        if (!dragging) {
            swipe -= swipe * SPRING;
            if (Math.abs(swipe) < 0.5f) {
                swipe = 0f;
            }
        }
        long now = net.minecraft.Util.getMillis();

        // What is coming, lit around the stack - which is exactly behind the card in front, so the light
        // is the only part of the next card you can see until you move the top one off it. It breathes;
        // the card you are holding does not. That is the difference between "this one" and "the next one"
        // said without words.
        if (reveal.tells()) {
            int lit = lightOf(reveal.nextUp());
            float pulse = reducedMotion ? 1f : (float) (0.66 + 0.34 * Math.sin(now / 240.0));
            GuiGlow.aroundCard(graphics, where.x(), where.y(), where.width(), where.height(),
                    Math.max(8, where.width() / 3), (Math.round(0xF0 * pulse) << 24) | (lit & 0x00FFFFFF));
        }

        // The card underneath, exactly behind and never offset: a stack of cards is a stack, and the one
        // you are about to reach is the one the top card is covering. It used to be drawn as a row of
        // boxes stepping away down the screen, which is a fan rather than a pack.
        //
        // Only while the top card is actually off it. A card is a rectangle with its corners cut, so two
        // of them exactly in line show each other through those cuts and the pair reads as one card with
        // sharp corners - which is what the owner saw the moment a swipe began.
        boolean moved = Math.abs(swipe) > 0.5f || leaving != null;
        if (moved && reveal.left() > 1) {
            CardComponent under = cards.get(reveal.shown() + 1);
            ClientCardCache.get().summary(under).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArtTurned(graphics, summary, false,
                            where.x(), where.y(), where.width(), where.height(), 0f, 0f, under.foil()),
                    () -> GatheringSprites.inset(graphics, where.x(), where.y(),
                            where.width(), where.height()));
        }

        // And the card in front lit in its own right, if it is one worth looking at. Steady, and tight
        // against the card, so it travels with it while the next card's light stays on the stack.
        if (reveal.inFront().worthAnnouncing()) {
            int mine = lightOf(reveal.inFront());
            graphics.pose().pushPose();
            graphics.pose().translate(swipe, 0f, 0f);
            GuiGlow.aroundCard(graphics, where.x(), where.y(), where.width(), where.height(),
                    Math.max(4, where.width() / 7), 0xC0000000 | (mine & 0x00FFFFFF));
            graphics.pose().popPose();
        }

        // And the card itself, leaning the way it is being pulled.
        CardComponent front = cards.get(reveal.shown());
        float lean = Math.clamp(swipe / Math.max(1f, where.width()), -1f, 1f);
        graphics.pose().pushPose();
        graphics.pose().translate(swipe, Math.abs(lean) * 6f, 0f);
        ClientCardCache.get().summary(front).ifPresentOrElse(
                summary -> CardInspectPanel.renderArtTurned(graphics, summary, false,
                        where.x(), where.y(), where.width(), where.height(),
                        lean * SWEEP, 0f, front.foil()),
                () -> GatheringSprites.inset(graphics, where.x(), where.y(),
                        where.width(), where.height()));
        graphics.pose().popPose();

        // And the one just taken off, still on its way out over the top of everything - which is what
        // makes this a card being moved aside rather than a card being replaced.
        drawTheOneLeaving(graphics, where, now);

        // How many are left. The stack is one card deep to look at, so this is the only thing that says
        // how much of the pack is still to come.
        GuiText.drawCentered(graphics, font,
                Component.translatable("screen.gathering.pack_turn", reveal.left()),
                where.x() + where.width() / 2, where.bottom() + 10, where.width() * 2, 0xFFBFC7D2);
    }

    /**
     * The card that was in front, sliding out of the way.
     * <p>Over everything else and easing as it goes, so what the eye follows is one card being moved
     * aside and the next one being underneath it all along.
     */
    private void drawTheOneLeaving(GuiGraphics graphics, Rect where, long now) {
        if (leaving == null) {
            return;
        }
        float through = (now - leftAt) / (float) FLIES_FOR;
        if (through >= 1f) {
            leaving = null;
            return;
        }
        // Fast at first and slowing: a flick, not a conveyor. And it carries on from wherever the hand
        // let go of it rather than starting again from the middle, which is what made a long drag end in
        // the card jumping back to the center to begin its journey.
        float eased = 1f - (1f - through) * (1f - through);
        float to = leavingWay * where.width() * FLIES;
        float away = leavingFrom + (to - leavingFrom) * eased;
        final float carried = eased;
        graphics.pose().pushPose();
        graphics.pose().translate(away, eased * 10f, 0f);
        CardComponent going = leaving;
        ClientCardCache.get().summary(going).ifPresentOrElse(
                summary -> CardInspectPanel.renderArtTurned(graphics, summary, false,
                        where.x(), where.y(), where.width(), where.height(),
                        // Carrying on from the angle the hand left it at rather than snapping to a full
                        // sweep, which is the same jump the travel had.
                        (leavingLean + (leavingWay - leavingLean) * carried) * SWEEP, 0f, going.foil()),
                () -> GatheringSprites.inset(graphics, where.x(), where.y(),
                        where.width(), where.height()));
        graphics.pose().popPose();
    }

    /** The shimmer, while something worth announcing is next. Called once a client tick. */
    void tick() {
        if (reveal.finished() || !reveal.tells()) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        if (now - shimmeredAt >= SHIMMER_EVERY) {
            shimmeredAt = now;
            PackSounds.shimmer(reveal.nextUp());
        }
    }

    boolean grabbed(double mouseX, double mouseY, Rect where) {
        if (reveal.finished() || !where.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        dragging = true;
        return true;
    }

    void draggedTo(double dragX) {
        if (dragging) {
            swipe += (float) dragX;
        }
    }

    /**
     * Let go. Past the threshold the card comes off the stack and the next one arrives; short of it, it
     * springs back - so a half-hearted drag is not a card turned by accident.
     */
    void letGo(Rect where) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (Math.abs(swipe) >= where.width() * TAKES) {
            turn();
        }
    }

    /** Takes the front card off the stack, however that was asked for. */
    void turn() {
        if (reveal.finished()) {
            return;
        }
        leaving = cards.get(reveal.shown());
        leavingWay = swipe < 0f ? -1f : 1f;
        leavingFrom = swipe;
        leavingLean = Math.clamp(swipe / 200f, -1f, 1f);
        leftAt = net.minecraft.Util.getMillis();
        reveal = reveal.turned();
        swipe = 0f;
        shimmeredAt = 0L;
        // The noise the card that has just arrived is worth. Nothing at the end: the pack is over, and a
        // fanfare for the empty space where a card was is a screen congratulating itself.
        if (!reveal.finished()) {
            PackSounds.turned(reveal.arriving());
        }
    }

    /** What color a tier is lit in, which is the same light the tear casts. */
    private static int lightOf(PackReveal.Tier tier) {
        return switch (tier) {
            case MYTHIC -> dev.gathering.core.ui.PackGlow.MYTHIC_LIGHT;
            case RARE -> dev.gathering.core.ui.PackGlow.RARE_LIGHT;
            default -> 0;
        };
    }
}
