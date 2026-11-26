---
type: picklist
source: Blood Awakening per-blood upgrades — YOUTH delegated to Quill, 2026-11-26 (Claude approved)
url: 
licence: n/a — pack design doc, not implemented
status: open
created: 2026-11-26
tags: [inbox, picklist, kingdoms, powers, blood-awakening]
---

# Blood Awakening — per-blood upgrade pick list

## Shared rule (already in, PR #2117)

Learned: every wait on a blood's move is halved (cooldowns, Kindle's heat, taking a foe in, the ice prison twice a day).
And what a heritage-key move gives lasts 1.5x (Kingdoms `TreeLocks.awakened` / `TreeLocks.wait` / `AWAKENED_WAIT` / `AWAKENED_LONGER`; Powers `core/TreeLocks.wait`).

## Open question (needs Andy's ruling, decided per move)

When Andy ticks a per-blood upgrade, does it **add to** the shared rule for that blood, or **replace** it? E.g. if the shared rule halves a 60s cooldown to 30s, and a ticked upgrade says "cheaper cost / shorter cooldown," does the blood get both (halved then cheaper) or just the upgrade?

Mark each row below `add` or `replace` — or leave it blank and Andy decides per move.

## Heritages (bloodlines)

### 1. Heartlander — Stroke of Fortune (120s cooldown) · gift Lucky · flaw Soft

- [ ] **Luck bleeds into loot** `M` ^ba-heartlander-loot
  While awakened, the Luck gift also raises loot quality on kills, not just the move's own roll.
  For us: stronger/longer version of the shared 1.5x. add or replace?
  Notes:

- [ ] **Crit refunds part of the cooldown** `M` ^ba-heartlander-cdr
  A lucky critical strike shaves seconds off Stroke of Fortune's wait.
  For us: cheaper cost / second step. add or replace?
  Notes:

### 2. Stoneborn — Shield Wall (40s) · no gift · no flaw

- [ ] **Wall also blocks knockback** `S` ^ba-stoneborn-kb
  While Shield Wall is up, the player resists being pushed back.
  For us: new use. add or replace?
  Notes:

- [ ] **Wall lasts 1.5x longer on the shared rule** `S` ^ba-stoneborn-dur
  Simply extends the existing duration under the awakened bonus.
  For us: stronger/longer version. add or replace?
  Notes:

### 3. Tideborn — Tidal Breath (40s) · no gift · flaw Landbound

- [ ] **Breath creates a water source** `M` ^ba-tideborn-source
  Using Tidal Breath leaves a small water pool where the player stands.
  For us: new use. add or replace?
  Notes:

- [ ] **Landbound softened in rain** `S` ^ba-tideborn-land
  The Landbound flaw eases while it is raining.
  For us: cheaper cost / flaw reduction. add or replace?
  Notes:

### 4. Sylvan — Root Snare (35s) · no gift · no flaw

- [ ] **Snare slows nearby mobs too** `M` ^ba-sylvan-slow
  Roots also apply a mild slowness to mobs near the snared target.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Roots persist longer** `S` ^ba-sylvan-dur
  The snare holds longer under the 1.5x awakened bonus.
  For us: stronger/longer version. add or replace?
  Notes:

### 5. Verdant — Stretch (held, 0s cooldown) · no gift · no flaw

The Verdant's push-button regrowth (`ZFighterLineage.regrowNow`) is already built; leave it unless Andy says otherwise.

- [ ] (skip unless Andy requests)

### 6. Emberborn — Forge Blessing (60s) · no gift · flaw Heavy Water

- [ ] **Blessing works in rain** `S` ^ba-emberborn-rain
  Forge Blessing is not cancelled or weakened by water/rain.
  For us: flaw reduction. add or replace?
  Notes:

- [ ] **Blessing stacks with the 1.5x duration** `S` ^ba-emberborn-dur
  The blessing's effect lasts longer when awakened.
  For us: stronger/longer version. add or replace?
  Notes:

### 7. Frostborn — Frostbind (held, 0s cooldown) · gift Glacial · flaw Fire Frail

- [ ] **Frostbind radius grows the longer it is held** `M` ^ba-frostborn-radius
  Held frost spreads wider the longer the player holds the key.
  For us: second step / new use. add or replace?
  Notes:

- [ ] **Fire Frail eased while frozen** `S` ^ba-frostborn-fire
  While Frostbind is active, fire damage is reduced.
  For us: flaw reduction. add or replace?
  Notes:

### 8. Nightborn — Shadowmeld (45s) · gift Night Eyes · no flaw

- [ ] **Meld extends night vision range** `S` ^ba-nightborn-vision
  While melded, Night Eyes reaches farther.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Meld cooldown halves on a kill** `M` ^ba-nightborn-kill
  Killing a mob while melded shortens the next wait.
  For us: cheaper cost. add or replace?
  Notes:

### 9. Warriorborn — War Cry (60s) · no gift · no flaw

- [ ] **Cry buffs nearby allies** `M` ^ba-warriorborn-aura
  Allies near the player gain a short strength or resistance buff.
  For us: new use. add or replace?
  Notes:

- [ ] **Cry fear radius larger** `S` ^ba-warriorborn-fear
  Mobs flee from a wider area.
  For us: stronger version. add or replace?
  Notes:

### 10. Earthling — Second Wind (60s) · no gift · flaw Hungry

- [ ] **Second Wind removes hunger for a time** `M` ^ba-earthling-hunger
  After using Second Wind, the Hungry flaw is paused briefly.
  For us: flaw reduction / new use. add or replace?
  Notes:

- [ ] **Heal more on use** `S` ^ba-earthling-heal
  The instant heal is larger.
  For us: stronger version. add or replace?
  Notes:

### 11. Stormborn — Thunderclap (held, 0s cooldown) · no gift · flaw Wet Grounded

- [ ] **Clap arcs to nearby mobs** `M` ^ba-stormborn-chain
  The thunderclap jumps from the first target to one or two nearby mobs.
  For us: second step / new use. add or replace?
  Notes:

- [ ] **Wet Grounded removed in rain** `S` ^ba-stormborn-wet
  The flaw does not apply while it is raining.
  For us: flaw reduction. add or replace?
  Notes:

### 12. Dawnborn — Sunflare (held, 0s cooldown) · no gift · no flaw

- [ ] **Flare blinds longer** `S` ^ba-dawnborn-blind
  Blinded mobs stay blinded longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Undead take extra flare damage** `M` ^ba-dawnborn-undead
  Zombies, skeletons and the like take more from Sunflare.
  For us: new use. add or replace?
  Notes:

### 13. Endborn — Ender Step (20s) · no gift · no flaw

- [ ] **Two steps before cooldown** `M` ^ba-endborn-double
  The player can step twice in quick succession before waiting.
  For us: second step / cheaper cost. add or replace?
  Notes:

- [ ] **Longer throw range** `S` ^ba-endborn-range
  The ender pearl equivalent flies farther.
  For us: stronger version. add or replace?
  Notes:

### 14. Dragonblood — Fire Breath (30s) · no gift · no flaw

- [ ] **Breath leaves a fire trail** `M` ^ba-dragonblood-trail
  The fire stream leaves burning ground behind briefly.
  For us: new use. add or replace?
  Notes:

- [ ] **Breath stream longer** `S` ^ba-dragonblood-range
  The fire reaches farther.
  For us: stronger version. add or replace?
  Notes:

### 15. Deepborn — Tremorsense (30s) · gift Deep Eyes · no flaw

- [ ] **Sense range larger** `S` ^ba-deepborn-range
  Vibrations are detected from farther away.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Detect ores through stone** `M` ^ba-deepborn-ore
  Tremorsense highlights nearby ore veins through walls briefly.
  For us: new use. add or replace?
  Notes:

### 16. Divine — Halo Pulse (45s) · no gift · flaw Soft

- [ ] **Pulse heals nearby allies** `M` ^ba-divine-heal
  The halo pulse restores a little health to players near the user.
  For us: new use. add or replace?
  Notes:

- [ ] **Soft flaw reduced** `S` ^ba-divine-soft
  The armor penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 17. Biobuilt — Engulf (held, 0s cooldown) · no gift · no flaw

- [ ] **Engulf damages more per tick** `M` ^ba-biobuilt-dmg
  The engulfing effect deals more damage over time.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Release cooldown shorter** `S` ^ba-biobuilt-cdr
  The time between engulf uses is reduced.
  For us: cheaper cost. add or replace?
  Notes:

### 18. Rubberfolk — Engulf (held, 0s cooldown) · no gift · no flaw

Same move as Biobuilt; upgrades can mirror or diverge from Biobuilt's.

- [ ] **Engulf bounces projectiles** `M` ^ba-rubberfolk-bounce
  While engulfing, incoming projectiles are deflected.
  For us: new use. add or replace?
  Notes:

- [ ] **Release cooldown shorter** `S` ^ba-rubberfolk-cdr
  The time between engulf uses is reduced.
  For us: cheaper cost. add or replace?
  Notes:

### 19. Beastfolk — Feral Sprint (30s) · gift Swift · flaw Hungry

- [ ] **Sprint faster** `S` ^ba-beastfolk-speed
  Feral Sprint grants a higher speed burst.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Hungry flaw pauses after sprint** `M` ^ba-beastfolk-hunger
  The hunger penalty is reduced right after sprinting.
  For us: flaw reduction. add or replace?
  Notes:

### 20. Hollow — Grave Chill (35s) · gift Undead Stomach · flaw Less Healing

- [ ] **Chill damages more** `M` ^ba-hollow-dmg
  Grave Chill deals more damage to nearby mobs.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Less Healing reduced** `S` ^ba-hollow-heal
  The healing penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

## Looks (variants)

### 21. Sunfreckled — Messenger's Dash (15s) · no gift · flaw Hungry

- [ ] **Dash travels farther** `S` ^ba-sunfreckled-dist
  The dash covers more ground.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Dash cooldown lower** `S` ^ba-sunfreckled-cdr
  The dash can be used more often.
  For us: cheaper cost. add or replace?
  Notes:

### 22. Skymarked — Unbreakable Stand (60s) · gift Sturdy · flaw Heavy Footed

- [ ] **Stand lasts longer** `S` ^ba-skymarked-dur
  The unbreakable stance holds longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Heavy Footed eased** `S` ^ba-skymarked-speed
  The speed penalty is smaller while standing.
  For us: flaw reduction. add or replace?
  Notes:

### 23. Obsidian — Rune of Warding (60s) · gift Warded · no flaw

- [ ] **Ward covers an area** `M` ^ba-obsidian-aura
  The warding protects nearby allies, not only the user.
  For us: new use. add or replace?
  Notes:

- [ ] **Ward lasts longer** `S` ^ba-obsidian-dur
  The rune stays up longer.
  For us: stronger/longer version. add or replace?
  Notes:

### 24. Magmavein — Mountain Leap (20s) · gift Featherfall · no flaw

- [ ] **Leap twice before cooldown** `M` ^ba-magmavein-double
  Two leaps in quick succession before waiting.
  For us: second step / cheaper cost. add or replace?
  Notes:

- [ ] **Leap higher** `S` ^ba-magmavein-height
  Each Mountain Leap reaches higher.
  For us: stronger version. add or replace?
  Notes:

### 25. Goldscale — Terra Quake (30s) · gift Sturdy · flaw Heavy Footed

- [ ] **Quake radius bigger** `M` ^ba-goldscale-radius
  The ground slam affects a wider area.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Heavy Footed eased** `S` ^ba-goldscale-speed
  The speed penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 26. Rimewyrm — Frost Breath (30s) · gift Ice Scales · flaw Fire Frail

- [ ] **Breath freezes longer** `S` ^ba-rimewyrm-freeze
  Frozen mobs stay frozen longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Fire Frail eased** `S` ^ba-rimewyrm-fire
  The fire weakness is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 27. Stormwyrm — Storm Breath (30s) · gift Storm Scales · flaw Wet Grounded

- [ ] **Breath chains lightning** `M` ^ba-stormwyrm-chain
  Lightning arcs from the first target to nearby mobs.
  For us: second step / new use. add or replace?
  Notes:

- [ ] **Wet Grounded removed** `S` ^ba-stormwyrm-wet
  The flaw does not apply.
  For us: flaw removal. add or replace?
  Notes:

### 28. Venomscale — Acid Breath (30s) · gift Poison Proof · flaw Cold Blooded

- [ ] **Acid lingers as a pool** `M` ^ba-venomscale-pool
  The acid leaves a damaging pool on the ground briefly.
  For us: new use. add or replace?
  Notes:

- [ ] **Cold Blooded eased** `S` ^ba-venomscale-cold
  The cold weakness is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 29. Inkmarked — Arcane Overflow (75s) · gift Warded · flaw Frail

- [ ] **Overflow burst bigger** `M` ^ba-inkmarked-burst
  The arcane blast covers a wider area.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Frail reduced** `S` ^ba-inkmarked-health
  The max-health penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 30. Dryad — Verdant Bloom (45s) · gift Photosynth · flaw Triple Fire

- [ ] **Bloom heals more** `M` ^ba-dryad-heal
  Verdant Bloom restores more health.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Plants grow faster after bloom** `S` ^ba-dryad-grow
  Crops and saplings near the bloom grow quicker.
  For us: new use. add or replace?
  Notes:

### 31. Changeling — Mirror Shift (20s) · gift Sneaky · flaw Hollow Self

- [ ] **Mirror lasts longer** `S` ^ba-changeling-dur
  The mirror image sticks around longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Hollow Self eased** `S` ^ba-changeling-self
  The armor penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 32. Sprite — Phase Shift (30s) · gift Gossamer · flaw Glass Cannon

- [ ] **Phase lasts longer** `S` ^ba-sprite-dur
  The player stays phased longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Glass Cannon eased** `S` ^ba-sprite-health
  The max-health penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 33. Nymph — Siren's Charm (45s) · gift Aquatic · flaw Bound To Water

- [ ] **Charm affects more mobs** `M` ^ba-nymph-charm
  More kinds of mobs can be charmed.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Bound To Water relaxed** `M` ^ba-nymph-water
  The player can stay away from water longer before the flaw bites.
  For us: flaw reduction. add or replace?
  Notes:

### 34. Faerie — Faerie Bargain (45s) · gift Lucky · flaw Cold Iron

- [ ] **Bargain gives better trades** `M` ^ba-faerie-trade
  The bargain yields better prices or extra items.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Cold Iron eased** `S` ^ba-faerie-iron
  The weakness to cold iron is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 35. High Elf — Arcane Reflex (25s) · gift Quick Hands · flaw Frail

- [ ] **Reflex triggers more often** `M` ^ba-highelf-reflex
  The dodge/reflex chance is higher.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Frail reduced** `S` ^ba-highelf-health
  The max-health penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 36. Moon Elf — Moonlit Veil (40s) · gift Moonsight · flaw Sun Weak

- [ ] **Veil lasts longer** `S` ^ba-moonelf-dur
  The moonlit veil holds longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Sun Weak eased** `S` ^ba-moonelf-sun
  The sunlight penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 37. Blood Elf — Blood Frenzy (60s) · gift Bloodscent · flaw Less Healing

- [ ] **Frenzy lasts longer** `S` ^ba-bloodelf-dur
  The frenzy state holds longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Bloodscent range bigger** `M` ^ba-bloodelf-scent
  Low-health mobs are detected from farther away.
  For us: stronger version. add or replace?
  Notes:

### 38. Arachinid — Web Snare (25s) · gift Venom · flaw Fire Frail

- [ ] **Webs last longer** `S` ^ba-arachnid-dur
  Webs stay in place longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Venom stronger** `M` ^ba-arachnid-venom
  Venom deals more damage over time.
  For us: stronger version. add or replace?
  Notes:

### 39. Avian — Wind Burst (25s) · gift Featherfall · flaw Hollow Bones

- [ ] **Burst pushes farther** `M` ^ba-avian-push
  The wind knockback sends mobs farther.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Hollow Bones eased** `S` ^ba-avian-health
  The max-health penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 40. Canine — Howl (60s) · gift Swift · flaw Ravenous

- [ ] **Howl buffs nearby allies** `M` ^ba-canine-aura
  Allies near the player gain a short speed or strength buff.
  For us: new use. add or replace?
  Notes:

- [ ] **Ravenous eased** `S` ^ba-canine-hunger
  The hunger penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 41. Feline — Pounce (15s) · gift Nine Lives · flaw Hates Water

- [ ] **Pounce leaps farther** `S` ^ba-feline-dist
  The pounce covers more distance.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Hates Water eased** `S` ^ba-feline-water
  The water penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 42. Kitsune — Foxfire (40s) · gift Lucky · flaw Spirit Frail

- [ ] **Foxfire makes more flames** `M` ^ba-kitsune-flames
  More foxfire flames appear per use.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Spirit Frail eased** `S` ^ba-kitsune-health
  The max-health penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 43. Serpen — Shed Skin (60s) · gift Poison Proof · flaw Cold Blooded

- [ ] **Shed Skin clears more debuffs** `M` ^ba-serpen-clear
  Shedding removes extra negative effects.
  For us: new use / stronger version. add or replace?
  Notes:

- [ ] **Cold Blooded eased** `S` ^ba-serpen-cold
  The cold weakness is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 44. Zombie — Undying Hunger (40s) · gift Tough · flaw Sun Decay

- [ ] **Hunger heals more** `M` ^ba-zombie-heal
  Eating restores more health.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Sun Decay eased** `S` ^ba-zombie-sun
  The sunlight penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 45. Skeleton — Conscript the Dead (60s) · gift Archer · flaw Brittle

- [ ] **More skeletons raised** `M` ^ba-skeleton-count
  Conscript the Dead summons additional undead.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Brittle eased** `S` ^ba-skeleton-health
  The max-health penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 46. Wraith — Spectral Phase (60s) · gift Ghostly · flaw Sun Holy

- [ ] **Phase lasts longer** `S` ^ba-wraith-dur
  The player stays phased longer.
  For us: stronger/longer version. add or replace?
  Notes:

- [ ] **Sun Holy eased** `S` ^ba-wraith-sun
  The holy/sunlight penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

### 47. Reaper — Soul Harvest (30s) · gift Soul Taker · flaw Half Healing

- [ ] **Harvest takes more souls** `M` ^ba-reaper-souls
  More souls are collected per kill.
  For us: stronger version. add or replace?
  Notes:

- [ ] **Half Healing eased** `S` ^ba-reaper-heal
  The healing penalty is smaller.
  For us: flaw reduction. add or replace?
  Notes:

## Notes

No upgrades are implemented before Andy selects them. This list is only ideas for Andy to tick. When Andy ticks a row, note whether it adds to or replaces the shared rule for that blood.
