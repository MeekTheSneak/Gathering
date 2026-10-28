package dev.gathering.client;

import dev.gathering.core.format.FormatPreset;
import dev.gathering.core.format.FormatPresets;
import dev.gathering.core.tournament.EventDraft;
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
 * Hosting a tournament at a Scorekeeper's Desk: its name and every setting, in two columns so a small
 * window holds them all, and a line saying what is chosen or why it cannot run - the same check
 * the server makes.
 * <p>Every choice lives in one {@link EventDraft} rather than in a field per control, so a subscreen
 * - the pack settings, the prizes - is handed the draft and hands it back changed, and reopening one
 * finds what was chosen in it rather than the defaults. The draft outlives this screen: it is kept
 * against the desk it is being made at ({@link EventDrafts}) until the tournament is created.
 * <p>A detour, so Escape and Cancel go back to the list this was opened from rather than out to the
 * world.
 * <p>Client-only.
 */
public final class EventCreateScreen extends ChildScreen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int WARN = 0xFFE0B15A;
    private static final int PANEL_WIDTH = 420;
    private static final int MARGIN = 8;
    private static final int ROW = 18;
    private static final int GAP = 3;
    private static final int LABEL_WIDTH = 52;

    private final BlockPos desk;
    private EventDraft draft;

    private Rect panel = Rect.NONE;
    private final List<int[]> labelAt = new ArrayList<>();
    private final List<Component> labels = new ArrayList<>();
    private final List<int[]> numberAt = new ArrayList<>();
    /** The build clock's number, drawn dim while the event has nothing to build. */
    private java.util.function.IntSupplier buildNumber;
    private final List<java.util.function.IntSupplier> numbers = new ArrayList<>();
    private String said = "";

    /**
     * @param back the screen this was opened from, which Cancel and Escape return to. Null closes to
     *             the world, which nothing production does: a tournament is only hosted from the list
     *             a Scorekeeper's Desk opens.
     */
    public EventCreateScreen(BlockPos desk, Screen back) {
        super(Component.translatable("screen.gathering.event.create"), back);
        this.desk = desk;
        this.draft = EventDrafts.at(desk, () -> EventDraft.blank(defaultFormat()));
    }

    /**
     * The desk a creation has been sent for and not yet answered.
     * <p>One at a time, because one screen sends one at a time.
     */
    private static BlockPos waitingFor;

    /**
     * The server has shown this client a tournament of its own, so a creation it was waiting on
     * worked: that desk's draft is finished with, and the next tournament hosted there starts blank.
     */
    static void hostedOne() {
        if (waitingFor != null) {
            EventDrafts.forget(waitingFor);
            waitingFor = null;
        }
    }

    /** The format a tournament starts on when nobody has chosen one. */
    private static String defaultFormat() {
        return FormatPresets.byId("modern").map(FormatPreset::id).orElse(FormatPresets.defaultPreset().id());
    }

    /** What has been chosen so far, kept against the desk so closing this screen loses none of it. */
    private void choose(EventDraft chosen) {
        draft = chosen;
        EventDrafts.keep(desk, chosen);
    }

    /** The draft as it stands. For the scripted client. */
    public EventDraft draft() {
        return draft;
    }

    /** Types a name into it, the way the box does. For the scripted client. */
    public void nameForTesting(String typed) {
        choose(draft.withName(typed));
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
        // The left column is the wider one: "Constructed" sits in it beside two other kinds,
        // while the right column's choices are all short.
        int both = panel.width() - MARGIN * 3;
        int column = both * 21 / 40;
        int rightColumn = both - column;
        int left = panel.x() + MARGIN;
        int right = left + column + MARGIN;
        int top = panel.y() + MARGIN + 14;

        // Left column.
        int y = top;
        label(left, y, "screen.gathering.event.name");
        EditBox field = new EditBox(this.font, left + LABEL_WIDTH, y, column - LABEL_WIDTH, ROW,
                Component.translatable("screen.gathering.event.name"));
        field.setMaxLength(40);
        field.setValue(draft.name());
        field.setResponder(typed -> choose(draft.withName(typed)));
        addRenderableWidget(field);
        y += ROW + GAP;
        y = choices(left, y, column, "screen.gathering.event.kind", EventSettings.Kind.values(),
                value -> draft.kind() == value,
                value -> {
                    // Everything that follows the kind - the build clock while it has been left
                    // alone, the pack settings - is EventDraft's to work out, and choosing the kind
                    // that is already chosen changes nothing there.
                    choose(draft.withKind(value));
                    rebuildWidgets();
                }, value -> "screen.gathering.event.kind." + value.key());
        label(left, y, "screen.gathering.event.format");
        List<FormatPreset> formats = FormatPresets.all();
        int format = Math.max(0, indexOf(formats, draft.formatId()));
        var formatButton = GatheringButtons.of(left + LABEL_WIDTH, y, column - LABEL_WIDTH, ROW,
                Component.literal(draft.kind().isLimited() ? FormatPresets.LIMITED.displayName()
                        : formats.get(format).displayName()),
                () -> {
                    choose(draft.withFormat(formats.get((format + 1) % formats.size()).id()));
                    rebuildWidgets();
                });
        formatButton.active = !draft.kind().isLimited();
        addRenderableWidget(formatButton);
        y += ROW + GAP;
        y = choices(left, y, column, "screen.gathering.event.best_of", new Integer[] {1, 3, 5},
                value -> draft.bestOf() == value, value -> choose(draft.withBestOf(value)),
                value -> "screen.gathering.event.best_of." + value);
        y = stepper(left, y, column, "screen.gathering.event.round_minutes", () -> draft.roundMinutes(),
                () -> choose(draft.withRoundMinutes(Math.max(10, draft.roundMinutes() - 5))),
                () -> choose(draft.withRoundMinutes(Math.min(EventSettings.MOST_MINUTES, draft.roundMinutes() + 5))));
        int beforeBuild = children().size();
        int buildIndex = numbers.size();
        y = stepper(left, y, column, "screen.gathering.event.build_minutes", () -> draft.buildMinutes(),
                () -> choose(draft.withBuildMinutes(Math.max(5, draft.buildMinutes() - 5))),
                () -> choose(draft.withBuildMinutes(Math.min(EventSettings.MOST_MINUTES, draft.buildMinutes() + 5))));
        // A constructed event brings its decks built: there is no building to time, so its
        // clock is shown grayed rather than taking presses that change nothing.
        buildNumber = numbers.get(buildIndex);
        for (var child : children().subList(beforeBuild, children().size())) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.active = draft.kind().isLimited();
            }
        }

        // Right column.
        y = top;
        y = stepper(right, y, rightColumn, "screen.gathering.event.rounds", () -> draft.rounds(),
                () -> choose(draft.withRounds(Math.max(0, draft.rounds() - 1))),
                () -> choose(draft.withRounds(Math.min(EventSettings.MOST_ROUNDS, draft.rounds() + 1))));
        // By player count unless the host says otherwise, the way rounds are.
        y = choices(right, y, rightColumn, "screen.gathering.event.top_cut", new Integer[] {EventSettings.AUTO_CUT, 0, 4, 8},
                value -> draft.topCut() == value, value -> choose(draft.withTopCut(value)),
                value -> value == EventSettings.AUTO_CUT ? "screen.gathering.event.auto" : "screen.gathering.event.top_cut." + value);
        y = choices(right, y, rightColumn, "screen.gathering.event.decks", EventSettings.DeckRegistration.values(),
                value -> draft.decks() == value, value -> choose(draft.withDecks(value)),
                value -> "screen.gathering.event.decks." + value.key());
        y = choices(right, y, rightColumn, "screen.gathering.event.check_in", new Boolean[] {false, true},
                value -> draft.largeEvent() == value, value -> choose(draft.withLargeEvent(value)),
                value -> value ? "screen.gathering.event.check_in.yes" : "screen.gathering.event.check_in.no");
        label(right, y, "screen.gathering.event.packs");
        var packs = GatheringButtons.of(right + LABEL_WIDTH, y, rightColumn - LABEL_WIDTH, ROW,
                Component.translatable("screen.gathering.event.packs_button"),
                () -> this.minecraft.setScreen(new PodCreateScreen(this, draft.pod(),
                        chosen -> choose(draft.withPod(chosen)))));
        packs.active = draft.kind().isLimited();
        addRenderableWidget(packs);
        y += ROW + GAP;
        // Prizes before there is a tournament to hold them: what is put up here is a place and the
        // slot holding it, and the items are taken when Create is pressed. See PrizeOffer.
        label(right, y, "screen.gathering.event.prizes");
        addRenderableWidget(GatheringButtons.of(right + LABEL_WIDTH, y, rightColumn - LABEL_WIDTH, ROW,
                Component.translatable("screen.gathering.event.put_up"),
                () -> this.minecraft.setScreen(new EventPrizeScreen(this, draft, this::choose))));

        int decide = panel.bottom() - MARGIN - ROW;
        addRenderableWidget(GatheringButtons.of(left, decide, column, ROW, Component.translatable("gui.cancel"), this::onClose));
        var create = GatheringButtons.of(right, decide, rightColumn, ROW, Component.translatable("screen.gathering.event.create_button"),
                this::create);
        create.active = desk != null;
        addRenderableWidget(create);
    }

    /** Where this format sits in the list, or -1. */
    private static int indexOf(List<FormatPreset> formats, String id) {
        for (int index = 0; index < formats.size(); index++) {
            if (formats.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private void label(int x, int y, String key) {
        labelAt.add(new int[] {x, y + (ROW - this.font.lineHeight) / 2 + 1});
        labels.add(Component.translatable(key));
    }

    private <T> int choices(int x, int y, int column, String key, T[] values, java.util.function.Predicate<T> isChosen,
            java.util.function.Consumer<T> choose, java.util.function.Function<T, String> labelKey) {
        label(x, y, key);
        // Shared by how much each label needs, so "Constructed" is not cut to fit beside "Draft".
        int room = column - LABEL_WIDTH - GAP * (values.length - 1);
        int[] wants = new int[values.length];
        int wanted = 0;
        for (int index = 0; index < values.length; index++) {
            wants[index] = this.font.width(Component.translatable(labelKey.apply(values[index]))) + 8;
            wanted += wants[index];
        }
        int left = x + LABEL_WIDTH;
        int given = 0;
        for (int index = 0; index < values.length; index++) {
            T value = values[index];
            int width = index == values.length - 1 ? room - given : Math.round(room * (float) wants[index] / wanted);
            addRenderableWidget(GatheringButtons.toggle(left, y, width, ROW,
                    Component.translatable(labelKey.apply(value)), () -> isChosen.test(value), () -> choose.accept(value)));
            left += width + GAP;
            given += width;
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
        return draft.settings();
    }

    String said() {
        return said;
    }

    private void create() {
        EventSettings settings = settings();
        if (settings.problem().isPresent() || desk == null) {
            return;
        }
        ClientNetworking.send(new CreateEventPayload(desk, draft.name(), settings, draft.prizes()));
        // Kept until the server says the tournament exists. Dropping it here threw away everything
        // somebody had typed whenever the server said no - the desk was busy, the format had gone,
        // they were already running as many as one person may - and left them with a blank screen and
        // a line of chat.
        waitingFor = desk;
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
            boolean unused = numbers.get(index) == buildNumber && !draft.kind().isLimited();
            GuiText.drawCentered(graphics, this.font, text, numberAt.get(index)[0], numberAt.get(index)[1], 60,
                    unused ? DIM : LABEL);
        }
        EventSettings settings = settings();
        String problem = desk == null ? "screen.gathering.events.host_at_a_desk" : settings.problem().orElse(null);
        List<FormatPreset> formats = FormatPresets.all();
        int format = Math.max(0, indexOf(formats, draft.formatId()));
        Component line = problem != null ? Component.translatable(problem)
                : Component.translatable("screen.gathering.event.chosen",
                        Component.translatable("screen.gathering.event.kind." + draft.kind().key()),
                        draft.kind().isLimited() ? FormatPresets.LIMITED.displayName() : formats.get(format).displayName(),
                        draft.bestOf(), draft.roundMinutes());
        GuiText.drawCentered(graphics, this.font, line, panel.x() + panel.width() / 2,
                panel.bottom() - MARGIN - ROW - 11, panel.width() - MARGIN * 2, problem != null ? WARN : DIM);
        said = line.getString();
    }
}
