# Tournaments - design record

Reviewed with the owner on 2026-09-14. The review page is the readable version of this file:
https://claude.ai/code/artifact/c2f6eba4-3bb7-4c33-979e-2d4ba48dd032. This file is the one the
repository keeps; where the two disagree, this one is corrected.

The fence from `docs/design-brief.md` holds: the mod runs the structure (entry, pairings, clocks,
standings) and players play and report. It never decides whether a play is legal. The only
check is the pre-game deck check the brief already allows.

## Owner decisions

| Question | Decision |
|---|---|
| Where matches are played | Registered tables. A long table can be set to *play apart*, each 2-seat table its own game, so a pod drafts and plays where it sits |
| Seating | The mod seats players. At a long table it moves them straight into their seat; in a venue it claims the seat and points the way |
| Results | Both players confirm; the host settles disputes, including in the host's own match. Players who object can leave the match or event |
| Ratings | Private: admins only. Players never see a rating or seed |
| What players see | Current and past event results, and every player's win-loss record and rates |
| Time called | A countdown of the current turn plus 5 extra turns (counted from the turn-passed log, never enforced); then the game in progress is a draw and the match is decided on games won |
| Leaving | 5 minutes' grace, then a match loss; still gone at the next round, dropped. Dropping mid-round concedes the current match |
| Pack entry | As customizable as possible at creation: who brings packs (each player / host sponsor / generated where config allows / cube), which packs count (any / one set / set per round), how many (draft 3, sealed 6), pick 1 or 2, optional pick clock, who keeps the cards (each player / all to sponsor / back to whoever brought the pack) |
| Formats | Draft, sealed, constructed in any preset |
| Constructed deck registration | Host setting. When on, signing up submits a deck, which is checked against the event's format (the pre-game deck check the brief allows); the passing list is registered to that player, and during the event the table refuses any deck that is not the registered list. The host chooses: registration off, checked only, or checked and locked to the list |
| Hosting | Any player may host an event and create a venue |
| Scale | Large events: advance sign-up from anywhere, a registration point in the venue, check-in, numbered tables with floating text labels |
| Top cut | Off by default; top 4 or 8 for events of 9 or more |
| Prizes | Any items, held from creation, handed out by final standing |

## Structure

Swiss: 3 match points for a win, 1 for a draw. Round 1 pairs the top half of the seeds against
the bottom half; later rounds pair within records, never a rematch. A bye is a 2-0 win for the
lowest-ranked player without one. Rounds: 3 for 5-8 players, 4 for 9-16, 5 for 17-32 (host may
change). Tiebreakers: opponents' match-win %, game-win %, opponents' game-win %, with the 33%
floor. Round clock 50 minutes, build clock 30, best of 3 - all host settings. Clocks run on real
time and pause while the server is stopped.

Building after a draft or sealed opening happens in the player's own seat on a board that exists
only on their client (the lesson's machinery), with the deck builder; Ready submits the deck,
which the server checks against that player's pool.

## Abuse rules

- Seed sandbagging backfires: round 1 folds the seeds, and from round 2 only the record matters.
- Ratings move only for finished events with at least 6 distinct players (configurable); a host
  runs one event at a time, with a configurable cooldown.
- The same two players: only the first 3 matches in any 7 days affect ratings. A reported result
  with no game played at the table stays in the standings and does not affect ratings.
- Admin tools: per-event audit log, voiding an event's rating effects, marking events official,
  leaving a player out of ratings.

## Build order

Each phase is usable when it lands and goes through the gate and graphical runs before the next.

1. **T1 - Pack and sealed pods at a table.** Creation screen with every entry option; entries
   held on joining; card-return policies; sealed pools. Proof: every card accounted for through
   cancellation, disconnect, restart and full inventories.
2. **T2 - Tables played apart.** The split setting, its lock during games, every view seeing each
   table as its own. Proof: three simultaneous games on a four-table cluster, both loaders.
3. **T3 - Tournament engine (pure).** States, Swiss pairing, byes, drops, standings,
   tiebreakers, clocks, extra-turn counting, persistence. Proof: exhaustive and randomized tests.
4. **T4 - Running an event in the world.** Hosting, joining, automatic seating, pairing slips,
   result confirmation and disputes, private building with Ready, constructed deck registration
   (format check at sign-up, list registered to the player, other decks refused at the table,
   host-configurable), restarts. Proof: a scripted 8-player draft event in the graphical client.
5. **T5 - Venues and large events.** Numbered tables with floating labels, advance sign-up,
   registration points, check-in, seating at scale. Proof: a scripted 16-player constructed event.
6. **T6 - Records, ratings and admin tools.** World-saved records, private rating, the abuse
   rules, admin commands. Proof: a guard per abuse case, each shown to fail without its rule.
7. **T7 - Top cut and prizes.** Elimination bracket, item prizes by standing, handouts, guide.
