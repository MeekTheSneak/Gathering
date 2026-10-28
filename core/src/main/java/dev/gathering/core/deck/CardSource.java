package dev.gathering.core.deck;

import dev.gathering.core.scryfall.CardQuery;
import dev.gathering.core.scryfall.CollectionResult;
import java.io.IOException;
import java.util.List;

/**
 * Where the importer gets cards from, without caring whether that is a cache, the network,
 * or a test fixture.
 */
@FunctionalInterface
public interface CardSource {

    CollectionResult resolve(List<CardQuery> queries) throws IOException;
}
