<!-- Written by tools/vault_sync.py on every sync (2026-11-23 17:16). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**7 problems.**

## Statuses the pipeline doesn't know

- [[Agents/corpse-limbs-drag-arm-digger-pits|corpse-limbs-drag-arm-digger-pits]]: `status: stopped` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Missing PR numbers

- [[Agents/orc-sees-stance-only-facing|orc-sees-stance-only-facing]]: in review with no `pr:`.

## Questions waiting on Andy

- [[Agents/wither-model|wither-model]]: blocked since 2026-11-19.
- [[Inbox/class-trees-plan|class-trees-plan]]: blocked since 2026-11-19: 2026-11-19 20:22 Parked (not for a bot): waits on Andy fleshing out the Monk/Paladin/Cleric/Druid/Warlock trees. Raised; don't raise again.
- [[Inbox/narrator-missing-lines|narrator-missing-lines]]: blocked since 2026-11-19: 2026-11-19 20:18 ON HOLD (Andy 2026-11-19: "hold off on these"). Nobody picks this up and nobody asks Andy about it again until he lifts the hold.
- [[Inbox/underground-stoneborn-goblin-villages|underground-stoneborn-goblin-villages]]: blocked since 2026-11-19: 2026-11-19 20:18 ON HOLD (Andy 2026-11-19: "hold off on these"). Nobody picks this up and nobody asks Andy about it again until he lifts the hold.

## Reviews sitting too long

- [[Inbox/ai-texture-pipeline|ai-texture-pipeline]]: in review since 2026-11-21 (PR #research).
