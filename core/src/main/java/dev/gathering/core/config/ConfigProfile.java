package dev.gathering.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A named set of server settings, for the three ways people actually run this.
 * <p>There are twenty-four settings and most operators want one of three things: a server
 * where cards turn up as you play, a server where a collection is a long project, or a server
 * where people bring decks they already have and the collecting is beside the point. Setting
 * twenty-four values by hand to get one of those is how you end up with a server that is
 * almost one of them and confusing in one particular corner.
 *
 * <p><b>A profile is a starting point, not a mode.</b> Applying one writes ordinary settings
 * through the ordinary validated path and then stops existing; there is no profile stored
 * anywhere, nothing that overrides a later edit, and no way for a profile to be "on". An
 * operator who applies one and then changes two things has a server with those two things
 * changed, which is the only behavior that does not surprise somebody a month later.
 *
 * <p><b>Nothing a player owns is touched.</b> These are settings about what happens next -
 * what drops, what a shop sells, what a pack costs. Applying one does not reach into anybody's
 * collection, inventory or decks, and cannot: it writes config keys and nothing else. A server
 * that changed what a card was worth would still leave every card where it was.
 *
 * <p>Pure. It knows key names and values; whether a key exists and whether a value is
 * acceptable are {@link GatheringConfig}'s, which is what the profiles are checked against.
 */
public final class ConfigProfile {

    private final String id;
    private final Map<String, String> settings;

    private ConfigProfile(String id, Map<String, String> settings) {
        this.id = id;
        this.settings = Map.copyOf(settings);
    }

    /** The profile's id, which is what an operator types. */
    public String id() {
        return id;
    }

    /** What it would set, in the order it reads best. */
    public Map<String, String> settings() {
        return settings;
    }

    /** The key its name is written under. */
    public String nameKey() {
        return "profile.gathering." + id;
    }

    /** The key its one-line description is written under. */
    public String aboutKey() {
        return nameKey() + ".about";
    }

    private static Map<String, String> map(String... pairs) {
        Map<String, String> made = new LinkedHashMap<>();
        for (int at = 0; at + 1 < pairs.length; at += 2) {
            made.put(pairs[at], pairs[at + 1]);
        }
        return made;
    }

    /**
     * Cards turn up as you play, and a collection builds itself.
     * <p>Loot from everything, a shop that rotates often, cheap packs. For a server where the
     * collecting is the ambient part of playing rather than the point of it.
     */
    public static final ConfigProfile CASUAL = new ConfigProfile("casual", map(
            "modes.collection_enabled", "true",
            "modes.import_enabled", "true",
            "collection.sealed_store_enabled", "true",
            "collection.sealed_price_booster", "4",
            "collection.sealed_rotation_hours", "12",
            "collection.village_shop_weight", "8",
            "collection.loot_sets", "[\"recent\"]",
            "collection.loot_recent_sets", "3"));

    /**
     * A collection is a long project.
     * <p>Rarer drops, a dearer shop that rotates slowly, and the whole back catalogue in the
     * pool so that what turns up is worth keeping. For a server that expects to be running in
     * a year.
     */
    public static final ConfigProfile LONG_RUN = new ConfigProfile("long_run", map(
            "modes.collection_enabled", "true",
            "modes.import_enabled", "false",
            "collection.sealed_store_enabled", "true",
            "collection.sealed_price_booster", "16",
            "collection.sealed_rotation_hours", "72",
            "collection.village_shop_weight", "2",
            "collection.loot_sets", "[\"all\"]",
            "collection.loot_recent_sets", "1"));

    /**
     * People bring decks they already have.
     * <p>Importing open to everyone, collecting out of the way. For a playgroup that wants the
     * table and not the economy - which is a perfectly ordinary way to want this mod, and the
     * one that is most annoying to configure by hand because it is mostly turning things off.
     */
    public static final ConfigProfile IMPORTED = new ConfigProfile("imported", map(
            "modes.import_enabled", "true",
            "import.allow_all_players", "true",
            "modes.collection_enabled", "false",
            "collection.sealed_store_enabled", "false",
            "collection.village_shop_weight", "0"));

    /** All of them, in the order they are offered. */
    public static List<ConfigProfile> all() {
        return List.of(CASUAL, LONG_RUN, IMPORTED);
    }

    /** The profile with this id, whatever case it was typed in. */
    public static Optional<ConfigProfile> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        return all().stream().filter(profile -> profile.id.equals(wanted)).findFirst();
    }

    /** One setting this profile would change, and what it would change it from. */
    public record Change(String path, String from, String to) {

        /** Whether this is actually a change, rather than a value that already matches. */
        public boolean matters() {
            return !to.equals(from);
        }
    }

    /**
     * What applying this would do, given what the server says now.
     * <p>Every setting the profile names, whether or not it differs - so an operator reading
     * the diff sees the whole of what the profile is about rather than only the part that
     * happens not to match today. {@link Change#matters()} separates the two.
     *
     * @param reads what the server currently answers for a given key, or null if it cannot
     */
    public List<Change> diff(java.util.function.Function<String, String> reads) {
        List<Change> changes = new java.util.ArrayList<>();
        settings.forEach((path, to) -> {
            String from = reads == null ? null : reads.apply(path);
            changes.add(new Change(path, from == null ? "" : from, to));
        });
        return List.copyOf(changes);
    }
}
