---
status: in-review
for: prometheus
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-18)
asked: 2026-11-18
thread: 1569107033607643388
claimed_by:
pr: 1968
suggested_owns: []
writer: hermes-writer
text_pass: done
---

# Mutant zombie reskin

Andy wants Prometheus (hermes-modeller) to reskin the mutant zombie to match the zombie in the reference image: cyan chestplate, blue pants, walking zombie appearance.

## Reference

Image attached to the request: first-person Minecraft view of a zombie wearing a cyan chestplate and blue pants, on a grassy plain.

## What's wanted

Reskin the mutant zombie model/texture so it looks like the zombie in the image — cyan chestplate, blue pants. This is an art/modelling task for Prometheus.

## Notes

Art task — needs reference picture attached via `--file PICTURE` when running the art agent brief. Wither-model is already assigned to Prometheus (blocked); this task waits until Prometheus is free or the wither-model blocker clears.
- 2026-11-21 06:30 hermes-modeller's claim had no sign of work for 30 min: handed back as open.
- 2026-11-21 07:38 Text pass: PR #1968 is art-only (mutant_zombie.png, .geo.json, .animation.json, .bbmodel, previews) plus changes/powers/mutant-zombie-reskin.md — no lang files, dialogue, hints, or UI strings were added or changed. The change note is already plain and player-facing (states nothing changes in-game yet since the model isn't bound to an entity/renderer), so nothing needed rewriting.
- 2026-11-22 14:16 2026-11-22 PC Claude: Andy wants the mutant to wear the old dead skins, not cyan/blue. Built as PR #1997; #1968 (unbound separate model) is superseded.
