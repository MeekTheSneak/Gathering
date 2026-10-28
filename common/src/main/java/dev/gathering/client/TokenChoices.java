package dev.gathering.client;

import dev.gathering.core.card.TokenVariant;
import dev.gathering.network.CardSummary;
import dev.gathering.network.MakeTokenPayload;
import dev.gathering.network.TokenChoicesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Making a token by name, and being asked which one when the name means several.
 * <p>One row per token, labeled by what tells them apart - "Cat · 2/2 green", "Cat · 1/1 white ·
 * Lifelink" - built from the card summaries the server sent. Picking one makes that exact token,
 * at the count already asked for, and remembers the pick so the table's remembered-token row
 * makes the same one next time.
 * <p>Client only.
 */
public final class TokenChoices {

    private TokenChoices() {
    }

    /** Puts the question up over whatever screen is open, and back to it when answered. */
    public static void show(TokenChoicesPayload payload) {
        Minecraft client = Minecraft.getInstance();
        var back = client.screen;
        List<ChoiceScreen.Option> rows = new ArrayList<>();
        for (CardSummary choice : payload.choices()) {
            rows.add(new ChoiceScreen.Option(
                    Component.literal(labelFor(choice)),
                    // No setScreen here: choosing closes this screen first, which puts the one
                    // behind it back. Setting it again removed and re-built that screen, and a
                    // table screen rebuilt mid-game drops the selection, the pointer and the
                    // rolls it was showing.
                    () -> make(payload.table(), choice.front().name(), choice.scryfallId(),
                            payload.count())));
        }
        client.setScreen(new ChoiceScreen(
                Component.translatable("screen.gathering.token.which", payload.asked()), rows, back));
    }

    /** "Cat · 2/2 green · Vigilance": the name, then what sets this one apart. */
    static String labelFor(CardSummary token) {
        String apart = TokenVariant.describe(
                token.front().strength(), token.colorIdentity(), token.front().oracleText());
        return apart.isEmpty() ? token.front().name() : token.front().name() + " · " + apart;
    }

    /** Makes one exact token and remembers which it was. */
    static void make(net.minecraft.core.BlockPos table, String name, UUID printing, int count) {
        RecentThings.rememberToken(name);
        RecentThings.rememberTokenVariant(name, printing);
        ClientNetworking.send(new MakeTokenPayload(table, printing, count));
    }
}
