---
status: claimed
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
