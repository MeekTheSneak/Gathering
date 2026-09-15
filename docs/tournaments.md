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
floor. Round clock 50 minutes, build clock 25 after a draft and 30 for sealed (MTR Appendix B), best of 3 - all host settings. Clocks run on real
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

## As built

All seven phases are implemented. What each part does, where the build differs from the plan
above, and what is still limited. `docs/working-record.md` carries the evidence.

**Playing.** `/gathering events` (or *Tournaments* on a table's setup screen) lists current and
past events. *Host one* opens the create screen: event kind, format, best of, round and build
minutes, rounds (auto or fixed), top cut, deck registration (any / checked / locked), check-in,
and for draft or sealed the full pack settings. The event screen has four tabs: overview (your
table, opponent, result buttons, practice), standings (records and rates, never a seed),
pairings, and host.

**Seating.** A round claims each pair's seats at their numbered table and starts their match with
the event's format and length. A player already sitting at that long table - a pod that drafted
there - is moved straight into the seat; anybody else is told the table's number and shown the
way by a pointer across the top of the screen until they arrive. Gathering everybody for a draft
or sealed event also brings in players standing nearby. Every event table shows its number, the
pairing and the round clock floating over it.

**Who plays first** (MTR 2.2). The first game of a Swiss match goes to a player chosen at random;
the first game of a cut match to the player who finished the Swiss rounds higher; every later game
to the loser of the one before, or after a drawn game to whoever went first in it. A drawn game
counts as one of the match's games, the last one too. The mod picks *play* for them - the usual
choice - and *Choose to draw* on the felt's menu, on the first turn, hands the first turn to the
other player instead.

**Reporting.** The result buttons cover every way a match ends: won and lost at each count, won at
time a game up with no game in progress (1-0), drawn at games apiece or with a game unfinished
(1-1, 1-1-1, 0-0-1), and 0-0 for a draw agreed before playing. A cut match cannot be drawn, so
its buttons offer no draws. A cut match still tied on games when the extra turns end goes to the
higher life total (MTR 2.4); tied on life as well, the host decides it.

**Registration point.** The host can press *Register here* on the host tab; from then on signing
up only works within 8 blocks of that spot, and anybody further away is pointed to it.

**Scorekeeper's Desk.** A block to run a tournament from (paper, book, paper over three planks and
two legs of planks). The host uses it while hosting and it becomes that tournament's desk and its
registration point, in one click. Anybody else using it is shown the tournament it runs - to sign
up, check in, see pairings and standings. A desk already running an unfinished tournament is taken
over only by sneaking, so no stray click moves somebody else's event; a finished or cancelled
tournament's desk is free. Breaking the desk takes signing up off that spot. With Create, a Display
Link against the desk reads the tournament onto a board, set in the link to show the standings,
this round's pairings, the round and clock, the final places, the prizes, or who has signed up.
Its look borrows vanilla's lectern until it has one of its own.

**Pick clock.** Off, 45 or 90 seconds a pick (any value up to 300 is accepted by the server), or
tournament timing - the Magic Tournament Rules' booster draft table (Appendix B), 40 seconds for a
fresh pack down to 5 for the last cards - for drafts only. When it runs out, everybody still to pick takes the first cards in their pack -
never a choice made for them on merit, and never a random one. The draft screen counts down, red
for the last ten seconds. The turn's start is not saved: a restart gives everybody a full clock.

**Practice.** *Practice* on the overview deals the deck in the player's hand - its sideboard when
the main deck is empty, as a fresh pool is - onto a board that exists only on their client, like the lesson's, for laying out and drawing hands while building.
Nothing done there reaches the server.

**Integrity** (after the audit in `docs/reviews/tournament-audit-2026-09-14/`). Results must be a
match of the event's length. A tournament's draft or sealed pools are named for the event and
recorded against the player they were handed to, and only that pool makes that player ready; a
tournament's players keep their pools, so sponsor and give-back card policies are for pods on
their own. The next round is paired by the round clock whenever a round stands complete, however
it got there, including after a restart. Prizes are saved before they move. An ended event never
touches tables a newer event plays at. Round and build clocks count real time (at most a second
per tick). A draft or sealed event that cannot seat everybody is refused before it begins. Each
event keeps a log: `/gathering events log <event>` for its host or an admin.

**Ratings and hosting rules.** A result counts toward ratings only when a game was played at the
match's table; a result two players only typed in stays in the standings and the record. Events
of fewer than `events.rated_min_players` (default 6) move no rating. An event an admin marks
official counts at full weight, anybody else's at half. A host runs one event at a time, and
`events.host_cooldown_minutes` (default 0) adds a wait between events. Admin commands:
`/gathering events record|rating|void|exclude|official`.

**Limits known.**
- A sign-up locked by a pack opening that never completes stays locked until restart.
- Prize descriptions use the server's item names, so they are not translated per player.
- The standings tab is plain text columns; long names can crowd the rates.
- A registered opponent who has never been online is dropped at the next round, as anybody gone
  at the next round is - so a two-player event with an absent opponent ends after round one.
