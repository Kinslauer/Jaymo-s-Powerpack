# Blood Awakening: Per-Blood Upgrades Plan

Split of Andy's 2026-11-26 Blood Awakening pick list (`Picklists/Blood awakening per-blood picks.md`, 90 of 94 rows ticked + Claude's 2 Scalebound rows + Claude's 2 Emberborn retry rows) into **5 patches by heritage group, 2 tracks at a time**.

## Standing rules (every patch)
- **Adds on top of PR #2117's shared rule** (Andy, 2026-11-26): every per-blood upgrade stacks with the shared halved waits (`TreeLocks.AWAKENED_WAIT = 0.5`) and 1.5x heritage-key effect durations (`TreeLocks.AWAKENED_LONGER = 1.5`). Nothing replaces the shared rule.
- **Gated on Blood Awakening**: every upgrade applies only while `TreeLocks.awakened(player)` is true (Kingdoms `TreeLocks.awakened`, Powers `core.TreeLocks.awakened`).
- **Tuning numbers**: live in `static final` constants at the top of each class.
- **Verdant (5)**: push-button limb regrowth (`ZFighterLineage.regrowNow`) already shipped in PR #2117; left as-is.
- **Emberborn (6) retry** (Andy 2026-11-26 "Surprise me"; Claude's 1:22 PM pick): `ba-emberborn-rain` dropped ("retry on this one") and `ba-emberborn-dur` unticked; replaced by `ba-emberborn-steam` (**Steam stride**) and `ba-emberborn-edge` (**A forge-hot edge**). No armour mending in combat (#979).
- **Lore first**: any upgrade introducing new world-canon facts (e.g., Kitsune as very powerful sorcerers with blue foxfire; Obsidian's blast-rewinding ward; Hollow passing completely as human) gets a `(new)` placement paragraph in its PR description for Andy to approve into `Lore.md`.

---

## Patch 1 — Plain Folk, Mountain Folk & Elemental/Sky Core (14 bloods/looks, 28 rows)
**Status:** Built in this PR (`t3/blood-awakening-per-blood-patch-1`).

### Track 1A — Plain & Mountain Folk (Kingdoms)
1. **Heartlander** — Stroke of Fortune (`120s`, gift `LUCKY`, flaw `SOFT`)
   - `ba-heartlander-loot`: Luck gift raises loot quality on mob kills (extra awakened `Attributes.LUCK` bonus + bonus drop quality roll on entity kills via `LootTableEvents.MODIFY_DROPS`).
   - `ba-heartlander-cdr`: Critical strikes (falling melee hits and crit arrows) shave seconds off Stroke of Fortune's remaining cooldown.
2. **Sunfreckled** (Heartlander look 1) — Messenger's Dash (`15s`, flaw `HUNGRY`)
   - `ba-sunfreckled-dist`: Dash launches farther.
   - `ba-sunfreckled-cdr`: Additional cooldown reduction on top of shared 0.5x.
3. **Skymarked** (Heartlander look 2) — Unbreakable Stand (`60s`, gift `STURDY`, flaw `HEAVY_FOOTED`)
   - `ba-skymarked-dur`: Stand lasts longer on top of shared 1.5x.
   - `ba-skymarked-speed`: Heavy Footed speed penalty halved passively when awakened and Slowness removed during Unbreakable Stand.
4. **Stoneborn** — Shield Wall (`40s`)
   - `ba-stoneborn-kb`: While Shield Wall is up, grants knockback and explosion-knockback resistance.
   - `ba-stoneborn-dur`: Extends Shield Wall duration on top of shared 1.5x (both Resistance and the knockback-block window).
5. **Obsidian** (Stoneborn look 1) — Rune of Warding (`60s`, gift `WARDED`)
   - `ba-obsidian-aura` *(Andy's note: "should work like the wizard ward reversing damage")*: Rune of Warding lays an obsidian ward circle on the ground covering an area around the cast point; allies inside receive the ward's Absorption/Resistance, and any explosion inside the ward has its destroyed blocks (non-block-entity, no item drops) rewound back into place over a few seconds with a violet glyph/mote shimmer.
   - `ba-obsidian-dur`: Ward (and its ground zone) lasts longer on top of shared 1.5x.
6. **Magmavein** (Stoneborn look 2) — Mountain Leap (`20s`, gift `FEATHERFALL`)
   - `ba-magmavein-double`: Can leap twice in quick succession (second-leap window) before the cooldown starts.
   - `ba-magmavein-height`: Each Mountain Leap launches higher (respecting fall damage via existing Slow Falling).
7. **Earthling** — Second Wind (`60s`, flaw `HUNGRY`)
   - `ba-earthling-hunger`: Pauses the `HUNGRY` flaw for a brief window after casting Second Wind and clears vanilla Hunger.
   - `ba-earthling-heal`: Instant heal is larger.
8. **Inkmarked** (Earthling look 2) — Arcane Overflow (`75s`, gift `WARDED`, flaw `FRAIL`)
   - `ba-inkmarked-burst`: Releases a wider arcane shockwave damaging and pushing back nearby foes around the caster.
   - `ba-inkmarked-health`: Halves the `FRAIL` max-health penalty.
9. **Warriorborn** — War Cry (`60s`)
   - `ba-warriorborn-aura`: Buffs nearby allies (players, kingdom folk, owned pets) with Resistance in addition to Strength and Speed.
   - `ba-warriorborn-fear`: Frightens (`Fear.expose`) and drives back hostile mobs across a wider radius.

### Track 1B — Elemental & Sky Core (Kingdoms + Powers)
10. **Emberborn** — Forge Blessing (`60s`, flaw `HEAVY_WATER`) *(1:22 PM retry picks)*
    - `ba-emberborn-steam` (**Steam stride**): While Forge Blessing is up, water boils off in steam particles/hiss and `HEAVY_WATER` stops slowing or sinking the Emberborn (synced to client for `LivingEntityHeavyWaterMixin`).
    - `ba-emberborn-edge` (**A forge-hot edge**): While Forge Blessing is up, the held weapon sparks hot and melee hits briefly set foes alight with flame/lava sparks.
    - *(Dropped/unticked: `ba-emberborn-rain`, `ba-emberborn-dur`.)*
11. **Frostborn** — Frostbind (held `FrostPrison` + `FrostBeam`, gift `GLACIAL`, flaw `FIRE_FRAIL`)
    - `ba-frostborn-radius`: Held Frostbind (`FrostBeam`) grows an expanding frost aura around its impact point the longer left-mouse is held (chilling/freezing nearby foes and water in a widening radius); `FrostPrison` also chills nearby foes around the prisoner.
    - `ba-frostborn-fire`: While Frostbind is active (an active `FrostPrison` or actively channeling `FrostBeam`), `FIRE_FRAIL` fire damage penalty is eased.
12. **Stormborn** — Thunderclap (held `HeritageMoves`, flaw `WET_GROUNDED`)
    - `ba-stormborn-chain`: Thunderclap arcs chain lightning from the strike point/target to up to 2 nearby hostile mobs.
    - `ba-stormborn-wet`: `WET_GROUNDED` damage penalty does not apply while it is raining.
13. **Dawnborn** — Sunflare (held `HeritageMoves`)
    - `ba-dawnborn-blind`: Blinded mobs stay blinded longer.
    - `ba-dawnborn-undead`: Undead take direct radiant flare damage in addition to being ignited.
14. **Nightborn** — Shadowmeld (`45s`, gift `NIGHT_EYES`)
    - `ba-nightborn-vision`: While melded, Darkvision reaches twice as far (`darkvision_far.json`, 32 blocks instead of 16, synced to client).
    - `ba-nightborn-kill`: Killing a living mob while melded halves Shadowmeld's remaining cooldown.

---

## Patch 2 — Tideborn (All 16 Looks), Deepborn, Endborn, Divine & Dragonblood (9 bloods/looks + 16 Tideborn variants, 18 rows)

### Track 2A — Sea & Deep (Kingdoms)
15. **Tideborn** — Tidal Breath (`40s`, flaw `LANDBOUND`)
    - `ba-tideborn-source` *(Andy's note: "each look needs their own")*: Every Tideborn look (all 16: Shoalkin/base, Reefcrowned, Abyssal, Greyfin, Mantawing, Eightarm, Lanternjaw, Driftbell, Moray, Shellclaw, Spinepuff, Lionmane, Weedfrond, Swordbill, Frillback, Oldshell) gets its own distinct Tidal Breath water/sea effect (e.g. Shoalkin spring pool, Reefcrowned coral shield water, Abyssal dark-depth mist, Greyfin blood-scent surge, Mantawing hydro-glide burst, Eightarm ink cloud, Lanternjaw glowing lure pool, Driftbell stinging ring, Moray eel slip, Shellclaw shell-brace splash, Spinepuff spike puff, Lionmane venom mist, Weedfrond kelp veil, Swordbill jet lunge, Frillback bright brine wave, Oldshell heavy tide anchor).
    - `ba-tideborn-land`: `LANDBOUND` speed penalty eased while it is raining.
16. **Deepborn** — Tremorsense (`30s`, gift `DEEP_EYES`)
    - `ba-deepborn-range`: Larger Tremorsense detection radius.
    - `ba-deepborn-ore`: Highlights nearby ore veins through stone briefly (reusing the `HeritagePaths.oreSense` glint pattern).

### Track 2B — End, Celestial & Dragonblood (Kingdoms)
17. **Endborn** — Ender Step (`20s`)
    - `ba-endborn-double`: Two steps in quick succession before cooldown starts.
    - `ba-endborn-range`: Longer teleport range.
18. **Divine** — Halo Pulse (`45s`, flaw `SOFT`)
    - `ba-divine-heal`: Larger/broader ally heal pulse (including kingdom folk & pets).
    - `ba-divine-soft`: `SOFT` armour penalty reduced.
19. **Dragonblood (Emberscale)** — Fire Breath (`30s`)
    - `ba-dragonblood-trail`: Breath leaves a brief burning ground trail.
    - `ba-dragonblood-range`: Breath stream reaches farther.
20. **Goldscale** (Dragonblood look) — Terra Quake (`30s`, gift `STURDY`, flaw `HEAVY_FOOTED`)
    - `ba-goldscale-radius`: Larger quake radius.
    - `ba-goldscale-speed`: `HEAVY_FOOTED` speed penalty eased.
21. **Rimewyrm** (Dragonblood look) — Frost Breath (`30s`, gift `ICE_SCALES`, flaw `FIRE_FRAIL`)
    - `ba-rimewyrm-freeze`: Breath freezes/chills longer.
    - `ba-rimewyrm-fire`: `FIRE_FRAIL` penalty eased.
22. **Stormwyrm** (Dragonblood look) — Storm Breath (`30s`, gift `STORM_SCALES`, flaw `WET_GROUNDED`)
    - `ba-stormwyrm-chain`: Breath chains lightning to nearby mobs.
    - `ba-stormwyrm-wet`: `WET_GROUNDED` removed when awakened.
23. **Venomscale** (Dragonblood look) — Acid Breath (`30s`, gift `POISON_PROOF`, flaw `COLD_BLOODED`)
    - `ba-venomscale-pool`: Acid lingers as a damaging ground pool briefly.
    - `ba-venomscale-cold`: `COLD_BLOODED` penalties eased.

---

## Patch 3 — Sylvan, Fae & Elves (9 bloods/looks, 18 rows)

### Track 3A — Sylvan Base & Fae (Kingdoms)
24. **Sylvan (base)** — Root Snare (`35s`)
    - `ba-sylvan-slow`: Roots also apply mild slowness to mobs near snared targets.
    - `ba-sylvan-dur`: Roots persist longer on top of shared 1.5x.
25. **Dryad** — Verdant Bloom (`45s`, gift `PHOTOSYNTH`, flaw `TRIPLE_FIRE`)
    - `ba-dryad-heal`: Bloom restores more health.
    - `ba-dryad-grow`: More crops/saplings grown and faster growth ticks near the bloom.
26. **Changeling** — Mirror Shift (`20s`, gift `SNEAKY`, flaw `HOLLOW_SELF`)
    - `ba-changeling-dur`: Mirror shift stealth/speed lasts longer (leaves `BorrowedFaces.java` untouched while voice mimic PR #2248 runs).
    - `ba-changeling-self`: `HOLLOW_SELF` armour penalty eased.
27. **Sprite** — Phase Shift (`30s`, gift `GOSSAMER`, flaw `GLASS_CANNON`)
    - `ba-sprite-dur`: Phase lasts longer.
    - `ba-sprite-health`: `GLASS_CANNON` max-health penalty eased.
28. **Nymph** — Siren's Charm (`45s`, gift `AQUATIC`, flaw `BOUND_TO_WATER`)
    - `ba-nymph-charm`: Charm affects more kinds of mobs (including non-`Enemy` hostile/neutral mobs in range, wider pull/pacify).
    - `ba-nymph-water`: `BOUND_TO_WATER` dry timer extended longer before weakening.
29. **Faerie** — Faerie Bargain (`45s`, gift `LUCKY`, flaw `COLD_IRON`)
    - `ba-faerie-trade`: Bargain grants better villager trade discounts / extra Luck value while active.
    - `ba-faerie-iron`: `COLD_IRON` damage penalty eased.

### Track 3B — Elves (Kingdoms)
30. **High Elf** — Arcane Reflex (`25s`, gift `QUICK_HANDS`, flaw `FRAIL`)
    - `ba-highelf-reflex`: Stronger/more frequent reflex window & automatic dodge/parry chance.
    - `ba-highelf-health`: `FRAIL` max-health penalty eased.
31. **Moon Elf** — Moonlit Veil (`40s`, gift `MOONSIGHT`, flaw `SUN_WEAK`)
    - `ba-moonelf-dur`: Veil holds longer.
    - `ba-moonelf-sun`: `SUN_WEAK` sunlight penalty eased.
32. **Blood Elf** — Blood Frenzy (`60s`, gift `BLOODSCENT`, flaw `LESS_HEALING`)
    - `ba-bloodelf-dur`: Frenzy lifesteal window holds longer.
    - `ba-bloodelf-scent`: Low-health mobs detected/sensed from farther away.

---

## Patch 4 — Beastfolk & Kitsune Sorcery (8 bloods/looks, 16 rows)

### Track 4A — Beastfolk Base & Wild Looks (Kingdoms)
33. **Beastfolk (base / Tawny)** — Feral Sprint (`30s`, gift `SWIFT`, flaw `HUNGRY`)
    - `ba-beastfolk-speed` *(Andy's note: "each look needs their own")*: Tawny gets a stronger Feral Sprint burst, and every Beastfolk look gets its own awakened movement/sprint twist (synced cleanly with each look's own kit).
    - `ba-beastfolk-hunger`: `HUNGRY` flaw pauses briefly right after sprinting/using the move.
34. **Arachnid** — Web Snare (`25s`, gift `VENOM`, flaw `FIRE_FRAIL`)
    - `ba-arachnid-dur`: Webs hold longer.
    - `ba-arachnid-venom`: Venom deals stronger/longer poison damage.
35. **Avian** — Wind Burst (`25s`, gift `FEATHERFALL`, flaw `HOLLOW_BONES`)
    - `ba-avian-push`: Burst also blasts nearby mobs back farther.
    - `ba-avian-health`: `HOLLOW_BONES` max-health penalty eased.
36. **Canine** — Howl (`60s`, gift `SWIFT`, flaw `RAVENOUS`)
    - `ba-canine-aura`: Howl buffs nearby allies (players, kingdom folk, wolves) with stronger Speed/Strength.
    - `ba-canine-hunger`: `RAVENOUS` hunger penalty eased.
37. **Feline** — Pounce (`15s`, gift `NINE_LIVES`, flaw `HATES_WATER`)
    - `ba-feline-dist`: Pounce leaps farther.
    - `ba-feline-water`: `HATES_WATER` penalties eased.

### Track 4B — Kitsune & Reptile Kin (Kingdoms)
38. **Kitsune** — Foxfire (`40s`, gift `LUCKY`, flaw `SPIRIT_FRAIL`)
    - `ba-kitsune-flames` *(Andy's note: "flames should be blue, and kitsune are VERY powerful sorcerers")*: Foxfire erupts with blue soul-fire/blue arcane flames (more orbiting/seeking blue foxfire bolts that burn and blind foes, using `Fx` blue motes/orbs + `SOUL_FIRE_FLAME`) AND awakened Kitsune gain a real Sorcery/spellcasting boost (stronger spell power / mana flow, plus a `(new)` lore paragraph establishing Kitsune as very powerful sorcerers wielding blue foxfire).
    - `ba-kitsune-health`: `SPIRIT_FRAIL` max-health penalty eased.
39. **Serpen** — Shed Skin (`60s`, gift `POISON_PROOF`, flaw `COLD_BLOODED`)
    - `ba-serpen-clear`: Shed Skin clears extra negative states (freezing, slowness/wither/bad effects, bleeding/blight where applicable) and grants stronger post-shed protection.
    - `ba-serpen-cold`: `COLD_BLOODED` penalties eased.
40. **Scalebound** — Shed Skin (`60s`, gift `ARCHER`, flaw `COLD_BLOODED`) *(Claude's 2 additions, both ticked)*
    - `ba-scalebound-mirror`: Shares Serpen's Shed Skin upgrades (`ba-serpen-clear`, `ba-serpen-cold`).
    - `ba-scalebound-aim`: After shedding, the `ARCHER` gift is stronger for a short while (truer, harder-hitting, faster bow draws).

---

## Patch 5 — Made Bloods (Biobuilt & Rubberfolk) & The Hollow (7 bloods/looks, 14 rows)

### Track 5A — Biobuilt & Rubberfolk (Kingdoms + client renders)
41. **Biobuilt** — Engulf (held/toggle `BioBuilt` / `HeritageHold`)
    - `ba-biobuilt-dmg` *(REPLACED per Andy's note: "it should turn into a grab attack")*: Awakened Biobuilt can trigger Engulf on standing/living foes as a tail grab attack that seizes and drains them rather than only absorbing downed/crippled targets.
    - `ba-biobuilt-cdr`: Release/budding recovery wait reduced further on top of shared 0.5x.
42. **Rubberfolk** — Engulf (held/toggle `RubberFolk` / `HeritageHold`)
    - `ba-rubberfolk-bounce`: While engulfing, incoming projectiles are deflected/bounced away.
    - `ba-rubberfolk-cdr` *(Andy's note: "should also turn into a glob you shooot out and attaches to a player, sapping them while attached. must be removed by someone else by attacking it")*: Shorter release wait AND can fire a stretchy pink glob projectile that latches onto a target player/foe, sapping health/stamina over time until another player or ally attacks the glob to knock it off (synced visual + hit-to-remove logic).

### Track 5B — The Hollow (Kingdoms)
43. **Hollow (Pallid / base)** — Grave Chill (`35s`, gift `UNDEAD_STOMACH`, flaw `LESS_HEALING`)
    - `ba-hollow-dmg`: Grave Chill deals real frost/necrotic damage to nearby foes in addition to Slowness/Weakness.
    - `ba-hollow-heal` *(REPLACED per Andy's note: "you should be able to blend in with humans, they cant tell you're undead at all.")*: Awakened Hollow completely pass as human — suppresses Hollow prejudice, Hollow sightings/blight tells, undead-detecting villager remarks, and holy/golem reactions so humans cannot tell they are undead at all.
44. **Zombie** — Undying Hunger (`40s`, gift `TOUGH`, flaw `SUN_DECAY`)
    - `ba-zombie-heal`: Undying Hunger heals more (both burst and lifesteal / eating restore).
    - `ba-zombie-sun`: `SUN_DECAY` sunlight damage eased.
45. **Skeleton** — Conscript the Dead (`60s`, gift `ARCHER`, flaw `BRITTLE`)
    - `ba-skeleton-count`: Summons additional skeleton conscripts.
    - `ba-skeleton-health`: `BRITTLE` max-health / fragility penalty eased.
46. **Wraith** — Spectral Phase (`60s`, gift `GHOSTLY`, flaw `SUN_HOLY`)
    - `ba-wraith-dur`: Phase lasts longer.
    - `ba-wraith-sun`: `SUN_HOLY` sunlight and holy damage penalty eased.
47. **Reaper** — Soul Harvest (`30s`, gift `SOUL_TAKER`, flaw `HALF_HEALING`)
    - `ba-reaper-souls`: Soul Harvest and Soul Taker reap more health/souls per foe/kill.
    - `ba-reaper-heal`: `HALF_HEALING` penalty eased.
