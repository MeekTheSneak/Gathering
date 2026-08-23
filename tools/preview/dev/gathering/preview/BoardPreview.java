package dev.gathering.preview;

import dev.gathering.core.game.SeatId;
import dev.gathering.core.game.TablePosition;
import dev.gathering.core.game.Zone;
import dev.gathering.core.table.TableCluster;
import dev.gathering.core.ui.BoardGeometry;
import dev.gathering.core.ui.HandFan;
import dev.gathering.core.ui.Rect;
import dev.gathering.core.ui.SeatColour;
import dev.gathering.core.ui.SurfaceBoard;
import dev.gathering.core.ui.TableScreenLayout;
import dev.gathering.core.ui.TableStacking;
import dev.gathering.core.ui.TableSurface;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws the board's layout to a picture, so it can be looked at without opening the game.
 *
 * <p>Not a test and not shipped: a pair of eyes. Every layout decision in this mod is made in
 * the pure module - where the mats are, how big a card is, where the zones sit, how the hand
 * fans - and all of it was being checked by reasoning about numbers and then waiting for
 * somebody to play the game and say it looked wrong. A stretched zone and a card a twentieth
 * of a mat wide are obvious in a picture and invisible in an assertion.
 *
 * <p>Run it, look at the pictures, fix what is ugly. That is the whole idea.
 */
public final class BoardPreview {

    private static final int TABLE_PIXELS = 900;

    private static final Color FELT = new Color(0x1B3A2A);
    private static final Color MAT = new Color(0x24513A);
    private static final Color MAT_EDGE = new Color(0x142A1F);
    private static final Color SLOT = new Color(0x122318);
    private static final Color SLOT_EDGE = new Color(0x0A150F);
    private static final Color CARD = new Color(0xC9BFA6);
    private static final Color CARD_EDGE = new Color(0x2A2418);
    private static final Color ACCENT = new Color(0x7FD4FF);
    private static final Color TEXT = new Color(0xE6E6E6);

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/preview");
        out.mkdirs();

        ImageIO.write(seated(2, 0, 854, 480), "png", new File(out, "seated-2-from-seat-0.png"));
        ImageIO.write(seated(2, 1, 854, 480), "png", new File(out, "seated-2-from-seat-1.png"));
        ImageIO.write(seated(4, 0, 854, 480), "png", new File(out, "seated-4-from-seat-0.png"));
        ImageIO.write(table(2), "png", new File(out, "table-2-seats.png"));
        ImageIO.write(table(4), "png", new File(out, "table-4-seats.png"));
        ImageIO.write(hand(854, 480, 7), "png", new File(out, "hand-7.png"));
        ImageIO.write(hand(854, 480, 15), "png", new File(out, "hand-15.png"));
        ImageIO.write(hand(1920, 1080, 7), "png", new File(out, "hand-7-big.png"));
        ImageIO.write(hand(320, 240, 10), "png", new File(out, "hand-10-tiny.png"));
        System.out.println("wrote previews to " + out.getAbsolutePath());
    }

    /**
     * The seated screen, from one particular chair.
     *
     * <p>The one thing a single client cannot be driven through: half the players sit at the
     * far edge of the table and see the whole surface the other way up, and until both chairs
     * are drawn side by side nothing says whether the two agree. Their own board should be the
     * near one in both, their own zones on their own right in both, and their own cards the
     * right way up in both.
     */
    private static BufferedImage seated(int seats, int viewer, int width, int height) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);
        BoardGeometry board = new BoardGeometry(
                TableCluster.assumedSeating(seats), width, height,
                layout.status().height(), layout.hand().height());
        board.focusOn(new SeatId(viewer));

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(FELT);
        g.fillRect(0, 0, width, height);

        for (int seat = 0; seat < seats; seat++) {
            SeatId id = new SeatId(seat);
            fill(g, board.matRect(id), MAT, new Color(SeatColour.of(seat)));
            for (int index = 0; index < Zone.PILES.size(); index++) {
                Rect slot = board.pileRect(id, index, Zone.PILES.size());
                fill(g, slot, SLOT, SLOT_EDGE);
                label(g, slot, Zone.PILES.get(index).name().substring(0, 3));
            }
            // One land row and one creature row, so which way up a board is drawn is visible
            // rather than deduced: a card's own angle is the seat's facing plus its rotation.
            for (int i = 0; i < 5; i++) {
                drawOne(g, board, id, TablePosition.of(2000 + i * 1200, 7600), 0);
            }
            drawOne(g, board, id, TablePosition.of(3000, 5000), 0);
            drawOne(g, board, id, TablePosition.of(4500, 5000), TablePosition.QUARTER_TURN);
        }

        g.setColor(new Color(0x00, 0x00, 0x00, 90));
        Rect strip = layout.hand();
        g.fillRect(strip.x(), strip.y(), strip.width(), strip.height());
        g.setColor(new Color(0x11, 0x11, 0x11));
        g.fillRect(layout.status().x(), layout.status().y(),
                layout.status().width(), layout.status().height());
        for (int index = 0; index < 6; index++) {
            HandFan.Slot slot = HandFan.slot(strip, 6, index, -1);
            card(g, slot.where().x(), slot.where().y(),
                    slot.where().width(), slot.where().height(), slot.angle(), false);
        }

        g.setColor(TEXT);
        g.drawString(seats + " seats, seen from seat " + viewer
                + "   own mat " + board.matRect(new SeatId(viewer))
                + "   card " + board.cardWidth(new SeatId(viewer))
                + "x" + board.cardHeight(new SeatId(viewer)) + "px", 10, 12);
        g.dispose();
        return image;
    }

    private static void drawOne(
            Graphics2D g, BoardGeometry board, SeatId seat, TablePosition at, int rotation) {
        Rect where = board.rectOf(seat, at);
        card(g, where.x(), where.y(), where.width(), where.height(),
                rotation + board.facingDegrees(seat));
    }

    /** The whole shared surface, as the table in the world lays it out. */
    private static BufferedImage table(int seats) {
        SurfaceBoard board = new SurfaceBoard(TableCluster.assumedSeating(seats));
        TableSurface surface = board.surface();
        unitsPerPixel = surface.width() / (double) TABLE_PIXELS;
        int tall = (int) Math.round(surface.height() / unitsPerPixel);

        BufferedImage image = new BufferedImage(TABLE_PIXELS, tall, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(FELT);
        g.fillRect(0, 0, TABLE_PIXELS, tall);

        for (int seat = 0; seat < surface.seatCount(); seat++) {
            SeatId id = new SeatId(seat);
            fill(g, surface.matOf(seat), MAT, new Color(SeatColour.of(seat)));

            for (int index = 0; index < Zone.PILES.size(); index++) {
                Rect slot = board.pileRect(id, index, Zone.PILES.size());
                fill(g, slot, SLOT, SLOT_EDGE);
                label(g, slot, Zone.PILES.get(index).name().substring(0, 3));
            }

            // A board somebody has actually arranged: a row of lands, some creatures, and a
            // pile of three on one spot.
            List<TablePosition> spots = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                spots.add(TablePosition.of(2000 + i * 1000, 7600));
            }
            for (int i = 0; i < 4; i++) {
                spots.add(TablePosition.of(2600 + i * 1200, 5000));
            }
            for (int i = 0; i < 3; i++) {
                spots.add(TablePosition.of(6800, 2400));
            }
            List<Integer> depths = TableStacking.depths(spots);
            for (int i = 0; i < spots.size(); i++) {
                Rect where = board.rectOf(id, spots.get(i));
                int lean = TableStacking.offsetFor(depths.get(i), board.cardWidth(id));
                card(g, where.x() + lean, where.y() + lean, where.width(), where.height(),
                        i == 8 ? 90 : 0);
            }
        }

        g.setColor(TEXT);
        g.drawString(seats + " seats   surface " + surface.width() + "x" + surface.height() + "   mat "
                + surface.matOf(0).width() + "x" + surface.matOf(0).height()
                + "   card " + Math.round(surface.cardWidthOn(0))
                + "x" + Math.round(surface.cardHeightOn(0))
                + "   (" + String.format("%.1f", surface.matOf(0).width() / surface.cardWidthOn(0))
                + " cards across)", 10, 20);
        g.dispose();
        return image;
    }

    /** The hand, as it sits over the felt at a given window size. */
    private static BufferedImage hand(int width, int height, int count) {
        TableScreenLayout layout = TableScreenLayout.of(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(FELT);
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(0x00, 0x00, 0x00, 90));
        Rect strip = layout.hand();
        g.fillRect(strip.x(), strip.y(), strip.width(), strip.height());
        g.setColor(new Color(0x11, 0x11, 0x11));
        Rect status = layout.status();
        g.fillRect(status.x(), status.y(), status.width(), status.height());

        int lifted = count / 2;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < count; index++) {
                if ((index == lifted) != (pass == 1)) {
                    continue;
                }
                HandFan.Slot slot = HandFan.slot(strip, count, index, lifted);
                card(g, slot.where().x(), slot.where().y(),
                        slot.where().width(), slot.where().height(), slot.angle(), false);
            }
        }
        g.setColor(TEXT);
        g.drawString(width + "x" + height + "   " + count + " cards   hand strip "
                + strip.height() + "px", 10, status.bottom() + 16);
        g.dispose();
        return image;
    }

    // ------------------------------------------------------------- drawing

    private static void fill(Graphics2D g, Rect rect, Color body, Color edge) {
        if (rect.isEmpty()) {
            return;
        }
        int x = scale(rect.x());
        int y = scale(rect.y());
        int w = Math.max(1, scale(rect.right()) - x);
        int h = Math.max(1, scale(rect.bottom()) - y);
        g.setColor(body);
        g.fillRect(x, y, w, h);
        g.setColor(edge);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(x, y, w, h);
    }

    private static void label(Graphics2D g, Rect rect, String text) {
        if (rect.isEmpty()) {
            return;
        }
        g.setColor(ACCENT);
        g.drawString(text, scale(rect.x()) + 3, scale(rect.y()) + 13);
    }

    private static void card(Graphics2D g, int x, int y, int w, int h, int angle) {
        card(g, x, y, w, h, angle, true);
    }

    private static void card(Graphics2D g, int x, int y, int w, int h, int angle, boolean onTable) {
        int px = onTable ? scale(x) : x;
        int py = onTable ? scale(y) : y;
        int pw = onTable ? Math.max(1, scale(w)) : w;
        int ph = onTable ? Math.max(1, scale(h)) : h;

        AffineTransform was = g.getTransform();
        if (angle != 0) {
            g.rotate(Math.toRadians(angle), px + pw / 2.0, py + ph / 2.0);
        }
        g.setColor(CARD);
        g.fillRoundRect(px, py, pw, ph, 4, 4);
        g.setColor(CARD_EDGE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(px, py, pw, ph, 4, 4);
        g.setTransform(was);
    }

    private static double unitsPerPixel = 1;

    private static int scale(int surfaceUnits) {
        return (int) Math.round(surfaceUnits / unitsPerPixel);
    }

    private BoardPreview() {
    }
}
