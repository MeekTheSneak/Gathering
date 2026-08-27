package dev.gathering.core.svg;

import java.util.ArrayList;
import java.util.List;

/**
 * An SVG path, read into plain closed rings of points.
 *
 * <p>Written rather than taken from a library on purpose. The mod ships no card art and no
 * pack art; a set's symbol is fetched from Scryfall like everything else, and Scryfall
 * publishes symbols as SVG. The alternative was a general-purpose SVG toolkit, which is
 * several megabytes of somebody else's code, most of it about text layout, gradients, filters
 * and scripting that a one-color silhouette will never touch.
 *
 * <p>So this reads the part that is actually used: the path commands, flattened to lines.
 * Curves become chords at a tolerance the caller sets from the size it is about to draw at,
 * so a symbol drawn small is flattened cheaply and one drawn large is still smooth.
 *
 * <p><b>Loud about what it does not understand.</b> A command letter this has never heard of
 * is an error naming the letter and where it was, not a silently missing piece of a symbol.
 * A symbol drawn wrong is worse than one that fails to draw, because nobody will ever know.
 *
 * <p>Pure.
 */
public final class SvgPathReader {

    /** As far as a curve is ever chopped, however coarse the tolerance. */
    private static final int FEWEST_SEGMENTS = 2;

    /** And as far as it is ever chopped however fine, which is smooth past any useful size. */
    private static final int MOST_SEGMENTS = 96;

    private final String path;
    private int at;

    private double x;
    private double y;
    private double startX;
    private double startY;

    /** Where the last curve's final control point was, for the smooth forms of C and Q. */
    private double lastCubicX;
    private double lastCubicY;
    private double lastQuadX;
    private double lastQuadY;
    private char lastCommand;

    private final double tolerance;
    private final List<double[]> rings = new ArrayList<>();
    private List<Double> current = new ArrayList<>();

    private SvgPathReader(String path, double tolerance) {
        this.path = path == null ? "" : path;
        this.tolerance = Math.max(1.0e-4, tolerance);
    }

    /**
     * Reads one {@code d} attribute.
     *
     * @param tolerance how far a chord may sit from the curve it replaces, in the same units
     *                  the path is written in
     * @return one array of {@code x, y, x, y, ...} per subpath, each an implicitly closed ring
     */
    public static List<double[]> read(String d, double tolerance) throws SvgException {
        SvgPathReader reader = new SvgPathReader(d, tolerance);
        reader.run();
        return List.copyOf(reader.rings);
    }

    private void run() throws SvgException {
        skipSeparators();
        while (at < path.length()) {
            char command = path.charAt(at);
            if (!Character.isLetter(command)) {
                throw new SvgException("expected a path command at " + at + ", found '"
                        + command + "'");
            }
            at++;
            step(command);
            lastCommand = command;
            skipSeparators();
        }
        closeRing();
    }

    private void step(char command) throws SvgException {
        switch (command) {
            case 'M', 'm' -> moveTo(command == 'm');
            case 'L', 'l' -> lineTo(command == 'l');
            case 'H', 'h' -> horizontal(command == 'h');
            case 'V', 'v' -> vertical(command == 'v');
            case 'C', 'c' -> cubic(command == 'c');
            case 'S', 's' -> smoothCubic(command == 's');
            case 'Q', 'q' -> quadratic(command == 'q');
            case 'T', 't' -> smoothQuadratic(command == 't');
            case 'A', 'a' -> arc(command == 'a');
            case 'Z', 'z' -> close();
            default -> throw new SvgException(
                    "'" + command + "' is not a path command this can read");
        }
    }

    // -------------------------------------------------------------- commands

    private void moveTo(boolean relative) throws SvgException {
        closeRing();
        double toX = number() + (relative ? x : 0);
        double toY = number() + (relative ? y : 0);
        x = toX;
        y = toY;
        startX = x;
        startY = y;
        add(x, y);
        markCurveless();
        // Every pair after the first is a line, not another move. A path that says
        // "M 1 2 3 4" draws a line to 3,4, and reading it as two moves loses the line.
        while (moreNumbers()) {
            lineTo(relative);
        }
    }

    private void lineTo(boolean relative) throws SvgException {
        do {
            x = number() + (relative ? x : 0);
            y = number() + (relative ? y : 0);
            add(x, y);
            markCurveless();
        } while (moreNumbers());
    }

    private void horizontal(boolean relative) throws SvgException {
        do {
            x = number() + (relative ? x : 0);
            add(x, y);
            markCurveless();
        } while (moreNumbers());
    }

    private void vertical(boolean relative) throws SvgException {
        do {
            y = number() + (relative ? y : 0);
            add(x, y);
            markCurveless();
        } while (moreNumbers());
    }

    private void cubic(boolean relative) throws SvgException {
        do {
            double baseX = relative ? x : 0;
            double baseY = relative ? y : 0;
            double c1x = number() + baseX;
            double c1y = number() + baseY;
            double c2x = number() + baseX;
            double c2y = number() + baseY;
            double toX = number() + baseX;
            double toY = number() + baseY;
            flattenCubic(c1x, c1y, c2x, c2y, toX, toY);
        } while (moreNumbers());
    }

    private void smoothCubic(boolean relative) throws SvgException {
        do {
            double baseX = relative ? x : 0;
            double baseY = relative ? y : 0;
            double c1x = reflectedCubicX();
            double c1y = reflectedCubicY();
            double c2x = number() + baseX;
            double c2y = number() + baseY;
            double toX = number() + baseX;
            double toY = number() + baseY;
            flattenCubic(c1x, c1y, c2x, c2y, toX, toY);
        } while (moreNumbers());
    }

    private void quadratic(boolean relative) throws SvgException {
        do {
            double baseX = relative ? x : 0;
            double baseY = relative ? y : 0;
            double cx = number() + baseX;
            double cy = number() + baseY;
            double toX = number() + baseX;
            double toY = number() + baseY;
            flattenQuadratic(cx, cy, toX, toY);
        } while (moreNumbers());
    }

    private void smoothQuadratic(boolean relative) throws SvgException {
        do {
            double baseX = relative ? x : 0;
            double baseY = relative ? y : 0;
            double cx = reflectedQuadX();
            double cy = reflectedQuadY();
            double toX = number() + baseX;
            double toY = number() + baseY;
            flattenQuadratic(cx, cy, toX, toY);
        } while (moreNumbers());
    }

    private void close() {
        if (!current.isEmpty()) {
            x = startX;
            y = startY;
        }
        closeRing();
        markCurveless();
    }

    // ------------------------------------------------------------------ arcs

    /**
     * An elliptical arc, as cubics.
     *
     * <p>Converted rather than drawn directly, so everything downstream sees curves of one
     * kind. The center and the sweep come from the endpoint form SVG writes arcs in, which is
     * a different parameterisation from the one the maths wants.
     */
    private void arc(boolean relative) throws SvgException {
        do {
            double rx = Math.abs(number());
            double ry = Math.abs(number());
            double rotation = Math.toRadians(number());
            boolean largeArc = flag();
            boolean sweep = flag();
            double toX = number() + (relative ? x : 0);
            double toY = number() + (relative ? y : 0);

            if (rx == 0 || ry == 0 || (toX == x && toY == y)) {
                // A degenerate arc is a line, which is what the specification says it is.
                x = toX;
                y = toY;
                add(x, y);
                markCurveless();
                continue;
            }
            arcToCubics(rx, ry, rotation, largeArc, sweep, toX, toY);
        } while (moreNumbers());
    }

    private void arcToCubics(
            double rx, double ry, double rotation, boolean largeArc, boolean sweep,
            double toX, double toY) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double midX = (x - toX) / 2.0;
        double midY = (y - toY) / 2.0;
        double x1 = cos * midX + sin * midY;
        double y1 = -sin * midX + cos * midY;

        double radii = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
        if (radii > 1) {
            // Radii too small to reach: scaled up until they just do, as the spec requires.
            double grow = Math.sqrt(radii);
            rx *= grow;
            ry *= grow;
        }

        double numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        double denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        double factor = Math.sqrt(Math.max(0, numerator / denominator));
        if (largeArc == sweep) {
            factor = -factor;
        }
        double cx1 = factor * rx * y1 / ry;
        double cy1 = -factor * ry * x1 / rx;
        double centerX = cos * cx1 - sin * cy1 + (x + toX) / 2.0;
        double centerY = sin * cx1 + cos * cy1 + (y + toY) / 2.0;

        double start = angleBetween(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry);
        double sweepAngle = angleBetween(
                (x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry);
        if (!sweep && sweepAngle > 0) {
            sweepAngle -= 2 * Math.PI;
        } else if (sweep && sweepAngle < 0) {
            sweepAngle += 2 * Math.PI;
        }

        // A cubic approximates a circular arc well up to about a quarter turn and badly past
        // it, so the sweep is cut into that many pieces.
        int pieces = (int) Math.ceil(Math.abs(sweepAngle) / (Math.PI / 2)) ;
        double each = sweepAngle / Math.max(1, pieces);
        double control = 4.0 / 3.0 * Math.tan(each / 4.0);
        for (int piece = 0; piece < pieces; piece++) {
            double from = start + piece * each;
            double to = from + each;
            double fromX = Math.cos(from);
            double fromY = Math.sin(from);
            double toUnitX = Math.cos(to);
            double toUnitY = Math.sin(to);

            double p1x = fromX - control * fromY;
            double p1y = fromY + control * fromX;
            double p2x = toUnitX + control * toUnitY;
            double p2y = toUnitY - control * toUnitX;

            flattenCubic(
                    onEllipse(centerX, rx, ry, cos, sin, p1x, p1y, true),
                    onEllipse(centerY, rx, ry, cos, sin, p1x, p1y, false),
                    onEllipse(centerX, rx, ry, cos, sin, p2x, p2y, true),
                    onEllipse(centerY, rx, ry, cos, sin, p2x, p2y, false),
                    onEllipse(centerX, rx, ry, cos, sin, toUnitX, toUnitY, true),
                    onEllipse(centerY, rx, ry, cos, sin, toUnitX, toUnitY, false));
        }
    }

    private static double onEllipse(
            double center, double rx, double ry, double cos, double sin,
            double unitX, double unitY, boolean horizontal) {
        double scaledX = unitX * rx;
        double scaledY = unitY * ry;
        return horizontal
                ? center + cos * scaledX - sin * scaledY
                : center + sin * scaledX + cos * scaledY;
    }

    private static double angleBetween(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double lengths = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double angle = Math.acos(Math.max(-1, Math.min(1, dot / lengths)));
        return (ux * vy - uy * vx) < 0 ? -angle : angle;
    }

    // ------------------------------------------------------------ flattening

    private void flattenCubic(
            double c1x, double c1y, double c2x, double c2y, double toX, double toY) {
        int steps = segmentsFor(distance(x, y, c1x, c1y)
                + distance(c1x, c1y, c2x, c2y) + distance(c2x, c2y, toX, toY));
        double fromX = x;
        double fromY = y;
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            double inverse = 1 - t;
            double a = inverse * inverse * inverse;
            double b = 3 * inverse * inverse * t;
            double c = 3 * inverse * t * t;
            double d = t * t * t;
            add(a * fromX + b * c1x + c * c2x + d * toX,
                    a * fromY + b * c1y + c * c2y + d * toY);
        }
        x = toX;
        y = toY;
        lastCubicX = c2x;
        lastCubicY = c2y;
        lastQuadX = toX;
        lastQuadY = toY;
    }

    private void flattenQuadratic(double cx, double cy, double toX, double toY) {
        int steps = segmentsFor(distance(x, y, cx, cy) + distance(cx, cy, toX, toY));
        double fromX = x;
        double fromY = y;
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            double inverse = 1 - t;
            add(inverse * inverse * fromX + 2 * inverse * t * cx + t * t * toX,
                    inverse * inverse * fromY + 2 * inverse * t * cy + t * t * toY);
        }
        x = toX;
        y = toY;
        lastQuadX = cx;
        lastQuadY = cy;
        lastCubicX = toX;
        lastCubicY = toY;
    }

    private int segmentsFor(double controlLength) {
        int steps = (int) Math.ceil(controlLength / tolerance / 3.0);
        return Math.max(FEWEST_SEGMENTS, Math.min(MOST_SEGMENTS, steps));
    }

    private static double distance(double ax, double ay, double bx, double by) {
        return Math.hypot(bx - ax, by - ay);
    }

    private double reflectedCubicX() {
        return isCubic(lastCommand) ? 2 * x - lastCubicX : x;
    }

    private double reflectedCubicY() {
        return isCubic(lastCommand) ? 2 * y - lastCubicY : y;
    }

    private double reflectedQuadX() {
        return isQuadratic(lastCommand) ? 2 * x - lastQuadX : x;
    }

    private double reflectedQuadY() {
        return isQuadratic(lastCommand) ? 2 * y - lastQuadY : y;
    }

    private static boolean isCubic(char command) {
        return command == 'C' || command == 'c' || command == 'S' || command == 's';
    }

    private static boolean isQuadratic(char command) {
        return command == 'Q' || command == 'q' || command == 'T' || command == 't';
    }

    /** After anything that is not a curve, a smooth curve's mirrored control point is here. */
    private void markCurveless() {
        lastCubicX = x;
        lastCubicY = y;
        lastQuadX = x;
        lastQuadY = y;
    }

    // ----------------------------------------------------------------- rings

    private void add(double px, double py) {
        current.add(px);
        current.add(py);
    }

    private void closeRing() {
        // Two points are a line and a line encloses nothing, so a ring needs three.
        if (current.size() >= 6) {
            double[] ring = new double[current.size()];
            for (int index = 0; index < ring.length; index++) {
                ring[index] = current.get(index);
            }
            rings.add(ring);
        }
        current = new ArrayList<>();
    }

    // --------------------------------------------------------------- numbers

    private void skipSeparators() {
        while (at < path.length()) {
            char character = path.charAt(at);
            if (character == ',' || character == ' ' || character == '\n' || character == '\r'
                    || character == '\t') {
                at++;
            } else {
                return;
            }
        }
    }

    private boolean moreNumbers() {
        skipSeparators();
        if (at >= path.length()) {
            return false;
        }
        char character = path.charAt(at);
        return character == '-' || character == '+' || character == '.'
                || (character >= '0' && character <= '9');
    }

    /** A flag in an arc, which is one character and never has a sign or a point. */
    private boolean flag() throws SvgException {
        skipSeparators();
        if (at >= path.length()) {
            throw new SvgException("an arc ran out before its flags, at " + at);
        }
        char character = path.charAt(at++);
        if (character != '0' && character != '1') {
            throw new SvgException("an arc flag at " + (at - 1) + " was '" + character + "'");
        }
        return character == '1';
    }

    private double number() throws SvgException {
        skipSeparators();
        int from = at;
        if (at < path.length() && (path.charAt(at) == '-' || path.charAt(at) == '+')) {
            at++;
        }
        boolean seenPoint = false;
        boolean seenDigit = false;
        while (at < path.length()) {
            char character = path.charAt(at);
            if (character >= '0' && character <= '9') {
                seenDigit = true;
                at++;
            } else if (character == '.' && !seenPoint) {
                // Only the first point belongs to this number: ".5.5" is two numbers, which
                // real path data does write.
                seenPoint = true;
                at++;
            } else if ((character == 'e' || character == 'E') && seenDigit) {
                at++;
                if (at < path.length() && (path.charAt(at) == '-' || path.charAt(at) == '+')) {
                    at++;
                }
            } else {
                break;
            }
        }
        if (!seenDigit) {
            throw new SvgException("expected a number at " + from);
        }
        try {
            return Double.parseDouble(path.substring(from, at));
        } catch (NumberFormatException notANumber) {
            throw new SvgException("'" + path.substring(from, at) + "' at " + from
                    + " is not a number");
        }
    }
}
