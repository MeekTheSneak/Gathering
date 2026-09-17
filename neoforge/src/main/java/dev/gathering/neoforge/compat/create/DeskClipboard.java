package dev.gathering.neoforge.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import dev.gathering.block.ScorekeepersDeskBlockEntity;
import dev.gathering.server.events.EventBoard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A Create Clipboard used on a Scorekeeper's Desk takes down the tournament: this round's pairings,
 * each ticked once its result is in, and then the standings. Something a host or a judge walking the
 * hall can carry and read without going back to the desk.
 * <p>What is written is what the event screen shows anybody - public results only. The pages are
 * the clipboard's own afterwards, to tick and add to. A desk running nothing leaves the clipboard to
 * do what it always does, and so does crouching, which Create gives to placing it.
 */
final class DeskClipboard {

    /** Lines to a page, heading included: a page holds about this many one-line entries with room to write. */
    static final int LINES_PER_PAGE = 12;

    private DeskClipboard() {
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || !AllBlocks.CLIPBOARD.isIn(held) || event.getEntity().isShiftKeyDown()
                || event.getEntity().isSpectator()
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof ScorekeepersDeskBlockEntity desk)) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            EventBoard.Board board = EventBoard.atDesk(level, event.getPos())
                    .filter(found -> found.phase() != dev.gathering.core.tournament.Tournament.Phase.CANCELLED).orElse(null);
            if (board == null) {
                return;
            }
            held.set(AllDataComponents.CLIPBOARD_CONTENT, new ClipboardContent(ClipboardType.WRITTEN, pagesOf(board), false));
            level.playSound(null, event.getPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 1.2f);
            dev.gathering.server.Notices.tell(event.getEntity(), Component.translatable("message.gathering.desk.clipboard", board.name()));
        } else if (!desk.label().isShown()) {
            // The client knows a desk runs something by its label; without one it is a lectern.
            return;
        }
        // Not also the desk's own use, which would open the tournament's screen over the hand. A called-off
        // tournament shows no label and is passed over above, so a host can still take the desk on.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
    }

    /** The pairings, then the standings, each a heading and as many pages as they need. */
    static List<List<ClipboardEntry>> pagesOf(EventBoard.Board board) {
        List<List<ClipboardEntry>> pages = new ArrayList<>();
        if (!board.pairings().isEmpty()) {
            List<ClipboardEntry> lines = new ArrayList<>();
            for (EventBoard.Match match : board.pairings()) {
                MutableComponent line = match.isBye()
                        ? Component.translatable("display.gathering.bye", match.first())
                        : Component.literal(match.table() + ". " + match.first() + " - " + match.second()
                                + (match.result().isEmpty() ? "" : "  " + match.result()));
                // Ticked when the result is in, as a judge ticks a match slip.
                lines.add(new ClipboardEntry(match.isBye() || !match.result().isEmpty(), line));
            }
            addPages(pages, Component.translatable("clipboard.gathering.pairings", board.round()), lines);
        }
        if (board.round() > 0 && !board.standings().isEmpty()) {
            List<ClipboardEntry> lines = new ArrayList<>();
            for (EventBoard.Standing row : board.standings()) {
                lines.add(new ClipboardEntry(false, Component.literal(row.rank() + ". " + row.name() + "  " + row.points()
                        + " (" + row.wins() + "-" + row.losses() + "-" + row.draws() + ")")));
            }
            addPages(pages, Component.translatable("clipboard.gathering.standings"), lines);
        }
        if (pages.isEmpty()) {
            addPages(pages, Component.literal(board.name()),
                    List.of(new ClipboardEntry(false, Component.translatable("display.gathering.signed_up", board.players()))));
        }
        return pages;
    }

    private static void addPages(List<List<ClipboardEntry>> pages, MutableComponent heading, List<ClipboardEntry> lines) {
        List<ClipboardEntry> page = null;
        for (ClipboardEntry line : lines) {
            if (page == null || page.size() == LINES_PER_PAGE) {
                page = new ArrayList<>();
                page.add(new ClipboardEntry(false, heading.copy().withStyle(ChatFormatting.BOLD)));
                pages.add(page);
            }
            page.add(line);
        }
    }
}
