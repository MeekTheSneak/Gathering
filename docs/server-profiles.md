# Settings profiles

Twenty-four settings, and most operators want one of three things. Setting them one at a time
to get there is how a server ends up almost being what somebody meant and confusing in one
particular corner.

## The three

| Profile | For |
|---|---|
| `casual` | Cards turn up as you play. Cheap packs, a shop that rotates often, loot from recent sets. |
| `long_run` | A collection as a long project. Rarer drops, a dearer shop that rotates slowly, the whole back catalogue in the pool. |
| `imported` | People bring decks they already have. Importing open to everyone, collecting out of the way. |

## Using them

```
/gathering profile                  # what there is
/gathering profile casual           # what it would change, before it changes anything
/gathering profile casual apply     # change it
/gathering profile restore          # put it back
```

`/gathering profile <name>` lists **every** setting the profile is about, not only the ones
that would move — so what you read is the whole of what the profile means rather than the part
that happens not to match today. Settings that would change are shown as `from -> to`; the rest
are marked `(already)`.

## What applying actually does

Each setting goes through the same path `/gathering config <setting> <value>` uses. That
matters: it re-reads the whole file after each edit and refuses a value the config would
silently ignore, so a profile cannot leave the file saying one thing and the server doing
another. A setting that is refused is reported and the rest still apply — stopping halfway
would leave exactly the confusing half-state this is meant to avoid, and you can restore.

**A profile is a starting point, not a mode.** Applying one writes ordinary settings and then
stops existing. Nothing is stored, nothing overrides a later edit, and no profile is ever "on".
Apply `casual` and then change two things and you have a server with those two things changed —
the only behavior that does not surprise somebody a month later.

## What it does not do

**Nothing anybody owns is touched.** These are settings about what happens next: what drops,
what a shop sells, what a pack costs. No collection, inventory, deck or table is read or
written. Applying a profile to a running world leaves every card exactly where it was, and
turning collecting off does not take anything away from anyone who already has it.

## Restoring

`/gathering profile restore` puts back **the whole file** as it was immediately before the last
apply — including comments, formatting, and keys no profile mentions. It is checked for
readability before it is written, because it goes over the one file a server needs in order to
start.

The restore point lives for as long as the server runs and no longer. It is an "undo that" for
somebody who has just tried a profile, not a promise to put back settings that may have been
edited on purpose a week later.

## Adding one

A profile is a list of `key = value` pairs in `ConfigProfile`. Two tests hold it to account:
one that every key it names is a real config key, and one that every value it sets is a value
the config will actually take — the second of which caught a `loot_sets` written as a word when
it wanted a list, which would have applied cleanly and done nothing.
