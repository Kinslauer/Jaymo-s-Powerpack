---
name: battlefield-dead
description: Clearing the dead (Bearers: graves, burning foes, MCA mourning) + Looters/poor villagers robbing corpses + ROBBING crime; + judgement of looters carried to a guard; PR #372 (Kingdoms 0.7.132 / Kitchen 0.5.9 / pack 0.15.41)
metadata:
  type: project
  modified: 2026-10-21T09:54:23.704Z
---
2026-10-20 Andy: "i want someone to clear the battlefield of dead, or maybe come try to rob the corpses".
PR #372 MERGED 2026-10-21 10:12Z (branch claude/project-thread-5pd11i), not playtested. Jars /mnt/project-files/battlefield-dead/.
Kingdoms `Bearers` (villagers/folk carry own dead to a row graveyard 16-44 from seat, headstone walls, attachment
jaymoskingdoms:graves; foes burned after 2 min; MCA kin mourn + visit 4 days; +3 rep for carrying a body) and `Looters`
(Folk role 80, bands at 3+ bodies near a player, poor jobless villagers, Theft.Crime.ROBBING via Kitchen
CorpseEntity.setOnRobbed). Kitchen added robBy/hasValuables/lookTag. Defaults chosen by the thread (not Andy's words):
player bodies never touched, golems leave settlement dead to bearers. See [[corpse-despawn]], [[golem-burning]].
2026-10-21 Andy: "drop a downed one in front of a guard for judgement (you'll later hear or see them jailed or executed, or freed!)".
Looters downable (not tended); set one down within 4 of a free guard -> Arrests.handOver -> pardon/flog/JAIL (new; replaces
exile for looters)/execute; bringer away >48 hears it later from a villager of that settlement (Looters.news, in memory only).
