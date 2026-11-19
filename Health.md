<!-- Written by tools/vault_sync.py on every sync (2026-11-19 20:26). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**4 problems.**

## Statuses the pipeline doesn't know

- [[Agents/harness-body-pieces|harness-body-pieces]]: `status: (none)` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Tasks for nobody

- [[Inbox/harness-body-pieces|harness-body-pieces]]: `for: pc-claude` matches no bot or agent, so no one will claim it.

## Project memory

- The memory index is 56 KB; only the first 16 KB load in a new thread. Shorten entries to one line and move detail into topic files.
- 5 index lines are over 300 characters (lines 2, 54, 159, 211, 220): move their detail into topic files.
