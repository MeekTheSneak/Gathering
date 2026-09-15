package dev.gathering.client;

import dev.gathering.core.match.TableTerms;
import dev.gathering.core.match.TableTerms.Note;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * What a table is playing, said in the board's top row and in full in its tooltip.
 * <p>The row is short on room - it shares the width with every seat's column - so this offers the
 * sentence at several lengths, longest first, and the board takes the first that fits. What is
 * unusual about a table - for keeps, no format, an odd match length - is the last thing to go and
 * the one part drawn in a warning color, so a table played for keeps says so even in a narrow
 * window. The tooltip always says everything.
 * <p>Client-only.
 */
final class TableTermsText {

    /** The color of what somebody sitting down would not expect. The same warm amber the board warns in. */
    static final int UNUSUAL = 0xFFE8B24A;

    /** The color of the rest of it: facts, said quietly. */
    static final int PLAIN = 0xFFA9A49A;

    private TableTermsText() {
    }

    /**
     * The turn and the table's terms, from the longest way of saying it to just the turn.
     *
     * @param turn "Turn 3 - Dev", already colored for whose turn it is - or null for the terms alone,
     *     on a row of their own
     */
    static List<Component> candidates(Component turn, TableTerms terms) {
        List<Component> ways = new ArrayList<>();
        String format = terms.format().map(preset -> preset.displayName()).orElse("");
        boolean oddLength = terms.notes().contains(Note.UNUSUAL_LENGTH);
        List<Component> warnings = warnings(terms);
        if (!format.isEmpty()) {
            if (terms.bestOf() > 1) {
                ways.add(join(turn, plain(Component.literal(format + ", "))
                        .append(colored(Component.translatable("screen.gathering.table.terms.best_of", terms.bestOf()),
                                oddLength))
                        .append(plain(Component.translatable("screen.gathering.table.terms.game", terms.gameNumber()))),
                        warnings));
                ways.add(join(turn, plain(Component.literal(format + ", "))
                        .append(plain(Component.translatable("screen.gathering.table.terms.game_short", terms.gameNumber()))),
                        warnings));
            } else {
                MutableComponent single = plain(Component.literal(format));
                if (oddLength) {
                    single.append(plain(Component.literal(", ")))
                            .append(colored(Component.translatable("screen.gathering.table.terms.one_game"), true));
                }
                ways.add(join(turn, single, warnings));
            }
            ways.add(join(turn, plain(Component.literal(format)), warnings));
        }
        if (!warnings.isEmpty()) {
            ways.add(join(turn, null, warnings));
            if (warnings.size() > 1) {
                // The one that matters most on its own, before falling back to a bare mark.
                ways.add(join(turn, null, warnings.subList(0, 1)));
            }
        }
        if (terms.unusual()) {
            // The shortest mark there is, for a window that has room for nothing else: the tooltip
            // says what it means.
            ways.add(join(turn, null, List.of(colored(Component.literal("!"), true))));
        }
        ways.add(turn == null ? Component.empty() : turn);
        return ways;
    }

    /** Everything about the table, one fact a line, for the tooltip over the turn. */
    static List<Component> tooltip(TableTerms terms) {
        List<Component> lines = new ArrayList<>();
        terms.format().ifPresentOrElse(
                preset -> lines.add(Component.translatable("screen.gathering.table.terms.tip.format", preset.displayName())),
                () -> lines.add(Component.translatable(terms.freePlay()
                        ? "screen.gathering.table.terms.tip.free_play" : "screen.gathering.table.terms.tip.no_format")));
        lines.add(terms.bestOf() > 1
                ? Component.translatable("screen.gathering.table.terms.tip.match", terms.bestOf(), terms.gameNumber())
                : Component.translatable("screen.gathering.table.terms.tip.one_game"));
        for (Note note : terms.notes()) {
            switch (note) {
                case FOR_KEEPS -> lines.add(Component.translatable("screen.gathering.table.terms.tip.for_keeps")
                        .withColor(UNUSUAL));
                case UNUSUAL_LENGTH -> lines.add(Component.translatable("screen.gathering.table.terms.tip.unusual_length",
                        terms.format().map(preset -> preset.displayName()).orElse(""), terms.usualLength()).withColor(UNUSUAL));
                case FREE_PLAY -> {
                    // Already the first line.
                }
                case EVENT -> lines.add(Component.translatable("screen.gathering.table.terms.tip.event", terms.eventTable()));
                case PRACTICE -> lines.add(Component.translatable("screen.gathering.table.terms.tip.practice"));
            }
        }
        return lines;
    }

    /** The unusual parts, in the order they matter, each already colored. */
    private static List<Component> warnings(TableTerms terms) {
        List<Component> warnings = new ArrayList<>();
        for (Note note : terms.notes()) {
            switch (note) {
                case FOR_KEEPS -> warnings.add(colored(Component.translatable("screen.gathering.table.terms.for_keeps"), true));
                case FREE_PLAY -> warnings.add(colored(Component.translatable("screen.gathering.table.terms.free_play"), true));
                case EVENT -> warnings.add(plain(Component.translatable("screen.gathering.table.terms.event", terms.eventTable())));
                case PRACTICE -> warnings.add(plain(Component.translatable("screen.gathering.table.terms.practice")));
                case UNUSUAL_LENGTH -> {
                    // Said in the length itself, colored there.
                }
            }
        }
        return warnings;
    }

    /** Parts after the turn, if there is one: two spaces after the turn, a dot between the rest. */
    private static Component join(Component turn, Component middle, List<Component> after) {
        MutableComponent line = Component.empty();
        boolean started = false;
        if (turn != null) {
            line.append(turn);
            started = true;
        }
        if (middle != null) {
            if (started) {
                line.append(plain(Component.literal("  ")));
            }
            line.append(middle);
            started = true;
        }
        for (Component part : after) {
            if (started) {
                line.append(plain(Component.literal(" \u00b7 ")));
            }
            line.append(part);
            started = true;
        }
        return line;
    }

    private static MutableComponent plain(MutableComponent part) {
        return part.withColor(PLAIN);
    }

    private static MutableComponent colored(MutableComponent part, boolean unusual) {
        return part.withColor(unusual ? UNUSUAL : PLAIN);
    }
}
