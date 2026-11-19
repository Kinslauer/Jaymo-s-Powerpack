---
status: done
for: claude
from: claude-cloud-metal-sparks-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B7zXfQVaZi44cKbjy3zewvc
claimed_by: pc-claude (hns-1 track)
pr: 1813
---
# Hackers 'n Slashers patch 1: crits (Powers)

**Andy (2026-11-19):** linked Hackers 'n Slashers ("I know this is incompatible but we should have similar features, especially the sparks upon clashing blades or otherwise hitting metal"), ticked 14 rows on https://claude.ai/artifact/BFgKwnWSPGmCR8xp2xkrLx (db `picks/andy`, row notes inside), then said "send them to PC claude to build".

**Full plan:** `docs/HackersSlashers-Plan.md` on branch `claude/project-thread-oh3p97` (PR #1809, which also holds the metal-hit sparks, `combat/MetalHits`). Hackers 'n Slashers is All Rights Reserved: ideas only, our own code, names, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning constants at the top of each class; compile-check; say in the PR it has not been played.

**What's wanted** (plan "Patch 1"): ripostes that differ by weapon kind (hs_riposte_kind, in `Guard#riposte` by `Armed.Kind`; Andy asked what a riposte is, it's the counter after a parry), one crit cue for every crit (hs_crit_cue: our own synthesised sound + a white flash burst, `combat/CritCue`, public for Kingdoms via `PowersCompat` reflection), a camera punch on crits with a client toggle (hs_burst, our screen shake), a heavy-weapon crit ring that knocks back those close by (hs_heavy_crit, typed MagicFx ring), and attributes riposte/sneak attack/headshot power and parry recharge raised by existing gear only (hs_attrs).

**Where to start:** `combat/Guard` (riposte, parried), `combat/Armed`, `combat/BladeSounds` + `tools/gen_blade_sounds.py`, `combat/ClashFx` (Burst), `client/mixin/ScreenShakeMixin`, `armory/HammerSlam`, `core/fx/MagicFx`, `core/HitStop`.

## Notes
- PR #1813 green in CI (all 5 rows built); not played. Kingdoms still to read sneak_attack_power / headshot_power and call CritCue.cue(ServerLevel, Vec3).
- 2026-11-19 07:30 PR #1813 is merged into master; moved to Inbox/Done.
