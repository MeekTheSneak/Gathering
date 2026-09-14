# Security review, 14 September 2026

Asked for by the owner: anywhere somebody could cheat, take what is not theirs, gain access they
should not have, or reach other people's computers - in game and outside it.

Reviewed on top of `6c91b145`. Three parts: the outside world (downloads, files, text, anything a
server or a website can push onto a player's computer) reviewed directly; every client-to-server
packet handler and command, and hidden information plus item and economy integrity, each swept by
an independent read-only reviewer. Every finding below was checked against the source before it
was acted on; the reviewers' reports were leads, not proof.

## What was found and fixed

Each fix has a guard that was shown to fail with the fix removed, unless it says otherwise.

| Severity | Finding | Fix | Guard |
|---|---|---|---|
| Critical | **Loaner decks minted real cards.** Any player could take unlimited free loaner decks and pour them into a collection, take the cards out, or stake them for keeps. | A loaner mark travels with the deck (item, table keeping, sideboarding, network). A loaner cannot be poured into a collection, have cards taken out or put in, or be played for keeps. One loaner at a time, five seconds between loans. Protocol 10. | `LoanerGameTest.aLoanerIsForPlayingNotKeeping`, `AntePotGameTest.aLoanerIsNotPlayedForKeeps` |
| Critical | **A cube draft copied the cube.** The cube stays with its owner and every drafter kept their picks as real cards. | Cube-draft pools are loaners. Event pools, from packs that were used up, stay real. | `DraftPodGameTest.acubeDraftsPoolsAreLoanersNotNewCards` |
| High | **An absent player's deck and stake were left on the table** when a game ended, for whoever ended it to pick up. Any seated player could also void a pot with `/gathering table end`. | Decks and stakes for a player who is not online go to the owed ledger (a new whole-item entry) and are handed over on their next join. The pot goes to whoever staked from the seat, not whoever is in the chair. `table end` is refused for non-operators while a pot is held. | `DeckCustodyGameTest.anAbsentOwnersDeckIsNotHandedToTheChair` (rewritten: it asserted the old behavior), `AntePotGameTest.aPotForSomebodyAwayIsKeptForThemNotLeftOnTheTable` |
| High | **A replay showed an opponent's whole deck mid-match.** Game one of a best of three was on the shelf, library order and all, before game two. Tournament decks are reused every round. | A set's games are held and written when the set ends. A game played by anybody in an unfinished tournament is shown only to operators until that event ends. | `MatchGameTest.aSetsGamesWaitForTheSetToEndBeforeTheyCanBeWatched` |
| High | **A game's log had no cap**, and undo refolds the whole log. A client could lag the server and grow a table's save past what it will read back, leaving the table unusable. | A game takes at most 50,000 records (half the reader's limit); ending it is always allowed. Moves are budgeted per player (60/s, burst 1,000 - the first setting of 30 and 256 dropped moves from the scripted client's piles), undos 2/s. | `GameSessionTest` record cap (core) |
| High | **The shared card-lookup worker could be flooded** by token lookups with random ids, set lists, basics and dungeons, stalling card data for everybody and risking a Scryfall ban. | Per-player budgets on every lookup-heavy request; whole-set lookups at most one per ten seconds; the worker's queue is bounded and a full queue fails the request at once. | Reviewed, not load-tested |
| Medium | **A second deck on a seat destroyed the first** once its library had run out. | A seat the table holds a deck for takes no other. | `AntePotGameTest.asecondDeckDoesNotReplaceTheOneTheTableHolds` |
| Medium | **Somebody sitting in a vacated chair mid-game took over that seat** - its hand, library, face-down cards and winnings. | A player cannot take a seat whose board is someone else's; the owner can return to it. | `SeatGameTest.astrangerInAnEmptyChairDoesNotTakeOverItsBoard` |
| Medium | **The deck builder handed out free foil basics of any printing, and empty decks, without limit.** | Free basics are non-foil (builder and import-from-collection). An empty build is refused. One build a second. | Reviewed |
| Medium | **Deck-list parsing could be made to backtrack for seconds per pattern**, and a long dotted line overflowed the stack, on the shared card worker. | Lines past 256 characters are reported, not parsed. | `DecklistParserTest.HostileLines` (6 cases) |
| Medium | **Starter-booster asks read a file from disk on the server thread every time.** | Budgeted per player. Still once per player, as before. | Reviewed |
| Low | **Card art and set symbols followed redirects anywhere.** Only the first address was checked against the Scryfall allowlist. | Redirects are followed by hand, each hop checked before it is requested. Set symbols read with a size bound. | Reviewed; not testable offline |
| Low | **Formatting codes in player text** (event names, built deck names and descriptions, import descriptions) could recolor, hide or impersonate text other players see. | Cleaned server-side like table talk already was. | Reviewed |
| Low | **Creative mode wiped decks.** A deck crosses to clients with its cards hidden, and the creative inventory sends that copy back, which the server stored. Data loss rather than a hole - creative players can make any item - but real. | A server hook on the creative slot packet (both loaders) restores the real deck of the same handle. | `CreativeDeckGameTest`, `FabricCreativeDeckGameTest` |
| Low | **Anybody within reach could split or join a long table.** | Needs a seat at it, or an operator. | `TablesApartGameTest.onlySomebodySeatedChangesHowTablesArePlayed` |
| Low | Ante answers were not reach-checked; the replay list, dice, reveals and random discards had no budget; server downloads had no size bound. | Reach check; budgets; 128 MB bound. | Reviewed |

## Checked and sound

- **No code execution paths.** No reflection-based loading, process execution, script engines,
  class loading or Java object deserialization anywhere in the mod.
- **No injection into chat.** Nothing parses components from text, and there are no click or hover
  events, so no command or link can be smuggled into a message.
- **No way to reach a player's computer beyond Scryfall.** The client fetches only HTTPS Scryfall
  addresses, checked on every hop, with size bounds. The SVG parser refuses doctypes and external
  entities. The client never sends commands or chat on a server's behalf, and writes files only
  under its own cache directories, named by hash or checked set code.
- **No listening sockets and no credentials.** The mod opens no ports and holds no API keys.
- **Server downloads** go only to Scryfall, MTGJSON and Archidekt over HTTPS, with URLs built from
  validated ids and URL-encoded queries, a rate limiter, no automatic redirects and now a size
  bound. Deck links are an allowlist, not a pattern that could name another host.
- **No path traversal.** Every file name comes from a UUID, a hash, a checked set code or a
  timestamp; replays are matched by id against real file names.
- **Visibility and randomness.** Views are per recipient; moves must be signed by the sender's own
  seat; hidden cards cannot be touched by others; shuffles and pack contents use `SecureRandom`
  (stronger than the `level.getRandom()` rule, and worth recording as the exception it is).
- **Admin commands** need operator level 2. Identity always comes from the connection.
- **Trades, pack openings, pod sign-ups and event prizes** already take before they give and record
  before they move.

## Not fixed, and why

- **By design, flagged for the owner:** any seated player can draw, exile, mill or shuffle another
  seat's library or hand. Nothing hidden is revealed and the log names who did it - this is the
  "no rules enforcement" rule - but it is a one-click way to wreck an opponent's game. Restricting
  it would be the first rule the mod enforces about play.
- **A collection box** can be moved by explosions or machines, keeping its owner's rights, and a
  dropped box's contents are sent to nearby clients in its item data. Low.
- **Offline-mode servers** give each new username a starter grant; that is what offline mode means.
- **Not load-tested:** the budgets and queue bound are reviewed, not measured under attack.
