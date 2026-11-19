<!-- Written by tools/vault_sync.py on every sync (2026-11-19 21:55). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**2 problems.**

## Project memory

- The memory index is 56 KB; only the first 16 KB load in a new thread. Shorten entries to one line and move detail into topic files.
- 5 index lines are over 300 characters (lines 2, 54, 159, 211, 220): move their detail into topic files.
