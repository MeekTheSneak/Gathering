package dev.gathering.server;

/**
 * Everything a server held in memory, dropped in one place when it stops.
 * <p>Written once rather than once per loader, for the reason {@link
 * dev.gathering.client.ClientState} is: a teardown list kept in two files is a list where a
 * new holder gets added to one of them, and in single-player that means state from the world
 * somebody just left turning up in the world they just opened.
 * <p>What stays with the loaders is what the loaders own: the card pipeline and the collation
 * service are constructed there and closed there.
 * <p>Server thread only.
 */
public final class ServerState {

    private ServerState() {
    }

    /** Drops everything the server that is stopping was holding. */
    public static void forgetTheWorld() {
        dev.gathering.service.ServerSettings.clear();
        Antes.clear();
        Archive.clear();
        CardShop.clear();
        CurrentSet.clear();
        LoanerDecks.clear();
        ReplayWatch.clear();
        SealedLoot.clear();
        TradeSessions.clear();
    }
}
