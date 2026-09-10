# Quality rollout

Six player-experience improvements, delivered in phases. What each phase actually changed,
what was tested, and what is still unverified. `docs/quality-backlog.csv` carries the tickets;
`docs/quality-baseline.md` carries the numbers this is measured against.

The rule for this document: a phase is written up after its gate is green, and an unverified
check is named rather than left out. A scripted client run is evidence that screens work, not
evidence that a person can use them.

## Phase 0 — baseline and readiness (Q00, Q01)

**Done.**

The guide was prepared against `3ff8f34`; the checkout is `16b2573`, two commits later. Both
of those commits are the second external review's fixes and are kept: the ownership, reward,
queue-accounting, throttle and lifecycle prerequisites the guide asks about are the V-series,
and they are closed — see `docs/audit-external-2026-09.md`. Concretely, of the prerequisites
the guide names:

| Prerequisite the guide names | State |
|---|---|
| Tutorial must not rest on unsafe loaner/delayed ownership | V-01 closed: deck placement carries an explicit origin |
| Bulk actions must not create unbounded work | V-03 closed: throttles drain from a real tick hook |
| Rewards must not build on duplicate-prone recovery | V-02 closed: settle before handing over, strike off before paying |
| Pending UI must match exact operation IDs | V-08 closed for imports and builds; Q05 generalizes it |

Every source file the guide's map names exists at the path it names. Nothing had to be
reconciled.

**Artwork.** `tools/artcheck.py` and `docs/art-hashes.txt` now hold the SHA-256 of all 2,152
textures, sprite sheets, `.mcmeta` files and sounds. The check runs in the gate. No phase of
this project may change one, and this is how that is shown rather than claimed.

**Measured:** gesture counts for the five baseline tasks, read off the handlers. **Unmeasured
and stated as such:** every human observation, and every frame-time number — this is a headless
container.

## Phase 1 — shared action and preference foundation (Q02-Q05)

Not started.

## Phase 2 — guided first game (Q06-Q08)

Not started.

## Phase 3 — frequent actions and discovery (Q09-Q12)

Not started.

## Phase 4 — crowded Commander boards (Q13-Q16)

Not started.

## Phase 5 — collection progression (Q17-Q19)

Not started.

## Phase 6 — modpack integration (Q20-Q24)

Not started.

## Phase 7 — feedback, accessibility and acceptance (Q25-Q27)

Not started.
