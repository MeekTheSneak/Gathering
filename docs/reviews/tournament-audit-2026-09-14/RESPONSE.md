# Response to the tournament audit of 14 September 2026

The audit reviewed `63bd0410`. Every finding was checked against the source before any change,
and every one was confirmed. The audit's guards are in the repository unchanged
(`core/.../TournamentAuditTest.java`, `neoforge/src/gametest/.../server/events/TournamentAuditGameTest.java`);
its evidence shows them failing on the reviewed commit. Six further guards, for cases the audit
asked to be covered, are in `EventsIntegrityGameTest`; each was shown to fail with its fix
removed.

| ID | Resolution |
|---|---|
| TN-01 | `MatchResult` bounds each count before adding them; `isAMatch` checks without building one. A report or settlement that is not a match is refused with a message, never turned into 0-0. Reports and settlements must also fit the match length (`fits(bestOf)`); the host's buttons now offer the same results as the players'. A saved report that is not a match loads as no report instead of the event refusing to load (core test). |
| TN-02 | Cancelling an event that has ended does nothing. Every destructive operation on an event's tables - clearing, splitting, joining, labels, sign-up hand-back - goes through `tablesStillOurs`, which leaves out any table a newer unfinished event plays at. Adding tables to an ended event is refused. Extra guard: an ended event's label update leaves the newer event's number. |
| TN-03 | A prize is saved before it leaves the hand; a failed save refuses it. Paying out assigns every prize to its owner and saves, then hands each online player theirs only after a save without it succeeds; a failed save keeps it for them until they next join. At most once, never twice. Extra guard: a payout whose save fails hands nothing over, and the winner receives it once saving works. |
| TN-04, TN-05 | No transient callback. The round clock notices a complete round however it became complete, announces it, and pairs the next round after a 300-tick pause - so a withdrawal, a player gone too long, time, and a restart all advance the same way. Players absent from an unfinished match are counted gone from when the clock first notices, so a player who never returns after a restart still runs out their grace. |
| TN-06 | A tournament's pod is named for the event (`event:<id>`), not its table, and the server records each player's pool when it is handed out. Ready needs the event's name and the same cards the server gave that player. Extra guard: the right pool is accepted, and a copy held by another player is not. Pools from before this change, or from pods outside a tournament, keep their coordinate names and are not tournament credentials. |
| TN-07 | Start now, and the build clock, refuse while the event's packs are in a sign-up, being opened, or being drafted. Cancelling during a draft ends it through the draft's own return path. |
| TN-08 | Starting play splits every long table among the event's tables, and refuses with the table's number if one is in use. Ending joins them back, only where no newer event plays. |
| TN-09 | `Standings.of` counts every match in the rounds, including against an opponent no longer in the list asked about. Extra core test covers the dropped player in the second chair. |
| TN-10 | Reports are kept from the first chair and turned to the viewer's chair exactly once. Extra guard reads one report from both chairs. |

Additional areas:

1. **Budgets.** An action that changes nothing (a second check-in, a repeated button) is no longer saved or broadcast. Each player is answered for at most eight event actions a second. Ended events beyond the 200 most recent are moved to an `archive` folder at start (prizes still held are never archived). Not load-tested.
2. **Clocks.** The round and build clocks count real time between server ticks, at most one second per tick, so a slow server still gives a full round and a stall or a stopped server counts nothing (guard). Saved in milliseconds; older saves' tick counts are read as 50 ms each. Disconnect grace is wall-clock. The pick clock and the pause between rounds are still counted in ticks.
3. **Sponsored and returned cards.** A tournament's pack settings must keep pools with the players; sponsor and give-back policies are refused for tournaments (the server cannot promise to take a pool back out of an inventory) and remain available for pods on their own.
4. **Audit trail.** Each event keeps a bounded log (500 lines) of creation, registration, withdrawal, begin, start now, pools handed out, ready, reports, settlements, drops, players gone too long, rounds, prizes and the ending. `/gathering events log <event>` shows the last 20 lines to the host or an admin. No hidden card or seed is written.
5. **Capacity.** A draft or sealed event with more players than its long table seats, or a pod allows, is refused before it begins. An unloaded home table no longer counts as a finished draft for the build clock. Multiple pods per event are not supported.

Not done here: a two-process restart test, socket-level disconnects, load tests, and a real
multiplayer event.
