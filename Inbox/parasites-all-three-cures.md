---
status: in-review
for: hermes
from: tiv (Discord, Questions thread, 2026-11-10)
asked: 2026-11-10
thread: 1569189294512537671
claimed_by: claude-cloud
pr: 1215
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java]
text_pass: 
---

# All three drinks remove parasites

Tiv, in the Questions thread: "make all three remove parasites" — hot chocolate already cleared the parasite from dirty water; willow bark tea and chamomile tea should too.

## What's done

- `kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java`: `curesParasite` checks hot chocolate, willow bark tea and chamomile tea. Powers' tea items are resolved by id at runtime — Kitchen never compiles against Powers.
- `changes/kitchen/claude-parasite-cure-all.md` change note added.
- `sh ./gradlew build` in kitchen/ → BUILD SUCCESSFUL.
- PR #1215 open on GitHub, ready for review.

## Notes

- Extends the merged parasites-and-cures task (PR #1165) which only had hot chocolate as the cure.
- One point an hour type grind isn't relevant here — this is just widening the cure to all three existing drinks, no new items.
- 2026-11-10 00:02 Cloud agent session sesn_01UoCcCrNZoyhRjftYXytx1a opened PR #1215.
