<!-- Written by tools/vault_sync.py on every sync (2026-11-19 20:20). Don't edit. -->
# Vault health

Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. `python tools/vault_sync.py --health` prints the same list.

**7 problems.**

## Statuses the pipeline doesn't know

- [[Inbox/class-trees-plan|class-trees-plan]]: `status: todo` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/harness-upkeep|harness-upkeep]]: `status: running` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)
- [[Inbox/medieval-patch10-temple-patrons|medieval-patch10-temple-patrons]]: `status: todo` (known: blocked, changes, claimed, done, in-review, open, ready, sent, writing)

## Missing PR numbers

- [[Inbox/deepseek-v4-pro-9b-uncensored-experiment|deepseek-v4-pro-9b-uncensored-experiment]]: `ready` with no `pr:`.
- [[Inbox/sky-lands-aether-biomes|sky-lands-aether-biomes]]: `ready` with no `pr:`.

## Tasks for nobody

- [[Inbox/harness-body-pieces|harness-body-pieces]]: `for: pc-claude` matches no bot or agent, so no one will claim it.
- [[Inbox/medieval-patch10-temple-patrons|medieval-patch10-temple-patrons]]: `for: Medieval build (branch claude/project-thread-9de3pc), patch 10` matches no bot or agent, so no one will claim it.
