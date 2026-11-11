# Plan: the band's makers on the Pale Moon

Andy (2026-11-11): "There should be an elder alien on the planet you go to develop the band, their society is extremely
advanced, (id like to import a kingdom on their planet that uses a futuristic aesthetic and is inhabited with the new
modeled species, i will look for inspiration but you can make models for it.) The band hunter was developed on the
moon/planet to get the band back, for the ruler who desires the power for himself." The planet is the Pale Moon.

Settled since: the species are the **Ocellan** (Andy's working name was "Galvan", which is taken; tiny, bug-eyed, robed, monocled, a high-class society, on the Gremlin's
model; PR #1323 draws the star forge smith as one, Kingdoms `GalvanBody`). The elder meant the band to be given away;
the ruler sent the hunter against that (Lore.md, *The hunter's orders*).

This is a plan only. Andy has answered all its open questions (2026-11-11); building waits for his go. Class names are the real ones.

---

## What is there now

- **The Star Forge** (`power/xenoform/BandForge`): each save picks one far world at random from `CHOICES` (Moon,
  Rustfall, Glacia, Cinder, Verdant) and keeps it in the `WORLD` attachment. The first time a band wearer walks that
  world, a stone hut with the forge is raised 40-64 blocks off (`AT`), with an alien smith (`SMITH_TAG`, drawn as a
  Ocellan by #1323) when Kingdoms and MCA are loaded. The band hums toward it. A reforge takes 4 star metal, a wreck core
  and 4 of Bulwark's dust, and gives the band its module slots (`BandUpgrades`).
- **The Pale Moon** (`dimension/moon.json`, one biome `moon_plains`): grey dust, a third of your weight
  (`ZFighterSpace.MOON_GRAVITY`), veiled ore and pale alloy (`farworld/FarWorld`), Endermen. No structures of its own.
- **The band hunter** (`power/xenoform/RivalHunter`): an MCA villager outcast with the rival band who comes some night
  for a band wearer in the Overworld, fights as a darkened alien, and drops his orders when beaten.
- **Kingdoms realms** live in the Overworld (with the stacked Aether as a height band, `Stacked.band`). A realm in its
  own dimension has never been done: the separate-dimension Aether's sky towns are towns without a kingdom.

## How it fits the lore

The canon says the Pale Moon has "nothing on it" (the raiders' tally) and pod crews' rumour that "somebody lives on
the Pale Moon" is not believed. So the Ocellan city is **hidden** (Andy, 2026-11-11: "hidden"): it lies **under the dust**, in a great
domed hollow beneath one crater, its doors shut to anyone without a band. The band "hums when it is near its maker's
work": that hum is what leads a wearer down. The Ocellan build in **pale alloy**, the moon's own metal, which explains
why the moon's ore is there at all. The Endermen are the one thing the Ocellan do not explain (open question 4).

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

## Track 2: the Ocellan city (Kingdoms)

**Files:** new `kingdoms/.../ocellan/OcellanCity` (layout, pieces), structure templates under
`data/jaymoskingdoms/structure/ocellan/`, `lang/parts/ocellan.json`, `ZFighterFolk` (Ocellan citizens).

- **Undersky**: one city per save, under the dust of a large crater some distance from where pods land, built like `SkyTowns`:
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
    false sky the Ocellan built themselves: light blue glass and lamps high overhead, with pale "clouds". From outside
    it is only a crater. Size (Andy agreed): about 150 blocks across, towers 60-80 blocks tall, the
    false sky just above the tallest.
- **Pieces:** the dome gate (shut until a band wearer comes near), the plaza with a tall ring instead of a bell, homes,
  a hall of records (tablets, the alien bodies the band holds shown as statues), **the elder's forge** (the Star Forge
  and the elder), **the ruler's court** (the seat), and a sealed workshop where the rival band was made.
- **Citizens:** MCA villagers given the Ocellan part (`ZFighterFolk.GALVAN`, from PR #1323, not merged yet), in robes; they speak only to band wearers
  and are aloof to anyone else. Guards are Ocellan in fitted suits rather than robes.
- **Endermen** (Andy, 2026-11-11: "they fight all the time, and steal important galvan blocks. the endermen have no
  idea what they do, they just seem to enjoy the mischief"): moon Endermen may pick up the city's own blocks (pale alloy
  lamps and panels, the false sky's lamps, forge parts), which vanilla Endermen never take, and carry them off over the
  dust; a gap left in a tower or a dark patch of sky shows where. Ocellan guards fight Endermen on sight, and Endermen
  pick fights with Ocellan. A player who brings a stolen block back to its gap, or kills the thief, earns the city's
  thanks (a small trade discount, or a line from the elder). Files: an `EndermanHoldable` tag entry for the Ocellan
  blocks (moon only, checked in a small mixin or goal), Ocellan guard targeting.
- **Not a realm** (Andy, 2026-11-11: "no war or trading with other kingdoms"): a city with a ruler and court, outside
  Kingdoms' realm machinery, so no war, raids, alliances or trade with Overworld kingdoms. Players can still trade
  with Ocellan citizens themselves.

## Track 3: the elder, the ruler and the hunter (Powers + Kingdoms)

**Files:** `BandForge` (elder talk), `BandUpgrades` (the forge check on `Action`), the band screen, `RivalHunter`, new `kingdoms/.../ocellan/OcellanCourt`, lang parts `xenoform` and
`ocellan`.

- **The elder, Ammet Thrum** ("the Old Maker"): an old Ocellan who made the band, at the forge. Talking: he knew the band would be found ("it was made
  to be given"), he reforges it as the smith does now, and every upgrade after it is his too (Andy, 2026-11-11: "all upgrades require a
  trip"). Read as: learning a module from its plan, fitting or swapping modules and buying band skills all happen at
  the elder's forge (`BandUpgrades.Action` is refused unless the player is near the forge, with a line saying the band
  must go back to its maker); away from it the band screen shows them but greys the buttons. Learning new aliens by
  scanning and tablets stays out in the world, since that is the band's own doing, not an upgrade. He never speaks
  against the ruler in the open; his lines hint.
- **The ruler, Sovereign Vorrel:** holds the court. He greets a band wearer warmly and asks for the band "for safe keeping". Refuse and
  he is polite and cold; from then on hunters come more often (`RivalHunter.COME_GAP` shortened for that player).
  **Hand it over** (Andy, 2026-11-11: "the ruler becomes a boss, and turns on you. starting a hard fight on your own.
  easier with still empowered friends."): he puts the band on and turns on you there in the court, and you have no band.
  The ruler fight is a boss (boss bar) who changes between the band's aliens as the band hunter does (`RivalAlien`,
  but with the player's own band and the aliens it had unlocked), tuned to be hard alone and fair for a group that still
  has its powers. His guards seal the court's doors while he lives. Beat him and the band falls from his wrist and is
  yours again (it still counts as the world's one Xeno-Band, `XenoBands`); die and he keeps it, and the court stays shut
  to you until you come back and win. The elder will not forge for a wearer who gave the band away until it is taken
  back.
- **The hunter:** his orders already say he was sent. New: a beaten hunter's orders carry the court's seal, which the
  city's gate guards know; the sealed workshop in the city is where the rival band was made. The rival band stays as
  now (the world's other band).
- A story beat (open question 8): bringing the hunter's orders to the elder, to show the ruler broke the elder's word.

## Track 5: the seat (later, after Tracks 1-3)

Andy (2026-11-11): "if he is given the band and killed, the new wearer can take the seat, becoming the king, and opening
intergalactic trade and warfare. The Oscellans had it figured out before."

- Kill the ruler while he wears the band (the hand-over fight, Track 3) and the one who picks the band up may sit in
  his seat in the court: they become the Ocellan's king (or queen), and the city takes them as its ruler.
- Only then does the city open to the worlds: **intergalactic trade and warfare**. While the Ocellan ruler holds the
  seat the city stays shut (no war or trade with kingdoms, Track 2); a player king opens it.
- What the king can do (Andy, 2026-11-11: "they can muster armies that attack overworld kingdoms with space pod
  invasions, or they can take the fight to the warrior born, drawing them to the moon, and having large sclae warfare
  on the moon itself"):
  - **Muster an army:** Ocellan guards in fitted suits, raised at the court for star metal and pale alloy, kept in
    barracks in Undersky.
  - **Pod invasions:** the king names an Overworld kingdom; the army comes down in pods near its seat and fights as a
    Kingdoms raid does (it uses Kingdoms' war and raid rules, as a realm with no land of its own on the Overworld).
    Beaten, the pods stay where they landed.
  - **War with the Warrior-born:** the king can call out the raider bands; they come to the Pale Moon in their pods and
    a large battle is fought on the dust outside Undersky, Ocellan army against raiders, waves and a raider chief at the
    end. Winning stops raider pod landings in the Overworld for a while.
  - Trade between worlds stays open as an idea for later.
- "The Oscellans had it figured out before": before Vorrel, the Ocellan traded and made war between the worlds from
  Undersky. A player king brings that back.

## Track 4: art (Prometheus / 3D modeller)

- The elder: a Ocellan with a longer, darker robe, a staff or tools at the belt, two monocles or a lens rig.
- The ruler: a Ocellan in a high collar and a heavier mantle, a circlet; his guards in fitted suits.
- Block textures for the pale alloy family and the dome glass.
- Waits for Andy's inspiration pictures (open question 3).

## Order

Two tracks at a time (CLAUDE.md). First **Track 1** (small, Powers only) with **Track 4** (art, can start on the elder
and ruler from the Ocellan model). Then **Track 2** (the big one), then **Track 3**, which needs the city's court, then **Track 5**.

---

## Open questions for Andy

1. ~~Old saves~~ Answered: no migration, the world is reset on release.
2. ~~Hidden or open?~~ Answered: hidden, under the dust behind a door only a band opens.
3. ~~Look~~ Answered: like Viltrum's cities (Track 2, Look).
4. ~~The Endermen~~ Answered: they and the Ocellan fight all the time; Endermen steal Ocellan blocks for the mischief (Track 2, Endermen).
5. ~~Kingdom or town~~ Answered: a city with a ruler and court; no war or trading with other kingdoms.
6. ~~The elder's upgrades~~ Answered: all upgrades require a trip to him (Track 3, the elder).
7. ~~The ruler's ask~~ Answered: yes; he puts it on, becomes a boss and turns on you (Track 3, the ruler).
8. ~~Ending~~ Answered: whoever kills the ruler while he wears the band can take his seat (Track 5).
9. ~~Names~~ Andy: "surprise me". Claude's picks (change any): the elder is **Ammet Thrum**, called the Old Maker;
   the ruler is **Sovereign Vorrel**; the city is **Undersky**. The people stay the Ocellan.
10. ~~Size~~ Answered: about 150 blocks across, towers 60-80 blocks, the false sky just above the tallest.
11. ~~Intergalactic trade and warfare~~ Answered: pod invasions of Overworld kingdoms, or war with the Warrior-born on the moon (Track 5).
12. ~~The Oscellans~~ Answered: the Ocellan are the elder's people (Lore.md's name in place of the working name "Galvan"). Before Vorrel they traded and warred between the worlds.
