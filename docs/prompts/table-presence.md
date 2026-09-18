# Table presence — the master prompt

A seated player's body says what they are doing. Their main hand follows their cursor across
the felt, their head follows it, and their other hand holds their cards at the table's edge,
fanned, backs out. Everyone standing around the table sees it. Nobody sitting at the table has
another player's body in the way of the board.

This file is the whole brief: what to build, what must not move, the files to write, what
"working" means for each one, the traps already found by reading, and how it gets verified.
Read `CLAUDE.md` and `DIALECT.md` first; this does not repeat them.

Everything here was written from the source as it stands on `claude/sweet-keller-ajtgmm`. Every
class named as existing was read. **Nothing in this file has been compiled or run** - it is a
plan and a skeleton, not a result.

---

## 1. What the player gets

**Watching from outside the table view** - standing beside the table, or sitting at it and
looking at another table:

- A seated player's main arm points at wherever that player's cursor is on the felt. It moves
  as their cursor moves, smoothly, and it never leaves their shoulder's reach.
- Their head turns and tips to look at the same place.
- Their off hand rests at the table's edge holding their hand of cards, fanned, **backs
  toward the room**.
- Whatever they were holding in their hands is not drawn while they are seated, because both
  hands are doing something else.

**Playing, in the table view** - the camera two blocks over the felt, looking down:

- Every seat's hand is drawn on the table below that seat's mat: your own faces up, everyone
  else's as backs.
- You do not see standing players at all, and you never see yourself.
- Other seated players are drawn, low and still, at the edges where they sit - enough to know
  someone is there, never enough to cover a card. **This one is the owner's call**: see §8.

## 2. What must not move

- **The visibility invariant.** The fan of cards in a player's off hand is drawn from a
  `ZoneView.count()` and a `Sleeve` and from nothing else. `VisibilityRules` already sends
  another seat's hand as `ZoneView.countOnly` - a number, with an empty card list
  (`VisibilityRules.java:149`). Build the renderer so it *cannot* reach a `CardView`: its input
  record carries an `int` and a `Sleeve`, and the class that builds that record is the only one
  that ever touches a `GameView`. A count is public information - the whole table watches you
  draw - and an identity never becomes one.
- **No rules enforcement.** Nothing here decides anything about a play. It is a body pose.
- **Textures are the owner's.** `tools/artcheck.py` holds the SHA-256 of all 2,152 assets. The
  fan draws `CardFaceRenderer.CARD_BACK` and the existing sleeve emblems. **Add no texture.**
- **`:core` sees no Minecraft.** The geometry goes in `:core` and is checked in milliseconds.
- **The pointer is not a control.** It never moves the real entity's rotation, never reaches the
  server as anything but a hint, and a player who lies about it can only make their own arm point
  somewhere silly. The server still drops a pointer from a player who is not seated at the table
  they name, because a payload nobody validates is a payload somebody will find a use for.

## 3. The shape

Six pieces, in the order they should be built. Each is finishable on its own.

### A. The geometry, in `:core`

| File | What it decides |
|---|---|
| `core/.../core/ui/TablePose.java` | Where an arm and a head point, given a target in the player's own frame, clamped to what a shoulder can do |
| `core/.../core/ui/HandFan.java` | How `n` cards sit in a held hand: per-card angle, slide and depth |

Both pure, both fully written in the skeleton beside this file, both with tests. `TablePose`
takes the target **already in the seated player's frame** - across, forward, down, in blocks -
so the world-to-seat rotation happens once, on the render side, and the tested part has no
compass in it.

The reach clamp is the requirement "do not remove their arm from their body", stated as
arithmetic: the target is pulled onto a sphere of `TablePose.ARM_REACH` about the shoulder
before any angle is taken. The jqwik property in `TablePoseTest` is exactly that sentence.

### B. The pointer on the wire

| File | What it does |
|---|---|
| `common/.../network/TablePointPayload.java` | Client to server: the table and the point on its felt, or that I have stopped pointing |
| `common/.../network/TablePointingPayload.java` | Server to nearby clients: this player is pointing there |
| `common/.../server/TablePointing.java` | Validates the seat, rate-limits, broadcasts, forgets |

- `GatheringProtocol.VERSION` goes to **28**, with its paragraph, because a client that sends a
  payload the server does not know is a client whose arm does nothing and whose player never
  finds out why.
- Register both in `GatheringProtocol.ROUTES` - `toServer(...)` for the first with
  `TablePointing::handle`, `toClient(...)` for the second. Neither loader gets a list of its own;
  that is what `GatheringProtocol` is for.
- Broadcast through `TableBroadcast.watchingNearby(level, tableOrigin)`, which already answers
  "everyone who can see this table". `Sending.to` is one player at a time; a loop over that list
  is the whole of it. Do not invent a second notion of nearby.
- **Cadence.** Send on the client tick, not on mouse movement, and only when the point has moved
  by more than a card's width or the pointing stopped. Four or five a second is plenty for
  something the eye reads as a moving arm, and the interpolation in `ClientTablePointing` is what
  makes it smooth rather than the packet rate.
- **Validation.** `TableBroadcast.seatOf(level, tableOrigin, player)` is the question the handler
  asks. Empty answer, drop the payload. Also drop one whose surface coordinates are off the
  cluster's own surface.

### C. What the client knows

| File | What it holds |
|---|---|
| `common/.../client/ClientTablePointing.java` | Per player: where they were pointing and where they are pointing, so a frame can sit between the two |
| `common/.../client/SeatedPlayers.java` | Per player: which table, which side of it, how many cards in hand, which sleeve |

`SeatedPlayers` invents nothing. It reads the boards this client already has in
`ClientTableState` - every table in sight sends its public view for the miniature - and the
cluster's own seats from `TableClusters.at(level, origin)`, whose `seats()` list is in the same
order as the board's, so seat index gives `SeatAnchor.side()`. Hand count comes off
`SeatView.zones().get(Zone.HAND).count()`. **No new payload for any of it.**

`ClientTablePointing` grows a `public static void clear()` and therefore **must be named in
`ClientState.forgetTheServer()`** or `tools/statecheck.py` fails the build. That check exists
because three holders once were not.

### D. The pose, decided once

`common/.../client/TableBodyPose.java` is the twin of `TableCameraView`: the two loaders' mixins
ask it and decide nothing themselves. It answers three questions:

- `poses(Player)` - is this player seated at a table with a pointer to follow?
- `aimOf(Player, float partialTick)` - the `TablePose.Aim` for this frame, interpolated.
- `hidesHeldItems(Player)` - are this player's hands busy?

It is also where the world-to-seat rotation happens: the pointed-at surface point through
`TableTop.inTheWorld`, then into the player's frame using the side they sit on.

### E. The two hooks

Both loaders, identical, deferring to `TableBodyPose`. This is the pattern `CameraMixin` and
`EntityRenderMixin` already set, and the reasons are the same: there is no event at the moment
that matters.

| Mixin | Where | Why not an event |
|---|---|---|
| `PlayerModelMixin` | `PlayerModel#setupAnim`, `@At("TAIL")` | `RenderPlayerEvent.Pre` fires before `setupAnim`, so anything it sets is overwritten a moment later. Nothing on either loader runs after the model has been posed and before it is drawn. |
| `ItemInHandLayerMixin` | `ItemInHandLayer#render`, `@At("HEAD")`, cancellable | Neither loader can drop one layer for one entity for one frame. `RenderHandEvent` is the first-person hand and is not this. |

Both `require = 0`, both with the `LoggerFactory` line in a static block, both named in **both**
`gathering.mixins.json` files under `client` - `tools/mixincheck.py` fails on a mixin no list
names, and it exists because a dropped name once silently disabled a whole gesture.

The fan of cards is **not** a mixin. It is a vanilla `RenderLayer`, so it lives in `:common`
next to `CardItemRenderer` for the same reason that one does - `BlockEntityWithoutLevelRenderer`
is a vanilla class and so is `RenderLayer`. Each loader registers it in its own client
bootstrap: NeoForge with `EntityRenderersEvent.AddLayers`, Fabric with
`LivingEntityFeatureRendererRegistrationCallback.EVENT` in
`net.fabricmc.fabric.api.client.rendering.v1`.

### F. The two views

- **`TableCameraView.hides`** currently returns true for every `Player` while the table camera is
  on (`TableCameraView.java:~470`). It becomes: always hide the viewer themselves; hide players
  who are not seated at the cluster being watched; keep the ones who are. A seated player's head
  sits about a third of a block above the felt and two blocks under the camera, so the ones kept
  are at the edges of the frame rather than over the board - but **look at the pictures before
  believing that sentence**, and if they are in the way, the setting in §8 is the answer.
- **Hands below the mat in the board view.** The seated screen draws your own hand across the
  bottom of the window. Each seat's hand should also appear on the felt below that seat's mat:
  backs for everyone else, from the same count, using `HandFan` so the two views fan a hand the
  same way. `TableCardRenderer` owns painting the board; that is where it goes.

## 4. Observable success, per piece

Written before implementing, as `CLAUDE.md` §2 asks. Each line: the player action, the visible
result, what may change, what must not, and what happens when it goes wrong.

**A. Geometry.** No player action. `:core:test` green, including a jqwik property that the posed
hand is never further from the shoulder than `ARM_REACH` for any target anywhere, and that a
target behind the player does not fold the arm backwards through the body.

**B. Pointer.** A seated player moves the mouse across the felt. On a second client standing
beside the table, that player's arm follows within about a fifth of a second. May change: one
entry in a client-side map. Must not change: the entity's real yaw and pitch, the board, the log,
anything saved. On refusal - not seated, coordinates off the surface - the payload is dropped and
the arm goes to rest; the player is told nothing, because nothing has gone wrong for them.
On disconnect the entry is dropped by `ClientState`; on the server, by the same lifecycle that
drops a seat. On restart there is no pointer until the player moves the mouse again, which is
correct: a pointer is not state.

**C. The fan.** A seated player draws a card. Every client watching sees one more card appear in
their off hand. Nobody sees a face. A player with an empty hand has an empty hand and no floating
sliver. A player with forty cards has a fan that is still a fan - `HandFan` caps the spread.

**D. Held items.** A player sits down holding a deck box. The box stops being drawn. They stand
up. It is drawn again. They sit down with nothing: nothing changes.

**E. The table view.** A player in the table view sees no standing spectators and not themselves,
whatever anybody is doing, however many people are around the table.

## 5. Traps found while reading

These are the ones that would cost an afternoon each. Add any new ones to `DIALECT.md`'s gotcha
list, which is what that list is for.

- **`PlayerModel#setupAnim` copies the arms onto the sleeves at its very end.** Injecting at
  `TAIL` and setting `rightArm.xRot` leaves `rightSleeve` in the vanilla pose - a jacket sleeve
  floating where the arm used to be. Re-copy the parts you moved (`rightSleeve.copyFrom(rightArm)`,
  and the same for the hat if the head moved) as the last thing the injection does.
- **One `PlayerModel` instance draws every player in the frame.** Never remember anything on the
  model between calls; read the entity handed to `setupAnim` and pose from that, every time.
- **The seated player's own first-person hand is already handled.** `TableCameraView` sets
  `options.hideGui`, which takes the crosshair and the held item together - its own comment says
  so, and the owner saw the arm before it did. Do not add a second mechanism.
- **A seated player is riding a `ChairSeat`**, so `HumanoidModel#setupAnim` has already put them
  in the riding pose with the arms forward and the legs out. The injection runs after that and
  overwrites what it needs; it must not assume the arms started at rest.
- **The model's `xRot`/`zRot` are radians, and the sign conventions for left and right arms are
  mirrored.** `TablePose` returns degrees with a stated convention; the mapping into model space
  belongs on the render side, and it is a thing to check in a running game rather than to reason
  about. Expect to flip a sign.
- **`TablePointer.at` is the seated screen's picker, and it is only valid inside a drawn frame** -
  it answers from matrices captured while the world was drawn. The sender reads the point the
  screen has already worked out; it does not run the picker from a tick.
- **`StreamCodec.composite` stops at six fields.** Both payloads here are well inside that, but
  if one grows, split the record rather than hand-rolling.
- **`TableClusters.at(...).seats()` is ordered**, and that order is the seat index in the board.
  That is the only thing that makes `SeatedPlayers` possible without a payload. It is also load
  -bearing enough to deserve a test: a cluster whose seats renumbered would put a player's cards
  in someone else's hand, visually.

## 6. The order to work in

Small batches, each finished before the next starts, as `CLAUDE.md` §2 requires.

1. `TablePose`, `HandFan`, and their tests. Nothing else. `:core:test` green.
2. The two payloads, the protocol bump, the server handler, the client holder,
   `ClientState.forgetTheServer`. A game test in
   `neoforge/src/gametest/.../server/` that seats two players, sends a pointer from one, and
   asserts the other's client would have been sent it - and asserts that a pointer from a player
   who is *not* seated is dropped. **Prove the second test fails without the validation.**
3. `SeatedPlayers`, `TableBodyPose`, and the `PlayerModelMixin` twins. Both mixin lists.
   `tools/gate.sh --quick`, then look at a running game.
4. `HandOfCardsLayer`, the back-only entry point on `CardFaceRenderer`, and both loaders'
   registration. The `ItemInHandLayerMixin` twins.
5. `TableCameraView.hides`, and hands below each mat in `TableCardRenderer`.
6. A `DevScene` step that seats a player, points at a named spot on the felt, photographs the
   table from outside it, and **asserts** the arm angle the pose class produced - a step that only
   photographs is weaker than one that photographs and asserts.

## 7. Verification

Nothing is done until `tools/gate.sh` exits zero. In particular this change touches:

- `:core:test` - the new geometry, and `tools/coretestcheck.py` will want the new test class to
  have actually run.
- `runGameTestServer` on both loaders - the pointer validation.
- `tools/mixincheck.py` - four new mixin names across two lists.
- `tools/statecheck.py` - `ClientTablePointing.clear` named in `ClientState`.
- `tools/prefcheck.py` - **only if** you add the setting in §8, and then production code must
  actually read it. A settings row that changes nothing is the exact defect that check exists for.
- `tools/artcheck.py` - unchanged, and it must stay unchanged. If it fails, a texture moved.

Then `tools/shots.sh`, and **look at the pictures**. The things a screenshot can settle here:
whether the arm reads as pointing rather than as broken, whether the fan reads as cards, whether
a kept seated player covers a card. The things it cannot: any of §4's interactions, whether the
pointer survives a reconnect, whether two players see each other correctly. Those want two
clients and a person, and if that run does not happen, say so in `docs/working-record.md` in
those words.

## 8. The owner's calls

Do not decide these; ask.

1. **Are other seated players drawn in the table view at all?** The brief says "maybe static
   versions". The honest options are: all seated players kept (risk: a head over a card at a
   crowded pod), none kept (safe, and the table feels empty), or a setting defaulting to kept. A
   setting costs a `ClientSettings` row, a lang key, and a `prefcheck` obligation.
2. **Does the arm point during another player's turn?** It follows the cursor whenever there is
   one, which means a player reading the board is visibly waving at it. That may be exactly right
   - it is what people do - or it may be noise.
3. **Does a player who is seated but has closed the table screen still point?** The proposal:
   no. No screen, no cursor, arm at rest.

## 9. The skeleton

Beside this file, under `docs/prompts/table-presence/`, in a tree that mirrors where each file
goes. `TablePose`, `HandFan` and `TablePoseTest` are written out in full - they are pure and
there is no reason to leave them as an exercise. Everything else carries its real signatures, the
reasoning, and a body that throws, because `// TODO` that compiles is banned here.

**None of it has been compiled.** It was written against the source, not against a build.
