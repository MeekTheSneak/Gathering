# Table presentation refactor

Based on `690753b8cf51bbcad31eb50819b2eaabff1f75eb`. This work builds on the completed
batching, board-presentation, collection-search and protocol changes at that revision.

## Boundaries

`TableScreen` continues to choose cards, positions, sleeves, selections, menus and actions.
`TableCardRenderer` paints the filtered card it receives. Its metadata lookup remains live,
so downloaded summaries and art can appear without waiting for a new board. The caller
passes the table position on each draw, including the separate tutorial and replay positions.

`TableReplayControls` owns transport clicks, fixed playback keys, dragging and painting.
It uses the existing pure `ReplayStrip` for both painting and hit testing. The screen still
handles camera panning, help/log panels and closing. `ClientReplay` still owns requests,
playback timing, pending responses and cancellation. No payload or persistence format changed.

## Counter preparation

`CardCounterLabels` prepares components once for each immutable `CardView` identity.
Using the instance id would be incorrect: the same physical card can receive different
counters or written strength in its next view. Written strength affects whether loyalty is
shown in the corner or in the counter stack. Anonymous cards work without an instance id.

Each screen retains at most 512 prepared views in a FIFO ring. Empty-counter cards bypass
it. Cards with more than 64 counter types or 4,096 total counter-name characters are prepared
without being cached, so unusual custom data cannot retain a history of oversized labels.
This limit does not remove or truncate any counters. Old replay frames are evicted; the
whole cache becomes collectible with its screen.
The renderer also reuses its two scratch row lists. These objects are confined to the client
thread. Component objects returned by the cache are for reading, not restyling in place.

Font widths, wrapping, scale and projected geometry are measured on every draw. Neither
resource-pack font changes nor zoom rely on invalidating a remembered width. Prepared text
contains only literal labels derived from the filtered view; it does not query hidden state.

This is a bounded steady-frame optimization. More than 512 distinct marked views between
reuses can cause misses, and a newly received board can replace every view. The allocation
probe deliberately measures both a warm cache and continuous eviction. It measures text
preparation only, not font work, painting, network cost or overall FPS.

## Verification

Run the canonical `tools/gate.sh`. Nine new NeoForge in-world tests exercise reuse, changed
counters, written strength/loyalty, ordering, anonymous cards, eviction, empty-card bypass
and oversized-counter bypass.

For a graphical characterization, run:

```sh
./gradlew :neoforge:runClient -Pdevscene -PscreenRefactorScene=refactored
```

The opt-in probe lives in the existing game-test source set and is excluded from release jars.
It invokes the screen's real card path at two sizes, then drives a real replay screen with
local fixture responses. It checks keys, clicking, dragging past the bar, releasing, and
closing help before closing the screen. Screenshots go to the run directory's `screenshots`
folder; the `refactored` label also prints allocation measurements. A request spy intercepts
fixture replay requests and is restored afterward. This probe exits the client when finished.

Using `-PscreenRefactorScene=observe` leaves the normal tour in control and logs text-prompt
focus and contents when prompts open and close, without providing input.

The full `-Pdevscene` tour is a separate integration check. The focused probe does not prove
that the entire tour, multiplayer or a third-party modpack works. Keep failures in that tour
visible even when the canonical gate and focused checks pass.

## Next boundaries to consider

The table screen remains large. Its action/menu construction and held-card gestures are the
next substantial candidates, but both carry selection and modal lifecycle state. Extract
one complete flow at a time, characterize its real input first, and preserve the latest
server batching and tutorial routing. Moving methods alone does not establish an optimization.
Do not merge older cleanup patches over this baseline or equate line count with runtime cost.
