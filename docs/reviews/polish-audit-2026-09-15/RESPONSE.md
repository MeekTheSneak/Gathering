# Response to the polish audit of 15 September 2026

The audit reviewed `4de7a183`. This covers its first slice, PQ-01 to PQ-04, done together as its
guide recommends. PQ-05 to PQ-10 are open and listed at the end.

The audit's own probe was not copied in; as its guide says, it logs and photographs the defects
and completes whatever it sees. Its observations were turned into assertions in a new scripted
client, `neoforge/src/gametest/java/dev/gathering/client/AccessibilityProbe.java`, run with
`tools/quietly.sh neoforge/run ./gradlew :neoforge:runClient -Paccessibilityprobe`. It opens the
production SettingsScreen and EventScreen, fed by real `Tournament` values rather than a hand-made
view, and fails on what the audit reported. With every fix below in place it reports
`[accessibility] failures: 0`. Each guard was shown to fail with its fix removed (two runs: 19 failures,
then 31, and a third for the report-row spacing).

| ID | Resolution |
|---|---|
| PQ-01 | `core/.../ui/EventScreenLayout` computes the screen's geometry from the window, control size and text size, the way `SettingsLayout` does for settings: row and tab heights through `InterfaceScale.rowHeightFor`, gaps with the control size, a panel that grows with either and is capped by the window. Every EventScreen widget is placed from it. Probe: result buttons 18 tall at 100% and 36 at 200%, tabs taller at 200%. Where a small window cannot hold everything, results and settle options are paged rather than shrunk: the overview offers "Report result", which opens the results a page at a time, and the probe reaches all 16 results through the page arrows at GUI scale 4 (427 x 240) with both sizes at 200%. Unit tests: `EventScreenLayoutTest` (5). |
| PQ-02 | Lines advance by a height measured from the text size, and nothing the screen draws is placed by a fixed offset. Buttons in a row now share one label size (`GatheringButtons.matchLabels`): the audit's "Standings" squeezed beside a full-size "Overview" was each label fitting itself. The title moved down by half its growth, because enlarged text is drawn centered on its line and its top sat on the panel edge at 200%. Host help is wrapped to the panel. Probe: every widget inside the panel and window, none overlapping, none shorter than its row, every row's labels at one size, and the result buttons below the line above them, at 100/100, 200 controls, 200 text, both, and the small window. |
| PQ-03 | `client/FocusKeeper` names each control for what it does ("tab:OVERVIEW", "report:2-1", "host:BEGIN"). After a rebuild, focus returns to the control with the same name, or to the screen's named fallback, which never changes anything; it is never restored by position. SettingsScreen and EventScreen both use it. Probe: focus stays on a setting after pressing it by keyboard; focus on Begin survives an identical snapshot; when a confirmed match removes the focused result button, focus goes to the Overview tab. |
| PQ-04 | `core/.../tournament/HostActions` is the one rule for when each host action applies: open check-in, begin (including too few players), start now (including packs still out), add tables, add a prize, mark registration, call off. The server refuses through it before acting (`Events.refused`), and sends the host's screen the same answers in `EventViewPayload.hostRefusals` (protocol 13). The screen grays a control that does not apply and gives the reason as its tooltip. Help text is written per phase. Calling an event off now asks first and says what happens. The rule is used only for presentation on the client; the server checks again. Core: `HostActionsTest` (6). Probe: during Swiss, Open check-in, Begin, Start now and Register here are grayed with reasons while Add this table and Call off are live; during signup they are live; Call off opens a confirmation. In-world: `ahostsStaleOrForgedControlsAreRefused` sends Begin, Open check-in, Start now and Mark registration during Swiss through the real action entry point and finds the event unchanged. A non-host's Call off and a one-player Begin are refused too. Removing the server's refusal fails it: marking a registration point during play was the one host action the server previously let through. |

One existing test changed its fixture: `aneventSurvivesItsSave` marked a registration point during
Swiss only to have one to save, which the phase rule now refuses. It saves the point from an event
that is still signing up, and still checks that the Swiss event comes back unchanged.

## Also, at the owner's request

- **Every screen at every GUI scale.** The scripted tour already checked each photographed screen
  for controls off-screen or on top of each other. It now also re-lays each screen it photographs
  at every GUI scale the window allows, from 1 to the largest, checks it the same way, and restores
  the original scale. This is geometry only: text cut short at one scale is found in the pictures,
  not by this check. The table's own board is left out, because its camera framing moves when it is
  re-laid; its resizes have their own steps.
- **The host tab in a small window.** Found by the tour at 200/200 in GUI scale 4: its fixed three
  columns cut "Prize: place N" short. It now uses as many columns as its longest label allows and
  pages beside Done. The probe checks all seven host controls are reachable at 427x240 and fails on
  any of the mod's own text being cut short.
- **Turn sounds.** The owner's two recordings play when the turn passes and when it comes to you.
- **Scripted clients stay out of the way.** `tools/quietly.sh` mutes the run's master volume before
  the game reads its options, restores it afterwards, and on a Mac returns focus to the app that was
  in front whenever the game window comes forward.

## Verification

- `tools/gate.sh`: green, 525 NeoForge / 16 Fabric in-world tests.
- Accessibility probe: `failures: 0`, screenshots `access-*.png` looked at.
- Scripted tour: `failures: 0`, 344 of 344 steps, with 27 screens also laid out at GUI scales 1 to 4.

## Open

- PQ-05, the outcome of an event action (pending, refused, done) shown on the screen instead of in
  chat. Needs a protocol change.
- PQ-09, CI and issue forms. These go on the owner's GitHub account, so they wait for the owner.
- PQ-07, the compatibility matrix and qualification.
- PQ-06, a placement preview for tables.
- PQ-08, one complete contextual lesson.
- PQ-10, profiling before optimizing.

Not verified: a person using the screens by keyboard, a controller, other languages' longer
labels, and the event screen on Fabric in a real client.
