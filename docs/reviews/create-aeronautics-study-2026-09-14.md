# What Create and Create Aeronautics suggest for Gathering

The owner asked for a look at Create (`Creators-of-Create/Create`, 6.0.10, `ac0c444d`) and Create
Aeronautics (`Creators-of-Aeronautics/Simulated-Project`, `50443d0`) for code quality and polish
this mod could learn from. Read from the source, not recalled. Numbers are counts over each tree's
main sources.

Each finding says what those projects do, what Gathering does, and what was decided.

## Taken up

**1. Every block says what it is for while it is still in a hand.** Create gives each item with
behavior a summary and a list of conditions and behaviors (`ItemDescription`, keys
`<item>.tooltip.summary`, `.condition1`, `.behaviour1`, shown on Shift). Gathering's tables, cards,
decks and packs had tooltips, but three blocks said nothing: the Collection, the Shop Counter and
the new Scorekeeper's Desk. A player who put a desk down before hosting anything learned only that
it was a lectern. **Done:** `DescribedBlockItem` gives all three a use and their gestures, in the
tooltip style the tables already use. Always shown, not behind Shift - Gathering's tooltips are
two or three short lines, and a key to hold for three lines is a step. The desk's Create line shows
only when Create is installed. Guard: `BlockTooltipGameTest.everyBlockSaysWhatItIsFor` walks every
block item the mod registers, so a fourth block cannot be added silent.

**2. Where something is, said where it is.** Create puts information on the block it describes -
goggle overlays, value boxes, display boards - rather than in a screen you open to find out.
Gathering already floats a table's number over it during an event. **Done:** a linked Scorekeeper's
Desk floats its tournament's name and where it has got to ("Sign up here", "Round 2 of 4", "Won by
Alice") over itself, worked out again from the event once a second, so it follows the event with
nobody touching the desk and clears when the event is called off. What is sent to clients is the
label, never which tournament the desk runs. The drawing is shared with the tables' labels
(`FloatingLabel`), so a hall of them reads as one set.

**3. An interaction is heard.** Create plays a sound on nearly every deliberate interaction. The
desk now turns a lectern's page (vanilla `BOOK_PAGE_TURN`, no new sound asset) when it takes a
tournament on.

**4. Display Link conventions.** Create's multi-line sources put their one setting on the first
line (`ScoreboardDisplaySource`, width 137) and single-line sources keep the label box there;
selection inputs are 80-120 wide and titled. The desk's *Show* setting follows that (first line,
120, titled). Create's single-line sources can be written to signs, which cut a long line off;
the table's match now splits over a narrow target's lines. Aeronautics' numeric sources
(`AbstractNumericDisplaysource`) keep the label and add their mode on the second line, which suits
a single-line value and is not what a list wants.

## Noted, not taken up (yet)

**5. Transient feedback on the action bar, not in chat.** Create sends 89 status messages to the
action bar (`CreateLang...sendStatus`) and 3 to chat. Gathering sends about 210 system messages to
chat and 2 to the action bar. Much of that is right for Gathering - results, what arrived in your
inventory, announcements to a table - and a board screen covers the action bar, so a blanket switch
would hide refusals from seated players. But refusals from clicking a block in the world, with no
screen opening (a collection you may not add to, a table that will not fit), are what the action
bar is for. **Done, for what is certain:** the messages from clicks that open no screen and can
repeat - putting a card into a collection (yes or no), sweeping loose cards in, and a table that
will not fit where it is being placed, which holding the button asks again every few ticks - now
go over the hotbar. Left in chat: anything followed by a screen (a screen covers the hotbar and the
line would fade behind it - the desk's messages are these), deck legality lists (many lines), and
everything a player needs to read later. Not verified in a client.

**6. Ponder scenes.** Both projects teach their blocks with Ponder (Create 86 schematics, Aeronautics
57 across its modules), shown by holding W over an item. **Done for the desk:** with Create installed,
W over a Scorekeeper's Desk plays a scene - what it is for, what using it does and who may, a Display
Link on it filling a board with standings and then pairings - and the desk is listed among Create's
sources for Display Links. It is built on Create's own Display Link schematic with the depot made a
desk, read from Create's jar at runtime, so this mod ships no schematic and the scene matches the one
beside it. Its words are in the lang file, where Ponder reads them; the pack scene checks every line
agrees with what the scene records, that the scene and the tag entry exist, and photographs it. The
desk's floating label faces the way the desk does inside Ponder, whose camera is not the player's.
**And for the tables:** size and seats, joining, sitting down with a deck, choosing a format, the
tournament number, a Display Link. Building it found a real defect: the table renderer finds a
table's game by block position alone, so in any level other than the one being played - a Ponder
scene, a Create schematic's preview - a table at the same coordinates drew some real game's board,
and overwrote the projection the pointer picks cards with. Shown in a photograph with the guard off
(a real board filed under the scene table's position drew on it); with the guard, nothing is drawn.

**7. Mechanical conventions.** Create has an `.editorconfig` (spaces, LF, final newline; JSON at 2).
**Done:** Gathering has one now, written from what its files already do (Java and Python at 4, JSON
and mcmeta at 2, LF, a final newline). Create imports what it names; Gathering's main sources name about 620 classes
fully qualified inline, many in the largest files. Changing that across the tree would be a large
diff of no behavior, against the repository's own "reads like the surrounding code" rule; new code
imports where its neighbors do.

**8. Things Gathering already does as well or better.** Service-loaded platform seams (Aeronautics'
`service` package; Gathering's `Platform`, `WorldSpace`). Registries in one place (Create's `All*`,
Aeronautics' `index`; Gathering's `GatheringContent`). In-world tests grouped by concern with a
regressions class (Create's `TestRegressions`; Gathering's review regression tests, far more of
them). Every sound with a subtitle (both). Gathering's gate - static checks for language, docs,
sprites, textures and a proof that tests were discovered - has no counterpart in either repository.

## Second pass (2026-09-15): the code-quality side

The first pass followed the desk and display-board work. This one read both trees for how they are
built rather than what they add.

**Taken up.**

- **Settings from the mods list.** Create registers `IConfigScreenFactory`, so NeoForge's mods list
  has a Config button for it. Gathering's own settings screen - text size, control size, reduced
  motion - opened only from a table's menu. It opens from the mods list now (the pack scene checks
  the factory's screen is the settings screen; shown failing without the registration). Fabric has no
  equivalent without depending on Mod Menu; not added.
- **One set of test helpers.** Create keeps its game-test helpers in `CreateGameTestHelper` (45
  public members). Gathering's in-world tests wrote out table placement in eleven classes, six
  variations; they call `TestTables` now. Other repeated helpers (`tableAt`, `deckOnTheFloor`,
  `clearItems`) are fewer and left for when a change touches them.

**Considered and left, with the reason.**

- **A `Mods` enum.** Create names every integration in one enum with its load state. Gathering asks
  whether another mod is loaded in four places; an enum for four checks is ceremony.
- **Config comments and ranges.** Create's `ConfigBase` gives every value a range and a comment.
  Gathering's settings file already writes its own explanations and refuses a value that would not
  take (`ServerSettings.set`), so there is nothing to borrow.
- **Refusal sounds.** Create plays a quiet "declining boop" (a vanilla note-block bass) on 44
  refusals. Gathering's refusals are text only, and a seated player's board covers the chat - a sound
  would help there. Not done yet: a sound sent from the server would ignore Gathering's own volume
  preference, so it wants a client-side cue, which is a protocol change.
- **Placement helpers.** Create shows a ghost of where the next shaft or cogwheel will go and snaps
  it into line. Tables join only side to side in a line, and a table that will not fit says why only
  after it is refused. A ghost of where a held table would join is the most Create-like polish left,
  and a real piece of client work.
- **Wrench, goggles and hovering information.** Create's are interfaces a block entity implements.
  Gathering's blocks live in `:common`, which cannot name a Create class, so each would need a
  NeoForge-only subclass or a mixin. Not worth either.
- **CI and issue templates.** Create builds and runs its game tests on every push and pull request
  and asks bug and crash reports for versions and logs through issue forms; Aeronautics has the forms.
  Gathering has neither. Both touch the owner's GitHub account (Actions minutes, the public issue
  page), so they are the owner's call.
