# Pod Parts: the space pod built from carried parts (Powers 0.18.49)

Andy (2026-10-30), on the SevTech Ages cross-reference: the pod should be a proper capsule "where you can disassemble
them and see the piece come off the model; parts for the pod are assembled separately with blocks and items like the
ships, then carried and placed onto the pod". Picked: four hull plates, and the pod becomes an entity (not a block).
Mockup: project files `art-overhaul/space-travel/pod-mockup.png`.

## What changes
- The pod is a `PodEntity` (`power/zfighter/pod`), about 2 blocks wide and 3.5 tall, drawn by our own `PodModel`
  (`client/power/zfighter/pod`). It stands where it lands, is ridden on a trip, and is what the raiders leave, the
  Patrol lands in, and the life-story starts wreck. The old `space_pod` block stays registered so saved worlds load:
  on chunk load / first tick a pod block becomes a complete `PodEntity` with its fuel (`ZFighterSpace.convertLegacy`).
- Every pod is a set of **slots** (`PodPart`): KEEL (1, the found piece), FIN x3, BELL (1), TANK (1), PLATE x4, CANOPY (1),
  MAST (1). Each slot is empty, sound or damaged. The model draws only the filled slots; damaged ones draw bent/cracked.
- **Building**: a part is a block (`PodParts`), carried in both arms with the existing carry (`CarryBlock.carriable`
  lists them). Looking at a pod entity within reach and releasing (right-click, the normal set-down) puts the part into
  its first free slot: `PodEntity.attach`. The keel is the one part that stands on its own: setting a keel block down
  on the ground makes a new bare `PodEntity` (keel only).
- **Taking apart**: hitting a pod with a hammer (Tinkers smithing hammer, any `#jaymospowers:hammers`, or the vanilla
  mace) pops the outermost filled slot (mast, canopy, plates, tank, bell, fins, keel last) off as a carried block in the
  hitter's arms when they are empty-handed enough, else as a falling block beside the pod: `PodEntity.detach`. A damaged
  part comes off as scrap (iron nuggets for metal, glass pane shards for the canopy) and the slot empties.
- **Flight**: `PodEntity.complete()` (every slot filled, none damaged) is needed to launch. A pod with one damaged fin or
  a cracked canopy may still launch but lands hard: the hitter takes fall damage and 1-2 random parts are knocked off.
  Fuel lives on the entity (0-12, same items as before) and needs a TANK; without a tank fuel can't be added.
- **No recipe for a pod**: there is no keel recipe. Keels come from raider bands (the kept pod is now a complete pod; the
  burnt-out pods leave a keel + 1-3 random sound parts each), from the Maw wrecks (each wreck now holds a wrecked
  `PodEntity`: keel + random parts, some damaged), from the Patrol (joining gives a complete pod), and from the life-story
  start wreck (a damaged pod missing its tank/canopy: the old 4-step mend becomes: find the parts).
- **Where parts are made** (no grid recipes for metal parts):
  - FIN (steel, 3 ingots), PLATE (iron, 4 ingots), MAST (iron, 2 ingots), KEEL never: `WeaponParts.Kind`s with
    `Assembly.ALONE` (cast at the casting table, assembled alone at the Tool Station), like the throwing star blank.
  - TANK: copper, 5 ingots, the same way (`SINGLE_COPPER`).
  - BELL: kiln. A copper block fired at full heat becomes a thruster bell (`Kiln.Recipe`).
  - CANOPY: Alchemy infusion (`data/jaymospowers/alchemy/infusion/pod_canopy.json`): centre glass block, components
    4 amethyst shards + 2 quartz, essences crystal/air, research `pod_canopy` (a new entry, taught by the Patrol or found
    in the Maw log).
- Sounds, particles: attach = anvil place + electric spark; detach = iron door + item break; hard landing = explode.

## Tracks (parallel, disjoint files)
- **Track A, the entity**: `pod/PodEntity` (synced slot masks, fuel, riding seat, interaction, legacy conversion,
  hard landing), `client/.../pod/PodModel` + `PodRenderer` + texture (tools/gen_pod.py), and switching every user of the
  block to the entity: `ZFighterSpace` (star map opens from the entity, `go` reads the entity's fuel), `ZFighterPodTrip`
  (ride the real entity, no block display), `ZFighterPatrol`, `ZFighterRaiders`, `ZFighterStarts`, `ZFighterFlightScreen`
  (draw the pod item or model), registration in `ZFighterPower.init` and `ZFighterPowerClient`.
- **Track B, the parts**: `pod/PodPart` is given (the contract); `pod/PodParts` (blocks + items for each part, block
  models/textures via tools/gen_pod_parts.py, carriable, placing onto a pod from the carry, hammer detach, scrap),
  `WeaponParts` kinds, the kiln recipe, the infusion recipe + research, loot (`chests/maw_wreck` gets parts), lang
  `lang/parts/zfighter_pod.json`, creative tab entries.
- Contract both compile against: `PodPart` and the public methods of `PodEntity` listed in its skeleton.

## Open questions (defaults taken)
- Hammer: any `#jaymospowers:hammers` item or a mace; sneaking while hitting is not required. (Default: plain hit.)
- A pod with no canopy can be ridden but not launched. (Default.)
- Patrol pods and raider pods in flight are complete pods drawn by the same model, no block display.
