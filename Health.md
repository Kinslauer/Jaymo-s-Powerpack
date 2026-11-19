<!-- Written by tools/vault_sync.py on every sync (2026-11-19 16:19). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**2 problems.**

## Statuses the pipeline doesn't know

- [[Agents/harness-upkeep-pc|harness-upkeep-pc]]: `status: (none)` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Stalled agent notes

- [[Agents/underground-stoneborn-goblin-villages|underground-stoneborn-goblin-villages]]: stalled, last change 2026-11-19. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
