---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [src/main/java/com/jaymospowers/core/wounds/Breaks.java, kingdoms/src/main/java/com/jaymoskingdoms/BedRest.java]
---

# Sleeping sets your broken bones

A night in a bed does what a splint does.

## What's wanted

Tiv, in the Tasks thread: "sleeping should set your broken bones. after hitting the sleep button and waiting, longer per break."

## Notes

- Right now a break is only set by a splint (two sticks and string), a cleric, or a priest's fee; until it's set it can't heal past the cap. Sleeping should set it instead.
- "After hitting the sleep button and waiting, longer per break": you press sleep, the night passes — and the longer you lie there, the more of it is done, with every additional break taking longer. So one broken arm is a night's sleep; two or three breaks need more than a single night, or a longer one.
- Say plainly in the PR how the time is counted: per break, added on top of each other, and whether a second night is needed or the sleep simply runs longer.
- Bed rest (`BedRest`, our own take on Sleeping Overhaul) is already how a player lies down by day; check whether resting counts as sleeping here or only a real night does, and keep it consistent.
- Don't remove the splint: it's what you use when there's no bed to be had.
- There is already a "beds mend" change in Kingdoms 0.7.646 — look at what a bed already does before writing new code, and extend it to breaks rather than building a second path.
- The break must still show as set, so the body renderer, the debuffs and the "patched up" check all agree afterwards.
