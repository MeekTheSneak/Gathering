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
./gradlew :neoforge:runGameTestServer # headless in-world behaviour; exit code = failed tests
```

Nothing is done until these exit zero. A passing read-through is not a substitute.
NeoForge is the primary target and runs the full gate; Fabric is verified per phase, not
per commit.

Client rendering is not mechanically checkable and the gate does not pretend otherwise. What
it does cover on the client side is everything below the pixels: payload codecs round-trip
against real registries in game tests, and `runClient` boots headless under Xvfb with
software GL, which proves the client classes load, the events register, and the models and
textures resolve. Whether the overlay *reads well* is a judgement call - play it.

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
  disk cache ends up storing a re-serialisation of only the fields today's codec reads.
- **Scryfall serves progressive JPEG, and Minecraft cannot read it.** `NativeImage.read` is
  stb_image, which handles baseline JPEG only, so every card image fails to decode and the
  only symptom is art that never appears. Decoding goes through `CardImageDecoder` (ImageIO,
  in `:core`, tested against a real progressive fixture). Never route card art back through
  `NativeImage.read`.
- **`setPixelRGBA` wants ABGR** (`0xAABBGGRR`) despite the name, while Java's `BufferedImage`
  gives ARGB. Getting it backwards renders plausible, wrong colours - blue lands come out
  orange - rather than failing.
- **Client image failures log at WARN, on purpose.** Each URL is attempted once, so it is one
  line per card, and art that will not draw is the most visible way this mod can look broken.
- **`BlockEntityWithoutLevelRenderer` is in `net.minecraft.client.renderer`**, not
  `.blockentity`. It is a vanilla class, which is why `CardItemRenderer` can live in
  `:common` and both loaders share one renderer.
- **Attach item renderers with `RegisterClientExtensionsEvent`** (in
  `neoforge.client.extensions.common`, not `client.event`). `Item#initializeClient` still
  works on 21.1.248 but is deprecated for removal, and using it would force a NeoForge-only
  subclass of an item that has no other loader-specific behaviour.
- **A custom item renderer is only consulted if the item's model is `builtin/entity`.**
  `card.json` is hand-authored for that reason and deliberately excluded from datagen; its
  display transforms are meant to be edited by hand.
- **The card back is our own art and must stay that way.** The mod ships no Wizards imagery -
  that is a project pillar and a Fan Content Policy requirement, not a placeholder. It is
  also the seed of the sleeve system in section 9.
- **GUI art is nine-slice sprites**, not rectangles drawn in code: PNGs under
  `textures/gui/sprites` with a `.mcmeta` beside each. Repainting the PNG reskins the mod and
  a resource pack can override it. Draw them through `GatheringSprites`, never `graphics.fill`.
- **A pasted deck link makes the server fetch a URL, so the host list is an allowlist.**
  `DeckLink` matches whole known hosts and rebuilds the address from the deck id; nothing a
  player typed reaches the network verbatim. Without that, any player could point the server
  at a metadata endpoint or an internal service and read the answer. The hostile-input cases
  are in `DeckLinkTest` - add to them before adding a provider.
- **Archidekt sends `"categories": null` for an uncategorised card**, not an empty list, and
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
