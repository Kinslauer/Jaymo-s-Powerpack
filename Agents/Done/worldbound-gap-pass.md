---
status: done
task: worldbound-gap-pass
branch: claude/worldbound-gap-pass
with: 
pr: 1173
mods: 
versions: 
started: 2026-11-08
owns:
  - docs/worldbound-gap-pass.md
---

# Worldbound: read its mod list once for gaps

**Task:** Read Worldbound (NeoForge 1.21.1 vanilla+ CurseForge pack) mod list once and name anything our pack is genuinely missing relative to its stated strengths (dense structures in all three dimensions, bigger Illager threat, enchantment overhaul, bosses). Research only — nothing ships; Worldbound is All Rights Reserved.

## Plan
- Find the Worldbound pack on CurseForge and get its full mod list (logged-in browser or pack zip manifest.json — do not guess names).
- Compare against our pack's mods (Dungeons & Taverns, Epic Structures, Structory, It Takes a Pillage, spell mods, Kingdoms content cover a lot).
- List each candidate: mod, what it does, whether we already have it; leftovers go to Andy as one-liners.

## Notes
- CurseForge serves the page in chunks and blocks plain fetches (~20 names max without a logged-in browser).
- suggested_owns: [] — research only, no files owned.

## Done
What a player will see: nothing — research only, no code or content changed. What wasn't tested: n/a.
Worldbound v0.0.14 (NeoForge 1.21.1, 315 mods) compared to our 147 mods; gaps listed in docs/worldbound-gap-pass.md. Nothing to merge into the pack unless Andy picks a leftover.
- 2026-11-09 01:34 PR #1173 is merged into master; moved to Agents/Done.
