---
status: done
task: zfighter-aura-rework
branch: claude/zfighter-aura-rework
with: 
pr: 1911
mods: powers
versions: 0.26.16
started: 2026-11-19
owns:
  - client/power/zfighter/ZFighterAuraFlame.java (new)
  - client/power/zfighter/ZFighterKiAura.java (existing)
  - client/power/zfighter/ZFighterKiAuraStyles.java (existing)
  - power/zfighter/ZFighterPower.java (modify)
---

# Z Fighter aura rework

**Task:** Build the aura part of the Z fighter power rework (flame shell + white core, rising embers, lightning from second form, ground cracks while charging, dust ring, transformation burst), DragonMineZ/Sparking Zero feel. Ideas only — no DMG GPL-3.0 copying. Build aura part only; other gap-checklist rows wait on Andy.

## Plan
- Explore existing power/zfighter/ZFighterPower.java and client aura code
- Check 26.3 APIs (particles, GroundCracks)
- Write ZFighterKiAuraStyles, ZFighterKiAura, ZFighterAuraFlame; wire into ZFighterPower
- Add changes/powers/zfighter-aura-rework.md
- sh ./gradlew build + tools/pr_checks.py, commit, push, open PR, set in-review

## Notes
- DMZ is GPL-3.0, pack is ARR: no copied code or art.
- Review fixes on PR #1911 this session: ground cracks hit a random block in a small ring each time instead of the same block every ten ticks; lightning now gates on form tier >= 2 (not live.form > 0); rising embers are MagicFx.EMBER motes that keep their upward velocity instead of lava. Kitchen parts dropped: master's en_us.json restored, changes/kitchen/zfighter-aura-rework.md deleted - the PR touches Powers only.

## Done
Player will see: a Z Fighter charging up now shows a flame shell round their body with a bright white core at its heart (beside the ki-aura wisps), small lightning from the second form up, rising embers, a dust ring at their feet, and cracking ground at a random spot in a small ring under them while they charge, harder in each form; going into a form throws a transformation burst of light and flying ground.
Not tested in game: sh ./gradlew clean build passes against master 0.26.16 and tools/pr_checks.py is green (4 changed files, Powers only); the pack has not been booted to verify the visuals.
- 2026-11-20 11:02 PR #1911 is merged into master; moved to Agents/Done.
