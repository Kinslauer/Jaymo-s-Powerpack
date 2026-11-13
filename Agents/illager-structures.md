---
status: done
task: Add Illager Structures with Andy's picks of who lives in each place
branch: claude/project-thread-fzaxhy
with: cloud Claude (Illager Structures thread)
pr: 1423
mods: kingdoms
versions: 
started: 2026-11-13
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/IllagerSites.java
  - kingdoms/src/main/java/com/jaymoskingdoms/SitePosts.java
  - kingdoms/src/main/resources/data/jaymoskingdoms/**/illager_sites/**
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/illager-sites.json
  - tools/illager_sites_port.py
---
# Illager Structures

**Task:** Andy linked Illager Structures and asked for every structure written up so he could pick who lives there
(bandits, pirates and so on, not only illagers). He picked on https://claude.ai/artifact/PpWAzWJwpkaHizGHmrhE4i, then
"use your suggestions" for the rest.

## Plan
- Port the 36 templates into Kingdoms (GPL v3 to GPL v3), mobs out, posts kept (tools/illager_sites_port.py).
- IllagerSites: empty until a nearby player opens the tier, then the picked people move in at the old posts and the
  unopened chests get their loot. Camps raid; garrisons and smugglers never do; monks and the recluse live in peace.

## Notes
- Hooks: Camps.tick/grow/chiefSlain, Raids.consider/member, Barbarians.prey/mayBecomeVillager, Captives.camps, Workers.step.

## Done
PR #1423. Built, dev-server checked (bandit camp, deep mole, monk tower, stable with fence, smeltery with ogres and a
captive moved in; chests relabelled; bandit leader kill clears the camp). Not played. Untested: realm garrisons, sea
places, the fence's trade screen, greetings. Crew capped at 40 per place (citadel had 65 mobs).
Merged 2026-11-13 (squash ad6454b72) on Andy's "merge". Lore paragraph "The old pillager holds" still (new), waiting on his OK.
