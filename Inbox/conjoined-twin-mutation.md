---
status: in-review
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-16)
asked: 2026-11-16
thread: 1572078106330533958
claimed_by: hermes-a
pr: 1670
suggested_owns: []
writer: hermes-writer
text_pass: none
---

# Conjoined twin cosmetic mutation for villagers and zombies

Andy wants a cosmetic mutation where villagers and zombies spawn as conjoined twins — a baby/toddler mounted on an adult's shoulder, like the video reference.

## What's wanted

**Cosmetic mutation:**
- Villagers spawn as conjoined twins: baby villager on adult villager's shoulder
- Toddler MCA on adult MCA shoulder
- Baby zombie on adult zombie shoulder
- Cosmetic only — no gameplay impact

**Reference:** Video showing baby villager on adult's shoulder (conjoined twins aesthetic)

## Notes

Research/investigation first — determine how to implement mounted/entity-as-passenger rendering for cosmetic purposes. No code changes yet.
- 2026-11-16 22:35 Text pass: PR #1670 (conjoined-twin-mutation) adds only code - new ConjoinedTwins attachment class, ConjoinedTwinSpawnMixin/VillagerMixin/ZombieMixin, has_conjoined_twin entity tag, mixins.json registration. No lang parts, change notes, dialogue, hints, item/ability text, chat lines or screens added or changed, so there is no player-visible text to rewrite.
