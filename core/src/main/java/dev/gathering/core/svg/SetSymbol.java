package dev.gathering.core.svg;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A set's symbol, as a shape that can be drawn at any size in any colour.
 *
 * <p>Scryfall publishes one of these per set and it is the only picture of a set that exists
 * anywhere the mod is allowed to take one from. There is no booster wrapper photograph in any
 * API this mod may use, and a shop's product shot is a shop's product shot - so the symbol on
 * a plain wrapper is what a pack looks like, which is also what a pack looks like in a
 * catalogue and on the back of a box.
 *
 * <p>Kept as shapes rather than as a picture, so one download serves every size a screen ever
 * wants and every colour a product comes in. What comes out is coverage - how much of each
 * pixel the symbol fills - and the colour is the caller's business.
 *
 * <p>Everything about the shapes that is not filled area is deliberately ignored: stroke,
 * gradients, opacity. A set symbol is a silhouette and this reads it as one.
 *
 * <p>Pure, and reads its XML with everything external switched off. This document came off
 * the network.
 */
public record SetSymbol(double width, double height, List<Outline> outlines) {

    /** One drawn shape: its path as written, and how it decides what is inside. */
    public record Outline(String path, boolean evenOdd) {
    }

    /** How finely curves are chopped, relative to a pixel. Smaller is smoother and slower. */
    private static final double SMOOTHNESS = 0.35;

    /** How many times each pixel row is sampled, which is how smooth an edge comes out. */
    private static final int ROWS_PER_PIXEL = 4;

    public SetSymbol {
        outlines = outlines == null ? List.of() : List.copyOf(outlines);
    }

    public boolean isEmpty() {
        return outlines.isEmpty() || width <= 0 || height <= 0;
    }

    /**
     * Reads one symbol document.
     *
     * @param svg the file exactly as Scryfall served it
     */
    public static SetSymbol read(String svg) throws SvgException {
        if (svg == null || svg.isBlank()) {
            throw new SvgException("there is no symbol here at all");
        }
        Element root;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // This document came off the network. Nothing in a set symbol needs a doctype, an
            // external entity or a schema, and every one of those is a way for a file to make
            // the reader go and fetch something else.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            root = builder.parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
        } catch (Exception notXml) {
            throw new SvgException("that symbol is not readable XML", notXml);
        }
        if (root == null || !"svg".equals(localName(root))) {
            throw new SvgException("that symbol does not begin with an <svg>");
        }

        double[] box = viewBox(root.getAttribute("viewBox"));
        List<Outline> outlines = new ArrayList<>();
        gather(root, false, "", outlines);
        return new SetSymbol(box[0], box[1], outlines);
    }

    /**
     * Walks the document for paths, carrying down what a group says about its children.
     *
     * <p>Groups are used for nothing but shared attributes in the symbols Scryfall publishes -
     * no transforms - so this inherits the two attributes that matter and would rather be
     * loud than quietly wrong about anything else.
     */
    private static void gather(Element element, boolean evenOdd, String fill, List<Outline> into)
            throws SvgException {
        String rule = element.getAttribute("fill-rule");
        boolean here = rule.isBlank() ? evenOdd : "evenodd".equalsIgnoreCase(rule.trim());
        String colour = element.getAttribute("fill");
        String inherited = colour.isBlank() ? fill : colour.trim();

        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element child)) {
                continue;
            }
            String name = localName(child);
            if ("path".equals(name)) {
                String own = child.getAttribute("fill");
                String effective = own.isBlank() ? inherited : own.trim();
                if ("none".equalsIgnoreCase(effective)) {
                    // Not drawn, so not part of the silhouette. Filling it anyway would put
                    // the inside of a hollow symbol back.
                    continue;
                }
                String ownRule = child.getAttribute("fill-rule");
                boolean childEvenOdd = ownRule.isBlank()
                        ? here
                        : "evenodd".equalsIgnoreCase(ownRule.trim());
                String path = child.getAttribute("d");
                if (!path.isBlank()) {
                    into.add(new Outline(path, childEvenOdd));
                }
            } else if ("g".equals(name) || "svg".equals(name)) {
                gather(child, here, inherited, into);
            } else if ("title".equals(name) || "desc".equals(name) || "metadata".equals(name)) {
                // Words about the picture rather than part of it.
                continue;
            } else {
                throw new SvgException("<" + name + "> is not something this can draw; a set "
                        + "symbol is expected to be paths and groups only");
            }
        }
    }

    private static String localName(Element element) {
        String name = element.getLocalName();
        return name == null ? element.getTagName() : name;
    }

    private static double[] viewBox(String raw) throws SvgException {
        if (raw == null || raw.isBlank()) {
            throw new SvgException("that symbol has no viewBox, so it has no size");
        }
        String[] parts = raw.trim().split("[ ,]+");
        if (parts.length != 4) {
            throw new SvgException("'" + raw + "' is not a viewBox");
        }
        try {
            double width = Double.parseDouble(parts[2]);
            double height = Double.parseDouble(parts[3]);
            if (width <= 0 || height <= 0) {
                throw new SvgException("a symbol " + width + " by " + height + " has no area");
            }
            return new double[] {width, height};
        } catch (NumberFormatException notNumbers) {
            throw new SvgException("'" + raw + "' is not a viewBox", notNumbers);
        }
    }

    /**
     * How much of each pixel the symbol covers, in a square of this many pixels a side.
     *
     * <p>Fitted to the square with its shape kept and centred in whichever direction it does
     * not fill, because set symbols are every proportion from nearly square to three times as
     * wide as they are tall, and one stretched to fit is a symbol nobody recognises.
     *
     * @return one byte a pixel, row by row from the top: nought outside, 255 fully covered
     */
    public byte[] mask(int size) throws SvgException {
        int across = Math.max(1, size);
        byte[] alpha = new byte[across * across];
        if (isEmpty()) {
            return alpha;
        }
        double scale = Math.min(across / width, across / height);
        double offsetX = (across - width * scale) / 2.0;
        double offsetY = (across - height * scale) / 2.0;
        double tolerance = SMOOTHNESS / scale;

        float[] coverage = new float[across * across];
        for (Outline outline : outlines) {
            List<double[]> rings = SvgPathReader.read(outline.path(), tolerance);
            fill(rings, outline.evenOdd(), scale, offsetX, offsetY, across, coverage);
        }
        for (int index = 0; index < alpha.length; index++) {
            int value = Math.round(Math.min(1f, coverage[index]) * 255f);
            alpha[index] = (byte) value;
        }
        return alpha;
    }

    /**
     * Scanline fill, sampling each pixel row several times.
     *
     * <p>Coverage from several outlines adds up, and is capped at solid. Taking the most any
     * one of them gave was the first instinct and is wrong where two shapes meet: a pixel the
     * boundary runs through is half covered by each, so the greater of the two is half - a
     * pale line down every join. Added, it comes to one, which is what a pixel with two
     * halves of a symbol in it should be. Nothing double-darkens because solid is solid.
     */
    private static void fill(
            List<double[]> rings, boolean evenOdd, double scale, double offsetX, double offsetY,
            int across, float[] coverage) {
        List<double[]> edges = new ArrayList<>();
        for (double[] ring : rings) {
            int points = ring.length / 2;
            for (int point = 0; point < points; point++) {
                int next = (point + 1) % points;
                double ax = ring[point * 2] * scale + offsetX;
                double ay = ring[point * 2 + 1] * scale + offsetY;
                double bx = ring[next * 2] * scale + offsetX;
                double by = ring[next * 2 + 1] * scale + offsetY;
                if (ay != by) {
                    edges.add(new double[] {ax, ay, bx, by});
                }
            }
        }
        if (edges.isEmpty()) {
            return;
        }

        float[] row = new float[across];
        List<double[]> crossings = new ArrayList<>();
        for (int pixelY = 0; pixelY < across; pixelY++) {
            java.util.Arrays.fill(row, 0f);
            boolean anything = false;
            for (int sub = 0; sub < ROWS_PER_PIXEL; sub++) {
                double sampleY = pixelY + (sub + 0.5) / ROWS_PER_PIXEL;
                crossings.clear();
                for (double[] edge : edges) {
                    double top = Math.min(edge[1], edge[3]);
                    double bottom = Math.max(edge[1], edge[3]);
                    if (sampleY < top || sampleY >= bottom) {
                        continue;
                    }
                    double t = (sampleY - edge[1]) / (edge[3] - edge[1]);
                    crossings.add(new double[] {
                            edge[0] + t * (edge[2] - edge[0]), edge[3] > edge[1] ? 1 : -1});
                }
                if (crossings.size() < 2) {
                    continue;
                }
                crossings.sort((left, right) -> Double.compare(left[0], right[0]));
                int winding = 0;
                for (int index = 0; index < crossings.size() - 1; index++) {
                    winding += (int) crossings.get(index)[1];
                    boolean inside = evenOdd ? (index % 2 == 0) : winding != 0;
                    if (!inside) {
                        continue;
                    }
                    anything |= span(row, crossings.get(index)[0], crossings.get(index + 1)[0],
                            across);
                }
            }
            if (!anything) {
                continue;
            }
            int base = pixelY * across;
            for (int pixelX = 0; pixelX < across; pixelX++) {
                coverage[base + pixelX] += row[pixelX] / ROWS_PER_PIXEL;
            }
        }
    }

    /** Adds one horizontal run to a row, with the two end pixels covered in part. */
    private static boolean span(float[] row, double fromX, double toX, int across) {
        double from = Math.max(0, fromX);
        double to = Math.min(across, toX);
        if (to <= from) {
            return false;
        }
        int firstPixel = (int) Math.floor(from);
        int lastPixel = (int) Math.ceil(to) - 1;
        for (int pixel = firstPixel; pixel <= lastPixel && pixel < across; pixel++) {
            if (pixel < 0) {
                continue;
            }
            double left = Math.max(from, pixel);
            double right = Math.min(to, pixel + 1);
            if (right > left) {
                row[pixel] += (float) (right - left);
            }
        }
        return true;
    }
}
