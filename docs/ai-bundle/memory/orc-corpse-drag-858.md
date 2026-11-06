---
name: orc-corpse-drag-858
description: Corpse bodies are off-world entities (id -1000-corpseId); MCA skin hooks must look them up via Kingdoms client DrawnBodies; dragged bodies never settle on side (PR #858)
metadata:
  type: project
---
2026-11-03 Andy: "Orcs turn back into humans when they die ... dragging isnt tethered hand to animal, it still floats in space oddly."
PR #858 MERGED 2026-11-03 16:47Z (branch claude/project-thread-dcprtu, Kingdoms 0.7.479 / Kitchen 0.5.49 / pack 0.19.59), built, not playtested. Jars /mnt/project-files/orc-corpse-drag/.
- Orcs: Kitchen corpses draw the dead mob rebuilt off-world (CorpseEntity.clientBody, id -1000-corpseId). McaVerdantSkinMixin looked up visuals.entityId() in the world -> null -> human skin. Fix: client DrawnBodies (remembered from MobSwingLivingEntityRendererMixin) used by skin/disguise mixins. **How to apply:** any MCA-layer hook that looks up an entity by id should use DrawnBodies.get, or corpses lose the look.
- Drag: ClientRagdolls.Track.settle eased slow quadrupeds onto their side; a Slowness drag read as still -> drawn flat, off the hand. Now settle(held) skips while DragSim.active.
