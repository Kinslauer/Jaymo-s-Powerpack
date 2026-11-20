---
status: in-review
task: zfighter-gaps-2-power
branch: claude/zfighter-gaps-2-power
with: 
pr: 1938
mods: powers
versions: 0.26.16
started: 2026-11-20
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterHooks.java (existing)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPower.java (existing)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterGuard.java (existing)
  - changes/powers/zfighter-gaps-2-power.md
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_stamina.json
---
# Z Fighter gaps 2: power (stamina)

**Task:** Stamina for Flash Step, guard and forms - one row of Andy's Z Fighter gap checklist (stamina costs drawn from the pack's existing stamina, meditation and rest refill it). Ideas only - no DragonMineZ copying. Part 1 of 5 picked rows built as PR #1938.

## Plan
- Fix PR #1938 review points: guard drain ticks while guarding (not only while charging); guard drops when the stamina spend fails; stamina checked before ki is spent on form entry (both player key paths); transform() keeps no stamina check so Battle, rites and God callers still always land.
- sh ./gradlew clean build + tools/pr_checks.py green; commit, push, set in-review.

## Notes
- Review (PR #1938, all five points): guard drain moved out of the charging block; ZFighterHooks.guardStamina returns the spend result and ZFighterPower ticks the guard down when it fails; stamina is checked before the ki spend in onUltimate and pickForm; enterFormStamina check removed from transform() itself.
- Caveat: zfighter-aura-rework (#1911, in-review, reviewer away) owns ZFighterPower.java while open, so these edits there are more than one-line hooks; if #1911 changes the same regions before merging, bring master into this branch and resolve.
- Still to build (rows 2-5 of the picked list, do not lose): holding back your power; more Limit Break tiers; unlocked potential (Lore.md first, quote it to Andy); fighting styles with a passive each. ZFighterPower.java edits for those rows need hooks-only changes or must wait for #1911 to merge.

## Done
Player will see: guarding and Flash Step cost stamina each tick/cast; entering a form checks stamina before spending ki; empty stamina shows "Running on empty: your stamina is spent." and a held guard drops when it runs out.
Not tested in game: sh ./gradlew clean build passes against master 0.26.16 and tools/pr_checks.py is green (5 changed files, Powers only); the pack has not been booted in game.
