---
status: in-review
task: parasites-and-cures
branch: claude/parasites-and-cures
with: 
pr: 1165
mods: kitchen
versions: 
started: 2026-11-08
owns:
  - kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java
---
# Bad water gives parasites, and a tea cures them

**Task:** Kitchen thirst: dirty water inflicts a slow, survivable parasite effect instead of vanilla poison, and an existing tea (Powers' willow_bark_tea or chamomile_tea, or a Kitchen drink) cures it. No new items.

## Plan
- Confirm how Thirst applies dirty-water poison in kitchen/.../climate/Thirst.java
- Add a Kitchen mob effect (parasite): slow damage ticks, survivable
- Replace the poison application with the parasite effect
- Add cure to one existing tea; state which tea in the PR
- Check codemap/kitchen.md; regenerate codemap if classes added

## Notes
- Andy's ruling: the cure goes on an existing item; no new items.
- The parasite effect must be ours (Kitchen mob effect or body component), replacing poison rather than borrowing it.
- Slow and survivable — a reason to boil water, not a death sentence.

## Done
Dirty water applies a slow, survivable parasite instead of nausea; hot chocolate cures it. PR #1165 opened; buildKitchen and pr_checks pass; not tested in game.
