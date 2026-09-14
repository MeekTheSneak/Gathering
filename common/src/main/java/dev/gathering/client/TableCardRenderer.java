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

/**
 * Paints one card, at a place and angle the table screen has already chosen.
 * <p>Moved out of the screen, which decides where every card goes and what pressing one does;
 * this decides only what a card looks like. It is handed the filtered card and nothing else, so
 * it can draw nothing the view did not show, and it keeps no game: no session, no selection, no
 * table of its own.
 * <p>Client thread only.
 */
final class TableCardRenderer {
    private static final int COUNTER_TEXT = 0xFFFFE9A8;
    /**
     * The color of the ring around a card somebody is pointing at.
     * <p>Warm and not any of the seat colors, so "look at this" is never mistaken for "this
     * is whose card it is".
     */
    private static final int POINTED_AT_RING = 0xFFE8B24A;
    private static final int SHADOW_OFFSET = 1;

    private final CardCounterLabels labels = new CardCounterLabels(512);
    // Scratch rows are reused between cards. Widths and wrapping are still measured on every
    // draw, so zoom, fonts and resource reloads cannot leave stale geometry in the cache.
    private final List<Component> lines = new ArrayList<>();
    private final List<Component> counts = new ArrayList<>();

    /**
     * Draws a card at a place the screen chose: picture, marks, hover ring, pointing ring.
     * <p>The sleeve is handed in rather than looked up, because a face-down card carries no
     * owner - that is the visibility rule. Whose card it is is a fact about the zone it lies in,
     * so it is known where the zones are walked and nowhere else.
     *
     * @param onTheFelt whether this is a card lying on the table, which is what earns it a
     *     shadow and a tapped tint - a card in a hand or in a list has neither
     */
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

    /**
     * The ring around a card somebody has just pointed at.
     * <p>"In response to that" needs a "that", and until now pointing did nothing whatsoever:
     * the event's own description promised it "highlights a public card for everyone for a few
     * seconds", and what it actually did was write a line in the log - which is the one place
     * nobody is looking while somebody is pointing at something.
     * <p>Drawn rather than painted, four bars around the edge, because there is no ring texture
     * and the artwork is somebody else's to make.
     * <p>Only ever a card this client can already identify. What is rung comes out of the log
     * line, which names a card only when the whole table may see it - see
     * {@link ClientTableNews}. A face-down card carries no id and gets no ring, which is the
     * right outcome rather than a gap: a ring would be this client saying which one it is.
     * <p>The table is handed in on every draw rather than kept: the live table, the
     * demonstration and a replay each file their news under a different position, and a renderer
     * that remembered one would ring cards from the wrong game.
     */
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

    /**
     * What somebody wrote on the card, across the top of it.
     * <p>At the top because the counters are along the bottom, and a card carrying both is
     * one somebody is keeping track of - exactly when both must be readable at once.
     * <p>Over the name rather than the art: the name is the one thing on a card its owner
     * already knows, and the art is what makes a board readable from across the table.
     */
    private void drawWriting(GuiGraphics graphics, Font font, CardView card, Rect art) {
        // Not on blank stock. There the writing is the card - drawn across the whole of it by
        // PaperFace - and a band repeating the first few words of it over the top would be
        // the same sentence twice, the second copy covering the first.
        if (PaperFace.isPaper(card)) {
            return;
        }
        CardInspectPanel.drawNote(graphics, font, card.writtenOn().orElse(null), art);
    }

    /**
     * The power and toughness somebody wrote on it, in the corner where the printed ones are.
     * <p>Where the card already puts them, so a board reads the same printed or written:
     * right-hand corner, one line, on a badge dark enough to read over whatever the art is
     * doing. Nothing is worked out - what is drawn is exactly what somebody typed. See
     * {@link dev.gathering.core.game.CardStrength}, and section 16 of the brief.
     *
     * @return the line the counters may stack up from, which is above this when there is one
     */
    private int drawStrength(GuiGraphics graphics, Font font, CardView card, Rect art) {
        return CardInspectPanel.drawStrength(
                graphics, font, CounterText.cornerNumber(card), art);
    }


    /**
     * What a frozen card looks like: a rime along its edges.
     * <p>Round the outside rather than over the art: a card may already carry a note across
     * its top, counters up its bottom and numbers in its corner, so the frame is the last
     * piece nothing else has claimed - and it reads at any size, which a corner mark does not.
     * <p>Cold against the warm gold of a written power and toughness and the cursor's cyan:
     * three marks on one card have to be three colors or they read as one.
     */
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

    /**
     * The counters on a card, along its bottom edge.
     * <p>On the card rather than beside it, because a counter that lives next to a card stops
     * being on that card the moment somebody moves either of them.
     * <p>The text of each label is prepared once per card view - see {@link CardCounterLabels} -
     * and measured every draw, so a zoom or a font change never meets a remembered width.
     */
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
