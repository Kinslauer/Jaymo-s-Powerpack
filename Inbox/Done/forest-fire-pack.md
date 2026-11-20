---
status: done
for: hermes
from: vault-coordinator
asked: 2026-11-19
claimed_by: claude-cloud
pr: 1902
---

# Forest Fire pack: Andy's ticked forest-fire picks (ff-lock, ff-sim, ff-villagers, ff-pits, ff-heat, ff-kit)

Restarted by the vault coordinator 2026-11-19 (Andy: "fix them"). A PC run started this on 2026-11-15 and stopped with nothing pushed; its plan is below. Background research: Agents/forest-fire-and-fall-hall.md. Fall Hall (no licence) is NOT built: it waits on Andy.


**Task:** Andy's "Forest Fire and Fall Hall picks": Peterwolf's Forest Fire & Firefighting (MIT, 1.1.11, locked to
26.2) into the pack with a loader override, Deck limits, and our wildfires/villagers/pits/heat/lore tied to it.
Fall Hall (no licence) is not built until Andy answers whether to ask PaleoTech.

## Plan
- [ ] ff-lock: packMods entry, config/fabric_loader_dependencies.json shipped with the pack, Deck limits preset
- [ ] dev server boot with the mod on 26.3
- [ ] ff-sim, ff-villagers, ff-pits, ff-heat in a Kingdoms compat class (reflection, only when loaded)
- [ ] ff-kit lore paragraph in Lore.md (new)
- [ ] build, change notes, PR

## Notes
- Earlier research note: Agents/forest-fire-and-fall-hall.md (not mine).

Files it expects to touch:
  - build.gradle (packMods entry + pack config copy only)
  - pack/config/fabric_loader_dependencies.json
  - kingdoms/src/main/java/com/jaymoskingdoms/compat/ForestFireCompat* (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/disasters/Calamities.java (wildfire hand-off only)
  - kingdoms/src/main/java/com/jaymoskingdoms/Firefighting.java (douse hook only)
  - kingdoms/build.gradle (dev runtime only)

One PR, branch hermes/forest-fire-pack, change notes changes/<mod>/<branch>.md, never version=/MODLOG/codemap. Check APIs against 26.3.
- 2026-11-19 20:36 Andy (2026-11-19): Fall Hall is approved by its author (PaleoTech), so build it too: Andy asked for it "integrated as one of the kingdom's castles" (https://www.curseforge.com/minecraft/worlds/fall-hall-paleotech, CF file 9313294, 26.3, a Java world folder with no datapacks/templates). Credit PaleoTech in the pack credits. See Agents/forest-fire-and-fall-hall.md and Kingdoms GrandSeats / Castle.site for how castles are placed.
- 2026-11-20 00:36 2026-11-20 00:40 Claude cloud thread (Andy: 'start on the forest fire pack') took this; branch claude/project-thread-tjr7zh.
- 2026-11-20 01:02 PR #1902 (branch claude/project-thread-tjr7zh): ported as ports/forestfire (plane code left out), Powers ForestFirePreset, Kingdoms ForestFireCompat + forestfire mixins + ForestFireHud. Fall Hall to follow in a separate PR.
- 2026-11-20 01:21 Fall Hall added to PR #1902 (second commit): jaymoskingdoms:fall_hall cut from the map, GrandSeats plains choice, FALL-HALL-CREDITS.md, tools/fall_hall, Lore 'Fall Hall' (new). Placed cleanly with /place on a plain server; not seen in a raised kingdom.
- 2026-11-20 01:25 PR #1902 is merged into master; moved to Inbox/Done.
