package dev.gathering.client;

import dev.gathering.core.draft.PodSettings;
import dev.gathering.core.ui.Rect;
import dev.gathering.network.CreatePodPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Setting up a draft or sealed event at this table.
 * <p>Every choice the host makes, in rows, with the one picked ringed - the same way the table's
 * own setup screen picks a format - and a line under them that says either what is chosen or
 * why it cannot be. What the line says comes from {@link PodSettings#problem()}, which is also
 * what the server asks, so a combination the screen accepts is one the server accepts.
 * <p>Client-only.
 */
public final class PodCreateScreen extends Screen {

    private static final int LABEL = 0xFFE8E4DC;
    private static final int DIM = 0xFF9A9690;
    private static final int WARN = 0xFFE0B15A;

    private static final int PANEL_WIDTH = 300;
    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 4;
    private static final int HEADING = 11;

    private final BlockPos table;

    private PodSettings.Kind kind = PodSettings.Kind.DRAFT;
    private PodSettings.Source source = PodSettings.Source.EACH_BRINGS;
    private PodSettings.SetRule.Mode setMode = PodSettings.SetRule.Mode.ANY;
    private int packsEach = PodSettings.USUAL_DRAFT_PACKS;
    private int picks = 0;
    private PodSettings.CardsGo cardsGo = PodSettings.CardsGo.PLAYERS_KEEP;

    private EditBox setsField;
    private String setsTyped = "";
    private Rect panel = Rect.NONE;
    private final List<int[]> headings = new ArrayList<>();
    private final List<Component> headingText = new ArrayList<>();
    private String saidLastFrame = "";

    public PodCreateScreen(BlockPos table) {
        super(Component.translatable("screen.gathering.pod.create"));
        this.table = table;
    }

    @Override
    protected void init() {
        headings.clear();
        headingText.clear();
        int rows = 7;
        int height = MARGIN * 2 + ROW_HEIGHT + rows * (HEADING + ROW_HEIGHT + GAP) + ROW_HEIGHT + GAP * 2
                + ROW_HEIGHT + GAP * 2;
        panel = new Rect((this.width - PANEL_WIDTH) / 2, Math.max(MARGIN, (this.height - height) / 2),
                PANEL_WIDTH, Math.min(height, this.height - MARGIN * 2));
        int y = panel.y() + MARGIN + ROW_HEIGHT;

        y = row(y, "screen.gathering.pod.kind", PodSettings.Kind.values(),
                value -> kind == value, value -> {
                    kind = value;
                    packsEach = value == PodSettings.Kind.SEALED ? PodSettings.USUAL_SEALED_PACKS : PodSettings.USUAL_DRAFT_PACKS;
                    if (value == PodSettings.Kind.SEALED) {
                        picks = 0;
                    }
                }, value -> "screen.gathering.pod.kind." + value.key());
        y = row(y, "screen.gathering.pod.source", PodSettings.Source.values(),
                value -> source == value, value -> source = value,
                value -> "screen.gathering.pod.source." + value.key());
        y = row(y, "screen.gathering.pod.sets", PodSettings.SetRule.Mode.values(),
                value -> setMode == value, value -> setMode = value,
                value -> "screen.gathering.pod.sets." + value.name().toLowerCase(Locale.ROOT));

        // What the set rule names, typed. One code, or one per pack separated by commas.
        heading(y, "screen.gathering.pod.set_codes");
        setsField = new EditBox(this.font, panel.x() + MARGIN, y + HEADING,
                panel.width() - MARGIN * 2, ROW_HEIGHT, Component.translatable("screen.gathering.pod.set_codes"));
        setsField.setMaxLength(PodSettings.MOST_PACKS_EACH * 6);
        setsField.setValue(setsTyped);
        setsField.setResponder(typed -> setsTyped = typed);
        addRenderableWidget(setsField);
        y += HEADING + ROW_HEIGHT + GAP;

        // How many packs each, as a number with a step either side.
        heading(y, "screen.gathering.pod.packs_each");
        int step = 24;
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, y + HEADING, step, ROW_HEIGHT,
                Component.literal("-"), () -> packsEach = Math.max(1, packsEach - 1)));
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN + step + GAP + 40 + GAP, y + HEADING, step,
                ROW_HEIGHT, Component.literal("+"), () -> packsEach = Math.min(PodSettings.MOST_PACKS_EACH, packsEach + 1)));
        packsAt = new int[] {panel.x() + MARGIN + step + GAP + 20, y + HEADING + (ROW_HEIGHT - this.font.lineHeight) / 2};
        y += HEADING + ROW_HEIGHT + GAP;

        Integer[] pickChoices = {0, 1, 2};
        y = row(y, "screen.gathering.pod.picks", pickChoices, value -> picks == value,
                value -> picks = kind == PodSettings.Kind.SEALED ? 0 : value,
                value -> value == 0 ? "screen.gathering.pod.picks.auto" : "screen.gathering.pod.picks." + value);
        y = row(y, "screen.gathering.pod.cards_go", PodSettings.CardsGo.values(),
                value -> cardsGo == value, value -> cardsGo = value,
                value -> "screen.gathering.pod.cards_go." + value.key());

        int half = (panel.width() - MARGIN * 2 - GAP) / 2;
        int decide = panel.bottom() - MARGIN - ROW_HEIGHT;
        addRenderableWidget(GatheringButtons.of(panel.x() + MARGIN, decide, half, ROW_HEIGHT,
                Component.translatable("gui.cancel"), this::onClose));
        addRenderableWidget(GatheringButtons.of(panel.right() - MARGIN - half, decide, half, ROW_HEIGHT,
                Component.translatable("screen.gathering.pod.create_button"), this::create));
    }

    private int[] packsAt = {0, 0};

    private <T> int row(int y, String headingKey, T[] values, java.util.function.Predicate<T> chosen,
            java.util.function.Consumer<T> choose, java.util.function.Function<T, String> labelKey) {
        heading(y, headingKey);
        int columns = values.length;
        int width = (panel.width() - MARGIN * 2 - GAP * (columns - 1)) / columns;
        for (int index = 0; index < columns; index++) {
            T value = values[index];
            addRenderableWidget(GatheringButtons.toggle(panel.x() + MARGIN + index * (width + GAP), y + HEADING,
                    width, ROW_HEIGHT, Component.translatable(labelKey.apply(value)),
                    () -> chosen.test(value), () -> choose.accept(value)));
        }
        return y + HEADING + ROW_HEIGHT + GAP;
    }

    private void heading(int y, String key) {
        headings.add(new int[] {panel.x() + MARGIN, y + 1});
        headingText.add(Component.translatable(key));
    }

    /** The settings as chosen right now, with the typed set codes read into a rule. */
    PodSettings settings() {
        List<String> typed = Arrays.stream(setsTyped.split("[,\\s]+"))
                .map(code -> code.trim().toLowerCase(Locale.ROOT)).filter(code -> !code.isEmpty()).toList();
        PodSettings.SetRule rule;
        try {
            rule = switch (setMode) {
                case ANY -> PodSettings.SetRule.ANY;
                case ONE_SET -> PodSettings.SetRule.oneSet(typed.isEmpty() ? "" : typed.get(0));
                case PER_PACK -> PodSettings.SetRule.perPack(typed);
            };
        } catch (IllegalArgumentException incomplete) {
            return null;
        }
        return new PodSettings(kind, source, rule, packsEach, kind == PodSettings.Kind.SEALED ? 0 : picks, cardsGo);
    }

    private void create() {
        PodSettings settings = settings();
        if (settings == null || settings.problem().isPresent()) {
            return;
        }
        ClientNetworking.send(new CreatePodPayload(table, settings));
        this.onClose();
    }

    /** What the line under the choices said last frame. For the scripted harness. */
    String said() {
        return saidLastFrame;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GatheringSprites.panel(graphics, panel.x(), panel.y(), panel.width(), panel.height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        GuiText.drawCentered(graphics, this.font, this.title,
                panel.x() + panel.width() / 2, panel.y() + 4, panel.width() - MARGIN * 2, LABEL);
        for (int index = 0; index < headings.size(); index++) {
            int[] at = headings.get(index);
            GuiText.draw(graphics, this.font, headingText.get(index), at[0], at[1], panel.width() - MARGIN * 2, DIM);
        }
        GuiText.drawCentered(graphics, this.font, Component.literal(Integer.toString(packsEach)),
                packsAt[0], packsAt[1], 40, LABEL);

        PodSettings settings = settings();
        String problem = settings == null ? "message.gathering.pod.set_codes_incomplete"
                : settings.problem().orElse(null);
        Component line = problem != null
                ? Component.translatable(problem)
                : Component.translatable("screen.gathering.pod.chosen",
                        Component.translatable("screen.gathering.pod.kind." + kind.key()), packsEach,
                        Component.translatable("screen.gathering.pod.cards_go." + cardsGo.key()));
        GuiText.drawCentered(graphics, this.font, line, panel.x() + panel.width() / 2,
                panel.bottom() - MARGIN - ROW_HEIGHT - GAP - this.font.lineHeight - 2,
                panel.width() - MARGIN * 2, problem != null ? WARN : DIM);
        saidLastFrame = line.getString();
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
