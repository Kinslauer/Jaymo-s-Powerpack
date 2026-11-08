---
name: symbiote-rigid-barrage
description: PR #112 (pack 0.9.8): rigid Doc Ock tendrils, two-web Web Whip with midair lasso hover, tendril barrage combo, wilder behemoth
metadata:
  type: project
  modified: 2026-10-13T14:06:32.274Z
---
2026-10-13: Andy liked Fisk's Superheroes' Venom/Doc Ock animations (rigid limbs while climbing/moving). Fisk's is Forge 1.7.10, All Rights Reserved, so nothing is reused; we remake ideas ourselves.
PR #112 (branch claude/project-thread-8v73n0, now 0.9.8 after merging master), opened from Andy's asks:
- Walking tendrils are two-bone jointed limbs (WebTendrilLayer.bend, WebTendrilWalk.LIMB/KNEE); other tendrils go stiff by walk speed (JOINTS, RIGID_SPEED).
- Web Whip: first Sneak+R/G only catches (WebWhip.Catch, CATCH_TICKS 60); second web starts it. Midair = always lasso + hover (noGravity + zero Nudge); seesaw only on living creatures.
- BARRAGE combo (WebTendrilCombos kind 4): one creature held, other grip empty, hold left click; client sends Release when the attack key is up.
- Behemoth: per-tendril lashes (WebBehemoth.STRIKE is a List), any living thing in 7.5, normal i-frames kept (offered to remove).
Later on the same PR: Shroud 5 s, suit debut 30 ticks, WebSuitGrowth on game time (respawn kept the suit), blob walks on rigid legs.
WebInhabit: a loose blob takes any mob (suit = web_symbiote_goo.png over the mob's own model via a layer on every living renderer); peaceful rejected 20-40 s, hostile buffed + behemoth lashes + decay, killed at 60-90 s; blob never retakes a spurned mob. Andy narrowed takeable to cow/chicken/sheep (+pig) and humanoid hostiles; behemoth (risen player body) +160 hp, roar, frenzy, snatch, shroud.
Then (commit 5f67c9df): pain growth (BULK_TICKS 60) + more feral idle, WebSlam held-Jump overhead slam, villagers as hosts, blob settles to goo after 5 min, Shift+Space quick dive, whip block pry 1.5 s, tendril tug 0.5 s + breaks spawners, takeover rips craters (RIP_CRATER 9), fast landings roll / slow ease into hero landing.
Then (52ab27ce): chattier narration, remarks on surroundings (REMARK_GAP 1400-500, see.<topic>), sightseeing takeovers (TOUR_CHANCE 4%, walks you to a view/water shore/flowers/villager via a hidden Interaction marker tagged TOUR_TAG).
MERGED 2026-10-13 14:40Z (pack 0.9.8). Not playtested.
