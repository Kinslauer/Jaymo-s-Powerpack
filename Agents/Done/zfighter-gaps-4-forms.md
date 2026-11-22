---
status: done
task: zfighter-gaps-4-forms
branch: claude/zfighter-gaps-4-forms
with: 
pr: 1965
mods: powers
versions: 
started: 2026-11-20
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterBranchForms.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterForms.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterLineage.java
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPower.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFormMenu.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterClientConfig.java
---
# Z Fighter gaps 4: forms

**Task:** Add new Z Fighter form lines for Earthling, Rubber-folk, Divine-born and Bio-built heritages per Andy's 2026-11-19 checklist. New form ids start after REACTOR (14). Each heritage gets its own ladder of forms with attributes, colours, menu entries, and lang keys.

## Plan
- [ ] Read all relevant form system files (BranchForms, Forms, Lineage, Power, Races, menu, config)
- [ ] Add new form ids (15+) in ZFighterBranchForms.java or new file
- [ ] Add ladder entries for Earthling, Rubber-folk, Divine-born, Bio-built heritages
- [ ] Add attribute arrays (fists, speed, health, ki, drain, colors, tiers) for new forms
- [ ] Update form menu (ZFighterFormMenu, ZFighterForms.choices) for new forms
- [ ] Add lang keys for new form names
- [ ] Build check passes
- [ ] Commit, push, create PR
- [ ] Set task status in-review

## Notes
- Form ids: 10-14 are branch forms (Gloom Giant, Ascendant, Sovereign, Overdrive, Reactor). New ids start at 15.
- No new original mobs (Andy ruling). Reuse player-model fighters.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java: keep edits there to one-line hooks.
- Build them one after another in number order unless Andy says otherwise.
- Not tested in game.

## Done
What a player will see: new transformation forms for Earthling, Rubber-folk, Divine-born and Bio-built Z Fighters.
Not tested in game.
- 2026-11-22 10:08 PR #1965 is merged into master; moved to Agents/Done.
