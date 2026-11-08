---
name: downed-guards
description: Every humanoid is downed (0.15.41, PR #372; villagers+folk since 0.7.84); guards (Kingdoms GUARD/ARCHER role or MCA guard/archer job) were first, PR #266: instead of killed: crawl or lie, disarm by right-click, no loot; PR #266 (pack 0.11.90 / Kingdoms 0.7.73)
metadata:
  type: project
---
2026-10-15 Andy: "guards, when killed dont drop as a corpse, instead they get downed, and either crawl or otherwise fall into a downed animation, they can be disarmed but not looted". Before this, guards left Kitchen corpses.
PR #266 MERGED 2026-10-16 02:5xZ (branch claude/project-thread-0gr5kg): Kingdoms `Downed` + DownedMixin/DownedMobMixin + client DownedPose. Half crawl away from the killer, half lie on their backs. Only a wound-out real death (wars/quests/crimes) fires then, with no corpse and no drops. Andy 16:52Z: unhit guards get back up (1 min, 30% health); a player's hit stops that, bleed out 2 min after last hit, 10th hit kills. Andy 2026-10-16 02:30Z: mobs, fire and falls can kill them too (6 hp, mobs target them again), and you can throw them: sneak+right-click carries over shoulder (player passenger), right-click throws, sneak+right-click sets down. Archers included (my default). Built, not playtested.

2026-10-16 Andy: "villagers should try to help others that are downed, and they can all be downed now, unless decapitated". PR #302 MERGED 2026-10-16 18:1xZ (pack 0.13.1 / Kingdoms 0.7.84, branch claude/project-thread-3upngs), not playtested: Downed.downable = any Villager + settlement Folk (not rulers/orcs/barbarians/attackers); beheading (jaymospowers:decapitation or jaymoskitchen.missing.head tag) kills. Everyone downed, guards included (Andy 18:05Z "guards should have corpses too"), leaves corpse + loot when they finally die. Nearest free adult villager (24 blocks) kneels and tends, 10 s gets them up. Same PR: ShutIn (villagers still 2 min by day with no path to open ground puff out; skip near Placed blocks) for Andy's clip of townsfolk packed in a closed pocket; cause not pinned down (no server log; client was on the Deck server).

2026-10-21 Andy: "ALL HUMANOIDS go down, if they werent decapitated or otherwise prevented from the downstate". PR #372
(pack 0.15.41 / Kingdoms 0.7.132): Powers Downed.downable = any BodyPlan.HUMANOID mob (monsters too); Kingdoms marks all
Folk downable; villagers tend only kin (villagers + settlement folk), not looters/raiders/monsters.
