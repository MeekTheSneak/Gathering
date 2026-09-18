package dev.gathering.client;

import dev.gathering.core.guide.GuidePage;
import dev.gathering.core.ui.GuideLayout;
import dev.gathering.core.ui.Rect;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * How to play: a handful of short pages, one topic each, opened from a "?" on the board.
 * <p>The board's key list says what each key does and the first-sit lesson walks through five of
 * them. Neither says how to start a game, what the top row means, how to host a tournament or what
 * playing for keeps costs - and a player who did not know was left to ask somebody. Charta, another
 * card mod, keeps a how-to-play page a button away from every game; this is that, for this mod.
 * <p>Each page is a Markdown file under {@code assets/gathering/guide/<language>/}, so a translator
 * writes one file per page and a resource pack can replace any of them. A language with no page of
 * its own reads the English one. Keys are written as the action they perform and filled in with
 * what that action is bound to now - see {@link GuidePage}.
 * <p>Client-only.
 */
public final class GuideScreen extends ChildScreen {

    /** The topics, in the order they are listed. Each names its page file and its title. */
    public enum Topic {
        TABLE, TURN, CARDS, VIEW, EVENTS, KEEPS, DECKS;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        Component title() {
            return Component.translatable("guide.gathering." + id());
        }
    }

    private static final int TITLE = 0xFFE8E4DC;
    private static final int TEXT = 0xFFD8D3C8;
    private static final int HEADING = 0xFFE8D29A;
    private static final int KEY = 0xFF6FD3E8;

    /** The topic last read, so opening the guide again goes back to it. */
    private static Topic lastRead = Topic.TABLE;

    private Topic topic;
    private GuideLayout layout;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private final FocusKeeper focus = new FocusKeeper();

    /** One line of the page as drawn: its text, how far in it starts, and the space above it. */
    private record Line(FormattedCharSequence text, int indent, int above) {
    }

    public GuideScreen(Screen back) {
        this(back, lastRead);
    }

    public GuideScreen(Screen back, Topic topic) {
        super(Component.translatable("screen.gathering.guide.title"), back);
        this.topic = topic;
    }

    /** The topic on screen, for the scripted run. */
    Topic topic() {
        return topic;
    }

    /** How many lines the page came to, for the scripted run. */
    int lineCount() {
        return lines.size();
    }

    /** The page as plain text, for the scripted run to read keys out of. */
    String pageText() {
        StringBuilder text = new StringBuilder();
        for (Line line : lines) {
            line.text().accept((index, style, codePoint) -> {
                text.appendCodePoint(codePoint);
                return true;
            });
            text.append('\n');
        }
        return text.toString();
    }

    @Override
    protected void init() {
        float asked = GuiText.askedScale();
        int widest = 0;
        for (Topic each : Topic.values()) {
            widest = Math.max(widest, Math.round(this.font.width(each.title()) * asked));
        }
        Component done = Component.translatable("gui.done");
        layout = GuideLayout.of(this.width, this.height, ClientSettings.controlScale(), asked, this.font.lineHeight,
                Topic.values().length, widest, Math.round(this.font.width(done) * asked));
        if (layout.oneAtATime()) {
            addRenderableWidget(focus.named("topic:earlier", GatheringButtons.arrow(layout.previous().x(), layout.previous().y(),
                    layout.previous().width(), layout.previous().height(), GatheringSprites.Element.ARROW_LEFT,
                    Component.translatable("screen.gathering.guide.earlier"), () -> show(step(-1)))));
            addRenderableWidget(focus.named("topic:current", GatheringButtons.toggle(layout.current().x(), layout.current().y(),
                    layout.current().width(), layout.current().height(), topic.title(), () -> true, () -> { })));
            addRenderableWidget(focus.named("topic:later", GatheringButtons.arrow(layout.next().x(), layout.next().y(),
                    layout.next().width(), layout.next().height(), GatheringSprites.Element.ARROW_RIGHT,
                    Component.translatable("screen.gathering.guide.later"), () -> show(step(1)))));
        } else {
            List<Button> column = new ArrayList<>();
            for (int index = 0; index < Topic.values().length; index++) {
                Topic each = Topic.values()[index];
                Rect at = layout.topics().get(index);
                column.add(addRenderableWidget(focus.named("topic:" + each.name(),
                        GatheringButtons.toggle(at.x(), at.y(), at.width(), at.height(), each.title(),
                                () -> topic == each, () -> show(each)))));
            }
            // One size for the whole list, not each name at its own.
            GatheringButtons.matchLabels(column);
        }
        Rect doneAt = layout.done();
        addRenderableWidget(focus.named("done", GatheringButtons.of(doneAt, done, this::onClose)));
        lay();
    }

    private Topic step(int by) {
        Topic[] all = Topic.values();
        return all[Math.floorMod(topic.ordinal() + by, all.length)];
    }

    private void show(Topic wanted) {
        if (wanted == topic) {
            return;
        }
        topic = wanted;
        lastRead = wanted;
        scroll = 0;
        // Built again because in a small window the topic's own name is a button; focus stays on
        // whichever control chose it.
        focus.rebuild(this, this::rebuildWidgets, () -> "done");
    }

    /** Reads the page and breaks it into lines at the width of the page and the text size asked. */
    private void lay() {
        lines.clear();
        float asked = GuiText.askedScale();
        int wrap = Math.max(40, Math.round((layout.page().width() - SCROLLBAR_ROOM) / asked));
        int line = this.font.lineHeight + 2;
        boolean first = true;
        for (GuidePage.Block block : GuidePage.parse(read(topic)).blocks()) {
            switch (block) {
                case GuidePage.Heading heading -> {
                    MutableComponent text = styled(heading.text(), Style.EMPTY.withColor(heading.level() == 1 ? TITLE : HEADING)
                            .withBold(heading.level() == 1));
                    add(text, wrap, 0, first ? 0 : line);
                }
                case GuidePage.Paragraph paragraph -> add(styled(paragraph.text(), Style.EMPTY.withColor(TEXT)), wrap, 0, line / 3);
                case GuidePage.Bullet bullet -> add(Component.literal("• ").withStyle(Style.EMPTY.withColor(HEADING))
                        .append(styled(bullet.text(), Style.EMPTY.withColor(TEXT))), wrap, this.font.width("• "), 1);
                case GuidePage.Step step -> {
                    String number = step.number() + ". ";
                    add(Component.literal(number).withStyle(Style.EMPTY.withColor(HEADING))
                            .append(styled(step.text(), Style.EMPTY.withColor(TEXT))), wrap, this.font.width(number), 1);
                }
            }
            first = false;
        }
        scroll = Math.max(0, Math.min(scroll, deepest()));
    }

    /**
     * Adds a block's lines, the first at the margin and the rest hanging at the indent.
     * <p>Every line is broken to the width left after the indent, the first included, so the lines
     * after a bullet can start under its words and still end where the page does - and the styles
     * of the words, keys among them, carry across the breaks.
     */
    private void add(Component text, int wrap, int indent, int above) {
        List<FormattedCharSequence> split = this.font.split(text, Math.max(20, wrap - indent));
        for (int index = 0; index < split.size(); index++) {
            lines.add(new Line(split.get(index), index == 0 ? 0 : indent, index == 0 ? above : 0));
        }
    }

    /** A block's words and keys as one styled line. */
    private static MutableComponent styled(List<GuidePage.Span> spans, Style style) {
        MutableComponent line = Component.empty();
        for (GuidePage.Span span : spans) {
            switch (span) {
                case GuidePage.Words words -> line.append(Component.literal(words.text()).withStyle(style.withBold(
                        words.bold() || style.isBold())));
                case GuidePage.Key key -> line.append(Component.literal("[").append(keyLabel(key.action())).append("]")
                        .withStyle(style.withColor(KEY).withBold(true)));
            }
        }
        return line;
    }

    /** What an action is bound to now: the read key is the loader's own mapping, every other a table verb. */
    static Component keyLabel(String action) {
        return "read".equals(action) ? CardZoomOverlay.keyName() : TableShortcuts.labelOrUnbound(action);
    }

    /**
     * A page's Markdown, in the language being played in, or in English where there is none.
     * <p>Never empty-handed: a page nobody wrote says so rather than showing a blank panel.
     */
    private static String read(Topic topic) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        for (String tried : List.of(language, "en_us")) {
            Optional<Resource> page = Minecraft.getInstance().getResourceManager().getResource(
                    ResourceLocation.fromNamespaceAndPath(dev.gathering.Gathering.MOD_ID,
                            "guide/" + tried + "/" + topic.id() + ".md"));
            if (page.isPresent()) {
                try (BufferedReader reader = page.get().openAsReader()) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException unreadable) {
                    return Component.translatable("screen.gathering.guide.unreadable").getString();
                }
            }
        }
        return Component.translatable("screen.gathering.guide.missing").getString();
    }

    /**
     * The space kept at the page's right edge for its scrollbar.
     * <p>Ten rather than eight: the track is drawn three narrower than this and its art is painted
     * eight pixels square with a two pixel border, which wants six before its ends stop meeting.
     * At eight the track came out five and was squashed whole.
     */
    private static final int SCROLLBAR_ROOM = 10;

    private int lineHeight() {
        return Math.round((this.font.lineHeight + 2) * GuiText.askedScale());
    }

    private int pageHeight() {
        int total = 0;
        for (Line line : lines) {
            total += Math.round(line.above() * GuiText.askedScale()) + lineHeight();
        }
        return total;
    }

    /** How far the page can scroll: to where its last line sits at the bottom. */
    private int deepest() {
        return layout == null ? 0 : Math.max(0, pageHeight() - layout.page().height());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double across, double down) {
        scrollBy((int) Math.round(-down * lineHeight() * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        int page = layout.page().height() - lineHeight();
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> scrollBy(lineHeight());
            case GLFW.GLFW_KEY_UP -> scrollBy(-lineHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(page);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-page);
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    private void scrollBy(int by) {
        scroll = Math.max(0, Math.min(deepest(), scroll + by));
    }

    /**
     * How far in front of the board the guide is drawn.
     * <p>The board under it draws its top row lifted toward the viewer, and the guide covers that row
     * - the one child screen that does. Drawn at the same depth, the board's writing came through the
     * guide's panel; in front of it, nothing of the board can.
     */
    private static final float IN_FRONT = 400f;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, IN_FRONT);
        Rect panel = layout.panel();
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        Rect inset = layout.page();
        GatheringSprites.inset(graphics, inset.x() - 2, inset.y() - 2, inset.width() + 4, inset.height() + 4);
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        renderPage(graphics);
        graphics.pose().popPose();
    }

    private void renderPage(GuiGraphics graphics) {
        Rect panel = layout.panel();
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2,
                layout.titleY(), panel.width() - GuideLayout.MARGIN * 2, TITLE);
        Rect page = layout.page();
        float asked = GuiText.askedScale();
        graphics.enableScissor(page.x(), page.y(), page.right(), page.bottom());
        int y = page.y() - scroll;
        for (Line line : lines) {
            y += Math.round(line.above() * asked);
            if (y + lineHeight() >= page.y() && y <= page.bottom()) {
                graphics.pose().pushPose();
                graphics.pose().translate(page.x() + 2 + line.indent() * asked, y, 0f);
                graphics.pose().scale(asked, asked, 1f);
                graphics.drawString(this.font, line.text(), 0, 0, TEXT, false);
                graphics.pose().popPose();
            }
            y += lineHeight();
        }
        graphics.disableScissor();
        if (deepest() > 0) {
            int trackX = page.right() - SCROLLBAR_ROOM + 2;
            GatheringSprites.scrollTrack(graphics, trackX, page.y(), SCROLLBAR_ROOM - 3, page.height());
            int thumb = Math.max(12, page.height() * page.height() / Math.max(1, pageHeight()));
            int thumbY = page.y() + (page.height() - thumb) * scroll / Math.max(1, deepest());
            GatheringSprites.scrollThumb(graphics, trackX, thumbY, SCROLLBAR_ROOM - 3, thumb);
        }
    }
}
