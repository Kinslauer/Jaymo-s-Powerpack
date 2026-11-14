---
type: picklist
source: Five mods Andy linked for cutscene ideas (Jujutsu Expansion, Iron's Spells - Furnace Unsealed, Cursed Fate - Cursed Discharge, Overlord RPG mod, Freecam)
url: https://modrinth.com/mod/freecam · CurseForge pages for the other four (CurseForge blocks automated reads, see "Couldn't check")
licence: mixed, see each section
status: open
created: 2026-11-10
tags: [inbox, picklist, mods, cutscenes]
---

# Jujutsu, Cursed Fate, Iron's Spells, Overlord and Freecam: fit check

This replaces the first draft under `Inbox/jujutsu-mod-checklist`. You asked for it so you can feed it to Fable for
better in-game cutscenes: the grue and an undead/orc legion pouring out, beating back what looked like an unstoppable
force of overworld hearthlanders.

Only Freecam runs on our pack (26.3 Fabric). The other four are Forge/NeoForge add-ons on older versions, so they're
ideas only. Facts below were read on 2026-11-10 from Modrinth's API and search listings. Nothing was tested in game.

## 1. Freecam: the one we can ship

`ship as-is` · MIT · Fabric, Forge and NeoForge · has a 26.3 build · source: github.com/MinecraftFreecam/Freecam

- [ ] **Add it to packMods unchanged** `S` ^fc-ship
  A client-side free camera: fly through blocks while your body stays put. MIT, so shipping it is fine.
  For us: the cheapest way to line up cutscene shots in a real world, take the pictures, and hand them to Fable.
  Notes:

- [ ] **Decide who may use it on the friends' server** `S` ^fc-server
  It can show what's behind walls (bases, loot, mobs in caves). Check on a test launch whether a server can block it, or only each player's own config.
  For us: creative/ops only, or everyone?
  Notes:

- [ ] **Check it doesn't fight our own cameras** `S` ^fc-cameras
  UltimateFilm, the giant camera, the stance wheel and First-person Model all move the camera too.
  For us: one launch with each to see who wins; turn Freecam off while a film plays if it breaks one.
  Notes:

## 2. Cursed Fate: Cursed Discharge, ideas only

`ideas only` · by Aero_AQ · add-on for Jujutsu Kaisen: Cursed Fate · Forge 1.20.1 · licence not readable (CurseForge)

- [ ] **A "discharge" burst as a film beat** `S` ^cf-discharge
  It adds one technique: Cursed Discharge, a raw blast of cursed energy. The same author also made the Ten Shadows
  add-on (summoned shadow beasts), and Cursed Fate has Disaster Tides and other technique add-ons.
  For us: the moment the legion breaks through, a wave of dark energy that knocks the front rank flat (our blast
  knockback + BlastFright), drawn in grue-black rather than JJK blue.
  Notes:

- [ ] **Summoned beasts from shadows** `M` ^cf-shadows
  Ten Shadows: creatures rise out of the caster's shadow.
  For us: the grue's creatures rising from the ground's shadows at night instead of from a portal (see the
  portal note at the end).
  Notes:

## 3. Iron's Spells: Furnace Unsealed, ideas only

`ideas only` · by Tminlese · add-on for Iron's Spells 'n Spellbooks · Forge 1.20.1 · licence not readable

- [ ] **Two spells at once (swap the second with a key)** `M` ^is-dual
  The first draft said it adds a primary/secondary spell pair, swapped with Y.
  For us: our casters already pick from a wheel; a held "off-hand" spell would be a Kingdoms Arcana change, not a
  cutscene one. Only take it if you want it in play too.
  Notes:

- [ ] **"Wheel of Adaptation": a foe that adapts to what hits it** `M` ^is-adapt
  Each time the same kind of damage lands, it does less.
  For us: the "unstoppable" force in your pictures. A champion of the legion whose resistance grows per damage type
  would make the hearthlanders swap weapons mid-fight, which reads well on film.
  Notes:

- [ ] **Relic items that power you up when eaten (Sukuna's Finger)** `S` ^is-relic
  For us: close to our corruption and forbidden-arts rules already. Skip unless you want a grue relic.
  Notes:

## 4. Jujutsu Expansion, ideas only

`ideas only` · by Altra_Z · a Jujutsu Craft add-on · Forge 1.20.1 (probably) · licence not readable

- [ ] **New cursed techniques** `S` ^je-techniques
  The page wasn't readable from here, so the technique list is missing.
  For us: paste the technique names from the page under Notes and I'll match each to a power or spell we already
  have, or mark it as new.
  Notes:

## 5. Overlord RPG mod, ideas only

`ideas only` · by blue_space02 · NeoForge 1.21.1 · needs Curios API and TerraBlender · about 40 downloads · licence not shown

- [ ] **An undead lord's tomb with floor guardians** `M` ^ov-tomb
  Based on the anime Overlord: the Great Tomb with floor guardians, the Katze Plains, ice dragon lairs, and a
  guidebook on first join.
  For us: the Stormborn Tomb and its Draugr are already our undead hall. A ruler of the dead holding court there,
  with a guardian per floor, is a strong source for the legion's leader (and for the still-open `tomb-boss` design).
  Notes:

- [ ] **The legion marching out in ranks** `M` ^ov-legion
  For us: our war hosts and Draugr already march; the film just needs them in ranks behind one leader, with the
  hearthlanders' line (guards, knights, Z Fighters) bending back.
  Notes:

- [ ] **Skip the biomes and dimension parts** `S` ^ov-skip
  TerraBlender means new biomes. Your rule: no new realms or portals, and demon content lives inside the Nether.
  Notes:

## Before it goes to Fable

- [ ] **The portal in your pictures** `S` ^fable-portal
  Your standing rule is no new portals or realms. On film the legion can still pour out of something: a Nether
  rift that opens in the overworld for the scene only, the grue's darkness itself, or a tomb's doors. Pick one.
  Notes:

- [ ] **One film or a set of short ones** `S` ^fable-length
  Our films (UltimateFilm) are short and skippable. A long battle would be a new kind of scene: a world event film
  played to everyone nearby.
  Notes:

## Couldn't check

CurseForge refuses automated reads, so the licence, exact version and full feature list of Jujutsu Expansion,
Furnace Unsealed and Cursed Discharge come from search listings and the first draft, not the pages themselves. None of
it changes a verdict: all three are Forge add-ons for mods that don't run on 26.3, so they're ideas only whatever the
licence says.

## Already ours (not re-suggested)

Downed and wounds (Powers), classes and smithing (Kingdoms), the Draugr and the Stormborn Tomb, the grue, blast
knockback and blast fright, war hosts, will staredowns, ultimate films.
