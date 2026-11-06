---
name: wild-great-ape
description: Wild Great Ape (non-Z Fighter Warrior-born) AI + ape shoulder camera fix, PR #303 (pack 0.13.5), 2026-10-16
metadata:
  type: project
  modified: 2026-10-16T18:58:37.891Z
---
2026-10-16 Andy: the wild ape didn't attack; it should roam attacking everyone and run from damage; third-person camera should be over the shoulder so you see the ape.
Cause: berserk targeting picked unreachable mobs (caves/water) in a huge box. PR #303 (branch claude/project-thread-q3c3nd, pack 0.13.5): sight + level + no-water filter, gives up after 15 s, roams (WILD_GOAL), flees 40 blocks on hurt (WILD_FLEE), and every ape's THIRD_PERSON_BACK view is over the right shoulder (ZFighterSpiritCamera); first person no longer pulled back for Z Fighter apes. Not playtested.
