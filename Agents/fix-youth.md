---
status: in-review
task: fix-youth
branch: claude/waterskin-old-id
with:
pr: 1266
mods: [kitchen]
versions:
started: 2026-11-10
owns:
  - Fix YOUTH.md
  - kitchen/src/main/java/com/jaymoskitchen/climate/Climate.java
  - kitchen/src/main/java/com/jaymoskitchen/climate/WaterskinItem.java
  - E:/hermes/SOUL.md
  - E:/hermes/config.yaml
  - E:/hermes/scripts/dispatch_bots.py
  - E:/hermes/powerpack/vault.py
---
# Fix YOUTH

**Task:** Work through `Fix YOUTH.md` top to bottom: the old-waterskin PR, YOUTH's new SOUL.md rules, the Hermes fixes Andy already asked for, and cleanup.

## Plan
- Waterskin old-id PR (editor agent in a worktree, then an independent review)
- SOUL.md rules, then restart the gateway with schtasks
- Writer list, model, cap, checklists PR, worktree cleanup, vault cards, memory pending

## Notes
- Gateway: `schtasks /run` doesn't restart a running gateway (IgnoreNew). SOUL.md reaches live sessions through
  `hermes sessions refresh-prompts`; config.yaml model changes apply on the next message.
- 0.22.0 was already done in the cloud as PR #1264. My duplicate #1267 is closed.

## Done
- **#1266** (Kitchen): a waterskin from before the tier update turns into a Leather Waterskin with its water (up to
  3 sips) instead of disappearing. Not tested in game: load a pre-0.5.98 world.
- **#1265**: Andy's 4 pick-lists in `docs/checklists/`.
- Hermes: SOUL.md hard rules; model back to Super; Quill the only writer; Prometheus counts toward the cap;
  leftover worktrees and the duplicate skill cleaned up. Details in `Fix YOUTH.md`.
