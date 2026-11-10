---
status: changes
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-b
pr: 1203
suggested_owns: [kingdoms/src/main/java/com/jaymoskingdoms/FarmhandHome.java, kingdoms/src/main/java/com/jaymoskingdoms/Peoples.java, kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/talk.json]
writer: hermes-writer
text_pass: 
---

# Family farmhand: a good-natured Heartlander family takes you in

Whoever you are, the family that takes you in is kind about it.

## What's wanted

Tiv, in the Tasks thread: "The family farmhand start should be a good natured heartlander family that takes you in. they may comment on your race but they are not against you in anyway, as long as you help if you stay."

## Notes

- The start already puts you in a barn or farmstead with a family (`FarmhandHome`): a farming couple moves in if the house has fewer than two grown villagers, and everyone in the house starts at 40 hearts toward you.
- What that family *is* needs deciding: plain Heartlander folk, good-natured, who take a stranger in.
- They may notice your race and say so — that's flavour, one-liners in their talk — but it must never carry a cost: no hearts lost, no fear, no refusal to trade, no distaste applied.
- The bargain is "as long as you help if you stay": the farmhand job is what keeps you fed and welcome. Leaving is allowed; staying idle is what sours it, not your blood.
- Watch `Peoples.distaste`: peoples' inherited dislike of other heritages is exactly what must not reach this household. Give the start its own exception rather than relying on defaults.
- Don't change who spawns where or the 40 hearts; this is about their temperament and what they say.
- 2026-11-09 19:20 Text pass: talk.json (farm_family.welcome, farm_family.notice) - removed em dash, tightened phrasing to sound like Heartlander farmers; changes/kingdoms/farmhand-kind-family.md - removed em dash, made player-facing. Note: PR branch has pre-existing Java compile errors (AttachmentType, Codec.UUID) unrelated to text changes.
- 2026-11-09 20:44 Review (head 1277d4a): changes needed. Build Kingdoms is red: FarmhandHome.java:60-61 won't compile. Add 'import net.fabricmc.fabric.api.attachment.v1.AttachmentType;' and use 'UUIDUtil.CODEC' (net.minecraft.core.UUIDUtil, as BioBud.java:36 does) instead of 'Codec.UUID', which doesn't exist. Build kingdoms/ before pushing; vault.py now refuses a hand-in while the checks are red. I didn't review the rest past the compile error.
