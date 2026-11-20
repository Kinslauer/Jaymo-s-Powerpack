<!-- Written by tools/vault_sync.py on every sync (2026-11-20 07:36). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**4 problems.**

## Statuses the pipeline doesn't know

- [[Inbox/zfighter-gaps-10-camera-hud|zfighter-gaps-10-camera-hud]]: `status: running` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)

## Stalled agent notes

- [[Agents/hands-on-cooking-realtime|hands-on-cooking-realtime]]: stalled, last change 2026-11-20. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/medieval-patch10-temple-patrons|medieval-patch10-temple-patrons]]: stalled, last change 2026-11-20. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/zfighter-gaps-1-stats|zfighter-gaps-1-stats]]: stalled, last change 2026-11-20. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
