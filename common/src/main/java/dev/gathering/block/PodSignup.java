package dev.gathering.block;

import dev.gathering.core.draft.PodLobby;
import dev.gathering.core.draft.PodSettings;
import dev.gathering.item.PackComponent;
import dev.gathering.registry.GatheringComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * A draft or sealed event being signed up for at a table, with the packs it is holding.
 * <p>The packs are kept as the items that were handed over, not as a description of them:
 * what goes back to somebody who withdraws is the stack they put in, whatever else it was
 * carrying. What the rules need to know about each one is read off it when they ask, so the
 * held item and the rules' idea of it are one value and cannot drift apart.
 *
 * @param host     who created it
 * @param settings what they decided, fixed from creation
 * @param held     every pack put in, in order, one item each
 */
public record PodSignup(UUID host, PodSettings settings, List<Held> held) {

    /** One pack the table is holding, and who it is held for. */
    public record Held(UUID contributor, ItemStack pack) {

        public Held {
            if (contributor == null || pack == null || pack.isEmpty()) {
                throw new IllegalArgumentException("A held pack needs a pack and whose it is");
            }
            pack = pack.copyWithCount(1);
        }

        /** What the rules need to know about this pack. */
        public PodLobby.PackRef ref() {
            PackComponent about = pack.get(GatheringComponents.PACK.get());
            return about == null
                    ? new PodLobby.PackRef("", "", "")
                    : new PodLobby.PackRef(about.setCode(), about.kind(), about.color());
        }
    }

    public PodSignup {
        if (host == null || settings == null) {
            throw new IllegalArgumentException("A signup needs a host and settings");
        }
        held = held == null ? List.of() : List.copyOf(held);
    }

    public static PodSignup open(UUID host, PodSettings settings) {
        return new PodSignup(host, settings, List.of());
    }

    /** The same event as the rules see it. */
    public PodLobby lobby() {
        List<PodLobby.Entry> entries = new ArrayList<>(held.size());
        for (Held pack : held) {
            entries.add(new PodLobby.Entry(pack.contributor(), pack.ref()));
        }
        return new PodLobby(host, settings, entries);
    }

    public PodSignup with(Held pack) {
        List<Held> more = new ArrayList<>(held);
        more.add(pack);
        return new PodSignup(host, settings, more);
    }

    /** This player's packs, in the order they went in. */
    public List<Held> heldFor(UUID player) {
        List<Held> theirs = new ArrayList<>();
        for (Held pack : held) {
            if (pack.contributor().equals(player)) {
                theirs.add(pack);
            }
        }
        return List.copyOf(theirs);
    }

    /** Without this player's packs, for when they are handed back. */
    public PodSignup without(UUID player) {
        List<Held> kept = new ArrayList<>();
        for (Held pack : held) {
            if (!pack.contributor().equals(player)) {
                kept.add(pack);
            }
        }
        return new PodSignup(host, settings, kept);
    }

    // ------------------------------------------------------------------ saving

    private static final String HOST = "host";
    private static final String KIND = "kind";
    private static final String SOURCE = "source";
    private static final String SET_MODE = "set_mode";
    private static final String SETS = "sets";
    private static final String PACKS_EACH = "packs_each";
    private static final String PICKS = "picks";
    private static final String CARDS_GO = "cards_go";
    private static final String HELD = "held";
    private static final String CONTRIBUTOR = "contributor";
    private static final String PACK = "pack";

    public CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(HOST, host);
        tag.putString(KIND, settings.kind().name());
        tag.putString(SOURCE, settings.source().name());
        tag.putString(SET_MODE, settings.sets().mode().name());
        ListTag sets = new ListTag();
        for (String set : settings.sets().sets()) {
            sets.add(StringTag.valueOf(set));
        }
        tag.put(SETS, sets);
        tag.putInt(PACKS_EACH, settings.packsEach());
        tag.putInt(PICKS, settings.picksPerTurn());
        tag.putString(CARDS_GO, settings.cardsGo().name());
        ListTag packs = new ListTag();
        for (Held pack : held) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(CONTRIBUTOR, pack.contributor());
            entry.put(PACK, pack.pack().save(registries));
            packs.add(entry);
        }
        tag.put(HELD, packs);
        return tag;
    }

    /**
     * What a saved signup read back as.
     *
     * @param signup         every pack that read, with the settings if they read too
     * @param settingsBroken whether the settings did not make sense any more. The packs are
     *                       still read, under the usual settings, so they can be handed back:
     *                       an event nobody can start must not keep anybody's packs
     */
    public record Loaded(PodSignup signup, boolean settingsBroken) {
    }

    /**
     * Reads a signup back, keeping every pack that reads.
     * <p>A pack that will not read is reported through {@code unreadable} rather than dropped
     * silently, so whoever it belonged to can be told: it was somebody's pack.
     */
    public static Loaded read(
            CompoundTag tag, HolderLookup.Provider registries, java.util.function.Consumer<UUID> unreadable) {
        UUID host = tag.getUUID(HOST);
        PodSettings settings;
        boolean broken = false;
        try {
            List<String> named = new ArrayList<>();
            ListTag sets = tag.getList(SETS, Tag.TAG_STRING);
            for (int index = 0; index < sets.size(); index++) {
                named.add(sets.getString(index));
            }
            settings = new PodSettings(
                    PodSettings.Kind.valueOf(tag.getString(KIND)),
                    PodSettings.Source.valueOf(tag.getString(SOURCE)),
                    new PodSettings.SetRule(PodSettings.SetRule.Mode.valueOf(tag.getString(SET_MODE)), named),
                    tag.getInt(PACKS_EACH),
                    tag.getInt(PICKS),
                    PodSettings.CardsGo.valueOf(tag.getString(CARDS_GO)));
            broken = settings.problem().isPresent();
        } catch (IllegalArgumentException unreadableSettings) {
            settings = PodSettings.usual(PodSettings.Kind.DRAFT);
            broken = true;
        }
        List<Held> held = new ArrayList<>();
        ListTag packs = tag.getList(HELD, Tag.TAG_COMPOUND);
        for (int index = 0; index < packs.size(); index++) {
            CompoundTag entry = packs.getCompound(index);
            UUID contributor = entry.hasUUID(CONTRIBUTOR) ? entry.getUUID(CONTRIBUTOR) : null;
            ItemStack pack = ItemStack.parse(registries, entry.getCompound(PACK)).orElse(ItemStack.EMPTY);
            if (contributor == null || pack.isEmpty()) {
                if (contributor != null) {
                    unreadable.accept(contributor);
                }
                continue;
            }
            held.add(new Held(contributor, pack));
        }
        return new Loaded(new PodSignup(host, settings, held), broken);
    }
}
