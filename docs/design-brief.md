# Gathering
## Design Brief v1.14

Working name, chosen. The name must not contain "Magic: The Gathering," "MTG," or imply official endorsement, per the WotC Fan Content Policy; "Gathering" gestures at the game without claiming the trademark, and the title screen carries the unofficial Fan Content disclaimer.

**One-line pitch:** A Minecraft mod for 1.21.1 (NeoForge and Fabric) that turns a multiblock table into a full tabletop card game surface, TTS-style manual mechanics with real hidden information, plus an optional server-configurable collection economy in the spirit of Pixelmon servers.

---

## 1. Vision and pillars

The mod serves two fantasies that share one card system:

**The play fantasy.** You and three friends sit down at a table inside your shared world and play Commander with your real decklists. The mod is the table, the cards, and the hands. The rules live in your heads, exactly like paper Magic or Tabletop Simulator. Spectators standing nearby can watch the board.

**The server fantasy.** On a configured server, cards are also things you find, open, buy, and trade. Booster packs drop from play, a card shop sits in spawn town, collections live in deckboxes, and the community runs gym nights where challengers face player gym leaders at arena tables.

Design pillars, in priority order:

1. **The table is contained.** A full four-player Commander board state fits on one table multiblock. Never one card per block.
2. **Hidden information is real.** Hands and face-down cards are cryptographically honest: card identity for hidden zones never reaches clients that are not entitled to it. A modified client learns nothing.
3. **Manual mechanics, zero in-game rules enforcement.** During play, the mod moves cards, tracks numbers, and shows things. It never says no. The one permitted referee is pre-game: an optional deck legality check when starting a formatted game, which is static validation like a tournament deck check and never touches play. This fence is permanent, not a phase-one limitation.
4. **Spectacle matters.** The public board state renders in-world so bystanders can watch a game happen. This is what makes gym nights server culture instead of a private GUI.
5. **Modes are config, not forks.** Import mode, collection mode, or both, chosen per server in one config file.

## 2. What this mod is not

Explicit non-goals. These are fences against scope creep, written down so future sessions do not relitigate them.

- **Not a rules engine.** No automatic triggers, no stack enforcement, no in-game legality checking, no win detection. If the group misplays, the group misplays. Deck legality validation before a formatted game is the sole exception, and it ends the moment the game starts.
- **Not an AI opponent.** Gym leaders are humans. See section 11 for why.
- **Not a real-money anything.** The in-game economy trades in Minecraft items only. No pricing hooks to real currency, ever. This keeps us clean under the Fan Content Policy and basic decency.
- **Not a card-art redistribution vehicle.** The mod ships zero card images. Every client fetches art from Scryfall itself and caches locally. The jar contains only our own UI art.
- **Not a physics sandbox.** Cards snap to zones and grid positions. TTS-style free physics is fun in TTS and misery in Minecraft's interaction model.

## 3. Server modes

One server config (TOML) with two master switches and their sub-settings:

```
[modes]
import_enabled = true        # decklist import produces decks from nothing
collection_enabled = false   # boosters, store, loot, trading

[import]
allow_all_players = true     # or restrict to a whitelist
formats = ["commander"]      # informational only, no enforcement

[collection]
pack_loot_sources = ["fishing", "structures", "trading"]
sealed_store_enabled = true          # NPC commerce sells sealed products only, never singles
sealed_price_item = "minecraft:diamond"  # flat admin-set prices per product; no IRL pricing anywhere
current_set = "auto"                 # admin pointer for pinned boosters; "auto" tracks latest release
stall_rotation_hours = 4             # server-global rotating pool; pinned tier never rotates
stall_rotating_slots = 6             # products visible per rotation; weights from IRL scarcity signals, admin-overridable
booster_model = "play"               # play boosters, set-configurable

[table]
max_tables_loaded = 16               # counted as clusters
max_cluster_tables = 4               # 2x2 tables, 2 facing seats each; capacity 2/4/6/8
max_cards_per_session = 1600         # 8 players, 100-card decks, tokens headroom

[ante]
enabled = false                      # requires collection_enabled; warns and stays off otherwise
cards_per_player = 1
exclusions = ["basic lands"]         # card categories that reroll if drawn as ante
allow_per_table_opt_out = true       # table owner can host ante-free games on an ante server
```

Mode interactions: with both enabled, servers can gate import behind progression (a permission node granted by rank, quest, or purchase) so collection remains meaningful. With import only, the mod is a pure play client. With collection only, it is a collecting game that still uses the table for play; players just have to own the cards they sleeve.

## 4. Card identity and the data pipeline

**Canonical identity is the Scryfall ID** (UUID of a specific printing). Everything else, including name, oracle text, image URIs, mana cost, and prices, is derived data fetched and cached from Scryfall.

- A card item carries one data component: `{ scryfall_id, foil, custom_id? }`. Nothing else. On 1.21.1 this is a registered `DataComponentType` with a codec, not raw NBT.
- The server maintains a card metadata cache (JSON on disk) populated on demand through Scryfall's API, plus per-set MTGJSON files fetched and cached the same way for booster collation (the all-sets file is enormous; per-set on demand only), using the collection endpoint for batch resolution, off the main thread, always. Rate limiting per Scryfall's guidelines (steady-state, small delay between requests, identify with a proper User-Agent).
- Clients fetch images independently from the `image_uris` the server relays. Card identity travels the network as a UUID plus display metadata; image bytes never travel our network at all. Each client builds its own disk cache. This is the same architecture that makes TTS work and it eliminates the entire image-sync problem class.
- **Custom cards** (collection servers will want them; the existing MTGCard mod proved demand): Cockatrice XML import for metadata plus server-hosted art upload with a size cap. Custom cards get a `custom_id` namespace so they can never collide with Scryfall IDs.

**Decklist import** accepts the common text formats (Moxfield/Archidekt export, MTGO style, plain "1 Card Name (SET) 123"). Parser is pure Java with exhaustive unit tests, because format edge cases are endless and this is the front door of the whole mod. Ambiguous lines resolve to the cheapest matching printing by default with a chooser in the import screen. Import produces a sleeved deck item bound to the importing player.

## 5. The Table

The centerpiece. One table is a 2x2 multiblock at table height, seating **2 players on facing sides**. Tables are a cosmetic family with identical function: a **basic table craftable in every wood type** (one model, per-wood palettes via data generation, the vanilla planks pattern), a **dyeable felt top** (right-click with dye, stored on the block entity and rendered as a tint, no blockstate explosion), and a few **themed tables** (Arcane with runes, Tournament Hall in clean black at launch, with more arriving as phase 3 cosmetic content). Every table shows a subtle glow accent while a session is live, so an active game reads as an event from across the room. All Gathering tables cluster-merge regardless of wood or theme, each keeping its own look, because mismatched tables pushed together is the most LGS-authentic image in the mod. Tables placed edge-adjacent auto-merge into a **cluster** sharing one surface and one session, and capacity scales linearly at 2 per table: 2, 4, 6, 8 seats for one through four tables. This lands exactly on the draft breakpoints (two tables is the 4-player pick-2 pod, four tables the 8-player draft), gives a 4-player Commander game a full 2x4 surface, and makes expansion the literal LGS gesture of pushing tables together when more people show up. Cluster shape cannot change during a live session: placing, removing, or breaking a table in an active cluster is blocked, and session persistence covers the whole cluster. Merged seat placement distributes around the cluster perimeter, pairs facing where the shape allows; exact placement is cosmetic, since seats are registration points and play happens in the GUI.

- **Seats and roaming:** two facing seats per table, capacity per the cluster rules above. Right-clicking a seat edge registers you in the session; it is a registration, not a chair lock. A seated player toggles between two states with one key: **play view** (the full battlefield GUI, mouse captured, all card manipulation) and **roam mode** (walk the world freely with a slim HUD showing your hand strip, life totals, and the turn indicator, while the in-world table shows the live board). Commander is a game of downtime, and being able to tend a furnace or heckle from behind a friend's shoulder mid-game is precisely what a Minecraft-native table offers that Cockatrice cannot. Leaving the area or logging out does not drop your seat; the session holds it until you leave the session or it ends.
- **Starting a game, two modes.** **Free play:** sit down, put cards down, no questions asked; this is also the sandbox/goldfish path. **Formatted game:** the starter picks a format preset, every player commits a deck, and the validator checks each deck before the session begins. Config chooses whether a failed check blocks the game or warns with a table-owner override.
- **Format presets.** A preset is data, not code: minimum deck size, copy limit (4-of or singleton), starting life, Scryfall legalities key, commander rules on or off, sideboard size. Shipping presets: Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Commander, Oathbreaker; adding a format later is a table entry. The validator, built almost entirely in the pure core from cached Scryfall data, checks per preset: deck size, copy limits with the singleton basic-land and oracle-text exceptions, format legality and banlist via per-card legalities (a legality of restricted validates at maximum one copy, which handles Vintage directly), commander eligibility where applicable (legendary creature type line or "can be your commander" text, Partner pairing), and color identity containment for commander formats.
- **Sideboards**, because 60-card formats are a demo without best-of-three: decklist import parses sideboard sections, presets validate sideboard size, and formatted matches at a table support best-of-three with a between-games sideboard swap screen. All of it is pre-game or between games; the in-game enforcement fence is untouched.
- **Session:** the table block entity owns one `GameSession`, the authoritative game state object. Sessions survive chunk unload and server restart (serialized with the block entity). A session ends by unanimous vote or a table owner action.
- **Containment:** every card in the session renders on or above the table surface only. The table top is one continuous surface; each seat gets a playmat region as a visual guide (tint, border) and a default drop target, but regions are not boundaries. Any battlefield card can sit anywhere on the table, because control-changing effects are core Magic (Tevesh Szat's own ultimate steals every commander at the table). The table never grows in world footprint regardless of board state.
- **Sandbox mode:** any player alone at a table can start a solo session (goldfish mode). All the same verbs, no other humans required. This exists so feel-testing can happen on a weeknight, and it ships in the first playable phase.

## 6. Zones and visibility

Commander-first zone set per seat: library, hand, battlefield, graveyard, exile, command zone. Plus one shared stack-free "reveal" area for showing cards to the table.

Visibility rules, enforced server-side at the packet level:

| Zone | Owner sees | Opponents see | Spectators see |
|---|---|---|---|
| Library | count only | count only | count only |
| Hand | full | count only | count only |
| Battlefield (face up) | full | full | full |
| Battlefield (face down) | full | anonymous marker | anonymous marker |
| Graveyard | full | full | full |
| Exile (face up) | full | full | full |
| Exile (face down) | full | anonymous marker | anonymous marker |
| Command zone | full | full | full |

- Face-down cards sync as an opaque per-session marker ID (random, stable within the session) so opponents can track "that face-down card moved" without any path to identity. Marker IDs are generated fresh per session, never derived from the Scryfall ID.
- Library search and scry-style looks open an owner-only view fed by owner-only payloads. A "reveal" action promotes specific cards to public sync.
**Ownership vs control.** Every card in a session carries an immutable owner (the player whose deck or collection it entered from) and a current position, which can be anywhere in any public zone. Control changes are just movement: drag the stolen creature to your side of the table. In the paper-Magic tradition, any seated player may manipulate any public-zone card; the mod never says no, and the event log attributes every action by name, which is the honesty mechanism. Hidden zones (hand, library, face-down looks) remain strictly owner-locked. At session end, every card returns to its owner's deck automatically regardless of where it ended up, with exactly one exception: ante (section 9).

**The hidden information lifecycle.** Three stages, one principle: unauthorized parties never hold secrets, sanitized or otherwise. **Live:** the server sends hidden card identity only to entitled clients per the visibility table; every other client, including spectators, receives action records without content ("scried 2, kept 1 on top"), so there is nothing in their logs, memory, or network traffic to extract, and a modified client sees what an honest one sees. **At rest:** session persistence writes hidden-information events and the shuffle seed to a separate encrypted stream, key sealed server-side and never stored beside the data, so a save file inspected mid-session with external tools yields ciphertext. The seed is treated as the most sensitive secret in the system, since seed plus decklist equals every future draw; no live code path exposes it in logs, debug output, or crash reports. **At session end:** the server decrypts and merges the streams into the replay artifact, which is the single moment scries, searches, and hands become record. Stated honestly: a malicious server host controls the process and could extract state from memory with effort; the host is trusted infrastructure here as in every online card game, and what this design eliminates is the entire attack surface below that bar.

- The invariant, stated once and tested forever: **no payload containing hidden card identity is ever addressed to a client that the visibility table does not entitle.** This is the one security property of the mod and it gets its own test suite.

## 7. Interaction verbs

The core verb set for v1, deliberately trimmed. Anything not here waits.

Card verbs: draw, play to battlefield, move (drag anywhere in public zones, any seat's region), tap/untap (single and untap-all), flip face up/down, send to graveyard/exile/hand/library (top, bottom, or shuffled in).

Pile verbs: shuffle (server-side, seeded, announced in log), search, scry N / surveil N (owner-only look with reorder and bin to top/bottom/graveyard), draw opening hand, mulligan.

Counter and token verbs, kept in core because the reference deck demands them: loyalty counters (Tevesh Szat is a planeswalker and cannot be played without them), +1/+1 and arbitrary named counters, create token via Scryfall token search (his +2 makes two Thrulls per activation), create copy-token of an existing card.

Player verbs: life total (40 in the Commander preset), commander damage grid, concede, end session. Command zone with commander tax tracking as a displayed number, not an enforcement.

Table verbs: **turn and phase marker**, a shared indicator of whose turn and which phase, advanced manually by the active player, displayed in every view including the roam HUD (zero enforcement, maximum shared clarity). **Ping**, click any public card to highlight it for everyone for a few seconds, the digital version of pointing at the table, essential for "in response to that." **Undo** (phase 2), configurable per table with a server default, three modes: **unanimous** (any rewind requires every seated player's consent, the competitive setting), **free** (a player rewinds their own most recent actions instantly, the casual setting), and **off**. In every mode, information boundaries are hard: the event log marks information-revealing events (draws, scries, reveals, searches), and no free undo can cross one, because a seen card cannot be un-seen; rewinding across an information boundary always escalates to unanimous consent. Shipped default is free-with-boundaries. Because the session is event-sourced, undo is a re-fold of the log, not a parallel bookkeeping system.

**Deferred past v1:** dice and coin flips, monarch and initiative markers, the shared reveal area, clone markers distinct from copy-tokens, per-player notes, dedicated mill button (library-top to graveyard via move covers it interim), sideways/upside-down state markers beyond tap.

A session event log (server-authoritative, visible to all seats and spectators) records every verb: "Chris drew a card. Chris tapped Halana and Tevesh Szat." This is the honesty layer that replaces physical presence, and it makes remote disputes resolvable.

## 8. Rendering and readability

Three renderers, one state:

**The in-world table view** renders the public state as miniature card quads laid out on the table top. Battlefield cards at roughly 2.5 by 3.5 inches scaled to about 0.09 blocks wide, arranged in each seat's playmat region. Tapped cards rotate 90 degrees. Piles render as thin stacks with a count. This view exists for spectators and for ambient legibility ("their board is huge, come look"). It uses the small Scryfall image tier and renders nothing hidden.

**The zoom overlay** is the universal reading tool. Look at any card (in world, in a GUI, in your hand) and hold a key to see the full-resolution card image as a screen overlay, with the printed face plus oracle text sidebar for errata accuracy. This must be instant, so normal-tier images preload for every card that enters public state or the owner's hand.

**The seated player view** is where you actually play: a battlefield GUI showing the table from your seat's perspective, rendered with a slight perspective tilt by default for tabletop feel, with a per-player option to switch to a flat top-down layout for maximum clarity. Your hand as a fan at the bottom, your zones in reach, opponents' public zones across the table, drag-and-drop for every card verb, radial or right-click menu for the rest. One key toggles between this and roam mode; a second, smaller **roam HUD** (hand strip, life totals, turn and phase indicator, ping alerts) keeps you in the game while you walk around. All manipulation happens in the play view; the in-world view and roam HUD are read-only.

**The spectator view:** any non-seated player near a table can open a read-only GUI of the full public state, and arena tables broadcast a joinable spectate camera. Spectator clients receive exactly the public payload set defined by the visibility table, nothing more, so a spectating client is incapable of leaking a hand even if modified.

**Texture budget**, informed by the TTS Azorius project: two image tiers per card, Scryfall small (146x204) for table miniatures and normal (488x680) for the overlay and GUI. A 4-player Commander game touches perhaps 450 distinct cards; at these tiers that is well under 200 MB of VRAM worst case, managed with an LRU cap (config, default 256 normal-tier textures resident) and disk cache for everything ever fetched. Never fetch or register textures on the render thread.

## 9. Collection mode (phase 3)

Config-gated. Ships after play mode is proven, because play mode is the differentiator and collection mode is well-trodden ground (the existing MTGCard mod demonstrates every mechanic here works; we are re-executing with better bones, not inventing).

- **Boosters:** set-selectable packs with true per-set collation via MTGJSON booster data: one generic interpreter (weighted booster variants drawing slots from weighted card sheets, sheets referencing exact printings, bridged to canonical identity through the Scryfall IDs MTGJSON carries) covers every set with published data, past and future, with zero per-set code. Sets without collation data fall back to the configurable rarity-slot odds. No booster structure is ever hardcoded from memory; the interpreter consumes data only. Opening is a small ceremony GUI with rarity reveal order, because the ceremony is the entire point of boosters.
- **Acquisition:** loot tables (fishing, structure chests, archaeology), a villager-style trade or two, and admin grant commands.
- **Sealed stall block:** NPC commerce sells sealed products only, ever, at flat admin-configured item prices, and is config-removable entirely. The line is the one paper Magic proved: the manufacturer sells sealed, singles come from people. Sealed carries no scarcity distortion because sealed is the faucet with published odds; an infinite-stock NPC selling singles is the classic player-economy killer, and IRL prices encode the supply history of a different universe than the server's. No singles are ever NPC-sold, no IRL price appears anywhere in the economy or its UI; what a card is worth on a server is discovered at its tables. **Rotating stock:** the stall's inventory is a **pinned tier** that never rotates, governed by one principle: scarcity is for chase products, never for infrastructure, so anything other systems structurally depend on (draft entry, sealed league, onboarding) is always in stock. The pinned tier is: current-set boosters, where "current set" is an admin pointer defaulting to the latest release (overridable, which quietly enables era servers living in a chosen block of Magic history), an admin-editable list of basic Commander precons with a sensible default spread, and the beginner starter deck products plus a **rotating pool** refreshed on a server-global clock (config interval, default a few hours; global by default so a restock is a shared event rather than something defeated by stall-hopping, with an optional restock broadcast). Each rotating product carries an appearance weight, defaulting to a formula derived from cached IRL signals (price percentile and scarcity tier of the product and its contents) and overridable per product. This is the one sanctioned use of IRL market data, under an explicit guardrail: **IRL data may shape availability, never cost, and never appears in any UI.** Fixed-price ever-present products would cap their singles near the product price; scarce availability between restocks lets the player market breathe above that line while keeping on-server scarcity loosely rhyming with the intuitions players carry from paper. The coverage auditor counts rotating products as valid faucet paths and reports expected availability, so an admin can see when a weight makes something effectively mythological.
- **Products, the generalized store inventory:** everything sellable is a data-defined product in one of three shapes. **Fixed bundles** (exact printings, no randomness): Secret Lair drops and Commander precons, the latter doubling as the onboarding product that has a new player piloting a real deck in their first hour. **Containers** (products holding products): display boxes as thirty packs plus their buy-a-box promo. **Rewards** (printings you are given, not sold): prerelease, judge, and festival promos delivered through advancement payouts and arena win configuration, the judge-promo fantasy applied to actual gym leaders. Product contents come from MTGJSON sealed-product and precon data under the same no-hardcoding rule as collation, with admin-defined product JSON as the fallback and as a feature in itself, since it lets a server curate its own seasonal drops from real printings. The store gains a rotating limited-time shelf with admin-scheduled availability, because the cadence is half of what makes a drop a drop. All of it points at real Scryfall printings; the mod never invents cards.
- **Complete obtainability, guaranteed rather than intended.** A server-config **catalog policy** defines the obtainable universe, computed from Scryfall data by filter rather than by list: every real paper printing by default, digital-only cards excluded (no paper existence, illegal in every paper format), memorabilia-class oddities excluded, with toggles for edge classes like serialized variants and oversized cards. Within that universe, completeness is **faucet coverage**: the union of pack sheets, product bundles, reward configs, and loot pools must cover the catalog, verified by a **coverage auditor**, a pure-core tool taking a server's actual config and computing which universe cards no faucet reaches, shipped as an admin command and run permanently in our test suite. The closing move: the auditor's output automatically becomes the sheet of the **Archive Pack**, a loot-only item (treasure, rare fishing, boss drops) drawing exclusively from the uncovered remainder, so old promos and long-tail oddities are reachable through play, and the archive shrinks by computation as servers add products. Completeness by construction, no vending machine: every acquisition path is gameplay or another player. Servers wanting hard scarcity can config-exclude cards from the universe itself, an explicit visible choice.
- **The collection block,** the home of a collection and the place decks get built. Storage with search and sort by set, colour, rarity, type and name, plus a deck-building screen that assembles a deck item usable at any table. Four decisions shape it:

  **It is one shared inventory, not an ender chest.** Every viewer opens the same contents. A per-player view would make the block a personal convenience; one shared inventory makes it a piece of social infrastructure, which is the point: a playgroup pools a collection, a gym leader keeps their decks in the clubhouse, a server runs a lending library.

  **Looking is public, touching is permissioned.** Anyone may open a collection block and read every card in it — a collection is a thing you show off, and being able to browse the playgroup's pool without asking is most of the value. Taking and adding are separate rights the owner grants per player, defaulting to owner-only so sharing is a deliberate act rather than a griefing surface. Separating the two rights buys the shapes that matter: a donation box (anyone adds, owner takes), a lending library (trusted players take, owner stocks), a display case (nobody but the owner touches anything). Breaking the block requires take permission, and it drops as an item holding its contents rather than as ten thousand card entities.

  **Contents are a tally, not a pile of item stacks.** A real collection runs to tens of thousands of cards, which no slot-based container survives. The block stores counts per printing-and-finish, which keeps it compact, makes search instant, and makes "add forty Forests" one entry. The exception is cards that have a biography — won in an ante pot, pulled from a particular pack (section 12) — which are stored individually beside the tally, because a card that remembers being taken from someone is not fungible with another copy of itself.

  **Sleeving moves cards, it does not reference them.** Building a deck takes the cards out of the collection and puts them in the deck item; dissolving the deck puts them back where they came from. That is paper-true, it is the one place collection mode adds a check import mode does not have (own the card to sleeve it), and it means a shared collection cannot quietly back two decks at once. A deck remembers which collection it was assembled from, which is what makes playgroup loaner decks work without any separate lending mechanic.

  In import mode the block still earns its place as a rack for deck items, with the card storage simply empty.
- **Trading:** a two-sided trade window with lock-in confirmation. No escrowless hand trades.
- **Loaner decks:** admin-defined decklists attached to any table, playable by anyone with no import permission or collection required. A new player's first game happens in their first minute on the server, the starter-deck moment of the whole Pixelmon fantasy.
- **Sleeves are how a card back works.** Every card renders as if sleeved: the printed face shows through the front, and the reverse is always the sleeve, opaque. That is what makes a face-down card unreadable from every angle rather than only from the front, so it is a requirement of the hidden-information pillar and not a cosmetic choice; the mod also ships no Wizards card back, and never will. Turning a double-faced card over shows its other printed side, which is a reading action rather than a hiding one - when the table arrives and face-down becomes a zone-level fact with a marker attached, the two want separating.
- **Cosmetics:** per-player playmat art and card-back sleeves (custom upload with the same size caps and moderation path as custom card art), plus additional themed tables as craftable or earnable showpieces. The luxury tier of the collection economy.
- Import mode and collection mode compose: a server can let players import basic lands and tokens freely while everything else must be owned, one config list.

**Ante.** Server-configurable, off by default, and hard-gated to collection mode: ante only means something when cards are property, so on an import-only server the config warns and stays inert. When a session starts at an ante-enabled table, every player sees an explicit consent screen naming the stakes; the session does not begin until all seats accept, and a seat can walk away instead. After decks shuffle, the configured number of cards moves at random from each library (top of a shuffled deck is random) to a public, face-up ante zone rendered at the table center, where they sit visibly for the whole game like a pot. Commanders are naturally exempt since they start in the command zone, and the exclusion list rerolls categories the server protects. Payout triggers when the session resolves to a single non-conceded player or a unanimous winner vote: ownership of every ante card transfers atomically to the winner's collection, logged permanently in the session log. If a session is voided by vote or dies to a crash, escrowed ante cards return to their owners on next load; the escrow is part of session persistence, so a server restart can never eat the pot. The drama of a visible pot with a real rare in it is the entire feature; that is what makes it server culture.

## 10. Draft

A pod system layered on the same card pipeline and visibility framework. Drafting is pure GUI with no table geometry: pods of 4 to 8 form at any table or arena block, players draft in the interface, then the pod splits into games at whatever tables they choose.

- **Entry, two paths.** Either every pod member stakes their own packs (3 each, consumed from inventory at pod start), or one player sponsors the pod from their inventory (pod size times 3). Sponsored pods choose at creation whether drafters keep their pools afterward or the cards return to the sponsor.
- **Variants by pod size:** pick-2 draft at 4 to 5 players, normal single-pick draft at 6 to 8. Passing direction alternates per pack. Picks are private, enforced by the same payload-level visibility rules as hands; pack passing is server-authoritative.
- **Modes.** In collection mode, packs are real and you keep what you draft (paper-true; this makes draft night simultaneously a game and a pack-opening ceremony, which is why it is the premier event format). In import mode, draft is a config option: packs are virtual, generated per chosen set by the same collation engine as real boosters, and pools evaporate when the pod ends since there is no collection.
- **Cube draft:** import any decklist as the draft pool instead of generating packs. No new infrastructure, no collection dependency, and it works in pure import mode, so a curated cube can be the group's first draft experience before the economy even exists.
- **After the draft:** a deckbuild screen with a 40-card minimum and free basic lands, then formatted "limited" games that validate each deck against that player's drafted pool plus basics. Pool records are session artifacts of the pod.
- **Deferred:** 2-player variants (Winston, Winchester), draft timers beyond a simple optional pick clock.

The draft engine itself (pod state, pack passing, pick resolution) is a pure-core state machine with exhaustive unit tests, and the cube/import path can ship ahead of collection mode because it depends on nothing in it.

## 11. Events and gyms (phase 4)

The Pixelmon-shaped layer, designed around its honest constraint: MTG battles do not automate, so a gym is a social structure, not an NPC. The mod ships tooling, the server ships culture.

- **Arenas:** a designation an admin applies to any table or cluster regardless of wood or theme, adding a challenge queue, a configurable gym leader list, win/loss recording (self-reported by the leader, an honor system with a paper trail), and the locked-public log and replay rules. Function and appearance are fully decoupled; a server's gym can be a cherry-wood table in a treehouse.
- **Badges:** cosmetic items grantable on gym victory, displayable on a player plaque block.
- **Spectate support:** arena tables broadcast a joinable spectator camera anchored over the table, plus a server-wide announcement hook when a challenge starts.
- **Match records:** per-player and per-deck win/loss history, fed by the same winner declaration ante and badges use. The raw material for ladders if the server wants them.
- **Deterministic replay (post-v1, guaranteed by architecture):** the sealed event streams plus the session's RNG seed, decrypted at session end, replay a finished game exactly and at full fidelity: replays are where hidden information (scry results, hands, searches) is finally divulged as record. Replay availability is a per-table setting (participants-only, public, or off) with arena tables locked public to match their logs, since gym integrity wants reviewable games. The playback UI ships whenever it earns its place; the event-sourced core means it is always buildable and never blocked.
- **Event scheduling stays out of scope.** Discord and server plugins do scheduling better than we ever will. We provide the arena, the log, and the badges.

This phase is deliberately thin. If the server fantasy catches on, it grows from real usage, not speculation.

## 12. Later down the road

Post-v1 mechanics, captured so they are never lost and never allowed to delay the phases. Nothing here may move forward until phase 3 exists; entries are sketches, expected to be tweaked when their time comes.

- **Card provenance.** Every card instance carries its biography: pulled by whom from what pack on what date, won in which ante pot, picked in which draft, with replays attached where they exist. The ownership-transfer log already records all of it, so this is mostly a display feature, and it is the most digital-native collecting idea available: cards stop being fungible copies and become artifacts that remember being taken from someone in an ante game.
- **Loot woven into Minecraft play.** Beyond fishing: structure-specific pack tables (older sets in desert temples, archaeology-flavored finds), treasure maps to cached packs, rare boss drops with juiced odds. The principle: acquisition should require playing Minecraft, not standing in a lobby, because that is the whole justification for this mod existing instead of Arena.
- **Trade binders.** A binder block holding standing offers in the villager-trade shape: the owner stocks a card and names its exact price in cards ("this Lightning Greaves for a Swiftfoot Boots"), with per-offer toggles for exact-printing-only and foil-only versus any-printing, and prices payable in cards or items ("this rare for six diamonds"), which makes player-run storefronts first-class without the mod operating any market. Because the owner defined the trade when posting it, consent already exists: any player can execute an offer at the binder while the owner is offline, atomically, the stocked card out and the payment card into the owner's received tray, repeatable while stock lasts. Offers may be bundles on either side. No later approval step, no negotiation layer; a binder is a vending machine for trades the owner already said yes to.
- **Sealed league.** The LGS classic: open six packs, build forty cards, play across weeks, add a pack to your pool weekly. Collection and gameplay braided into a standing server event with near-zero admin effort, and the friendliest format to a weak collection since every pool starts equally random.
- **Advancements integration.** Native Minecraft advancements: first pack, first mythic, first draft, first ante win, set completion, paired with cosmetic unlocks (a sleeve for ten ante wins) so the cosmetics tier gains a progression spine instead of only a shop.
- **Set-release cadence.** An admin lever flipping new packs into loot tables when a real set releases, giving servers "set release weekend" as a recurring festival with zero content authored by us. WotC provides the content calendar forever.

**Shelved:** live auction events (cut at the design table; the trade binder covers asynchronous exchange and nothing here needs a second market mechanism right now). **Rejected on principle:** card condition and wear (digitally it is deflation with extra steps and punishes playing your cards) and Arena-style dust crafting (generous crafting quietly deletes the reasons to trade, ante, and draft, which is where the social texture lives; at most a config-off option someday).

## 13. Architecture

Multiloader from day one: `common` + `fabric` + `neoforge` modules, and unlike the reference mod we studied, `common` holds zero loader imports, enforced by a build-time check. Platform-specific surface is small and behind interfaces: registration bootstrap, network send/receive, client init, config path.

Layering per the established discipline, sized so the checkable part is as large as possible:

| Layer | Contents | Verification |
|---|---|---|
| Pure core (no `net.minecraft`) | GameSession as an **event-sourced state machine** (every verb is an event, state is the fold of the log, undo and replay fall out by construction), zones, visibility rules, verb application, format validator, decklist parser, Scryfall client + cache, session log | JUnit + property tests, milliseconds |
| Definitions | items, blocks, data components, payloads, one registration class per registry | `runData` |
| Adapters | table block entity, payload handlers mapping packets to core verbs, persistence codecs | `runGameTestServer`, including scripted multi-fake-player visibility tests |
| Client | three renderers, seated GUI, overlay, texture manager | human play, structured feel-test checklists |

The visibility invariant from section 6 is tested at the adapter layer with game tests that connect fake players and assert on the actual payload streams each receives. This is the test suite that must never regress.

1.21.1 specifics locked now so no session burns time rediscovering them: `ResourceLocation.fromNamespaceAndPath` (the class is not yet renamed to `Identifier` on this version), `DeferredRegister.create(BuiltInRegistries.X, MOD_ID)` with custom-registry argument order id-first, networking via `CustomPacketPayload` with `StreamCodec` on both loaders (NeoForge registers through the payload handler event, Fabric through `PayloadTypeRegistry` plus play networking), and pinned docs at `docs.neoforged.net/docs/1.21.1/`. NeoForge is the primary development target with the full verification gate; Fabric is a port target verified per phase, not per commit.

All I/O (Scryfall HTTP, image fetch, disk cache) lives on dedicated executors. Nothing blocks a game thread, client or server. Repository ships with the DIALECT conventions file recording all of the above.

## 14. Phase plan

Each phase has a deliverable a human can hold, and a phase is done when its deliverable survives its checklist, not when its code compiles.

**Phase 0, the pipeline.** Scaffold both loaders, card item with data component, Scryfall client and caches, decklist import producing a deck item, zoom overlay working on cards in inventory. Deliverable: paste the Halana and Tevesh list, get a deck, read every card in full resolution. Mostly checkable-core work; this phase is heavily autonomous.

**Phase 1, the solo table.** Table multiblock, GameSession core with full zone/verb set, seated GUI, in-world miniature rendering, sandbox mode. Deliverable: goldfish a real Commander deck alone for 30 minutes without wanting to quit. This phase contains the bulk of feel iteration and is gated on weeknight solo playtests.

**Phase 2, the real game.** Multiplayer sessions, per-player visibility sync, spectator rendering, session log, life and commander damage, persistence across restart. Deliverable: a full 4-player Commander game with your group on a server, completed and enjoyed. Gated on group scheduling; the visibility test suite must be green before the first group session so friend time is never spent on desync bugs.

**Phase 3, collection and draft.** Everything in sections 9 and 10, config-gated. The draft engine's cube/import path has no collection dependency and can land at the tail of phase 2 if the group wants draft night before the economy exists. Deliverable: a fresh server where a player fishes up a pack, opens it, builds a jank deck from pulls, and plays it at a table; and one completed 4-player pick-2 draft pod, entry to games.

**Phase 4, arenas.** Section 10. Deliverable: one staged gym night.

Sequencing rationale: phases 0 through 2 produce the thing nobody else has built and the thing your group can use immediately. Collection re-treads proven ground and safely waits. If motivation or time dies after phase 2, what exists is already the complete play experience.

## 15. Legal posture

- Complies with the WotC Fan Content Policy: free, no paywalls or real-money hooks, clear "unofficial Fan Content" disclaimer in the mod description and title screen credit line, no WotC trademarks in the mod name or logo.
- Complies with Scryfall API guidelines: attribution in the UI where card data appears, respectful request rates, proper User-Agent, no bulk redistribution of their data or images in the jar.
- Mod code under MIT, matching the ecosystem norm and permitting the reference-reading courtesy we benefited from.

## 16. Decisions locked by this brief

So build sessions never stall on them: Scryfall UUID as sole canonical identity. Hands are GUI-only, never physical items in inventory during a session. All manipulation through the play view; in-world rendering, roam HUD, and spectator view are read-only. Server-authoritative everything; the client predicts nothing in v1. Commander is the first preset built, but formats are data-driven validator presets (Standard, Pioneer, Modern, Legacy, Vintage, Pauper, Commander, Oathbreaker at launch) with no in-game format logic; sideboards and best-of-three exist only in the pre-game and between-game layer. Tables are 2x2 multiblocks seating 2 facing players each, auto-merging into clusters of up to 4 tables with linear capacity 2/4/6/8, frozen in shape during live sessions. One session per cluster. No card physics; cards snap to a grid and stay where they are dropped, and a card's square is state rather than a per-client layout guess, so every client draws the same board. Battlefield squares are relative to a seat's region, so dragging a stolen permanent to your own side is one move event and control falls out of it. Piles have an order and no geometry. NeoForge-first, Fabric per-phase. No in-game rules enforcement, ever; deck legality validation is pre-game only and skipped entirely in free play. Playmat regions are visual guides, never boundaries; ownership is immutable, position is free, and session end returns every card to its owner except ante payouts and kept draft pools. The v1 verb set is section 7's trimmed core; deferred verbs wait for post-v1 evidence of need. Ante exists only where collection mode makes cards property, always behind unanimous per-session consent. GameSession is event-sourced. Seated view is tilted by default with a per-player flat toggle. Seats are session registrations with free roaming, never chair locks. Undo is per-table configurable (unanimous, free, off) with free-with-information-boundaries as the shipped default, and no mode can silently rewind across a revealed card. Draft pods are GUI constructs independent of table geometry; pick-2 at 4 to 5 players, single-pick at 6 to 8; collection drafts consume real packs and drafters keep pools unless a sponsor chose otherwise. Tables are a cosmetic family with identical function: the basic table in every wood type with dyeable felt, themed tables (Arcane and Tournament Hall at launch, more as phase 3 cosmetics), a universal live-session glow, and cross-skin cluster merging; arena is an admin designation on any table, never a block. The GUI is readability-first dark felt with accent colors rather than a theme inheritance. The public event log records actions, never hidden content, and is public by default with a per-table privacy toggle; arena tables are locked public. Hidden-information events and the shuffle seed persist only in an encrypted server-side stream sealed until session end; replays are the sole disclosure moment, with per-table availability (participants, public, off) and arenas locked public. NPC commerce sells sealed only and never singles; all singles value is player-emergent, and no IRL price appears anywhere in the economy or its UI. Stall stock is pinned tier (current-set boosters via admin pointer, basic Commander precons, beginner starter decks; scarcity is for chase, never infrastructure) plus a weighted server-global rotating pool; IRL market data may shape availability weights, never cost, and is never displayed. Completeness is faucet coverage enforced by the auditor, with the Archive Pack sweeping the computed remainder into loot. Working name: Gathering.

## 17. Open decisions

None. Every prior open decision has been resolved and folded into section 16. New ideas from here accumulate on a post-v1 list rather than reopening this document; the next questions get answered by the Phase 1 goldfish table, not by revision.
