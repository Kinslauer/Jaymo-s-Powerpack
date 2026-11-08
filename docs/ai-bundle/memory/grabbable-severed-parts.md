---
name: grabbable-severed-parts
description: Severed heads/limbs carryable in one hand via Carry.PART, PR #133 MERGED 2026-10-14 as pack 0.10.5
metadata:
  type: project
  modified: 2026-10-14T04:46:02.350Z
---
2026-10-14 Andy: "make decapitated limbs grabbable". PR #133 MERGED 04:56Z (branch claude/project-thread-l3t7k0, pack 0.10.5): SeveredPart is pickable and goes through core/Carry with a new one-hand hold Carry.PART. Right-click with an empty hand (or sneak + right-click) picks it up; it dangles from the right hand. Right-click drops it, sneak + right-click throws it. A part doesn't slow you down or age while held. Not playtested.
Related: [[darktide-wind-fixes]] (where dismemberment came from).
2026-10-14 Andy: "skeletons should have a higher chance to lose limbs": Dismember.SKELETON_CHANCE_SCALE 3 (#minecraft:skeletons: 15% per blade hit, 60% katana), PR #142 MERGED 06:51Z as pack 0.10.16.
2026-10-15 Andy: "use Real Camera for the decapitated head cam". Real Camera (MIT, 26.3 Fabric) only follows the player's own model head, can't be enabled per case, so not used. Own head cam instead: HeadCamMixin + Dismembered.headView lock the first-person view in the fallen head, tumbling (full quaternion incl. roll) with it. PR #245 MERGED 2026-10-15 13:3xZ (pack 0.11.54), jar /mnt/project-files/head-cam/. Not playtested.
2026-10-31 Andy: "lost limbs should not grow back on death". PR #757 MERGED 2026-10-31 09:5xZ as pack 0.18.87 / Kingdoms 0.7.422 (branch claude/project-thread-mjlcqi): Dismember.MISSING copyOnDeath; respawn keeps arms/legs, only the head returns; prosthetics still drop with the body (offered to keep them on instead). Regrowth only via Hermit Bean / Verdant.
Same PR #757: Bio-built regrow a limb every 30 s (BioBuilt.REGROW_EVERY); Rubber-folk sever x2 (Dismember.RUBBER_SEVER_SCALE) and regrow one limb per swallow plus the 20 s timer; blunt blows x0.6 (RubberFolk.BOUNCE, incl. bare fists).
