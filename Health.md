<!-- Written by tools/vault_sync.py on every sync (2026-11-19 07:29). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**24 problems.**

## Statuses the pipeline doesn't know

- [[Inbox/class-trees-plan|class-trees-plan]]: `status: todo` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/harness-upkeep|harness-upkeep]]: `status: running` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/medieval-patch10-temple-patrons|medieval-patch10-temple-patrons]]: `status: todo` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Agents/hands-on-cooking-realtime-SPEC|hands-on-cooking-realtime-SPEC]]: `status: ready-for-implementation` (known: blocked, done, in-review, merged, ready, running, sent, stalled)
- [[Agents/medieval-deco-5-farm-water|medieval-deco-5-farm-water]]: `status: working` (known: blocked, done, in-review, merged, ready, running, sent, stalled)
- [[Agents/story-x-mark|story-x-mark]]: `status: (none)` (known: blocked, done, in-review, merged, ready, running, sent, stalled)
- [[Agents/zfighter-aura-rework|zfighter-aura-rework]]: `status: in-progress` (known: blocked, done, in-review, merged, ready, running, sent, stalled)

## Stalled agent notes

- [[Agents/deepseek-v4-pro-9b-uncensored-experiment|deepseek-v4-pro-9b-uncensored-experiment]]: stalled, last change 2026-11-13. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/forest-fire-pack|forest-fire-pack]]: stalled, last change 2026-11-16. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/hands-on-cooking-realtime|hands-on-cooking-realtime]]: stalled, last change 2026-11-17. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/harness-upkeep|harness-upkeep]]: stalled, last change 2026-11-19. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/rig-beetle-6-senses-nova|rig-beetle-6-senses-nova]]: stalled, last change 2026-11-18. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/scrying-orb|scrying-orb]]: stalled, last change 2026-11-19. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.
- [[Agents/sky-lands-aether-biomes|sky-lands-aether-biomes]]: stalled, last change 2026-11-16. Restart it, or set it done and move it to Agents/Done/ if the work is finished or dropped.

## Agent notes whose task is done

- [[Agents/dialogue-letterbox-pov|dialogue-letterbox-pov]]: still `in-review`, but its task `dialogue-letterbox-pov` is in Inbox/Done/.
- [[Agents/fishing-fire-and-crates|fishing-fire-and-crates]]: still `stalled`, but its task `fishing-fire-and-crates` is in Inbox/Done/.
- [[Agents/future-gun-gear|future-gun-gear]]: still `blocked`, but its task `future-gun-gear` is in Inbox/Done/.
- [[Agents/rig-beetle-5-the-mind|rig-beetle-5-the-mind]]: still `in-review`, but its task `rig-beetle-5-the-mind` is in Inbox/Done/.

## Missing PR numbers

- [[Agents/dialogue-letterbox-pov|dialogue-letterbox-pov]]: in review with no `pr:`.
- [[Agents/epic-fight-animation-research|epic-fight-animation-research]]: in review with no `pr:`.
- [[Agents/realistic-water-trial|realistic-water-trial]]: in review with no `pr:`.
- [[Inbox/deepseek-v4-pro-9b-uncensored-experiment|deepseek-v4-pro-9b-uncensored-experiment]]: `ready` with no `pr:`.
- [[Inbox/sky-lands-aether-biomes|sky-lands-aether-biomes]]: `ready` with no `pr:`.

## Tasks for nobody

- [[Inbox/medieval-patch10-temple-patrons|medieval-patch10-temple-patrons]]: `for: Medieval build (branch claude/project-thread-9de3pc), patch 10` matches no bot or agent, so no one will claim it.
