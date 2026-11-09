---
status: open
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: 
pr:
suggested_owns: [src/main/java/com/jaymospowers/core/wounds/]
---

# Permanent hearts found in ore

Powers: extra hearts come out of the world — mined, not levelled.

## What's wanted

From the pick-list, row **Crystal hearts found in ore** (Scaling Health's crystal hearts, MIT). Andy's note: "finding permanent hearts is a good addition."

## Notes

- **Design first, before code.** This changes what health is: right now health is a level, and Powers' wounds/downed state assumes that. A found heart has to survive death, or be lost on death (Andy decides), and it must not break the downed state or the wound system.
- Suggested shape: a rare crystal in deep ore, each one a permanent half-heart, capped, with the cap shown somewhere quiet.
- Bring Andy the design in two lines before building; he answers, then it is a normal PR.
- 2026-11-08 20:31 Design (two lines, before code): a rare 'heart crystal' ore generates deep in deepslate; mining it drops a crystal, and using one raises max health by one permanent half-heart, up to a cap, shown quietly (an extra line under the health bar or an inventory tooltip). Questions for Andy, he decides then I build: 1) Does a found heart survive death, or is it lost on death? 2) Cap: how many half-hearts total, and where should it show?
- 2026-11-08 20:58 2026-11-08 Andy approved adding the ore hearts ("i approved adding the ore hearts, send it to the modeller"). Art handed to the modeller as task ore-hearts-art: heart_crystal_ore + heart_crystal_ore_deepslate textures, heart_crystal item, models and blockstates, ids jaymospowers:heart_crystal_ore / _deepslate / heart_crystal. Still blocked on Andy's two design answers: does a found heart survive death, and what is the cap / where does it show; both asked in the Needs Andy thread 1569168864384913589.
- 2026-11-08 21:02 2026-11-08 Andy's answers: 1) a found heart is LOST when you die, but it grants you a life - another revive before reincarnation; 2) cap five full hearts above base, shown quietly as a faint extra run on the health bar. Read as: each heart held is an extra sure wake - a death burns one heart and is turned away (left for dead, as Mortality's SURE_WAKES wakes do) instead of rolling; with no hearts left the cause roll runs as today, and reincarnation clears the hearts and the extra max health. Art is with the modeller (ore-hearts-art); ids jaymospowers:heart_crystal_ore, _deepslate, heart_crystal. Unblocked, back in the pool.
- 2026-11-09 07:53 Released by ops triage (t_a6774332): Apollo's run (started 05:55) hung on a model call just after 06:05 and was hard-interrupted at 07:20 (session turn lease lost; cron job inbox-builder-a failed). Nothing was committed and no PR was opened. Partial work is still uncommitted in worktree E:/wt/ore-hearts on branch claude/ore-hearts: new core/wounds/HeartCrystals.java plus hooks in JaymosPowers.registerAttachments(), Downed's AFTER_RESPAWN handler and Mortality.roll(); the last two still carry broken indentation from a half-applied patch, so tidy those before building. Continue in that worktree instead of cutting a new branch - the ore and item art is already on master (PR #1167): heart_crystal_ore, heart_crystal_ore_deepslate, heart_crystal.
