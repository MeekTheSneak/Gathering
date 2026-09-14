package dev.gathering.client;

import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.tournament.EventSettings;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.CreateEventPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Hosting a tournament at this table: its name and every setting, in two columns so a small
 * window holds them all, and a line saying what is chosen or why it cannot run - the same check
 * the server makes.
 * <p>Client-only.
 */
public final class EventCreateScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int WARN = 0xFFE0B15A;
    private static final int PANEL_WIDTH = 420;
    private static final int MARGIN = 8;
    private static final int ROW = 18;
    private static final int GAP = 3;
    private static final int LABEL_WIDTH = 62;

    private final BlockPos table;
    private String name = "";
    private EventSettings.Kind kind = EventSettings.Kind.CONSTRUCTED;
    private int format;
    private int bestOf = 3;
    private int roundMinutes = EventSettings.USUAL_ROUND_MINUTES;
    private int buildMinutes = EventSettings.USUAL_BUILD_MINUTES;
    private int rounds;
    private int topCut;
    private EventSettings.DeckRegistration decks = EventSettings.DeckRegistration.LOCKED;
    private boolean large;
    private PodSettings pod = PodSettings.usual(PodSettings.Kind.DRAFT);

    private Rect panel = Rect.NONE;
    private final List<int[]> labelAt = new ArrayList<>();
    private final List<Component> labels = new ArrayList<>();
    private final List<int[]> numberAt = new ArrayList<>();
    private final List<java.util.function.IntSupplier> numbers = new ArrayList<>();
    private String said = "";

    public EventCreateScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.event.create"));
        this.table = table;
        List<FormatPreset> formats = FormatPresets.all();
        for (int index = 0; index < formats.size(); index++) {
            if (formats.get(index).id().equals("modern")) {
                format = index;
            }
        }
    }

    @Override
    protected void init() {
        labelAt.clear();
        labels.clear();
        numberAt.clear();
        numbers.clear();
        int height = MARGIN + 14 + 6 * (ROW + GAP) + 12 + ROW + MARGIN;
        int width = Math.min(PANEL_WIDTH, this.width - 12);
        panel = new Rect((this.width - width) / 2, Math.max(4, (this.height - height) / 2), width, Math.min(height, this.height - 8));
        int column = (panel.width() - MARGIN * 3) / 2;
        int left = panel.x() + MARGIN;
        int right = left + column + MARGIN;
        int top = panel.y() + MARGIN + 14;

        // Left column.
        int y = top;
        label(left, y, "screen.gathering.event.name");
        EditBox field = new EditBox(this.font, left + LABEL_WIDTH, y, column - LABEL_WIDTH, ROW,
                Component.translatable("screen.gathering.event.name"));
        field.setMaxLength(40);
        field.setValue(name);
        field.setResponder(typed -> name = typed);
        addRenderableWidget(field);
        y += ROW + GAP;
        y = choices(left, y, column, "screen.gathering.event.kind", EventSettings.Kind.values(), value -> kind == value,
                value -> {
                    kind = value;
                    if (value.isLimited()) {
                        pod = PodSettings.usual(value == EventSettings.Kind.SEALED ? PodSettings.Kind.SEALED : PodSettings.Kind.DRAFT);
                    }
                    rebuildWidgets();
                }, value -> "screen.gathering.event.kind." + value.key());
        label(left, y, "screen.gathering.event.format");
        List<FormatPreset> formats = FormatPresets.all();
        var formatButton = GatheringButtons.of(left + LABEL_WIDTH, y, column - LABEL_WIDTH, ROW,
                Component.literal(kind.isLimited() ? FormatPresets.LIMITED.displayName() : formats.get(format).displayName()),
                () -> {
                    format = (format + 1) % formats.size();
                    rebuildWidgets();
                });
        formatButton.active = !kind.isLimited();
        addRenderableWidget(formatButton);
        y += ROW + GAP;
        y = choices(left, y, column, "screen.gathering.event.best_of", new Integer[] {1, 3, 5}, value -> bestOf == value,
                value -> bestOf = value, value -> "screen.gathering.event.best_of." + value);
        y = stepper(left, y, column, "screen.gathering.event.round_minutes", () -> roundMinutes,
                () -> roundMinutes = Math.max(10, roundMinutes - 5), () -> roundMinutes = Math.min(EventSettings.MOST_MINUTES, roundMinutes + 5));
        y = stepper(left, y, column, "screen.gathering.event.build_minutes", () -> buildMinutes,
                () -> buildMinutes = Math.max(5, buildMinutes - 5), () -> buildMinutes = Math.min(EventSettings.MOST_MINUTES, buildMinutes + 5));

        // Right column.
        y = top;
        y = stepper(right, y, column, "screen.gathering.event.rounds", () -> rounds,
                () -> rounds = Math.max(0, rounds - 1), () -> rounds = Math.min(EventSettings.MOST_ROUNDS, rounds + 1));
        y = choices(right, y, column, "screen.gathering.event.top_cut", new Integer[] {0, 4, 8}, value -> topCut == value,
                value -> topCut = value, value -> "screen.gathering.event.top_cut." + value);
        y = choices(right, y, column, "screen.gathering.event.decks", EventSettings.DeckRegistration.values(),
                value -> decks == value, value -> decks = value, value -> "screen.gathering.event.decks." + value.key());
        y = choices(right, y, column, "screen.gathering.event.check_in", new Boolean[] {false, true}, value -> large == value,
                value -> large = value, value -> value ? "screen.gathering.event.check_in.yes" : "screen.gathering.event.check_in.no");
        label(right, y, "screen.gathering.event.packs");
        var packs = GatheringButtons.of(right + LABEL_WIDTH, y, column - LABEL_WIDTH, ROW,
                Component.translatable("screen.gathering.event.packs_button"),
                () -> this.minecraft.setScreen(new PodCreateScreen(this, pod, chosen -> pod = chosen)));
        packs.active = kind.isLimited();
        addRenderableWidget(packs);

        int half = (panel.width() - MARGIN * 3) / 2;
        int decide = panel.bottom() - MARGIN - ROW;
        addRenderableWidget(GatheringButtons.of(left, decide, half, ROW, Component.translatable("gui.cancel"), this::onClose));
        var create = GatheringButtons.of(right, decide, half, ROW, Component.translatable("screen.gathering.event.create_button"),
                this::create);
        create.active = table != null;
        addRenderableWidget(create);
    }

    private void label(int x, int y, String key) {
        labelAt.add(new int[] {x, y + (ROW - this.font.lineHeight) / 2 + 1});
        labels.add(Component.translatable(key));
    }

    private <T> int choices(int x, int y, int column, String key, T[] values, java.util.function.Predicate<T> isChosen,
            java.util.function.Consumer<T> choose, java.util.function.Function<T, String> labelKey) {
        label(x, y, key);
        int width = (column - LABEL_WIDTH - GAP * (values.length - 1)) / values.length;
        for (int index = 0; index < values.length; index++) {
            T value = values[index];
            addRenderableWidget(GatheringButtons.toggle(x + LABEL_WIDTH + index * (width + GAP), y, width, ROW,
                    Component.translatable(labelKey.apply(value)), () -> isChosen.test(value), () -> choose.accept(value)));
        }
        return y + ROW + GAP;
    }

    private int stepper(int x, int y, int column, String key, java.util.function.IntSupplier value, Runnable less, Runnable more) {
        label(x, y, key);
        addRenderableWidget(GatheringButtons.of(x + LABEL_WIDTH, y, 20, ROW, Component.literal("-"), less));
        addRenderableWidget(GatheringButtons.of(x + column - 20, y, 20, ROW, Component.literal("+"), more));
        numberAt.add(new int[] {x + LABEL_WIDTH + (column - LABEL_WIDTH) / 2, y + (ROW - this.font.lineHeight) / 2 + 1});
        numbers.add(value);
        return y + ROW + GAP;
    }

    EventSettings settings() {
        String formatId = kind.isLimited() ? "" : FormatPresets.all().get(format).id();
        PodSettings podSettings = kind.isLimited() ? aligned(pod) : null;
        return new EventSettings(kind, formatId, podSettings, bestOf, roundMinutes, buildMinutes,
                EventSettings.USUAL_EXTRA_TURNS, rounds, topCut, decks, large);
    }

    /** The pack settings, with their draft or sealed matching the event's. */
    private PodSettings aligned(PodSettings chosen) {
        PodSettings.Kind wanted = kind == EventSettings.Kind.SEALED ? PodSettings.Kind.SEALED : PodSettings.Kind.DRAFT;
        return chosen.kind() == wanted ? chosen : new PodSettings(wanted, chosen.source(), chosen.sets(),
                wanted == PodSettings.Kind.SEALED ? PodSettings.USUAL_SEALED_PACKS : PodSettings.USUAL_DRAFT_PACKS,
                0, chosen.cardsGo());
    }

    String said() {
        return said;
    }

    private void create() {
        EventSettings settings = settings();
        if (settings.problem().isPresent() || table == null) {
            return;
        }
        ClientNetworking.send(new CreateEventPayload(table, name, settings));
        this.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiText.drawCentered(graphics, this.font, this.title, panel.x() + panel.width() / 2, panel.y() + 4,
                panel.width() - MARGIN * 2, LABEL);
        for (int index = 0; index < labels.size(); index++) {
            GuiText.draw(graphics, this.font, labels.get(index), labelAt.get(index)[0], labelAt.get(index)[1], LABEL_WIDTH - 2, DIM);
        }
        for (int index = 0; index < numbers.size(); index++) {
            int value = numbers.get(index).getAsInt();
            Component text = value == 0 ? Component.translatable("screen.gathering.event.auto") : Component.literal(Integer.toString(value));
            GuiText.drawCentered(graphics, this.font, text, numberAt.get(index)[0], numberAt.get(index)[1], 60, LABEL);
        }
        EventSettings settings = settings();
        String problem = table == null ? "screen.gathering.events.host_at_a_table" : settings.problem().orElse(null);
        Component line = problem != null ? Component.translatable(problem)
                : Component.translatable("screen.gathering.event.chosen",
                        Component.translatable("screen.gathering.event.kind." + kind.key()),
                        kind.isLimited() ? FormatPresets.LIMITED.displayName() : FormatPresets.all().get(format).displayName(),
                        bestOf, roundMinutes);
        GuiText.drawCentered(graphics, this.font, line, panel.x() + panel.width() / 2,
                panel.bottom() - MARGIN - ROW - 11, panel.width() - MARGIN * 2, problem != null ? WARN : DIM);
        said = line.getString();
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
