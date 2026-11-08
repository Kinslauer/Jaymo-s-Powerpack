---
name: deeds-captives
description: Villagers react to deeds (fires out, saves, rescues) + chained captives in camps/dungeons; PR #255 (Kingdoms 0.7.61 / pack 0.11.68)
metadata:
  type: project
---
2026-10-15 Andy: "villagers should react to your deeds, putting out fires, saving them from harm. rescue from dungeons, etc."
PR #255 MERGED 2026-10-15 (branch claude/project-thread-tnela9), not playtested. Also: MCA voice lines greet/yes/no/celebrate/angry heard as vanilla grunts unless Languages.understands (EntityVoiceMixin). `Deeds`: fire credit = watched Firefighting fire gone out
within 6 blocks of a player who used hands/item in the last 3 s; save = killing what hit/targeted a villager; per-player
DEEDS attachment per settlement, greet calls 7 days, Approaches GOSSIP hook, daily spread to nearest unaware settlement.
`Captives`: NoAI + permanent invulnerability (canBeSeenAsEnemy false), synced BOUND flag for right-click; camps 80% 1-2,
dungeons 35% (spawner CHECKED attachment); follow home, reward 3-8 emeralds. Test: `/kingdom captive`. Jar /mnt/project-files/deeds/.
Lines: talk.jaymoskingdoms.deed.<fire|save|rescue>.* and gossip.deed.*, 12 each via Oddities.line. See [[townsfolk-oddities]].

2026-10-20 Andy: "villagers should reward you more for returning a captured prisoner". PR #338 MERGED 2026-10-20 23:3xZ (pack 0.14.2 / Kingdoms 0.7.100, branch claude/project-thread-z51n11): captive standing folk/guard 20%/ruler's kin 10%; family purse + treasury reward scaled by Wealth.worth; guard/kin gifts; family thanks; FAVOURS attachment (no tolls/searches, pardons one Theft.arrest). Enemy or masked: no treasury reward/favour.
