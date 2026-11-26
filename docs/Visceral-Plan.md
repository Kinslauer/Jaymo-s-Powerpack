# Visceral picks: plan

Andy's 42 picks (2026-11-24) from the Visceral: Ragdoll & Gore pick list
(https://claude.ai/artifact/3NMtJdzmLuH1WyzbYV672f, db `picks/andy`). Visceral is All Rights Reserved, Forge 1.20.1,
no public source: **ideas only**, our own code, names and art. Nothing is copied.

Andy's notes, which win over the row text:
- Kill moment: "mostly for the last mob in an encounter, so you dont get hit during a moment".
- Piñata: "activates automatically in the candy caves" (Alex's Caves' Candy Cavity, `RubberFolk.CANDY_CAVITY`).
- Hurt stances: "should help with hunting deer and gazelle (who should be just as skittish)".
- Takedown: "given to brute on spawn" (Kingdoms' Brute class, `BruteRage`).
- Healing a fresh mob corpse: "the druid does this, no?" It doesn't yet (`Druids` has no corpse rite), so it becomes a Druid rite.
- Lore: "The leech's kit" paragraph approved (Lore.md, after "Hedge poisons and weighted nets").

Not picked: death camera (Limbo already does it), body bag, blood test tools, the regrow draught.

Every row follows the three living-things rules where it touches animals, the medieval canon, and the Blood setting
(`BloodSettings`: anything gory is hidden when blood is Off or streamer safe is on). Tuning numbers go in `static final`
constants at the top of each class.

Andy's PixelGore picks (2026-11-24, https://claude.ai/artifact/QJJyJXFrL5jxYxcL9kPRZJ, db `picks/andy`; PPs PixelGore is
All Rights Reserved, ideas only) are folded into the tracks below as `pg_` rows: 16 of 18 (not the carved voxel bodies, not
the shovel carry). His notes:
- Mobs drop what they wore: "we already have this, but it disappears if they lose the limb, this should leave them whole
  unless they are eaten or burned in lava".
- Shovel (not picked as carry): "shovels should be able to bury corpses, and you should see npcs doing it. it requires a
  hole at least two blocks deep and right clicking them with the shovel should fill the dirt back in". Added as `pg_bury`.

Tracks are split so no two edit the same files. Two run at a time. One PR for the whole set, one change note per mod.

---

## Track 1: Cuts and skin (Powers, `core/wounds`, `client/wounds`)
Files: `WoundMarks`, `WoundMarksLayer`, `Dismember` (depth only), `Dismembered` (first-person stump).
- **vi_swingcut** Cuts along the real swing: a CUT mark keeps the swing's angle across the part (and the next part it
  crosses), from Better Combat's swing direction (`LimbSwingMixin` / `LimbBoxes` already know where it crossed). The layer
  draws the line at that angle.
- **vi_depth** Cut depth by tier and Sharpness: shallow (wood, stone), normal (iron, gold), deep (diamond, netherite);
  Sharpness adds a step. Depth scales the mark's width, the bleed (`Bleeding`) and the sever chance in `Dismember`.
- **vi_bullet** Bullet entry and exit: a new mark kind HOLE (small entry, bigger ragged exit on the far side) for
  `BladeBlood.bullet` hits.
- **vi_heal** Marks close as the part heals: a mark's life follows its part's health fraction in `Wounds`, not only time.
- **vi_stump** First-person stump: a lost arm draws its stump in first person (Dismembered's stump art on the hand renderer).
- **pg_fparm** Your own wounded arm in first person shows its WoundMarks.
- **pg_insides** Bone and ribs under deep cuts: a deep cut draws what's under it by BodyPlan part (ribs, skull, limb bone, gut).
- **pg_sever** Cuts on one limb add up (a running total kept with Wounds); past a threshold the limb tears off in
  `Dismember` even if no single blow was big enough.

## Track 2: How hits land (Powers client + Kitchen ragdolls)
Files: new `HitReactions` (server cue + client pose), `MobHurtStances`, `MobRagdolls` (falls), `KillMoment`, a Reduced
motion option next to `BloodSettings`.
- **vi_react** The struck part flinches, strength = damage / target weight (box volume), clamped; light mobs get a
  shove too.
- **vi_stance** Hurt stances for mobs: hunch (body under half), sway (head under half), limp (leg), hanging arm, by
  BodyPlan part health. Hurt prey (deer, gazelle and other skittish grazers) also flee sooner and limp slower, so a wounded
  animal can be tracked and caught: the feedback loop is the blood trail plus the limp.
- **vi_falls** Mobs knocked limp by a high fall (players already are, `LivingRagdolls`), through `MobRagdolls`.
- **vi_hobble** A low leg cut gives a hobble (slow, no sprint, no jump) for a while instead of the crawl.
- **vi_killcam** Kill moment: on the killing blow of the last hostile within 24 blocks that was fighting you, a short
  slow-motion (client-side view slow, about 0.6 s) with a thin letterbox. Off with Reduced motion. Never while another
  hostile is targeting you.
- **vi_motion** Reduced motion setting: turns off camera kicks, flinch shake and the kill moment.

## Track 3: Combat rules (Powers combat, Kingdoms Brute)
Files: `Breaks` (arm loss), new `Disarms`, `Bleeding` (credit), `Carry` + new `Takedown`, `SoundKinds`.
- **vi_disarm** A hard located hit on the hand or forearm (Wounds hit routing) knocks a held weapon loose to the ground.
  Players too, rarer. Chance up with damage, down with gauntlets.
- **vi_arms** Mobs missing one arm deal half damage; no arms, no melee (they flee or kick).
- **vi_bleedcredit** Whoever opened the wound that kills by bleeding gets the kill (loot, advancements, bounties).
- **vi_takedown** Grab, take down and throw: with enough brawn (Carry's brawn count), grab a living mob, wrestle it down
  (it goes limp for a moment), or throw it. Kingdoms' Brute class gets it from the start.
- **vi_sounds** Hit and break sounds by material: bone, metal, slime, flesh, with subtitles (our own sounds).
- **pg_blastside** Blasts only harm the parts with a clear line to the blast centre (`BlastGore`, Kitchen `Gibs` asks the same).
- **pg_pellets** A shotgun's pellets from one shot (TaCZ) join into one spread wound and one shove.
- **pg_aimdown** Crawlers and the downed get a lying hitbox for the crosshair and arrows (`LimbBoxes`).

## Track 4: Armour (Powers, `ArmourWear`, `Blows`, armour render)
- **vi_holes** Holes worn into armour: hits wear marks into the piece where they land, stored on the item, drawn on the
  worn armour; an anvil repair clears them.
- **vi_openface** Open-face helmets: a blow that lands on the face (front, eye height) skips helmets without a visor.
- **vi_helmoff** A heavy head blow can knock a helmet off to the ground; a huge one breaks it.
- **vi_deflect** Each material's share against cut / stab / blunt (leather soaks blunt, iron turns blades, chain turns
  cuts but not stabs, diamond and netherite most things).
Runs after Track 1 (both touch `Dismember`).

Order: 2 + 5 first, then 1 + 3, then 4 + 6a, then 6b + 7, then 8.

## Track 5: Blood in the world (Powers `core/blood`, `client/blood`)
- **vi_surfaces** Glass, metal and ice bead blood (smaller drops, wipe off quickly); dirt and sand soak it (darker,
  dries faster); snow turns pink.
- **vi_drip** Blood sprayed up on a ceiling drips down; pools run down stairs and slabs.
- **vi_water** Blood in water: a short-lived cloud that drifts with the flow; heavy bits sink.
- **vi_burn** Fire and lava burn stains away; rain carries pools over ledges.
- **vi_screen** Blood on your screen from a big hit close by, for a second or two.
- **vi_creature** More creature bloods: creepers green, endermen purple, skeletons bone chips, golems sparks, slimes goo.
- **vi_presets** Gore presets (Light, Cinematic, Brutal) on the Blood button, setting blood, gore and hit reactions.
- **vi_safe** Streamer safe: grey dust for blood, no dismemberment drawn, living mobs drawn whole.

## Track 6a: How corpses look and go (Kitchen `corpse`, client)
- **vi_char** Burning bodies blacken, and a long burn leaves a bone stage before the ash pile.
- **vi_frozen** Death in powder snow leaves an ice statue that shatters into ice shards when struck.
- **vi_aging** Corpses stiffen, then grey, then sink slowly into the ground as they rot.
- **vi_ender** Endermen fade out a part at a time instead of leaving a corpse.
- **vi_slime** Slimes squish when hit and burst into goo on death (and still split).
- **vi_skel** Skeleton corpses collapse into loose bones.
- **vi_hang** Tie a corpse with a lead to a fence post: it hangs there and keeps without rotting.
- **vi_pinata** Piñata: bodies burst into sweets (a gib kind). Server setting, and always on in the Candy Cavity.
- **pg_rotblotch** Rot goes in blotches: patches darken before they fall away, outer flesh first, last stage the bones.
- **pg_crush** A falling anvil or block flattens the body under it (squashed corpse pose, flattened gibs).
- **pg_debris** Small scraps knocked off tumble, bounce and slide to a stop by material, then sink away.
- **pg_budget** A cap on ragdolls moving at once; the oldest settle and freeze. Lower in low-end mode.

## Track 6b: Handling the dead (Kitchen `corpse` + `butchery`, Kingdoms `Graves`)
- **pg_feed** The dead are eaten bite by bite: zombies crouch over a body and tear at it (wolves and other meat-eaters too,
  through Kitchen's `Scavengers`); the body visibly shrinks to bones. A fed zombie isn't hunting.
- **pg_gut** Gutting with a blade: hold use on a corpse, the cut drawn along the belly stroke by stroke, pried open, insides out.
- **pg_yield** What butchering gives depends on what's left of the body (cut away, rotten, burnt, torn).
- **pg_opened** A gutted body lies open, ribs showing, insides beside it.
- **pg_gear** A mob's worn and held gear stays whole when it loses a limb (today it vanishes); it's lost only if the body
  is eaten or burns in lava.
- **pg_bury** Any corpse can be buried: lay it in a hole at least two blocks deep, then right-click with a shovel to fill
  the dirt back in (extends `Graves`/`Burial`, which today lower a dead player's body straight in). Villagers bury
  their own dead the same way (gravediggers dig, lay, fill), which fits "The nights at the grave".

## Track 7: The leech's kit (Kingdoms, Alchemy + Druids)
Lore: "The leech's kit" (approved). Thorns of bone and glass, filled at a brewing stand or the apothecary; leeches and
apothecaries sell them, hedge witches sell the dark ones.
- **vi_kit_heal** Staunch (stops bleeding), Numbing (no limp, stagger or knockdown, 2 min), Quickening (speed and haste
  30 s, then a crash), Restorative (full heal). Use on yourself, another, or a corpse.
- **vi_kit_dark** Corroding (pits a body, melts a corpse), Stripping (corpse to skeleton), Rotting (corpse rots in
  seconds), Raising (a corpse with its head rises undead). Using them counts as the forbidden arts (`ForbiddenArts`).
- **vi_kit_knock** Knockout draught (limp 15 s) and a blowgun with sleep darts.
- **vi_kit_mix** Mixing two draughts makes a third (frenzy, a minute you can't die, a corpse to a skeleton), taught by
  advancements.
- **vi_revive** A Druid rite: a fresh corpse with its head comes back alive (not undead) within a few minutes of death.

## Track 8: Mob and weapon reader (Powers, ops only)
- **vi_reader** A screen that lists how each mob was read (parts, weight, blood kind) and each weapon (cut, stab, blunt),
  lets an op change them, and saves to a config file read at start.
- **pg_guess** Better guesses for modded mobs: blood and body from tags, the class they extend, or their id.

---

## Checks
Each track compiles (Gradle, `compileJava compileClientJava` per mod) and gets an independent verifier agent that reads
the diff against its rows. Nothing is played before merge (Andy tests by merging).
