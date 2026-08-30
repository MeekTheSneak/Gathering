# Project Conventions

Read before writing any code in this repository.

## Target versions

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 (`neo_version`) |
| Java | 21 |
| Parchment | 2024.11.17 (parameter names only) |
| NeoForm (`:common` vanilla mode) | 1.21.1-20240808.144430 |
| Fabric Loader / API / Loom | 0.19.3 / 0.116.15+1.21.1 / 1.9.2 |
| Docs URL | https://docs.neoforged.net/docs/1.21.1/ |

Every version lives in `gradle.properties`. Never use API forms from another Minecraft
version. Resolve unfamiliar APIs against the decompiled sources in the Gradle cache first,
the version-pinned docs second, existing working code in this repo third. Never recall.

**1.21.1 forms that differ from later versions**, locked here so no session rediscovers them:

| Concern | Correct on 1.21.1 |
|---|---|
| Identifier class | `ResourceLocation`, **not** `Identifier` |
| Construction | `ResourceLocation.fromNamespaceAndPath(ns, path)` |
| Registry get | `BuiltInRegistries.X.get(id)`, not `getValue(id)` |
| `DeferredRegister` (vanilla registry) | `DeferredRegister.create(BuiltInRegistries.X, MOD_ID)` |
| `DeferredRegister` (custom registry) | `DeferredRegister.create(MOD_ID, REGISTRY)` — **id first** |
| Item data | data components (`stack.get/set`), never `getOrCreateTag()` |
| Datagen event | `GatherDataEvent`, not `GatherDataEvent.Client` |
| Networking | `CustomPacketPayload` + `StreamCodec` on both loaders |

## Verification

```
./gradlew verify         # all three stages
```

which is:

```
./gradlew build                       # compile, JUnit + jqwik, module fences
./gradlew :neoforge:runData           # datagen; catches generated-resource drift
./gradlew :neoforge:runGameTestServer # headless in-world behavior; exit code = failed tests
```

Nothing is done until these exit zero. A passing read-through is not a substitute.
NeoForge is the primary target and runs the full gate; Fabric is verified per phase, not
per commit.

Client rendering is not mechanically checkable and the gate does not pretend otherwise. What
it does cover on the client side is everything below the pixels: payload codecs round-trip
against real registries in game tests, and `runClient` boots headless under Xvfb with
software GL, which proves the client classes load, the events register, and the models and
textures resolve. Whether the overlay *reads well* is a judgment call - play it.

```
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a ./gradlew :neoforge:runClient
```

Every mechanical stage has been confirmed capable of failing — a gate that cannot fail manufactures
confidence rather than providing it. Re-confirm after changing the gate itself.

## Structure

Four modules, in order of how cheaply they can be checked:

| Module | Contents | Verified by |
|---|---|---|
| `:core` | Pure logic. **No `net.minecraft`, no loader.** Card identity, decklist parser, Scryfall client and cache, deck import, the event-sourced `GameSession`, zones, verbs, visibility rules, format validator | JUnit + jqwik, milliseconds |
| `:common` | Minecraft-facing, loader-agnostic. Items, blocks, data components, payloads, block entities, persistence codecs. **Zero loader imports** | `runData`, `runGameTestServer` |
| `:neoforge` | NeoForge entry point, registration bootstrap, network wiring, client init | full gate |
| `:fabric` | Fabric entry point, the same small platform surface | per phase |

The fences are mechanical, not conventional:

- `:core` is compiled with no Minecraft on its classpath at all, and `:core:checkCorePure`
  fails the build on a `net.minecraft` / loader import.
- `:common` is compiled against **vanilla Minecraft only** (ModDevGradle vanilla mode via
  NeoForm), so a NeoForge or Fabric class is a compile error rather than a review note.
  `:common:checkNoLoaderImports` backs that up.

Design intent: make `:core` as large as it can be. Everything in it is checkable in
milliseconds; everything outside it needs a game.

## Conventions

| Concern | Use | Not |
|---|---|---|
| Registration | `DeferredRegister`, one class per registry type | `RegisterEvent` directly |
| Event handlers | `@EventBusSubscriber(modid = MOD_ID)`, all handlers static | manual bus guessing |
| Card identity | Scryfall UUID via the `CardIdentity` data component | names, NBT blobs |
| I/O | dedicated executors; `ScryfallClient` and the disk cache block by design | any game thread |
| JSON | Gson (Minecraft ships it; `:core` takes it `compileOnly`) | a new dependency |
| Card art | fetched per client from Scryfall `image_uris`, cached on disk | shipped in the jar, or relayed over our network |
| Client/server seam | a holder the client bootstrap binds into | registration code naming a client class |
| Async results | `CompletableFuture` returned to the caller | a blocking method anyone could call from a tick |
| Hidden zones | server-side visibility filtering at the payload level | client-side hiding |
| Game state | events appended to the log; the board is the fold | mutating state directly |
| Card position | a `TablePosition` in state, settled by the fold | letting each client lay the board out |
| Undo | mark entries undone and re-fold | inverse operations, or deleting log entries |
| Authorization | owner-locked only where the act would reveal to the actor | blocking "illegal" plays |

## Banned

- `// TODO` that compiles — unimplemented paths throw instead
- Registry queries during registration
- Blocking calls on any game thread (all Scryfall and disk work is executor-only)
- Client-only classes referenced from common code
- New mixins where a NeoForge event exists
- Card images in the jar; card data bulk-redistributed
- Any in-game rules enforcement. Deck validation is pre-game only and stops at session start
- Positional matching of Scryfall collection results (see `CardQueryMatcher`)

## Known gotchas

Project-specific traps that have caused real bugs here. Add on every discovery — this
section is the highest-value part of the file over time, because it is the only part that
cannot be inferred from reading the code.

- **Scryfall's collection endpoint drops not-found cards from `data` rather than nulling
  them.** Matching results to requests by array position silently misaligns every card
  after the first miss. Always match by content — `CardQueryMatcher`.
- **`//` is a comment only at the start of a decklist line.** `Fire // Ice` is a card name.
- **An Archidekt category looks exactly like a set code.** `[Land]` is a category, `[C21]`
  is a set; the parser distinguishes them by case, which is a heuristic and not a law.
- **Decklist set hints go stale.** A wrong set code must fall back to a name lookup, never
  to a missing card.
- **`runGameTestServer` reports success when there are no tests to run.** The server throws
  `IllegalArgumentException: No test functions were given!`, Minecraft exits 0, and Gradle
  calls it a pass. Adding a module without adding a game test therefore *widens* the gap
  the gate is supposed to close. Keep at least one real game test registered, and check the
  log says how many tests passed rather than trusting the exit code alone.
- **Game tests need a structure template even when they do not touch the world.** Ours is
  `data/gathering/structure/empty.nbt` (`structure`, singular, since 1.21).
- **`helper.fail(...)` returns void**, so it cannot be the body of an `orElseGet`. Throw
  `GameTestAssertException` instead.
- **`project(':x')` inside the `neoForge { }` block is a dependency, not a `Project`.** Use
  `rootProject.file(...)` for paths into another module.
- **Taking both `sourceSets.main.output` and `sourceSets.main.resources` from `:common`
  puts every shared asset in the jar twice.** The output already contains the processed
  resources; take only that.
- **`StreamCodec.composite` stops at six fields.** Seven is a compile error that reads like
  a type mismatch. Split the record instead of hand-rolling a codec - the split usually
  matches the domain anyway.
- **`AbstractContainerScreen#hoveredSlot` is protected and `:common` gets no access
  transformers**, because it compiles against vanilla. The hovered stack comes from each
  loader's tooltip event into `ClientHoverState`.
- **Fabric's `ClientPlayConnectionEvents` lives in `...client.networking.v1`**, not in
  `...client.event.lifecycle.v1` where the server-side lifecycle events are.
- **Scryfall's collection endpoint answers, but does not echo, the raw JSON per query.**
  Keep each card's original body alongside the parsed model (`CollectionResult#raw`) or the
  disk cache ends up storing a re-serialization of only the fields today's codec reads.
- **Scryfall serves progressive JPEG, and Minecraft cannot read it.** `NativeImage.read` is
  stb_image, which handles baseline JPEG only, so every card image fails to decode and the
  only symptom is art that never appears. Decoding goes through `CardImageDecoder` (ImageIO,
  in `:core`, tested against a real progressive fixture). Never route card art back through
  `NativeImage.read`.
- **`setPixelRGBA` wants ABGR** (`0xAABBGGRR`) despite the name, while Java's `BufferedImage`
  gives ARGB. Getting it backwards renders plausible, wrong colors - blue lands come out
  orange - rather than failing.
- **Client image failures log at WARN, on purpose.** Each URL is attempted once, so it is one
  line per card, and art that will not draw is the most visible way this mod can look broken.
- **`BlockEntityWithoutLevelRenderer` is in `net.minecraft.client.renderer`**, not
  `.blockentity`. It is a vanilla class, which is why `CardItemRenderer` can live in
  `:common` and both loaders share one renderer.
- **Attach item renderers with `RegisterClientExtensionsEvent`** (in
  `neoforge.client.extensions.common`, not `client.event`). `Item#initializeClient` still
  works on 21.1.248 but is deprecated for removal, and using it would force a NeoForge-only
  subclass of an item that has no other loader-specific behavior.
- **A custom item renderer is only consulted if the item's model is `builtin/entity`.**
  `card.json` is hand-authored for that reason and deliberately excluded from datagen; its
  display transforms are meant to be edited by hand.
- **`Screen#render` calls `renderBackground` itself, and that applies a full-screen blur.**
  Drawing a panel in `render` before `super.render` therefore blurs the panel and every
  hand-drawn label on it, while widgets stay sharp - which reads as "part of the screen is
  fuzzy" rather than as a render-order mistake. Draw screen backgrounds by overriding
  `renderBackground`, never before `super.render`.
- **`item/generated` is the reference for flat items, not `item/handheld`.** Handheld's
  third-person `[0,-90,55]` is a fist grip on a tool; applied to a card it looks skewed and
  edge-on. Flat items use `[0,0,0]`.
- **Display transforms reload with F3+T**, so iterating on how an item sits in hand does not
  need a restart - edit the copy under `build/resources/main`.
- **The card back is our own art and must stay that way.** The mod ships no Wizards imagery -
  that is a project pillar and a Fan Content Policy requirement, not a placeholder. It is
  also the seed of the sleeve system in section 9.
- **A card's reverse is always the sleeve, never the printed face behind it.** Every card is
  sleeved, and the back of a sleeve is opaque. This is a security property rather than a
  look: complementary sides would put a face-down card's face on its own back, so anyone
  could read it by moving round behind it. Never "fix" the two-backs appearance of a
  face-down card - that appearance is the point.
- **GUI art is nine-slice sprites**, not rectangles drawn in code: PNGs under
  `textures/gui/sprites` with a `.mcmeta` beside each. Repainting the PNG reskins the mod and
  a resource pack can override it. Draw them through `GatheringSprites`, never `graphics.fill`.
- **A pasted deck link makes the server fetch a URL, so the host list is an allowlist.**
  `DeckLink` matches whole known hosts and rebuilds the address from the deck id; nothing a
  player typed reaches the network verbatim. Without that, any player could point the server
  at a metadata endpoint or an internal service and read the answer. The hostile-input cases
  are in `DeckLinkTest` - add to them before adding a provider.
- **Archidekt sends `"categories": null` for an uncategorized card**, not an empty list, and
  its deck entries carry the printing's Scryfall id in `card.uid` - which is why a link
  import resolves exactly where a text export can only guess.
- **Scryfall's collection endpoint refuses combined card names.** `{"name": "Fire // Ice"}`
  comes back not-found; `{"name": "Fire"}` returns the whole card. Same for transform and
  modal double-faced cards - "Delver of Secrets // Insectile Aberration" fails, "Delver of
  Secrets" works. Every exporter writes the combined form, so without `CardQuery#lookupName`
  every split and double-faced card in every decklist fails to import. Verified against the
  live API, not inferred.
- **Card instance ids are handed out in decklist order, and Commander decklists are usually
  public.** A public log line naming a card by raw id therefore identifies it, with no hidden
  payload ever sent. Log lines reference cards through `CardRef`, which picks id, opaque
  marker, or "a card" against the board. Never put a bare `CardInstanceId` in a `LogLine`.
- **`java.util.Random` cannot shuffle a deck.** 48 bits of state reach 2^48 permutations; a
  100-card library has 100! of them. Shuffles use `DeterministicRandom` (SHA-256 counter
  mode) so they stay replayable *and* reachable. Never swap it for `Random` "for speed".
- **Only the battlefield is a surface.** `Zone#isSurface` decides whether cards there carry a
  `TablePosition`; piles have an order and no geometry, and a card moving into one drops its
  square. A property test asserts the two never disagree.
- **A record accessor and a wither cannot share a name.** `SeatState.conceded()` is the
  accessor, so the wither is `withConcede()`; `invalid accessor method in record` is what
  that collision looks like.
- **`SimpleContainer#setItem` clamps to the item's max stack size.** Cards are `stacksTo(1)`,
  so a slot can never hold two of them however the test set it up - which made a test of the
  multi-card insert path fail against code that was correct. The cursor stack is not clamped
  the same way, which is why the multi-copy path is reachable there and only there.
- **A deck takes the bundle gesture but only half of it.** `overrideOtherStackedOnMe` and
  `overrideStackedOnOther` put cards in; the empty-cursor branch that a bundle answers by
  handing one item back is deliberately declined. Taking a card is done from the deck list,
  where the card's name is visible before the click. Never make the gesture symmetrical -
  blind-pulling from a hundred-card deck is a mistake a player makes by accident.
- **The deck screen holds no copy of the deck.** It reads the stack in the named hand every
  frame. A screen built from a snapshot would show the deck as it was before every edit the
  server applied, and there is no packet pushing a fresh copy at it - held-item sync already
  does that.
- **The carried stack's `overrideStackedOnOther` runs before the slot stack's
  `overrideOtherStackedOnMe`.** Both fire from `tryItemClickBehaviorOverride`, first match
  wins, so which item implements which hook decides who handles a click. Card-onto-card is
  handled by the card *in the slot*, which is also the only one of the two hooks handed a
  `SlotAccess` for the cursor.
- **A card is drawn for reading in exactly one place**, `CardInspectPanel`, in three
  geometries: beside the cursor, into a box a screen set aside, and filling the screen.
  `CardZoomOverlay` decides whether and what; it does no drawing. A second card renderer is
  how the table view and the inventory view drift apart.
- **The inspect panel replaces the vanilla tooltip, it does not cover it.** Both draw in the
  same place, and the tooltip's Scryfall attribution line is wider than the panel, so a
  covered tooltip peeks out around the edges. NeoForge cancels `RenderTooltipEvent.Pre`;
  Fabric has no cancellable equivalent, so `ItemTooltipCallback` empties the line list and
  vanilla skips drawing. Emptying is only safe because a card has no tooltip image -
  `GuiGraphics#renderTooltip` does `list.add(1, image)`, which throws on an empty list.
- **The Scryfall credit is pinned to the bottom of a panel with its height reserved**, never
  flowed in with the oracle text. Flowed, it is the first thing a wordy card pushes off the
  end - and it is the one line that is not allowed to be optional.
- **Screen layout arithmetic lives in `:core`, not in the screen.** `DeckScreenLayout` is
  plain integers with no Minecraft in it, so it can be checked at every window size from
  320x240 (the smallest GUI-scaled screen Minecraft produces) to 3840x2160 rather than at the
  one the author happened to be running. A GUI is otherwise only ever tested at one size, and
  everything that breaks at the others breaks silently.
- **A slot item is drawn at depth 150 and up.** Anything drawn over a screen afterwards at
  depth zero still comes out *behind* the items, so the item pokes through the panel. Vanilla
  puts tooltips at 400; the inspect panel sits with them.
- **The tapered panel edge and the scrollbar on it agree by construction, not by
  arithmetic.** The scrollbar is an ordinary vertical bar drawn under a shear
  (`Matrix4f#m10`, which multiplies y into x) along the same line the texture's edge was
  drawn along. Two separate pieces of math would drift; the hit test undoes the same shear,
  so the bar you can see and the bar you can grab stay together.
- **`GuiText`, never `graphics.drawString`, for anything a player did not choose.** Card
  names are arbitrary text of arbitrary length and the font has one size, so text shrinks to
  fit and only trims when shrinking further would stop it being readable.
- **The GUI sprites are generated from a palette** by `tools/gui_textures.py`, so a whole
  coherent set - and later a whole theme - comes from one block of colors rather than from
  editing seven PNGs by hand. The PNGs are still the source of truth: repaint one and the
  screens change, and a resource pack can replace any of them.
- **An empty deck deletes itself.** `DeckEdits` removes it when the edit that emptied it is
  applied, and `DeckItem#inventoryTick` is the server-side backstop for every other route.
  A deck item carrying no component at all is left alone - that is the creative-menu deck,
  which is blank rather than empty.
- **Mana symbols are a bitmap font, not a custom text renderer.** Each braced code maps to a
  private-use character styled with `gathering:mana`, so the game's own layout does wrapping,
  width and drawing for symbols exactly as it does for letters. Writing a token-aware layout
  pass instead means reimplementing all three and having them disagree with the rest of the
  text.
- **Three artifacts have to agree on which glyph is which**: `ManaSymbols.NAMES`, the
  generator that draws the textures, and `font/mana.json`. Nothing connects them at compile
  time and getting it wrong does not fail - it draws a different mana symbol, which on a card
  is a different cost. `ManaFontGameTest` reads the font back and matches it against the
  list. Append to `NAMES`, never insert: inserting renumbers every glyph after it.
- **The mana symbols are the mod's own art**, lettered discs rather than Wizards'
  pictographs, for the same reason the card back is. The five colors are the conventional
  ones because which color a cost is happens to be the information the symbol carries.
- **`TAPER_TOP` is 0.90 and not 1.0 on purpose.** The panel's edge line and the shadow
  outside it need somewhere to go, and at 1.0 they run off the right of the texture - which
  is not a subtle artifact, it is the top corner of the panel arriving unfinished.
- **The scrollbar is anchored to the edge at the *top* of the panel.** The shear carries it
  the rest of the way in. Anchoring it to the narrow end and then shearing it as well tapers
  it twice and walks it off the panel.
- **Card art gets its corners rounded at decode, not by drawing over them.** What sits behind
  a card is different everywhere it appears - a frame, a panel, a dimmed world - so a corner
  mask painted in one background color is right in exactly one place. Only the alpha is
  cleared, never the color, because `entityCutout` in the world does not blend and a
  zeroed pixel would leave black corners.
- **`GuiText` works in `FormattedText`, never in `String`.** Flattening to a string to
  measure or trim silently drops the styling the caller added - a bold title comes out plain
  the moment it is long enough to need shrinking, which is the one case nobody tests.
- **Inside a screen the read key follows the cursor and nothing else.** It deliberately does
  not fall back to the card in the player's hand: falling back meant a held card shadowed
  every slot the cursor was not over, and answered a question nobody had asked.
- **"Two faces" and "two sides" are different things.** A split, flip, adventure or aftermath
  card is two lots of rules text printed on one piece of card; a transform card is two of
  everything. Scryfall says which by where it puts the art - on the card for the first kind,
  on each face for the second - so the codec gives a card-level image to the **front face
  only**, and `CardSummary#printedSides` is what gets drawn while `faces()` is what gets
  read. Copying the card image onto both faces makes a split card look like two faces that
  happen to match, and every renderer downstream then draws the same picture twice.
- **A client is told about cards it holds, and something has to keep asking.** Metadata is
  memory only and cleared on disconnect on purpose, so after a restart a card in an inventory
  is a UUID and nothing else. Opening a deck asked about that deck; nothing asked about a
  loose card, so it stayed nameless and artless until it happened to be in a deck somebody
  opened. `ClientCardRequests` sweeps the inventory; what is worth asking about is decided by
  `MetadataRequests` in `:core`, because this runs from a tick handler and the failure mode
  of getting it wrong is a request storm aimed at somebody else's server.
- **A loader can serve the mod's classes without its assets, and nothing fails.** Fabric did
  exactly that: Fabric Loader works out a mod's roots from where it found the
  `fabric.mod.json`, so `:common`'s processed resources - every texture, model, lang file and
  font - sat on the classpath as an entry no mod claimed. The mod compiled, built, passed
  every test, booted, registered everything and logged happily, and would have drawn missing
  textures and raw translation keys. Both loaders now take their module list from
  `rootProject.ext.sharedModules` so they cannot drift apart again.
- **`rootProject.sharedModules` does not resolve inside a plugin's configuration closure.**
  The delegate finds something of its own and the failure reads as a missing property on an
  unrelated type. Capture it in a local at the top of the build file.
- **`./gradlew verify` cannot prove a loader was wired up right**, only that the code is. Run
  `tools/smoke.sh` before claiming one works: it boots both loaders, client and dedicated
  server, and checks the mod's resource pack is actually in the list the game loaded. Its
  Fabric check was confirmed to fail when the fix for the above is reverted. Grep the mod id
  alone and it passes regardless - the id is all over any log.
- **`BlockEntityType.BlockEntitySupplier` is package-private in vanilla.** Both loaders
  access-transform or replace it, so `:common` - which compiles against vanilla and nothing
  else - cannot name `BlockEntityType.Builder`. The block entity's constructor stays in
  `:common` and each loader builds the type: two lines each, which is the right size for the
  platform surface.
- **A table is four blocks and one of them is real.** The north-west corner carries the block
  entity and owns the table; the other three know where it is. Breaking any quarter takes the
  whole table, guarded by checking the neighbor really is this table's quarter - without
  that, breaking a corner takes a bite out of the table pushed up against it.
- **Seat claims live on the table whose edge they are.** A seat is (table, edge), so storing
  the claim there means it is saved with that table and comes back with it, and reshaping a
  cluster cannot lose somebody else's seat. Sides are written by **name**, never by ordinal:
  an ordinal means something different the moment the enum gains a value, and this is a save
  file.
- **A block cannot decline to be broken in vanilla.** By the time the block hears about it the
  decision is made, so "you cannot break a table somebody is sitting at" lives in each
  loader's break event - `BlockEvent.BreakEvent` on NeoForge, `PlayerBlockBreakEvents.BEFORE`
  on Fabric - over one shared predicate.
- **Cluster capacity is a property of the cluster, not of each table.** Four tables always
  seat eight, but push them into a T and the middle one has a single outward edge: two seats
  on it would put somebody inside the furniture and one would make a four-table cluster seat
  seven. So the count is per cluster and the seats are placed around its perimeter, facing
  pairs first. The property tests caught this; the first implementation seated seven.
- **Color lives in the block entity, not the blockstate.** A dyeable surface in the
  blockstate multiplies a block's state count by sixteen to answer a question only the
  renderer ever asks. The cost is that a blockstate change will not sync it, so the block
  entity has to say so itself: `sendBlockUpdated` on change, plus `getUpdateTag` and
  `getUpdatePacket` for clients that join later. Those are two separate ways to lose the same
  value, so both have a test.
- **`DyeColor#getTextureDiffuseColor` is ARGB and a block tint is RGB.** Leaving the alpha in
  makes every dyed surface draw as though it were unlit.
- **The game test world is on disk and outlives the run.** `neoforge/run/world` is not
  recreated per invocation, so an entity a test spawns is still there next time. A test that
  looks for something in the world and does not clear first passes whether or not the feature
  works - the drop-on-full-inventory test did exactly that, and only stopped when it was
  checked against the fix being deleted. Clear, act, assert, clear.
- **A cluster's game lives on its first cell in sorted order.** That is the same order that
  numbers the seats, so every table in a cluster agrees where the game is without anything
  being written down about it, and seat *n* of the session is the *n*th of the cluster's seat
  positions. The shape is frozen while a game runs, so those numbers cannot move under it.
- **A session is restored lazily, never at world load.** Reading one needs the session key,
  and a key that cannot be read must never be the reason a world fails to load. A session that
  will not open keeps its bytes and is written back unchanged: it is still somebody's game,
  and replacing it with nothing because this server could not read it is the one irreversible
  option available.
- **Anything that reads the board for a player goes through `VisibilityRules`.** Reading
  `GameState` directly is easier and is how one convenience becomes the single place in the
  mod that can see everybody's hand. The chat status readout is built from a `GameView` for
  exactly that reason, even though it runs on the server and could have looked.
- **What crosses the wire to a client is a `GameView`, never a `GameState`.** The view has
  already been through the visibility rules, so there is nothing in the packet to extract. One
  view is built per recipient and addressed to them alone - a shared board packet would have
  to contain everybody's hand and every client would hold it.
- **A client signs its own moves and the server checks the signature.** An action arrives as
  an encoded event; the server refuses any whose actor is not the sender's seat. That check is
  what makes the permissiveness safe: any seated player may move any public card *because* the
  log says who did. Deleting the check makes a forged move go through, which is what its test
  asserts.
- **The miniature draws the spectator view**, the same public board anybody standing at the
  table would get, and the seated view is a different object for a different recipient. Two
  views of one game, and only one of them has your hand in it - which is why the client keeps
  them keyed by table rather than merged.
- **Nothing is applied on the client before the server agrees.** A board that showed a move
  and then took it back would be leaking, because "take that back" is a sentence with
  information in it.
- **Moving a card between piles is one operation, in every direction.** Not "make commander"
  plus "move to sideboard": a verb per destination is how a deck editor quietly becomes a
  Commander deck editor, and the formats that live on their sideboard are the ones that would
  notice. The mod is Commander-first in its defaults, never in what it makes possible.
