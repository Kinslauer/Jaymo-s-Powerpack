# bodies that die flat start lying
- Andy: "the death ragdoll when enemies and players are prone or crawling should keep them in that position". A mob's pose is already DYING when its corpse is made, so the old crawling/sleeping check never fired and crawlers popped up to standing. The pose a body dies in is now noted as `die` begins (`LivingEntityMixin`, `Corpses.noteDying`), and a body that died crawling, asleep, gliding or down on its belly from a leg shot starts lying (a leg-shot crawler face down) and doesn't keel over.
- Built; not played.
