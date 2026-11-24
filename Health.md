<!-- Written by tools/vault_sync.py on every sync (2026-11-24 13:15). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**12 problems.**

## Statuses the pipeline doesn't know

- [[Agents/corpse-limbs-drag-arm-digger-pits|corpse-limbs-drag-arm-digger-pits]]: `status: stopped` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Stalled agent notes

- [[Agents/changeling-voice-mimic|changeling-voice-mimic]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/otherworld-fabric-picklist|otherworld-fabric-picklist]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/quill|quill]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.

## Questions waiting on Andy

- [[Agents/wither-model|wither-model]]: blocked since 2026-11-19.
- [[Inbox/narrator-missing-lines|narrator-missing-lines]]: blocked since 2026-11-19: 2026-11-19 20:18 ON HOLD (Andy 2026-11-19: "hold off on these"). Nobody picks this up and nobody asks Andy about it again until he lifts the hold.
- [[Inbox/underground-stoneborn-goblin-villages|underground-stoneborn-goblin-villages]]: blocked since 2026-11-19: 2026-11-19 20:18 ON HOLD (Andy 2026-11-19: "hold off on these"). Nobody picks this up and nobody asks Andy about it again until he lifts the hold.
- [[Inbox/wither-model|wither-model]]: blocked since 2026-11-21: 2026-11-21 10:18 Andy hold instruction (2026-11-19 23:40 ET): leave the model as-is (v3 previews); do not pick up or raise this again until Andy hands it on. N…

## Tasks for nobody

- [[Inbox/alexs-caves-integration|alexs-caves-integration]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/bleed-out-coma|bleed-out-coma]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/reference-mods-picklist-2026-11-24|reference-mods-picklist-2026-11-24]]: `for: any (needs Andy's PC: the folder is local)` matches no bot or agent, so no one will claim it.
- [[Inbox/structure-spawn-rate-wooden-hut|structure-spawn-rate-wooden-hut]]: `for: coordinator` matches no bot or agent, so no one will claim it.
