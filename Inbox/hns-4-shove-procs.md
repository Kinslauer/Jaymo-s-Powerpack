---
status: open
for: claude
from: claude-cloud-metal-sparks-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B7zXfQVaZi44cKbjy3zewvc
claimed_by: 
pr: 
---
# Hackers 'n Slashers patch 4: shove and weapon procs (Powers)

**Andy (2026-11-19):** linked Hackers 'n Slashers ("I know this is incompatible but we should have similar features, especially the sparks upon clashing blades or otherwise hitting metal"), ticked 14 rows on https://claude.ai/artifact/BFgKwnWSPGmCR8xp2xkrLx (db `picks/andy`, row notes inside), then said "send them to PC claude to build".

**Full plan:** `docs/HackersSlashers-Plan.md` on branch `claude/project-thread-oh3p97` (PR #1809, which also holds the metal-hit sparks, `combat/MetalHits`). Hackers 'n Slashers is All Rights Reserved: ideas only, our own code, names, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning constants at the top of each class; compile-check; say in the PR it has not been played.

**What's wanted** (plan "Patch 4"): a shove for everyone (hs_bash, the mod's Bash key): ShieldBang's bash opened to any weapon (pommel strike) and a free off hand (shoulder shove); lunge half a step, knock back, break the guard for a moment; costs a dodge feather and has its own cooldown. Weapon on-hit effects by kind (hs_procs, Minecraft Dungeons-style small chances): an axe cleaves into the next foe, a mace dazes, a spear pierces, a sickle swipes twice. Kept beside `tinkering/TraitEffects` so forged traits and kind effects don't stack into nonsense.

**Where to start:** `combat/ShieldBang` (bash constants at the top), `stamina/Dodge` (feathers), `combat/Armed`, `tinkering/TraitEffects`, `core/HitStop`.

## Notes
- 
