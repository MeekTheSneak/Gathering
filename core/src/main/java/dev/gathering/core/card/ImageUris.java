package dev.gathering.core.card;

import java.util.Optional;

/**
 * The image tiers Scryfall publishes for one card face.
 *
 * <p>The mod ships zero card images and never relays image bytes over its own network.
 * Each client fetches from these URIs itself and keeps its own disk cache; card identity
 * travels as a UUID and these strings travel as display metadata.
 *
 * <p>Two tiers are actually used: {@code small} for the in-world table miniatures and
 * {@code normal} for the zoom overlay and the seated GUI.
 */
public record ImageUris(String small, String normal, String large, String png, String artCrop, String borderCrop) {

    public static final ImageUris EMPTY = new ImageUris(null, null, null, null, null, null);

    public Optional<String> tier(ImageTier tier) {
        return Optional.ofNullable(switch (tier) {
            case SMALL -> small;
            case NORMAL -> normal;
            case LARGE -> large;
            case PNG -> png;
            case ART_CROP -> artCrop;
            case BORDER_CROP -> borderCrop;
        });
    }

    /** Best available at or below the requested tier, so a card missing a tier still renders. */
    public Optional<String> bestFor(ImageTier tier) {
        return switch (tier) {
            case SMALL -> first(small, normal, large, png);
            case NORMAL -> first(normal, large, png, small);
            case LARGE -> first(large, png, normal, small);
            case PNG -> first(png, large, normal, small);
            case ART_CROP -> first(artCrop, normal, large);
            case BORDER_CROP -> first(borderCrop, normal, large);
        };
    }

    public boolean isEmpty() {
        return small == null && normal == null && large == null && png == null;
    }

    private static Optional<String> first(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
