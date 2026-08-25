package dev.gathering.core.draft;

import dev.gathering.core.card.CardIdentity;
import dev.gathering.core.game.PlayerRef;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The pieces both of the pod's codecs write the same way.
 *
 * <p>There are two - one for what a drafter is sent and one for what the server keeps - and
 * they share every primitive below. Written out twice they would agree on the day they were
 * written; the first time either grew a field, a pod saved by one would come back wrong
 * through the other, which is a draft that survives a restart as somebody else's cards.
 */
final class DraftBytes {

    /** A ceiling on any length read from the wire or from disk, checked before it sizes. */
    static final int MAX_ENTRIES = 20_000;

    private DraftBytes() {
    }

    static void identities(DataOutput out, List<CardIdentity> cards) throws IOException {
        out.writeInt(cards.size());
        for (CardIdentity card : cards) {
            boolean printing = card.scryfallId() != null;
            out.writeBoolean(printing);
            if (printing) {
                out.writeLong(card.scryfallId().getMostSignificantBits());
                out.writeLong(card.scryfallId().getLeastSignificantBits());
            } else {
                out.writeUTF(card.customId());
            }
            out.writeBoolean(card.foil());
        }
    }

    static List<CardIdentity> identities(DataInput in) throws IOException {
        int count = size(in.readInt());
        List<CardIdentity> cards = new ArrayList<>(Math.min(count, 1024));
        for (int index = 0; index < count; index++) {
            if (in.readBoolean()) {
                UUID id = new UUID(in.readLong(), in.readLong());
                cards.add(CardIdentity.ofPrinting(id, in.readBoolean()));
            } else {
                cards.add(CardIdentity.ofCustom(in.readUTF(), in.readBoolean()));
            }
        }
        return cards;
    }

    static void player(DataOutput out, PlayerRef player) throws IOException {
        out.writeLong(player.id().getMostSignificantBits());
        out.writeLong(player.id().getLeastSignificantBits());
        out.writeUTF(player.name());
    }

    static PlayerRef player(DataInput in) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());
        String name = in.readUTF();
        if (name.isBlank()) {
            throw new IOException("A drafter with no name");
        }
        return new PlayerRef(id, name);
    }

    /**
     * A length off the wire or off the disk, refused before it sizes anything.
     *
     * <p>A hostile or merely corrupt source saying "two billion cards follow" must not get an
     * allocation of two billion out of this before the read fails.
     */
    static int size(int size) throws IOException {
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IOException("Implausible length in a pod: " + size);
        }
        return size;
    }

    static int place(int index) throws IOException {
        if (index < 0 || index > DraftRules.LARGEST_POD) {
            throw new IOException("Implausible place in a pod: " + index);
        }
        return index;
    }
}
