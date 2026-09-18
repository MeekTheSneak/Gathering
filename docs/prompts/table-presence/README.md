# Skeleton — where each file goes

Written against the source, **never compiled**. The tree mirrors the destination, so each file's
path below its own module directory is where it belongs in the repository.

| Skeleton file | Goes to | State |
|---|---|---|
| `core/.../core/ui/TablePose.java` | same path in the repo | **written in full** |
| `core/.../core/ui/HandFan.java` | same path | **written in full** |
| `core/src/test/.../TablePoseTest.java` | same path | **written in full** |
| `common/.../network/TablePointPayload.java` | same path | written; needs its route in `GatheringProtocol` |
| `common/.../network/TablePointingPayload.java` | same path | written; needs its route in `GatheringProtocol` |
| `common/.../server/TablePointing.java` | same path | signatures and reasoning; bodies throw |
| `common/.../client/ClientTablePointing.java` | same path | signatures; **must be named in `ClientState`** |
| `common/.../client/SeatedPlayers.java` | same path | signatures |
| `common/.../client/TableBodyPose.java` | same path | signatures |
| `common/.../client/TablePointSender.java` | same path | signatures; called from `ClientTicks` |
| `common/.../client/HandOfCardsLayer.java` | same path | signatures; registered by each loader |
| `neoforge/.../mixin/PlayerModelMixin.java` | same path, **and a twin under `fabric/.../mixin/`** | signatures; **name both in both `gathering.mixins.json`** |
| `neoforge/.../mixin/ItemInHandLayerMixin.java` | same path, **and a twin** | written; **name both** |

Not skeletoned, because they are edits to files that already exist:

- `GatheringProtocol` — `VERSION` to 28, with its paragraph, and two routes.
- `ClientState.forgetTheServer` — `ClientTablePointing.clear()`.
- `ClientTicks.tick` — `TablePointSender.tick(client)`.
- `CardFaceRenderer` — a back-only entry point taking a `Sleeve`, never an `ItemStack`.
- `TableCameraView.hides` — keep the seated, hide the standing, always hide the viewer.
- `TableCardRenderer` — each seat's hand below its mat, through `HandFan`.
- Each loader's client bootstrap — the render layer.
- `DIALECT.md` — the gotchas in §5 of the brief.
- `docs/working-record.md` — what is true after each batch, and what was not run.

An unwritten body throws rather than returning a plausible value, because `// TODO` that compiles
is banned here and a stub that quietly returns zero is a feature that looks finished.
