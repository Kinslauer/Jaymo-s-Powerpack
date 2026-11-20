---
status: in-review
task: zfighter-aura-rework
branch: claude/zfighter-aura-rework
with: 
pr: 1911
mods: powers
versions: 
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

## Done
Player will see: a Z Fighter charging up now shows a flame shell round their body with a bright white core at its heart (beside the ki-aura wisps), and cracking ground rises under them while they charge, harder in each form.
Not tested in game: the pack compiles and passes pr_checks (PR #1911), but I have not booted the pack to verify the visuals.
