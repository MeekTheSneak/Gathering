package dev.gathering.client;

import com.mojang.math.Axis;
import dev.gathering.client.GatheringSprites.Element;
import dev.gathering.core.card.Sleeve;
import dev.gathering.core.game.visibility.CardView;
import dev.gathering.core.ui.CardText;
import dev.gathering.core.ui.CounterText;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.CardSummary;
import dev.gathering.item.CardComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Paints a filtered card at a position chosen by the table screen.
 * Does not select cards, route actions, or retain a game session. Client thread only. */
final class TableCardRenderer {
    private static final int COUNTER_TEXT = 0xFFFFE9A8;
    private static final int POINTED_AT_RING = 0xFFE8B24A;
    private static final int SHADOW_OFFSET = 1;

    private final CardCounterLabels labels = new CardCounterLabels(512);
    // Scratch rows are reused between cards. Widths and wrapping are still measured on every
    // draw, so zoom, fonts and resource reloads cannot leave stale geometry in the cache.
    private final List<Component> lines = new ArrayList<>();
    private final List<Component> counts = new ArrayList<>();

    /** Draws the card, its public annotations, hover feedback, then the ping ring.
     * The zone supplies the sleeve because an anonymous card carries no owner.
     * Cards on the felt receive shadows and tapped/frozen tints; hand and drag cards do not. */
    void draw(
            GuiGraphics graphics, Font font, BlockPos table, CardView card, Sleeve sleeve,
            Rect where, int angle, boolean hovered, boolean onTheFelt) {
        if (where.isEmpty()) {
            return;
        }
        boolean turned = Math.floorMod(angle, 360) != 0;
        if (turned) {
            graphics.pose().pushPose();
            graphics.pose().translate((float) where.centerX(), (float) where.centerY(), 0f);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            graphics.pose().translate((float) -where.centerX(), (float) -where.centerY(), 0f);
        }
        if (onTheFelt) {
            // Cast first, under everything, so the card above reads as being above. Only the
            // two edges that would show: filling the whole card again and moving it is the
            // same picture with far more of it hidden under the card, and the part that is
            // not hidden is the part that made it look airborne.
            GatheringSprites.draw(graphics, Element.CARD_SHADOW,
                    where.x() + SHADOW_OFFSET, where.bottom(), where.width(), SHADOW_OFFSET);
            GatheringSprites.draw(graphics, Element.CARD_SHADOW,
                    where.right(), where.y() + SHADOW_OFFSET,
                    SHADOW_OFFSET, where.height() - SHADOW_OFFSET);
        }
        if (card.isFaceDown()) {
            // Even to the player who knows what it is. Their board has to look to them the
            // way it looks to everyone else, or they cannot tell what they have given away.
            CardSleeves.draw(graphics, sleeve,
                    where.x(), where.y(), where.width(), where.height());
        } else {
            summary(card).ifPresentOrElse(
                    summary -> CardInspectPanel.renderArt(
                            graphics, summary, card.turnedOver(),
                            where.x(), where.y(), where.width(), where.height()),
                    () -> PaperFace.drawOrInset(graphics, font, card, where));
        }
        if (onTheFelt && card.tapped()) {
            // A tapped card is already lying sideways; the tint is what tells it apart from
            // one somebody turned by hand, without a word of text over the art.
            GatheringSprites.draw(graphics, Element.TAPPED_TINT,
                    where.x(), where.y(), where.width(), where.height());
        }
        if (onTheFelt && card.frozen()) {
            // A frozen card looks frozen. Everything about this feature happens on a press
            // made next turn, without looking, so a freeze that is only in the log is a
            // freeze that gets untapped by habit and argued about afterwards.
            drawFrost(graphics, where);
        }
        drawWriting(graphics, font, card, where);
        // The numbers first, because they sit in the corner and the counters stack up off
        // the top of them. A power and toughness under a pile of counters is a card whose
        // most important number is the one you cannot see.
        drawCounters(graphics, font, card, where, drawStrength(graphics, font, card, where));
        if (hovered) {
            GatheringSprites.draw(graphics, Element.FOCUS_RING,
                    where.x(), where.y(), where.width(), where.height());
        }
        // Last, over everything, because it is the one mark on a card that is somebody at the
        // table talking rather than a fact about the card.
        drawPointedAt(graphics, table, card, where);
        if (turned) {
            graphics.pose().popPose();
        }
    }

    /** Rings only an identified card in this table's public news. The caller supplies the
     * live, tutorial or replay position; retaining it here would confuse those lifecycles. */
    private void drawPointedAt(GuiGraphics graphics, BlockPos table, CardView card, Rect where) {
        if (!(card instanceof CardView.Visible visible)) {
            return;
        }
        long since = ClientTableNews.pointedAtFor(
                table, visible.id(), ClientCardFlights.now());
        int thick = dev.gathering.core.ui.Pointing.thickness(
                Math.min(where.width(), where.height()), since);
        if (thick <= 0) {
            return;
        }
        int color = dev.gathering.core.ui.Pointing.color(POINTED_AT_RING, since);
        // Around the card rather than over it. Inside its edge the ring covers the art, and
        // on a card the cursor happens to be on it blends with the hover ring into a third
        // color that is neither - which a screenshot of this caught.
        int left = where.x() - thick;
        int top = where.y() - thick;
        int right = where.right() + thick;
        int bottom = where.bottom() + thick;
        graphics.fill(left, top, right, top + thick, color);
        graphics.fill(left, bottom - thick, right, bottom, color);
        graphics.fill(left, top + thick, left + thick, bottom - thick, color);
        graphics.fill(right - thick, top + thick, right, bottom - thick, color);
    }

    /** Written notes sit above counters. Paper stock already draws its note as the face. */
    private void drawWriting(GuiGraphics graphics, Font font, CardView card, Rect art) {
        // Not on blank stock. There the writing is the card - drawn across the whole of it by
        // PaperFace - and a band repeating the first few words of it over the top would be
        // the same sentence twice, the second copy covering the first.
        if (PaperFace.isPaper(card)) {
            return;
        }
        CardInspectPanel.drawNote(graphics, font, card.writtenOn().orElse(null), art);
    }

    /** Written strength or loyalty in the corner; returns the floor for the counter stack. */
    private int drawStrength(GuiGraphics graphics, Font font, CardView card, Rect art) {
        return CardInspectPanel.drawStrength(
                graphics, font, CounterText.cornerNumber(card), art);
    }


    /** Edge rime and tint leave space for notes, counters and hover feedback. */
    private void drawFrost(GuiGraphics graphics, Rect where) {
        GatheringSprites.draw(graphics, Element.FROZEN_TINT,
                where.x(), where.y(), where.width(), where.height());
        // Caked along the top and bottom rather than ringing the card. A full outline is what
        // the cursor draws, in a blue close enough to this one that a frozen card under the
        // cursor had two rings nobody could tell apart - and the one that matters is the one
        // that is still there when you look away.
        int rime = Math.max(1, where.height() / 16);
        GatheringSprites.draw(graphics, Element.FROZEN_EDGE,
                where.x(), where.y(), where.width(), rime);
        GatheringSprites.draw(graphics, Element.FROZEN_EDGE,
                where.x(), where.bottom() - rime, where.width(), rime);
    }

    /** Counter bands stay attached to the card and wrap at its current projected width. */
    private void drawCounters(GuiGraphics graphics, Font font, CardView card, Rect art, int floor) {
        if (card.counters().isEmpty()) {
            return;
        }
        int room = Math.max(1, art.width() - 4);
        // Against the card rather than against the screen, the same as the note across its
        // top - see CardText. A counter pinned to the font's own size took a third of a card
        // on a board zoomed out, and three of them took the card.
        float scale = CardText.scaleFor(art.height(), CardText.COUNTER, font.lineHeight);
        // Everything measured below is measured at the size it will be drawn at.
        int roomInLetters = Math.max(1, Math.round(room / scale));
        lines.clear();
        // The count that goes flush right on the line at the same index, or null. Parallel to
        // the lines rather than folded into them, because the count must never be the part
        // that gets trimmed off - it is written separately so it is fitted separately.
        counts.clear();
        for (CardCounterLabels.Label counter : labels.forCard(card)) {
            Component name = counter.name();
            if (counter.count() == null) {
                lines.add(name);
                counts.add(null);
                continue;
            }
            Component amount = counter.count();
            if (font.width(name) <= roomInLetters - font.width(amount) - 3) {
                lines.add(name);
                counts.add(amount);
            } else {
                // A card on a crowded table is narrower than "+1/+1 x2", and a name squeezed
                // into what is left of it comes out as "+" - which in Magic is a different
                // thing entirely, and is the whole reason this stopped being one string. So
                // the count drops to its own line rather than the name losing its end.
                lines.add(name);
                counts.add(null);
                lines.add(amount);
                counts.add(null);
            }
        }
        int high = CardText.lineAt(scale, font.lineHeight);
        int line = floor - high * lines.size();
        for (int index = 0; index < lines.size(); index++) {
            Component amount = counts.get(index);
            int amountRoom = amount == null
                    ? 0 : Math.round(font.width(amount) * scale) + 3;
            GatheringSprites.draw(graphics, Element.COUNTER_BAND,
                    art.x(), line - 1, art.width(), high);
            GuiText.drawTrimmedAt(graphics, font, lines.get(index),
                    art.x() + 2, line, room - amountRoom, 1, scale, COUNTER_TEXT);
            if (amount != null) {
                GuiText.drawTrimmedAt(graphics, font, amount,
                        art.right() - 1 - Math.round(font.width(amount) * scale), line,
                        amountRoom, 1, scale, COUNTER_TEXT);
            }
            line += high;
        }
    }

    /** Metadata remains a live lookup: an arriving summary must appear without a new view. */
    static Optional<CardSummary> summary(CardView card) {
        if (!(card instanceof CardView.Visible visible)) {
            return Optional.empty();
        }
        return ClientCardCache.get().summary(CardComponent.of(visible.identity()));
    }
}
