---
status: blocked
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-b
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
