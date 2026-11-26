<!-- Written by tools/vault_sync.py on every sync (2026-11-26 01:31). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**17 problems.**

## Statuses the pipeline doesn't know

- [[Agents/corpse-limbs-drag-arm-digger-pits|corpse-limbs-drag-arm-digger-pits]]: `status: stopped` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Stalled agent notes

- [[Agents/changeling-voice-mimic|changeling-voice-mimic]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/climate-picks|climate-picks]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/codex-downed-harness-loot|codex-downed-harness-loot]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/codex-reference-dinos-venom|codex-reference-dinos-venom]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/otherworld-fabric-picklist|otherworld-fabric-picklist]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/quill|quill]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/symbiote-113-picks|symbiote-113-picks]]: stalled, last change 2026-11-25. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.

## Missing PR numbers

- [[Agents/hollow-captains|hollow-captains]]: in review with no `pr:`.

## Agent notes whose task is done

- [[Agents/wither-model|wither-model]]: still `blocked`, but its task `wither-model` is in Inbox/Done/.

## Questions waiting on Andy

- [[Agents/wither-model|wither-model]]: blocked since 2026-11-19.
- [[Inbox/narrator-missing-lines|narrator-missing-lines]]: blocked since 2026-11-19: 2026-11-19 20:18 ON HOLD (Andy 2026-11-19: "hold off on these"). Nobody picks this up and nobody asks Andy about it again until he lifts the hold.
- [[Inbox/underground-stoneborn-goblin-villages|underground-stoneborn-goblin-villages]]: blocked since 2026-11-19: 2026-11-19 20:18 ON HOLD (Andy 2026-11-19: "hold off on these"). Nobody picks this up and nobody asks Andy about it again until he lifts the hold.

## Tasks for nobody

- [[Inbox/bleed-out-coma|bleed-out-coma]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/reference-mods-picklist-2026-11-24|reference-mods-picklist-2026-11-24]]: `for: any (needs Andy's PC: the folder is local)` matches no bot or agent, so no one will claim it.
- [[Inbox/structure-spawn-rate-wooden-hut|structure-spawn-rate-wooden-hut]]: `for: coordinator` matches no bot or agent, so no one will claim it.

## Project memory

- The memory index is 17 KB; only the first 16 KB load in a new thread. Shorten entries to one line and move detail into topic files.
