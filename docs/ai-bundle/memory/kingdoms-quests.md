---
name: kingdoms-quests
description: Kingdoms 0.5.29 quests (PR #52): villager/king quests rebuilt in Kingdoms since Quest Giver has no 26.3 Fabric build
metadata:
  type: project
  modified: 2026-10-12T09:09:25.757Z
---

Andy asked (2026-10-12) for Quest Giver-style quests from villagers and kings only. Quest Giver is Forge/NeoForge 1.21.1 only, so it was rebuilt in Kingdoms (Quests, QuestBoard, QuestFoes, Relics, QuestNet; client QuestScreen/QuestClient). PR #52 merged 2026-10-12 as Kingdoms 0.5.29 (pack version not bumped). Jar + screenshots in /mnt/project-files/quests/.

Andy's rules: king quests very hard with strong enemies; villager quests fit the job (mail in town or to a nearby fiefdom, fetch from dungeons, slay counts, animal parts); use existing structures; build MCA hearts; givers show "!"; rewards match the giver (farmer hay, smith weapon/armour, emeralds, books, relics with passives, materials).

Regicide (Andy's follow-ups): any king but a noble may decree a rival king's death, each temper with its own reasons (chance by temper; warlords seldom). Approaches: false oath (pledge to the target realm) or a noble king's round (he stops for you with bread); either pays +50% emeralds. Warlords would rather wage war: half their decrees are 'lead my war party' (declares war on accept; unsworn players may /kingdom war join; done on sack).

**Why:** future quest tweaks should keep these rules.
**How to apply:** tuning lives at the top of Quests/QuestBoard/QuestFoes/Relics. Ops test with /kingdom quests offer and finish. Kings wander and teleport on their round, so givers are held still while the quest screen is open (Royals checks Quests.listening). Spawn fix (PR #57 merged as 0.5.33, 2026-10-12), Andy's rulings: bosses fight inside the dungeon in a room with space, never spawned above it; trail ruins are not boss lairs but villager dig quests (keepsake in suspicious gravel, brush lent); Andy wants more quest variety. Test: /kingdom quests offer champion <lair> | dig. Related: [[kingdoms-workers-mca]], [[kingdoms-caravans]].
