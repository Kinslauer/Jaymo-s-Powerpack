# Ragdolls (Jaymo's Kitchen 0.5.1)

Andy asked (2026-10-20) for ragdolls like **Sable: Ragdolls**, **Sable: Ragdoll Corpse** and **Ragdoll Reactions** in
place of the pack's corpses, and for living players too. All three mods are All Rights Reserved (by leonardoinc22,
NeoForge 1.21.1, built on the Sable physics engine), so nothing of theirs is copied or decompiled: this is our own
physics, written from how they behave.

## What players see
- **Corpses are ragdolls.** A mob (or player) that dies crumples where it stood instead of keeling over: it falls under
  gravity, limbs flopping, folds over whatever it lands on (steps, ledges, fences, slabs), slides off edges, tumbles down
  stairs, floats in water. Thrown, dropped or tipped out of a cart, it tumbles and lands as physics says. Hitting it,
  rummaging it, chopping it, scavengers biting it, blasts and anyone walking or riding over it shove it about.
- **Living players go limp** (0.5.3) when they take a big hit or blast (8+ damage before armour), land hard (a fall
  that really did 6+ damage: no ragdoll when Smoke, slow falling or a power's cushion cancels it), crash into a wall on
  an elytra, or get run over by a cart, boat or charging rider. Speed alone never counts, so dashes, zips and launches
  from powers don't knock you down. They tumble as a ragdoll, the camera pulls back to third person, they can't move
  or use anything, and they get back up once they stop (1.5 to 7 seconds). Fall and crash damage are unchanged. A
  player who dies limp leaves exactly that body.
- Carried, slung and carted corpses still hang limp in the arms as before (the old Limp/Flop springs).

## How it works
- **Engine** (`kitchen/.../ragdoll/`, no Minecraft code, checked by `tools/ragdoll_sim`):
  - `RagdollRig`: a skeleton from the body's standing size alone, one of three shapes: HUMANOID (shoulders, hips,
    neck, head, hands, feet), QUADRUPED (four torso corners, snout, muzzle, four feet), BOX (a rigid block). Shape by
    entity tag (`jaymoskitchen:ragdoll_humanoid` / `_quadruped` / `_box`), else guessed from width and height.
  - `RagdollBody`: Verlet point masses, rigid sticks (the torso is rigid), each limb kept in a cone (legs lean forward:
    they curl up far more than back), every point pushed out of block collision boxes with friction and a small
    bounce, sub-steps so nothing tunnels, water lifts and lava drags, walkers and vehicles kick it, sleeps when still.
- **World** (`LevelRagdollWorld`): block collision shapes, living things/boats/minecarts/carts moving through, fluids.
- **Sync** (`RagdollNet`, `RagdollSnapshot`): whoever simulates a body sends its points every tick it moves (shorts,
  1/256 block, about 80 bytes); others draw them between the last two ticks (`client/ragdoll/ClientRagdolls`).
- **Drawing** (`client/ragdoll/RagdollPose`): the model is drawn standing, then moved by T(torso now) R T(-torso
  standing); each limb's ModelPart gets the rotation that points it along its bone. Applied after every other pose
  (`RagdollModelMixin`, priority 3000).
- **Corpses**: simulated on the server (`corpse/CorpseRagdoll`), saved with the corpse, sent to watchers.
- **Living players**: simulated on their own client (movement is client-side), relayed by the server
  (`LivingRagdolls`), which keeps the last body for the corpse. Jaymo's Powers vetoes it while a power holds you.

Tuning numbers are the `static final` constants at the top of each class.
