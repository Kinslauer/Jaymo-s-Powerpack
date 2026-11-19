<!-- Written by tools/vault_sync.py on every sync (2026-11-19 23:36). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**3 problems.**

## Stalled agent notes

- [[Agents/wither-model|wither-model]]: stalled, last change 2026-11-19. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.

## Project memory

- The memory index is 57 KB; only the first 16 KB load in a new thread. Shorten entries to one line and move detail into topic files.
- 5 index lines are over 300 characters (lines 2, 54, 159, 211, 220): move their detail into topic files.
