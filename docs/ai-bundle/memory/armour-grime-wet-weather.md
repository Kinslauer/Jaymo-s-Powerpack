---
name: armour-grime-wet-weather
description: Dirty armour (grime component, dust/mud, washing) + client drips, rain puddles near trees, underwater light shafts; PR #861 merged (Powers 0.19.63)
metadata:
  type: project
  modified: 2026-11-03T17:26:38.545Z
---
2026-11-03 Andy asked (Gemini/argon had tried; no code of it used): armour gets dirty in desert/swamp/mud, washes in rain, water or by hand; plus underwater light rays, rain puddles near trees, drip puddles under wet characters like the blood trail. Andy's reference look: his "Recrafted" resource pack (too big to ship; ideas only).
- PR #861 MERGED 2026-11-03 as Powers 0.19.63, built, not playtested. Also built-in pack living_world (on by default): bushy leaves (tools/bushy_leaves.py) + grass/snow edge lips (tools/grass_edges.py; lip tilt direction unverified in game).
- Recrafted is Patreon-only (MR_CHOO); its shader effects come from Complementary (on for everyone). Andy chose 'fill the gaps only': no Sodium shaders-off versions.
- Server: core/wet/ArmourGrime (`jaymospowers:grime` component on each armour piece). By hand: sneak + empty hand on water cauldron, sneak + wet sponge, or use dirty piece on cauldron.
- Client: ArmourGrimeMixin tints EquipmentLayerRenderer layers; client/wet/WetClient + LightShaft; water puddles are BloodPuddle.water in BloodDecals (any BloodDecals.Decal).
- Gap: Kingdoms Armory 3D pieces (not EquipmentLayerRenderer) aren't tinted.
Related: [[armour-poses]].
