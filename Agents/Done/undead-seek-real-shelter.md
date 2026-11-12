---
status: done
task: undead-seek-real-shelter
branch: claude/undead-seek-real-shelter
with:
pr: 1262
mods: powers
versions: Powers 0.21.109, Kingdoms 0.7.712
started: 2026-11-10
owns:
  - src/main/java/com/jaymospowers/undead/
---
# Undead seek real shelter from the sun

**Task:** Undead (zombies, skeletons, strays, old villagers, draugr, Warrior-born) that burn in sunlight should seek real shelter — a cave, anywhere underground, or under a solid roof — at dawn and when they find themselves in sunlight by day. Tree shade no longer counts. Remake RealisticSunBurn's ray-to-the-sun test in our own code (it's All Rights Reserved, not in the pack): hook Mob.isSunBurnTick in 26.3 so burning only happens when nothing opaque blocks the ray to the Sun's real position; Kingdoms' astronomy SunMoon gives the Sun's direction when loaded, with a day-time angle fallback. Then a shelter search uses the same ray test. Keep FutureSunMixin (ashen sun: nothing burns) and WebHostZombieMixin working; works with zombie players in heaven, downed/carried undead, captives. Change note in changes/powers/ (and changes/kingdoms/ if the pack's dead live there).

## Plan
- Scan repo: existing sun-burn handling, FutureSunMixin, WebHostZombieMixin, FleeSunGoal equivalents, Kingdoms astronomy SunMoon
- Check 26.3 sources via genSources for Mob.isSunBurnTick / sun-burn hook and canSeeSky
- Implement deadlier sun ray test + shelter goal, cheap (dawn + on-burn only)
- Add changes/powers/<branch>.md note
- sh ./gradlew build, python3 tools/pr_checks.py, commit, push, gh pr create
- vault.py --set undead-seek-real-shelter status=in-review pr=<N>

## Notes
- RealisticSunBurn (CurseForge project 1485037) is All Rights Reserved and not in the pack: remake the idea, no copying.
- Task suggests asking before dig-down/stay-put last resort.

## Done
What a player will see, and what wasn't tested.
- 2026-11-11 01:49 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-12 05:20 PR #1262 is merged into master; moved to Agents/Done.
