---
status: in-review
for: claude
from: claude-cloud-metal-sparks-thread
asked: 2026-11-19
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B7zXfQVaZi44cKbjy3zewvc
claimed_by: pc-claude (hns-2 track)
pr: 1812
---
# Hackers 'n Slashers patch 2: stealth and posture (Kingdoms)

**Andy (2026-11-19):** linked Hackers 'n Slashers ("I know this is incompatible but we should have similar features, especially the sparks upon clashing blades or otherwise hitting metal"), ticked 14 rows on https://claude.ai/artifact/BFgKwnWSPGmCR8xp2xkrLx (db `picks/andy`, row notes inside), then said "send them to PC claude to build".

**Full plan:** `docs/HackersSlashers-Plan.md` on branch `claude/project-thread-oh3p97` (PR #1809, which also holds the metal-hit sparks, `combat/MetalHits`). Hackers 'n Slashers is All Rights Reserved: ideas only, our own code, names, sounds and art. One PR per patch, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap; tuning constants at the top of each class; compile-check; say in the PR it has not been played.

**What's wanted** (plan "Patch 2"): a small backstab bonus for everyone (hs_backstab, in Senses, under the Sneak Attack perk's). Andy's note: "dont make it break commandments for those who have them". So a player sworn to a patron whose law holds `sneak_attack` (the Crowned Sun, `Commandments`) gets no new bonus, and nothing new adds a break. Also: cover that hides everyone a little (hs_cover: tall grass, leaves and bushes, rain, sprinting gives you away, the last attacker is easier to see; the Stalker Ranger keeps its far stronger hiding); posture for ordinary fighters (hs_posture: BossPosture's idea scaled down for MCA guards, raiders and knights and armed monsters, broken posture means a moment open to a sure hit, shown as a sagging stance and no bar). Call patch 1's `CritCue` by reflection if it's merged, otherwise leave a TODO-free guarded call that does nothing when it's missing.

**Where to start:** `kingdoms/.../Senses.java` (sight sum, sneak attack around line 900), `ThiefSneak`, `Commandments` (law table, `behind`), `BossPosture`, `MobParry`, `PowersCompat`.

## Notes
- PR #1812 built green in CI, not played. Rain cover already existed (FieldWeather); Powers parry has no hook into Kingdoms posture; CritCue called as cue(ServerLevel, Vec3).
