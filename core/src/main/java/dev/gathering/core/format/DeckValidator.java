package dev.gathering.core.format;

import dev.gathering.core.card.CardMetadata;
import dev.gathering.core.card.Legality;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The one referee this mod permits.
 *
 * <p>A static deck check before a formatted game begins, exactly like a tournament deck
 * check, and then it is over. Nothing in here runs during play, nothing in here is consulted
 * by any verb, and free play skips it entirely. This fence is permanent, not a phase-one
 * limitation.
 *
 * <p>Everything is driven from the {@link FormatPreset} and from cached Scryfall data, so
 * the ban lists are Scryfall's rather than ours and go stale only as fast as the cache does.
 */
public final class DeckValidator {

    private DeckValidator() {
    }

    public static ValidationResult validate(ValidatableDeck deck, FormatPreset preset) {
        List<ValidationIssue> issues = new ArrayList<>();

        checkDeckSize(deck, preset, issues);
        checkCommanders(deck, preset, issues);
        checkCopyLimits(deck, preset, issues);
        checkLegality(deck, preset, issues);
        checkColourIdentity(deck, preset, issues);
        checkSideboard(deck, preset, issues);

        return new ValidationResult(preset, issues);
    }

    // ------------------------------------------------------------- deck size

    private static void checkDeckSize(ValidatableDeck deck, FormatPreset preset, List<ValidationIssue> issues) {
        int size = deck.size();
        if (size < preset.minimumDeckSize()) {
            issues.add(ValidationIssue.error("deck_too_small",
                    preset.displayName() + " needs at least " + preset.minimumDeckSize()
                            + " cards; this deck has " + size + "."));
        }
        if (preset.hasDeckSizeMaximum() && size > preset.maximumDeckSize()) {
            issues.add(ValidationIssue.error("deck_too_large",
                    preset.displayName() + " allows at most " + preset.maximumDeckSize()
                            + " cards; this deck has " + size + "."));
        }
    }

    // ------------------------------------------------------------ commanders

    private static void checkCommanders(ValidatableDeck deck, FormatPreset preset, List<ValidationIssue> issues) {
        CommanderRules rules = preset.commanderRules();
        List<CardMetadata> commanders = deck.commanders();

        if (!rules.inUse()) {
            if (!commanders.isEmpty()) {
                issues.add(ValidationIssue.warning("no_command_zone",
                        preset.displayName() + " has no command zone; "
                                + commanders.size() + " card(s) were listed as commanders."));
            }
            return;
        }

        if (commanders.size() < rules.minimumCommanders() || commanders.size() > rules.maximumCommanders()) {
            issues.add(ValidationIssue.error("commander_count",
                    preset.displayName() + " needs between " + rules.minimumCommanders() + " and "
                            + rules.maximumCommanders() + " commanders; this deck has " + commanders.size() + "."));
            return;
        }

        for (int position = 0; position < commanders.size(); position++) {
            CardMetadata commander = commanders.get(position);
            if (!rules.isEligible(commander, position)) {
                issues.add(ValidationIssue.error("commander_ineligible",
                        commander.name() + " cannot lead a " + preset.displayName()
                                + " deck; that slot needs " + rules.describeEligibility(position) + "."));
            }
        }

        if (!rules.allowsPairing(commanders)) {
            issues.add(ValidationIssue.error("commander_pairing",
                    "Two commanders are only allowed when both have Partner."));
        }
    }

    // ----------------------------------------------------------- copy limits

    private static void checkCopyLimits(ValidatableDeck deck, FormatPreset preset, List<ValidationIssue> issues) {
        Map<String, List<CardMetadata>> byCard = new LinkedHashMap<>();
        for (CardMetadata card : deck.deckProper()) {
            byCard.computeIfAbsent(copyKey(card), key -> new ArrayList<>()).add(card);
        }

        for (List<CardMetadata> copies : byCard.values()) {
            CardMetadata card = copies.get(0);
            int limit = copyLimitFor(card, preset);
            if (limit == UNLIMITED || copies.size() <= limit) {
                continue;
            }
            issues.add(ValidationIssue.error("too_many_copies",
                    "A " + preset.displayName() + " deck may contain at most " + limit + " copies of "
                            + card.name() + "; this deck has " + copies.size() + "."));
        }
    }

    private static final int UNLIMITED = -1;

    private static int copyLimitFor(CardMetadata card, FormatPreset preset) {
        // Basic lands, and cards whose own text says a deck may contain any number of them.
        // Both read off the card rather than a maintained list, so a new printing needs no
        // code change.
        if (card.isBasicLand() || card.allowsAnyNumber()) {
            return UNLIMITED;
        }
        // Restricted carries its own ceiling of one, which is the whole of Vintage's
        // restricted list handled without a line of Vintage-specific code.
        int ceiling = card.legalityIn(preset.legalitiesKey()).copyCeiling();
        return ceiling > 0 ? Math.min(ceiling, preset.copyLimit()) : preset.copyLimit();
    }

    /**
     * Copies are counted per card, not per printing.
     *
     * <p>Scryfall's oracle id is shared by every printing of the same card, which is exactly
     * what "four copies" means. Falling back to the name keeps a card with missing data from
     * silently escaping the limit.
     */
    private static String copyKey(CardMetadata card) {
        UUID oracleId = card.oracleId();
        return oracleId != null ? oracleId.toString() : "name:" + String.valueOf(card.name()).toLowerCase(
                java.util.Locale.ROOT);
    }

    // -------------------------------------------------------------- legality

    private static void checkLegality(ValidatableDeck deck, FormatPreset preset, List<ValidationIssue> issues) {
        Set<String> reportedBans = new LinkedHashSet<>();
        Set<String> reportedUnknowns = new LinkedHashSet<>();

        for (CardMetadata card : deck.deckProper()) {
            Legality legality = card.legalityIn(preset.legalitiesKey());
            if (legality == Legality.UNKNOWN) {
                if (reportedUnknowns.add(card.name())) {
                    issues.add(ValidationIssue.warning("legality_unknown",
                            "No " + preset.displayName() + " legality is cached for " + card.name() + "."));
                }
                continue;
            }
            if (!legality.playable() && reportedBans.add(card.name())) {
                issues.add(ValidationIssue.error("card_not_legal",
                        card.name() + " is " + legality.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                                + " in " + preset.displayName() + "."));
            }
        }
    }

    // ------------------------------------------------------- colour identity

    private static void checkColourIdentity(
            ValidatableDeck deck, FormatPreset preset, List<ValidationIssue> issues) {
        if (!preset.checksColourIdentity() || deck.commanders().isEmpty()) {
            return;
        }

        Set<String> allowed = new LinkedHashSet<>();
        deck.commanders().forEach(commander -> allowed.addAll(commander.colorIdentity()));

        Set<String> reported = new LinkedHashSet<>();
        for (CardMetadata card : deck.mainboard()) {
            Set<String> outside = new LinkedHashSet<>(card.colorIdentity());
            outside.removeAll(allowed);
            if (!outside.isEmpty() && reported.add(card.name())) {
                issues.add(ValidationIssue.error("colour_identity",
                        card.name() + " is outside the commander's colour identity (" + String.join("", outside)
                                + " not in " + (allowed.isEmpty() ? "colourless" : String.join("", allowed)) + ")."));
            }
        }
    }

    // ------------------------------------------------------------- sideboard

    private static void checkSideboard(ValidatableDeck deck, FormatPreset preset, List<ValidationIssue> issues) {
        int size = deck.sideboard().size();
        if (size == 0) {
            return;
        }
        if (!preset.hasSideboard()) {
            issues.add(ValidationIssue.error("no_sideboard",
                    preset.displayName() + " has no sideboard; this deck lists " + size + " cards."));
            return;
        }
        if (size > preset.maximumSideboard()) {
            issues.add(ValidationIssue.error("sideboard_too_large",
                    preset.displayName() + " allows a sideboard of " + preset.maximumSideboard()
                            + "; this one has " + size + "."));
        }
    }
}
