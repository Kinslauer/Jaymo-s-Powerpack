---
name: goblin-villages
description: Goblin villages (palisade fort) + goblin mask, plus ported Loot Goblins (LGPL) in Kingdoms (PR #54, Kingdoms 0.5.31 / pack 0.8.64, merged 2026-10-12)
metadata:
  type: project
  modified: 2026-10-12T09:20:16.459Z
---
Andy asked for Goblins Tyranny's "infiltrate goblin villages" idea. Goblins Tyranny is Forge 1.20.1 and all rights reserved, so it was rebuilt as our own code: camp kind "goblin" (Goblins roles 30-32, GoblinVillages, GoblinBuildings, Disguise). Wearing a goblin mask with only leather armour fools goblins, until suspicion fills or the player is caught stealing or vandalising. Melee backstabs do 3x damage. Goblin villages never raid. Walls are a palisade fort (Andy asked for tall spiked logs).
Andy also asked for Loot Goblins' goblins: that mod is LGPL-3.0, so its art and behaviour were ported (package lootgoblins, licence in licenses/lootgoblins/); only the 8 loot goblin looks, ~1 in 10,000 natural spawns. Andy said NOT to add its ore mobs or their treasure items (removed 2026-10-12).
PR #54 merged 2026-10-12 13:08Z as Kingdoms 0.5.31 / pack 0.8.64. Screenshots are in /mnt/project-files/goblins/.
**How to apply:** Folk role ids 30-32 are taken. Bring master in and take the next free version before merging; see [[kingdoms-war-tempers]] for the camp pattern.
