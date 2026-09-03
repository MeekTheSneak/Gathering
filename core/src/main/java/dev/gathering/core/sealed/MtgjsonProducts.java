package dev.gathering.core.sealed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.gathering.core.booster.BoosterCodec;
import dev.gathering.core.booster.BoosterCodecException;
import dev.gathering.core.card.CardIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads a set's real products out of the same file its collation comes from.
 * <p>Sealed product is published per set alongside the booster sheets - every pack, box,
 * case, bundle, precon and prerelease kit that was actually sold, with what is in each. So
 * the two ways into a collection are built from the same place the packs are: what a player
 * finds in the world and what a shop sells are both real products, and nothing exists in the
 * game that was not on a shelf.
 * <p>Read the way collation is read, and for the same reasons. Card contents are bridged to
 * Scryfall printings through the bridge the caller hands in, so a bundle's promo is a real
 * printing rather than a name; anything that cannot be bridged is left out and counted rather
 * than guessed at. And a container names what it holds by that product's own id rather than
 * copying its contents, so a booster's odds live in exactly one place.
 * <p>Pure. Nothing here reaches a network or a file.
 */
public final class MtgjsonProducts {

    private MtgjsonProducts() {
    }

    /**
     * What one set file's products came to.
     *
     * @param products everything published for the set, in the order it was published
     * @param notes    what was left out and why, in plain words
     */
    public record Reading(String setCode, List<SealedProduct> products, List<String> notes) {

        public Reading {
            products = products == null ? List.of() : List.copyOf(products);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        public boolean isEmpty() {
            return products.isEmpty();
        }

        /** Just the single boosters, which are the products the opener can already open. */
        public List<SealedProduct> boosters() {
            List<SealedProduct> packs = new ArrayList<>();
            for (SealedProduct product : products) {
                if (product.isOneBooster()) {
                    packs.add(product);
                }
            }
            return List.copyOf(packs);
        }

        /** One product by its published id, for a container naming what it holds. */
        public SealedProduct byId(String productId) {
            for (SealedProduct product : products) {
                if (product.productId().equals(productId)) {
                    return product;
                }
            }
            return null;
        }
    }

    /**
     * Reads the products in a set file.
     *
     * @param bridge MTGJSON's own card ids to Scryfall printings, as
     *               {@link dev.gathering.core.booster.MtgjsonCollation#printings} gives them
     */
    public static Reading read(JsonObject file, Map<String, UUID> bridge)
            throws BoosterCodecException {
        JsonObject data = BoosterCodec.object(file, "data");
        String setCode = BoosterCodec.string(data, "code").trim()
                .toLowerCase(java.util.Locale.ROOT);
        Map<String, UUID> printings = bridge == null ? Map.of() : bridge;

        List<String> notes = new ArrayList<>();
        List<SealedProduct> products = new ArrayList<>();
        JsonElement sealed = data.get("sealedProduct");
        if (sealed == null || sealed.isJsonNull()) {
            notes.add(setCode + " publishes no sealed product");
            return new Reading(setCode, products, notes);
        }
        if (!sealed.isJsonArray()) {
            throw new BoosterCodecException(setCode + ": 'sealedProduct' is not a list");
        }

        for (JsonElement element : sealed.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new BoosterCodecException(setCode + ": a product that is not an object");
            }
            products.add(product(element.getAsJsonObject(), setCode, printings, notes));
        }
        return new Reading(setCode, products, notes);
    }

    /**
     * Every set's products, out of MTGJSON's one list of every set.
     * <p>The same entries the per-set files carry, published together. What it buys is a
     * server that draws its packs from every set ever printed for one request rather than
     * several hundred.
     * <p>No card bridge, because the list carries no cards to bridge with. Cards published
     * inside a product are counted as unbridged rather than dropped, so a bundle with a promo
     * in it is still a bundle here and not a booster - see
     * {@link SealedProduct.Contents#unbridged()}. That makes this the right thing to read for
     * what is on a shelf and what is in the world, and the wrong thing to read for what comes
     * out of a product: that is the set's own file, fetched when somebody opens one.
     *
     * @return one reading per set that publishes sealed product, keyed by set code
     */
    public static Map<String, Reading> readSetList(JsonObject setList)
            throws BoosterCodecException {
        return readSets(BoosterCodec.array(setList, "data"));
    }

    private static Map<String, Reading> readSets(JsonArray sets) throws BoosterCodecException {
        Map<String, Reading> bySet = new LinkedHashMap<>();
        for (JsonElement element : sets) {
            if (!element.isJsonObject()) {
                throw new BoosterCodecException("the set list holds something that is not a set");
            }
            JsonObject set = element.getAsJsonObject();
            String setCode = text(set, "code").trim().toLowerCase(java.util.Locale.ROOT);
            JsonElement sealed = set.get("sealedProduct");
            if (setCode.isEmpty() || sealed == null || sealed.isJsonNull()) {
                continue;
            }
            if (!sealed.isJsonArray()) {
                throw new BoosterCodecException(setCode + ": 'sealedProduct' is not a list");
            }
            List<String> notes = new ArrayList<>();
            List<SealedProduct> products = new ArrayList<>();
            for (JsonElement each : sealed.getAsJsonArray()) {
                if (!each.isJsonObject()) {
                    throw new BoosterCodecException(setCode + ": a product that is not an object");
                }
                products.add(product(each.getAsJsonObject(), setCode, Map.of(), notes));
            }
            if (!products.isEmpty()) {
                bySet.put(setCode, new Reading(setCode, products, notes));
            }
        }
        return Map.copyOf(bySet);
    }

    private static SealedProduct product(
            JsonObject json, String setCode, Map<String, UUID> printings, List<String> notes)
            throws BoosterCodecException {
        String name = json.has("name") ? BoosterCodec.string(json, "name") : "";
        String where = setCode + " '" + name + "'";
        String productId = json.has("uuid") ? BoosterCodec.string(json, "uuid", where) : "";
        String category = text(json, "category");
        String subtype = text(json, "subtype");
        int cards = json.has("cardCount")
                ? BoosterCodec.weight(json.get("cardCount"), where + ", cardCount")
                : 0;
        String own = json.has("setCode") ? text(json, "setCode") : setCode;

        JsonElement contents = json.get("contents");
        if (contents == null || contents.isJsonNull()) {
            // A product with nothing published in it. Real, and not something to guess at.
            notes.add(where + ": nothing is published about what is in it");
            return new SealedProduct(productId, name, own, category, subtype, cards, null);
        }
        if (!contents.isJsonObject()) {
            throw new BoosterCodecException(where + ": 'contents' is not an object");
        }
        return new SealedProduct(productId, name, own, category, subtype, cards,
                contents(contents.getAsJsonObject(), setCode, where, printings, notes));
    }

    private static SealedProduct.Contents contents(JsonObject json, String setCode, String where,
            Map<String, UUID> printings, List<String> notes)
            throws BoosterCodecException {
        List<SealedProduct.Booster> boosters = new ArrayList<>();
        for (JsonObject entry : listOf(json, "pack", where)) {
            boosters.add(new SealedProduct.Booster(text(entry, "set"), text(entry, "code")));
        }

        List<SealedProduct.Held> holds = new ArrayList<>();
        for (JsonObject entry : listOf(json, "sealed", where)) {
            holds.add(new SealedProduct.Held(
                    text(entry, "uuid"), text(entry, "name"),
                    entry.has("count")
                            ? BoosterCodec.weight(entry.get("count"), where + ", count")
                            : 1));
        }

        List<CardIdentity> cards = new ArrayList<>();
        int unbridged = 0;
        for (JsonObject entry : listOf(json, "card", where)) {
            UUID printing = printings.get(text(entry, "uuid"));
            if (printing == null) {
                unbridged++;
                continue;
            }
            boolean foil = entry.has("foil") && entry.get("foil").isJsonPrimitive()
                    && entry.get("foil").getAsJsonPrimitive().isBoolean()
                    && entry.get("foil").getAsBoolean();
            cards.add(CardIdentity.ofPrinting(printing, foil));
        }
        if (unbridged > 0) {
            notes.add(where + ": " + unbridged + " card(s) in it are printed in another set "
                    + "and were left out");
        }

        // Named, not listed. What is actually in it lives further down the same file and is
        // read by MtgjsonDecks; the set is carried along because a starter kit names decks
        // that belong to a different set from the box.
        List<SealedProduct.InDeck> decks = new ArrayList<>();
        for (JsonObject entry : listOf(json, "deck", where)) {
            String named = text(entry, "set");
            decks.add(new SealedProduct.InDeck(
                    text(entry, "name"), named.isEmpty() ? setCode : named));
        }

        // The dice, the storage box, the reference cards. Kept by name so a product can say
        // honestly what was in the box, and never turned into anything a player receives.
        List<String> extras = new ArrayList<>();
        for (JsonObject entry : listOf(json, "other", where)) {
            extras.add(text(entry, "name"));
        }
        return new SealedProduct.Contents(boosters, holds, cards, decks, extras, unbridged);
    }

    private static List<JsonObject> listOf(JsonObject json, String field, String where)
            throws BoosterCodecException {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw new BoosterCodecException(where + ": '" + field + "' is not a list");
        }
        List<JsonObject> entries = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement each : array) {
            if (!each.isJsonObject()) {
                throw new BoosterCodecException(where + ": '" + field + "' holds something "
                        + "that is not an entry");
            }
            entries.add(each.getAsJsonObject());
        }
        return entries;
    }

    private static String text(JsonObject json, String field) {
        JsonElement element = json.get(field);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    /** Every product in a set file, keyed by its published id, for looking a container up. */
    public static Map<String, SealedProduct> byId(Reading reading) {
        Map<String, SealedProduct> byId = new LinkedHashMap<>();
        if (reading != null) {
            for (SealedProduct product : reading.products()) {
                if (!product.productId().isEmpty()) {
                    byId.put(product.productId(), product);
                }
            }
        }
        return byId;
    }
}
