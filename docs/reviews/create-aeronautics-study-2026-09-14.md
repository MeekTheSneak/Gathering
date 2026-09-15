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
57 across its modules), shown by holding W over an item. With Create installed, Gathering could
register scenes for the table and the desk - the most Create-native way to teach them. It needs
schematic files built for each scene and a scene script per block; worthwhile, and larger than
anything above.

**7. Mechanical conventions.** Create has an `.editorconfig` (spaces, LF, final newline; JSON at 2);
Gathering has none. Create imports what it names; Gathering's main sources name about 620 classes
fully qualified inline, many in the largest files. Changing that across the tree would be a large
diff of no behavior, against the repository's own "reads like the surrounding code" rule; new code
imports where its neighbors do.

**8. Things Gathering already does as well or better.** Service-loaded platform seams (Aeronautics'
`service` package; Gathering's `Platform`, `WorldSpace`). Registries in one place (Create's `All*`,
Aeronautics' `index`; Gathering's `GatheringContent`). In-world tests grouped by concern with a
regressions class (Create's `TestRegressions`; Gathering's review regression tests, far more of
them). Every sound with a subtitle (both). Gathering's gate - static checks for language, docs,
sprites, textures and a proof that tests were discovered - has no counterpart in either repository.
