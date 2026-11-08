---
name: otectus-mods-fit
description: 2026-11-03 comparison of Andy's 18 Otectus-era CurseForge links (MCA add-ons, Immersive X) vs the pack; checklist artifact + picks doc
metadata:
  type: project
  modified: 2026-11-03T20:57:19.148Z
---
2026-11-03 Andy asked which of 18 links (mostly Otectus: MCA Quests/Reputation/Conversations/Kingdoms/Mob Compat, Immersive Tanning/Smithing/Brewing/Jewelry/Apotheosis, Locks Reforged, Plant Fibers, Runic Gods, Custom Trade Currency, Ars 'n Spells, Guard Villager/Recruits Skins, Sitting+) are already in.
None ship: all Forge/NeoForge 1.20.1/1.21.1 (Sitting+ Fabric only to 1.21.5); GPLv3 / ARR / CC BY-NC, so ideas only.
Verdict: ours = Quests, Smithing, Reputation, MCA Kingdoms; partly = Sitting, Tanning, Jewelry (Baubles), Locks (loot-chest Lockpicking), Plant Fibers, Conversations, Brewing; missing = trade coins, patron gods; not needed = both skin add-ons, Mob Compat, Ars 'n Spells, Immersive Apotheosis.
Checklist with tickable gaps: https://claude.ai/artifact/ShEhPAGKjExHKUE7AFcmAu ; picks saved to db doc picks/andy (ArtifactData get collection picks, doc andy). Per-mod lines in Reference-Notes.md "Otectus batch".
**How to apply:** when Andy says he saved picks, read picks/andy and plan patches per [[modpack-cross-reference]]. See [[reference-notes]].

Coins (patch 3) detail: [[coins-replace-emeralds]]. Patch 4/6 work: [[otectus-jewellery-locks]].
Older log lines: [[otectus-mods-fit-log]].
- 12:30Z Andy "Continue" (no merge of #916/#917 yet; master taken to Powers 0.20.12 / Kingdoms 0.7.512 by fairies #914, so #916/#917 must renumber at merge). Building patrons (c) temples/champions/weapons in /home/user/patrons3 (based on claude/otectus-patrons2; local Kingdoms 0.7.515 / Powers 0.20.15, hints 91-92) and patch 10 diplomacy/elections in /home/user/diplomacy (Kingdoms 0.7.516, hint 93) via workflow wf_6e36dc08-ec3.
- 13:35Z Andy "Merge": #916 brewing merged as Powers 0.20.13; #917 patrons (b) merged as Kingdoms 0.7.513 / Powers 0.20.14 (hints 89-90, HINTS=90). Next free: Powers 0.20.15, Kingdoms 0.7.514. patrons3 (based on old patrons2 branch) and diplomacy still building; take versions after master at push.
- 15:40Z PRs open on master 0.20.15 / 0.7.514 (skill web): #922 patrons (c) (claude/otectus-patrons3, Kingdoms 0.7.515 / Powers 0.20.16, hints 91-92, HINTS=92; 8 temples, champions PatronChampion, weapons armory/PatronWeapons, hunt mark), #923 diplomacy (claude/otectus-diplomacy, Kingdoms 0.7.516 / Powers 0.20.17, hint 93, HINTS=93; Treaties/Envoys/RulersDesk/Elections). Merge #922 first. Built + booted, not played. Left in Otectus plan: patches 8 (talks that matter) and 9 (quests to 260), run one at a time (same code).
- 16:10Z Andy "Merge": #922 patrons (c) merged as Kingdoms 0.7.518 / Powers 0.20.18 (hints 91-92); #923 diplomacy merged as Kingdoms 0.7.519 / Powers 0.20.19 (hint 93, HINTS=93). Patron gods all three parts merged. Next free: Powers 0.20.20, Kingdoms 0.7.520. Left: Otectus patches 8 (talks that matter) and 9 (quests to 260), one at a time.
- 16:15Z Andy "Continue": building patch 8 talks that matter in /home/user/talks (talks-work, local Kingdoms 0.7.521 / Powers 0.20.20, hints 94-95) via workflow wf_7e0e8365-665. Patch 9 quests after it.
- 2026-11-04 19:37Z: patch 8 talks #930 MERGED as Kingdoms 0.7.523 / Powers 0.20.25 (hints 94-95, HINTS=95; dragons #915 took 0.7.522/0.20.24). Next: patch 9 (quests to ~260). Master dev-boot needs ctov dev:false (lithostitched is dev:false) since #927.
- 2026-11-04 19:40Z: patch 9 quests building: worktree /home/user/quests (branch quests-work), workflow wf_b8c640b5-2b2 (script otectus-quests.js), local Kingdoms 0.7.524 / Powers 0.20.26, hints 96-97, lang parts/quests-more.json. Scope: 75+ templates to >=260, town-situation quests, shared village projects. Renumber at push.
- 2026-11-04 21:35Z: patch 9 quests #938 MERGED 21:37Z (claude/otectus-quests, Kingdoms 0.7.530 / Powers 0.20.33, hints 96-97). It also removed ~12k archived MODLOG lines a 'both'-resolve merge (Golemworks thread) re-added: when resolving MODLOG conflicts after #928, never keep archived entries (check docs/modlog/). After 9 merges, all Otectus patches done.
