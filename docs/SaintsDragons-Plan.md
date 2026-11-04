# Jaymo's Kingdoms: the lesser dragons (after Saint's Dragons)

Andy, 2026-11-04: "we NEED these dragons" (Saint's Dragons, CurseForge 1358098, by Leon Saint).

## Why we rebuild instead of shipping it
- Saint's Dragons 0.9.88 is built for Minecraft **1.20.1 only** (Forge + Fabric, GeckoLib 4.8). The pack is 26.3, so
  the jar can't load. There is no 26.3 build.
- Its licence (`LICENSE.md` in github.com/LilRicefield/saints-dragons) is dual:
  - **Code: MIT.** Java, build scripts and JSON data may be ported and changed. We keep the MIT notice (below).
  - **Art and audio: All Rights Reserved.** Textures, `.geo.json`/`.bbmodel` models, animations and sounds may only be
    used inside the unmodified mod, and expressly not in other dragon mods. Some effects are bought unTied Games
    assets (`CREDIT.md`), also not ours to reuse.
- So: we port its behaviour (moves, flight, riding, taming, breeding, spawning rules), and every model, texture,
  animation and sound is our own, drawn in the pack's style (cuboid vanilla-style models, `tools/gen_<species>.py`,
  16px textures with `#variable` faces, see the 26.3 texture-variable note). No names of their art files, no traced
  silhouettes. Their species names stay (names aren't art), credited to Leon Saint on the pack page and in-mod.

## How they fit beside the wind dragons
Our five wind dragons (`docs/Dragons-Plan.md`) are the great dragons of the four winds and their keeper. These eight
are the **lesser dragons**: beasts of a single land, not of the winds. They tame and ride the same way as the wind
dragons (trust meter, saddle, momentum flight, own stamina, piggyback rider) so players learn one system.
- Built on the same base, `WindDragon`, with hooks: `flies()` false makes a walker (grazes, fights on its feet, ridden
  like a horse with R for its own move); swimmers and the floater get their own hooks in their patches.
- Ids keep "dragon" in them (`jaymoskingdoms:raevyx_dragon` etc.) so Dragonblood wakes for them too.
- Every one rolls a subtle look like the heritages (`DragonLook`): hide shade, horns, spikes, marking glow.
- **Wild ones are monsters, not bosses**, except Ignivorus and Nulljaw. They count as monsters for world tier like
  any mob; Ignivorus and Nulljaw are monsters of legend (no tier unlock, like ogres).
- No new dimension or portal. Nulljaw lives in our existing Aether (`jaymospowers:aether`), as its boss.
- Lore (only as oblique hints in villager talk and books, never screen text): the lesser dragons are the wind
  dragons' far kin, bred down when Aeolus bound the winds.

## The eight
| Dragon | Gait | Home (wild) | Nature and moves (ported from MIT code) | Bonds faster with |
|---|---|---|---|---|
| **Raevyx** | flyer | meadows, windswept hills, groves, cherry groves; comes with thunderstorms | lightning beam (drains its own charge, regrows), chain lightning, dive impact, horn gore, ground rend, roar, can call a storm | Stormborn, Wind Control |
| **Ignivorus** | flyer, huge (the Kintsugi wyvern) | anywhere in the Nether | the boss: fire breath, fireballs, magma pillars, stomp, body slam, wing swipe, roar; a last-stand ultimate that costs it health. Young hatch small and grow when fed | Emberborn, Notus tamers |
| **Cindervane** | flyer, small | mountains, hills, badlands | quick fire drake: bite, double bite, slash-grab carry, magma volley, burning body that sets attackers alight | Emberborn |
| **Volitans** | flyer + swimmer | oceans (beaches, shores as fallback); hunts from under water | poison breath, poison ball, burrows into sand/dirt and bursts out, claws, horn gore, ultimate | Tideborn, Verdant |
| **Nulljaw** | floater, huge | the Aether sky (Andy, 2026-11-04: "an aether boss that just flies around, and can find you anywhere but heaven") | the Aether's boss, untameable: never lands, roams the Aether sky, blinks forward to bite. Once it has marked you (you hurt it, or it sights you in the Aether) it hunts you into any dimension, blinking in near you; it can't reach the Other World (the afterlife site, `ZFighterAfterlife.inOtherWorld`) and loses you there. A boss bar, `c:bosses`, a monster of legend for world tier | (none, it can't be tamed) |
| **Stegonaut** | walker | jungles, plains; also lush caves | plated, slow, very tough (armour 15); eats through blocks with its jaw, chin slam, ground slam that raises stone pillars, a roar that buffs its allies and rider. The easy first tame | Stoneborn |
| **Atroxiia** | walker + swimmer | snowy plains, ice spikes, cold biomes | serpent frost dragon on two arms and its tail (no hind legs), nemes head with gold: slither, slam, swipe, underwater bite, precise strike (stun), devastating sweep, Gungnir stab, Helheim quake (big stun), frost walker trail | Frostborn |
| **Varasuchus** | walker + swimmer | its own roost on beaches and swamps | crocodile dragon, blocky head, a tail long enough to wrap to its front, rears bipedal when raging: bites, claws, tail lash, horn gore, slash barrage, phase shift (a short dodge through), tailguard (tail parry, ties into our guard/parry) | Tideborn |

Taming foods follow the source's choices where they made sense (meat for most, fish for the swimmers) and are
listed in the taming tooltip only once you've seen the species, never as a lore line.

## Not taken
- Their small mobs (Moop, Mossback, Latcher, Winged, Whettled, the Draconian swarm waves) and Ivy Oleander the dragon
  merchant: later, if Andy wants them. Their dragonheart/worldroot ores and Draconic Crucible likewise.
- GeckoLib: our models are vanilla cuboid models with hand-written animation, like the wind dragons.

## Beside the eight
- **Aetherion**, a star dragon Andy sent (public domain, CC0; not Saint's Dragons'), rebuilt on its body plan with new
  wings and head, flies over the Aether (Andy: "Aether sky dragon, plus Nulljaw"; Nulljaw stays the Aether's boss).
  Bite, starlight breath beam, Cataclysm of falling stars. In the same PR as patches 1, 2 and 5.

## Patches (each its own PR, merged in order; versions set to master + 1 right before merging)
0. This plan.
1. Walker hooks on `WindDragon` (the wind dragons unchanged). Stegonaut (walker) end to end as the proof:
   model, look rolls, spawning, taming, riding, moves.
2. Atroxiia + Varasuchus (walker/swimmer, tailguard on our parry). Done in the same PR as patch 1; Varasuchus spawns
   wild on beaches and swamps for now, its own roost structure can come with Ignivorus's in patch 5.
3. Raevyx + Cindervane (flyers, storm spawning).
4. Volitans (flyer/swimmer, burrow) + Nulljaw (the Aether boss that hunts you anywhere but the Other World).
5. Ignivorus. Done early in the same PR, on Andy's call ("put the effects onto Kintsugi's model then put that in
   the nether"): drawn as eanncrod's Kintsugi wyvern (CC BY 4.0, as Eurus and Aeolus) in lava hides, it flies about
   anywhere in the Nether and only fights whoever hurts it: fire breath, fireball volleys, a line of magma pillars.
   Still to come: its roost and its growing young, the stomp, body slam, wing swipe, roar and last-stand ultimate.
Previews (front/side renders of each model) go in `/mnt/project-files/art-overhaul/lesser-dragons/` before each PR.

## Credit
Behaviour ported from Saint's Dragons by Leon Saint, MIT License:

> Copyright (c) 2025 Leon Saint. Permission is hereby granted, free of charge, to any person obtaining a copy of this
> software and associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
> conditions: The above copyright notice and this permission notice shall be included in all copies or substantial
> portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

None of its art, models, animations or sounds are used.
