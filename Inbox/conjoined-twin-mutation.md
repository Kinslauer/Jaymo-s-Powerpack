---
status: done
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-16)
asked: 2026-11-16
thread: 1572078106330533958
claimed_by: hermes-a
pr: 1670
suggested_owns: []
writer: hermes-writer
text_pass: 
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
- 2026-11-17 04:30 Review (head e66f51d, PR #1670): changes needed. CI green, but it can't work as built.
1. ConjoinedTwinVillagerMixin.java:27-28 and ConjoinedTwinZombieMixin.java:27-28: @WrapOperation targets positionRider in Villager/Zombie, which neither declares (only Entity does), and an INVOKE of Entity.moveFunction, which doesn't exist (vanilla calls MoveFunction.accept). jaymospowers.mixins.json has defaultRequire 1, so this is a critical injection failure when Villager or Zombie first loads. Do it the way PerchRidingMixin.java:51 does: one @Inject at HEAD of Entity.positionRider, check for the twin, move.accept(...), cancel.
2. ConjoinedTwins.java:67: Zombie isn't an AgeableMob, so zombie, husk and drowned twins spawn adult-sized. Call Zombie.setBaby(true) for zombies.
3. ConjoinedTwins.java:46: village villagers spawn with EntitySpawnReason.STRUCTURE, and villagers aren't in natural spawn lists, so villagers never get a twin. Accept STRUCTURE (and decide on BREEDING) for villagers.
4. ConjoinedTwins.java:86: addFreshEntityWithPassengers(child) runs inside finalizeSpawn before the spawner adds the adult with its passengers, so the child is added twice ('UUID of added entity already exists' in the log on every natural twin). Drop the explicit add; the spawner adds both.
5. JaymosPowers.registerAttachments(): add "mutation.ConjoinedTwins" (CLAUDE.md: every class holding an attachment, since this one syncs).
6. The task said research first, no code yet, and nothing places the twin in Lore.md. Add a (new) paragraph saying what this mutation is (CLAUDE.md lore rule) and confirm with Andy that building it now is what he wants.
7. CHANCE (ConjoinedTwins.java:34) and the shoulder offsets in both mixins (side/up/forward) should be static final constants at the top of the class.
- 2026-11-17 08:16 hermes-a's last run stopped before finishing; handed back as changes so it can carry on.
- 2026-11-17 08:34 PR #1670 is merged into master; moved to Inbox/Done.
