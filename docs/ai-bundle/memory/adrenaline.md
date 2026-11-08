---
name: adrenaline
description: Adrenaline at low health (PR #1026 MERGED 2026-11-05, Powers 0.20.110 / Kingdoms 0.7.592 / hint 117): 10 s where broken limbs don't hinder; my readings of Andy's ask
metadata:
  type: project
  modified: 2026-11-05T15:41:02.949Z
---
2026-11-05 Andy: adrenaline at low health lets broken limbs (not head/torso) still sprint, attack, stand up, 10 s after a hit below half health or within 40% of a death threshold.
PR #1026 merged (branch claude/project-thread-mqedyw), core/wounds/Adrenaline. Built, not played. Readings I chose:
- Trigger: attack hit (Downed.attack) leaving health <= 50%, head/body fraction <= 0.4 (the "death threshold"), or downed. Going down's own 10 s rush (Downed.adrenaline) counts.
- Broken limbs are lifted (Breaks.armBroken/crawlOnly/apply/tick, LimbUse.strength, Downed.blocksRise arms). Severed limbs are still gone. Head/body unchanged.
- Only sign: one heartbeat sound to the player. No text.
See [[downed-self-rise]], [[accurate-hitboxes]].
Screen (client/wounds/AdrenalineHud): rushing = 6% letterbox + faint throbbing water-thirst veins; wearing off = full dry veins (Kitchen's no-water look) 3 s then 7 s fade. I chose water veins over vampire veins (offered to switch).
