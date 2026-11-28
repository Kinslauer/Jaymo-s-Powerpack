<!-- Written by tools/vault_sync.py on every sync (2026-11-28 14:35). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**42 problems.**

## Statuses the pipeline doesn't know

- [[Inbox/court-wizard-creeper-ward|court-wizard-creeper-ward]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/dragged-bodies|dragged-bodies]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/hollow-captains-fix|hollow-captains-fix]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/otherworld-fabric-picklist-restart|otherworld-fabric-picklist-restart]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/realistic-pathable-archery-positions|realistic-pathable-archery-positions]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/small-armies-picks|small-armies-picks]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/structures-1-siege-camps|structures-1-siege-camps]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/structures-3-aqueduct-curse-design|structures-3-aqueduct-curse-design]]: `status: (none)` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/structures-3-aqueduct-curse|structures-3-aqueduct-curse]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/structures-5-faction-table|structures-5-faction-table]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/structures-6-stable-mine-farm|structures-6-stable-mine-farm]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/wrist-table-ui-mockup|wrist-table-ui-mockup]]: `status: cancelled` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Agents/corpse-limbs-drag-arm-digger-pits|corpse-limbs-drag-arm-digger-pits]]: `status: cancelled` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Stalled agent notes

- [[Agents/blood-awakening-per-blood-patch-1|blood-awakening-per-blood-patch-1]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/changeling-voice-mimic|changeling-voice-mimic]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/codex-bleed-out-coma|codex-bleed-out-coma]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/court-wizard-creeper-ward|court-wizard-creeper-ward]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/effekseer-hd|effekseer-hd]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/quill|quill]]: stalled, last change 2026-11-24. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/structures-1-siege-camps|structures-1-siege-camps]]: stalled, last change 2026-11-26. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.

## Agent notes whose task is done

- [[Agents/codex|codex]]: still `stalled`, but its task `legends-p6-core-animations` is in Inbox/Done/.
- [[Agents/hunters-road-3-models|hunters-road-3-models]]: still `stalled`, but its task `hunters-road-3-models` is in Inbox/Done/.
- [[Agents/legends-p3|legends-p3]]: still `stalled`, but its task `legends-p3-big-symbiote` is in Inbox/Done/.

## Missing PR numbers

- [[Agents/hollow-captains|hollow-captains]]: in review with no `pr:`.
- [[Agents/otherworld-fabric-picklist-restart|otherworld-fabric-picklist-restart]]: in review with no `pr:`.
- [[Inbox/block-choice-box|block-choice-box]]: `in-review` with no `pr:`.

## Tasks for nobody

- [[Inbox/bleed-out-coma|bleed-out-coma]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/bleed-out-npc-items|bleed-out-npc-items]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/blood-awakening-per-blood-build|blood-awakening-per-blood-build]]: `for: gemini` matches no bot or agent, so no one will claim it.
- [[Inbox/changeling-voice-mimic|changeling-voice-mimic]]: `for: codex` matches no bot or agent, so no one will claim it.
- [[Inbox/gore-1-no-arms-no-attack|gore-1-no-arms-no-attack]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/gore-2-cut-faces|gore-2-cut-faces]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/gore-3-cauterized|gore-3-cauterized]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/gore-4-head-to-toe-split|gore-4-head-to-toe-split]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/gore-5-living-guts|gore-5-living-guts]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/gore-6-ash-unmaking|gore-6-ash-unmaking]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/hunters-road-5-models|hunters-road-5-models]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/hunters-road-8-models|hunters-road-8-models]]: `for: antigravity` matches no bot or agent, so no one will claim it.
- [[Inbox/reference-mods-picklist-2026-11-24|reference-mods-picklist-2026-11-24]]: `for: any (needs Andy's PC: the folder is local)` matches no bot or agent, so no one will claim it.
- [[Inbox/structure-spawn-rate-wooden-hut|structure-spawn-rate-wooden-hut]]: `for: coordinator` matches no bot or agent, so no one will claim it.
- [[Inbox/structures-4-mohknee|structures-4-mohknee]]: `for: gemini` matches no bot or agent, so no one will claim it.
- [[Inbox/underground-stoneborn-goblin-villages|underground-stoneborn-goblin-villages]]: `for: codex` matches no bot or agent, so no one will claim it.
