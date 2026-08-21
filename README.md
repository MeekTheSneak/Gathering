# Gathering

A Minecraft mod for **1.21.1** (NeoForge and Fabric) that turns a multiblock table into a
full tabletop card game surface: TTS-style manual mechanics with real hidden information,
plus an optional server-configurable collection economy.

> **Unofficial Fan Content.** Not approved or endorsed by Wizards of the Coast. Portions of
> the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC.
>
> Card data and images come from [Scryfall](https://scryfall.com). The mod ships no card
> images and redistributes no card data; every client fetches and caches its own.

The full design is in [`docs/design-brief.md`](docs/design-brief.md). Project conventions,
version pins, and the verification gate are in [`DIALECT.md`](DIALECT.md). Read both before
writing code.

## What this is

Two fantasies, one card system:

- **Play.** Four friends sit at a table inside a shared world and play Commander with their
  real decklists. The mod is the table, the cards, and the hands. The rules live in your
  heads — there is **no in-game rules enforcement, ever**.
- **Collect.** On a configured server, cards are also things you find, open, buy, and trade,
  with gym nights at arena tables.

And a short list of things it is deliberately **not**: a rules engine, an AI opponent, a
real-money anything, a card-art redistribution vehicle, or a physics sandbox.

## Layout

| Module | What lives there | Rule |
|---|---|---|
| `core` | Pure logic — card identity, decklist parsing, the Scryfall client and cache, deck import | no `net.minecraft`, no loader |
| `common` | Minecraft-facing, loader-agnostic — items, components, blocks, payloads | no loader imports |
| `neoforge` | NeoForge entry point. Primary development target | full gate |
| `fabric` | Fabric entry point. Port target | verified per phase |

Both rules are enforced by the build, not by convention: `core` has no Minecraft on its
classpath at all, and `common` is compiled against vanilla Minecraft only, so a loader class
is a compile error. `:core:checkCorePure` and `:common:checkNoLoaderImports` back that up.

The point of the split is to make the layer that can be checked in milliseconds as large as
possible.

## Building

Java 21. Everything is pinned in `gradle.properties`.

```bash
./gradlew verify               # the gate: build + runData + runGameTestServer
./gradlew :core:test           # the fast loop - pure core only, seconds
./gradlew :neoforge:runClient  # play it
```

`verify` is the bar. Every stage of it has been confirmed capable of failing, because a
gate that cannot fail manufactures confidence rather than providing it.

## Status

**Phase 0 — the pipeline.** In progress.

- [x] Multiloader scaffold with enforced layer fences
- [x] Card identity (`{scryfall_id, foil, custom_id?}`) and card metadata
- [x] Decklist parser — Moxfield, Archidekt, Arena, MTGO, deckstats, plain
- [x] Scryfall client: batched collection resolution, rate limiting, retry, disk cache
- [x] Deck import: pasted text to a resolved deck
- [x] Card and deck items carrying the data component, on both loaders
- [x] Verification gate: JUnit + jqwik, data generation, headless game tests
- [ ] In-game decklist import screen
- [ ] Zoom overlay reading cards at full resolution

Phases 1–4 (the solo table, the real game, collection and draft, arenas) are described in
the design brief, section 14.

## Licence

Mod code is [MIT](LICENSE). It contains no Wizards of the Coast assets and no Scryfall data.
