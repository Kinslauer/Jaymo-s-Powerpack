---
status: in-review
task: kitchen-rats
branch: claude/kitchen-rats
with: 
pr: 1150
mods: Kitchen
versions: 
started: 2026-11-08
owns:
  - kitchen/src/main/java/com/jaymoskitchen/rats/
  - kitchen/src/client/java/com/jaymoskitchen/client/rats/
  - changes/kitchen/kitchen-rats.md
---
# Kitchen rats

**Task:** Rats that come for stored food in Kitchen (sneak, sniff, nibble, steal, flee), plus a snap trap, a cage trap and cats that hunt them.

## Findings
- Kitchen has no rats yet (codemap and code grep).
- Rats mod (AlexModGuy/Rats) is LGPL-3.0, Forge/NeoForge 1.20.1 at newest. Reuse allowed with credit; waiting on Andy.
- Kitchen doesn't depend on GeckoLib: plan is a vanilla model with code animations (like Kingdoms' loot goblin).

## Status
- Look page for Andy's OK: https://claude.ai/artifact/Suq8VYQKEur8T2j71iSFWY . No code written yet.
- Andy chose the Rats mod's textures (LGPL, credited in LICENSE_rats). Built rats, snap trap, cage trap, cat hunting.
- PR #1150 (https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/1150). Built, pr_checks pass; not played.
