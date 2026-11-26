<!-- Written by tools/vault_sync.py on every sync (2026-11-26 19:34). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**17 problems.**

## Statuses the pipeline doesn't know

- [[Inbox/structures-3-aqueduct-curse-design|structures-3-aqueduct-curse-design]]: `status: (none)` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Agents/corpse-limbs-drag-arm-digger-pits|corpse-limbs-drag-arm-digger-pits]]: `status: stopped` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Stalled agent notes

- [[Agents/changeling-voice-mimic|changeling-voice-mimic]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/codex-bleed-out-coma|codex-bleed-out-coma]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/court-wizard-creeper-ward|court-wizard-creeper-ward]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/hollow-captains-fix|hollow-captains-fix]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/quill|quill]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/structures-1-siege-camps|structures-1-siege-camps]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/structures-6-stable-mine-farm|structures-6-stable-mine-farm]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.

## Missing PR numbers

- [[Agents/hollow-captains|hollow-captains]]: in review with no `pr:`.

## Tasks for nobody

- [[Inbox/bleed-out-coma|bleed-out-coma]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/bleed-out-npc-items|bleed-out-npc-items]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/legends-p6-core-animations|legends-p6-core-animations]]: `for: codex` matches no bot or agent, so no one will claim it.
- [[Inbox/reference-mods-picklist-2026-11-24|reference-mods-picklist-2026-11-24]]: `for: any (needs Andy's PC: the folder is local)` matches no bot or agent, so no one will claim it.
- [[Inbox/structure-spawn-rate-wooden-hut|structure-spawn-rate-wooden-hut]]: `for: coordinator` matches no bot or agent, so no one will claim it.

## Project memory

- The memory index is 62 KB; only the first 16 KB load in a new thread. Shorten entries to one line and move detail into topic files.
- 5 index lines are over 300 characters (lines 2, 54, 159, 211, 220): move their detail into topic files.
