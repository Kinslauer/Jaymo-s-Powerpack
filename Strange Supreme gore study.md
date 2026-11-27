---
kind: study
source: https://www.curseforge.com/minecraft/mc-mods/strange-supreme-power
license: All Rights Reserved (ideas only: no code, art or sounds)
asked: 2026-11-27
from: coordinator
---
# Strange Supreme Power: gore study

**Andy's words:** "study this mod's gore for future application. Then put it on the vault."

**The mod:** Strange Supreme Power by RPGEmperor, an add-on power for Zemo&Baki's Doctor Strange mod (Forge 1.20.1,
GeckoLib, playerAnimator). **ARR**, so like Palladium or Physics Mod: we remake what we like ourselves and copy nothing.
Everything below comes from the mod page's own description. Nobody has played it or watched it in-game yet, so how good
it looks is unknown.

## How its gore works

- **Voxel cuts:** a cut body is drawn as voxels and comes apart piece by piece. Flesh, bone and organs show inside the
  cut, and the wounds stay on the body.
- **Lost limbs stay lost:** no arms means it can't attack; no legs means it drags itself along.
- **Bleeds what it's made of:** skeletons, golems, slimes, blazes and machines are solid all the way through and bleed
  their own material.
- **Three gore levels, per player** (client config `goreLevel`):
  - 0 **clean**: cuts are cauterized. They glow, then cool to black, with sparks and smoke instead of blood.
  - 1 **normal** (default): ordinary blood and wounds.
  - 2 **extreme**: more blood, and a cut through the torso spills the guts.
- **Kills by spell:**
  - Severing Portal splits a target head to toe.
  - Sundering Portal splits a target at the waist.
  - Tentacles drag, drain and slam.
  - Unmaking turns a target to ash piece by piece.
  - Transmutation turns a target into butterflies, or tears it apart.
  - Shattered Reality throws everything in it into the void.
  - Fire spells (Supreme Ray, Demonic Breath, the Hydra) burn.
- **Bosses are spared** the portals, Unmaking and Transmutation by default, with a server toggle to allow it.

## What we already have

| Theirs | Ours |
|---|---|
| Split at the waist | `Dismember#halve` (axe or greatsword, torso, `HALVE_DAMAGE` 16): legs off, the top half drags itself by the arms and bleeds out in `HALVE_BLEED_OUT_TICKS` |
| No legs, drags itself | Andy 2026-11-14 ruling, in `Dismember`; `CrawlPose` |
| Limbs stay lost | `Dismember`, `SeveredPart`, `Dismembered`, carried onto Kitchen's corpse |
| Bleeds its own material | `BladeBlood` (splinters, sparks, goo; Stoneborn and some hollows don't bleed); skeletons chip bone |
| Guts | Kitchen `Gibs#gutsPulled` (scavengers), `Gibs` blast chunks |
| Per-player gore levels | `BloodSettings`: Off/Light/Normal/Heavy, presets Light/Cinematic/Brutal, Streamer safe |

## What's new to us (ideas for later)

1. **Cauterized cuts, a look of its own:** the cut face glows hot, cools to black, sparks and smoke, no blood.
   - As a **per-blow look**: fire, energy and heat blades (beams, Emberborn, a red-hot blade, the Flame Avatar).
   - Or as a **gore preset** beside Streamer safe: "no blood" that still looks violent, instead of grey blood.
2. **Cut faces with insides:** a severed stump or a halving shows a cross-section (meat, a ring of bone, an organ at the
   torso) that stays on the body and on the `SeveredPart`, instead of a flat stump. This is the biggest visual step
   up. It could be a texture on the stump faces in `Dismembered#stubPart`, not voxels.
3. **Head-to-toe split:** a vertical halving as well as the waist one: an overhead greatsword or axe chop, an execution,
   or a power cut. Both halves fall as ragdolls (they drop where they stand, #1020).
4. **Guts on a torso cut, at Brutal only:** a waist halving or a deep torso cut spills guts from the living body, not
   only from eaten corpses. It can reuse `Gibs` guts pieces.
5. **No arms, no attack:** check whether a mob that lost both arms still attacks (unknown, not found in `Dismember`).
   If it does, stop it, and maybe let it bite or kick.
6. **Ash, piece by piece:** an unmaking kill for dark or void powers. The body crumbles to ash part by part (limbs
   first) and leaves no corpse.
7. **Bosses spared by default** from instant-kill and split effects, with a toggle.

## Fits with Andy's rules

- "Blood toned down, no pixel blood": ideas 2 and 4 must stay modelled or textured, never pixel splats.
- Humanoids go down instead of dying unless beheaded or gibbed. A head-to-toe split (3) and ash (6) count as gibbing.
  **Stoneborn are never gibbed**, and royals can be gibbed but are recovered.
- Rubber-folk survive halving and mend, so a vertical split needs the same exception.
- Halved players bleed out in about 8 to 10 seconds.
- Butterflies (Transmutation) and the void throw are tied to Doctor Strange's look, so they're left out unless Andy
  wants them.

## Open questions for Andy

- Cauterized: a look for heat and energy blows, a gore preset, or both?
- Head-to-toe split: from weapons (which ones) or only from powers?
- Which of 1 to 7 should become Inbox tasks, and in what order?
