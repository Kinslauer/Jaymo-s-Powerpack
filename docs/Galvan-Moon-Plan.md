# Plan: the band's makers on the Pale Moon

Andy (2026-11-11): "There should be an elder alien on the planet you go to develop the band, their society is extremely
advanced, (id like to import a kingdom on their planet that uses a futuristic aesthetic and is inhabited with the new
modeled species, i will look for inspiration but you can make models for it.) The band hunter was developed on the
moon/planet to get the band back, for the ruler who desires the power for himself." The planet is the Pale Moon.

Settled since: the species are the **Galvan** (tiny, bug-eyed, robed, monocled, a high-class society, on the Gremlin's
model; PR #1323 draws the star forge smith as one, Kingdoms `GalvanBody`). The elder meant the band to be given away;
the ruler sent the hunter against that (Lore.md, *The hunter's orders*).

This is a plan only. Nothing is built until Andy answers the open questions at the end. Class names are the real ones.

---

## What is there now

- **The Star Forge** (`power/xenoform/BandForge`): each save picks one far world at random from `CHOICES` (Moon,
  Rustfall, Glacia, Cinder, Verdant) and keeps it in the `WORLD` attachment. The first time a band wearer walks that
  world, a stone hut with the forge is raised 40-64 blocks off (`AT`), with an alien smith (`SMITH_TAG`, drawn as a
  Galvan by #1323) when Kingdoms and MCA are loaded. The band hums toward it. A reforge takes 4 star metal, a wreck core
  and 4 of Bulwark's dust, and gives the band its module slots (`BandUpgrades`).
- **The Pale Moon** (`dimension/moon.json`, one biome `moon_plains`): grey dust, a third of your weight
  (`ZFighterSpace.MOON_GRAVITY`), veiled ore and pale alloy (`farworld/FarWorld`), Endermen. No structures of its own.
- **The band hunter** (`power/xenoform/RivalHunter`): an MCA villager outcast with the rival band who comes some night
  for a band wearer in the Overworld, fights as a darkened alien, and drops his orders when beaten.
- **Kingdoms realms** live in the Overworld (with the stacked Aether as a height band, `Stacked.band`). A realm in its
  own dimension has never been done: the separate-dimension Aether's sky towns are towns without a kingdom.

## How it fits the lore

The canon says the Pale Moon has "nothing on it" (the raiders' tally) and pod crews' rumour that "somebody lives on
the Pale Moon" is not believed. So the Galvan city is **hidden** (Andy, 2026-11-11: "hidden"): it lies **under the dust**, in a great
domed hollow beneath one crater, its doors shut to anyone without a band. The band "hums when it is near its maker's
work": that hum is what leads a wearer down. The Galvan build in **pale alloy**, the moon's own metal, which explains
why the moon's ore is there at all. The Endermen are the one thing the Galvan do not explain (open question 4).

---

## Track 1: the forge goes to the Pale Moon (Powers)

**Files:** `BandForge`, `lang/parts/xenoform.json` (the hum and smith lines).

- `CHOICES` becomes the Moon alone (kept as a constant so it can be reopened).
- Old saves: none to handle. Andy (2026-11-11): "doesnt matter, we will reset the world on release."
- Without Kingdoms (no city), the forge is raised as now, but on the Moon: the stone hut becomes a pale alloy shelter
  half sunk in a crater wall.
- With Kingdoms, Track 2's city holds the forge and Powers stops raising its hut there (Kingdoms sets the forge's `AT`
  through `PowersCompat` when it raises the city).
- The band's hum gets a second stage: on the Moon, near the hidden city, it says the band "knows this place".

## Track 2: the Galvan city (Kingdoms)

**Files:** new `kingdoms/.../galvan/GalvanCity` (layout, pieces), structure templates under
`data/jaymoskingdoms/structure/galvan/`, `lang/parts/galvan.json`, `ZFighterFolk` (Galvan citizens).

- One city per save, under the dust of a large crater some distance from where pods land, built like `SkyTowns`:
  lots on a grid round a central plaza, every piece named so Kingdoms can read it.
- **Look** (Andy, 2026-11-11: "kind of like the viltrumite cities", a still of Viltrum from *Invincible*; our own
  buildings from the idea, nothing traced): tall, slender white towers that lean and curve as they rise, cut into
  long panels by dark seams with rows of small lit windows (cyan and warm yellow); gardens on the tower tops and
  ledges; raised ring roads and rails that sweep round the towers on pillars; low shell-domed halls; green park strips
  between white plazas.
  - **Palette:** pale alloy (new block family: block, smooth panel, pillar, lamp) and white concrete, calcite and
    quartz for the walls; black and grey concrete with tinted glass for the seams; cyan stained glass over sea lanterns
    and warm yellow froglights for windows; moss, azalea and grass for the gardens. No wood or cobble.
  - **Under the dust:** towers need room, so the city stands in a vast hollow under the crater, and its roof is a
    false sky the Galvan built themselves: light blue glass and lamps high overhead, with pale "clouds". From outside
    it is only a crater. (Open question 10: how big.)
- **Pieces:** the dome gate (shut until a band wearer comes near), the plaza with a tall ring instead of a bell, homes,
  a hall of records (tablets, the alien bodies the band holds shown as statues), **the elder's forge** (the Star Forge
  and the elder), **the ruler's court** (the seat), and a sealed workshop where the rival band was made.
- **Citizens:** MCA villagers given the Galvan part (`ZFighterFolk.GALVAN`, from PR #1323, not merged yet), in robes; they speak only to band wearers
  and are aloof to anyone else. Guards are Galvan in fitted suits rather than robes.
- **Realm or town?** Phase 1: a town with a ruler and court, not a Kingdoms realm (no war, raids or trade with Overworld
  realms, like the sky towns). Phase 2, if Andy wants it: realms that carry a dimension. (Open question 5.)

## Track 3: the elder, the ruler and the hunter (Powers + Kingdoms)

**Files:** `BandForge` (elder talk), `RivalHunter`, new `kingdoms/.../galvan/GalvanCourt`, lang parts `xenoform` and
`galvan`.

- **The elder:** an old Galvan who made the band, at the forge. Talking: he knew the band would be found ("it was made
  to be given"), he reforges it as the smith does now, and later upgrades are his (open question 6). He never speaks
  against the ruler in the open; his lines hint.
- **The ruler:** holds the court. He greets a band wearer warmly and asks for the band "for safe keeping". Refuse and
  he is polite and cold; from then on hunters come more often (`RivalHunter.COME_GAP` shortened for that player).
  Hand it over and the band is lost (open question 7: should that be allowed at all?).
- **The hunter:** his orders already say he was sent. New: a beaten hunter's orders carry the court's seal, which the
  city's gate guards know; the sealed workshop in the city is where the rival band was made. The rival band stays as
  now (the world's other band).
- A story beat (open question 8): bringing the hunter's orders to the elder, to show the ruler broke the elder's word.

## Track 4: art (Prometheus / 3D modeller)

- The elder: a Galvan with a longer, darker robe, a staff or tools at the belt, two monocles or a lens rig.
- The ruler: a Galvan in a high collar and a heavier mantle, a circlet; his guards in fitted suits.
- Block textures for the pale alloy family and the dome glass.
- Waits for Andy's inspiration pictures (open question 3).

## Order

Two tracks at a time (CLAUDE.md). First **Track 1** (small, Powers only) with **Track 4** (art, can start on the elder
and ruler from the Galvan model). Then **Track 2** (the big one), then **Track 3**, which needs the city's court.

---

## Open questions for Andy

1. ~~Old saves~~ Answered: no migration, the world is reset on release.
2. ~~Hidden or open?~~ Answered: hidden, under the dust behind a door only a band opens.
3. ~~Look~~ Answered: like Viltrum's cities (Track 2, Look).
4. **The Endermen:** what are they to the Galvan? Strays from the End who wander up, the Galvan's old servants, or
   something the Galvan made and lost?
5. **Kingdom or town:** a city with a ruler and court that can't war or trade with Overworld kingdoms (recommended, for
   now), or a full Kingdoms realm you can raid, ally with or conquer?
6. **The elder's upgrades:** does the elder do only the reforge (as the smith does now), or do later band upgrades
   (more slots, band skills, new aliens) also need a trip back to him?
7. **The ruler's ask:** can a player actually hand the band to the ruler, and what happens then (the band is gone; the
   ruler becomes a boss who wears it)?
8. **Ending:** should there be a way to bring the ruler down (with the elder's help, or the Patrol's), or does he stay
   on his seat?
9. **Names:** the elder's and the ruler's names, and the city's. ("Galvan" is your working name; no line in the game
   says it yet.)
10. **Size:** how big a city? Recommended: about 150 blocks across, towers up to 60-80 blocks, in a hollow whose
    false sky sits just above the tallest tower (the moon's build height allows it). Bigger is grander but slower to
    generate.
