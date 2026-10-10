# Plan for 0.8: the user's playtest changes

Based on a full read of the 0.7.1 jar. Class names are the real ones in `src/`. Player-facing text goes in `lang/parts/<power>.json`. Default keys: R = Lift / Ability 1, G = Ability 2, V = Ability 3, B = Ability 4, C = Ultimate, Z = Mode, H = Menu.

Work is split into tracks so agents can run in parallel without touching the same files.

---

## Track 1 — Gravity (Collapsing Horizon)

### 1.1 Skyfall + Weightless synergy
**Files:** `GravitySkyfall`, `GravityPower` (`toggleWeightless`, `setWeightless`, the weightless tick)

**Decided:** a Weightless caster rises with the inversion, is **never slammed and takes no damage**, and stays airborne for the rest of Weightless's duration.

**Change:**
- If the caster has Weightless on during the rise (cast while weightless, or toggled on in the first 60 ticks), add them to the Skyfall as a **rider**. They're lifted with everyone else.
- At slam time, riders are skipped: no downward velocity, no impact damage.
- After the slam, the rider **holds their altitude** (a gentle hover, no sinking) for whatever is left of Weightless (up to `WEIGHTLESS_MAX_TICKS`). When Weightless ends (timeout, toggled off, water, hunger), they drift down at normal Weightless gravity with no fall damage, as Weightless already allows.
- Add a small public query, `GravitySkyfall.airborne(Entity)` / `riding(ServerPlayer)`, so other powers can react later (e.g. Wind or Web hitting someone mid-Skyfall).

### 1.2 Staggered slam with varied damage
**File:** `GravitySkyfall.tick`

**What happens now:** At t = 60 every lifted entity and all 40 blocks get −3.5 velocity on the same tick. One sonic boom plays.

**Change:**
- Each lifted entity gets its own random `dropAt` in a 0–30 tick window after the rise ends. Blocks also fall in a staggered order.
- Each impact rolls a damage multiplier of **×0.6–1.5** on top of the existing fall-height × size formula.
- Each impact plays its own heavy mace thud plus a small shake. The final boom plays when the last one lands.
- Extend the cleanup timeout (currently `t > 65` / `t > 260`) to cover the stagger window.

### 1.3 Orbit cap 3, blocks drop one by one on G+B
**Files:** `GravityOrbit`, `GravityPower.crush` / `caveIn`

**What happens now:**
- `MAX_ORBIT = 8`.
- In G+B (Crush released inside your Gravity Well), `crush()` returns early into `caveIn()`. `GravityOrbit.shockwave()` is never called, so the orbiting blocks keep circling the crush point and never do anything. **This is a bug as well as a design change.**

**Change:**
- Set `MAX_ORBIT = 3`. Count orbiting + drifting blocks toward the cap.
- Add `GravityOrbit.rain(player, well)`, called from `caveIn()`. It releases the orbit blocks **one at a time**, every ~8 ticks.
- Each block lines up above the nearest living target inside the well and plummets straight down onto it.
- Plain B (Crush with no well) keeps the current shockwave.

### 1.4 Soft blocks do ~half a heart
**Files:** `core/BlockCluster.serverTick` (thrown-hit damage, currently 2.5 + 2.5×speed, up to 12), `GravityPower.caveIn` (`setHurtsEntities(2.0F, 40)`)

**Decided:** applies to **every** thrown or dropped soft block, from any power.

**Change:**
- A block is **soft** if its hardness is ≤ 0.6: dirt, grass, sand, gravel, clay, soul sand, snow, concrete powder, leaves, wool and the like. Put the 0.6 threshold in a constant.
- A cluster made only of soft blocks caps its hit damage at **1.0** (half a heart). A cluster with any hard block keeps the normal formula.
- This covers Gravity throws, the orbit, Skyfall, cave-ins and Web tendril throws, since they all go through `BlockCluster` or falling blocks.

---

## Track 2 — Arsenal

### 2.1 Void Flintlock hand animation
**Files:** `assets/jaymospowers/items/flintlock.json`, `models/item/flintlock.json`, `client/power/arsenal/ArsenalArmPose` + a new mixin hook

**Cause:** The Void Flintlock (`jaymospowers:flintlock`) renders as a **flat 2D handheld sprite** (`parent: item/handheld`). The 3D Balkon-style gun models in the bundled `held_guns` resource pack only apply to **WeaponMod's** `weaponmod:flintlock`, and they use WeaponMod's texture.

**Change:**
- Give the Void Flintlock its own 3D model built from the same geometry as `gun/flintlock.json`, with a void-tinted texture in our own namespace. That way it works without WeaponMod installed.
- Use a `select` on `display_context`, the same way the held_guns pack does: 2D icon in the GUI, 3D model in hand.
- When the gun is spent (`SPENT` component), switch to a `flintlock_reload` variant with the hammer down.
- Add a held pose: arm extended and level with the look direction (Balkon-style aim, not a sword grip), plus a recoil kick on fire.

### 2.2 Swords in the Stone get unique powers
**File:** `SwordStone` (right now all 5 are a plain netherite sword, 4 / −2.6, lore only). Follow the `RelicWeapons` pattern: a passive, a right-click active with a cooldown, and lore lines.

| Sword | Found | Passive | Right-click |
|---|---|---|---|
| **Durandal** | Overworld | Unbreakable; no knockback while held | **Plant**: drives into the ground, staggering shockwave (r = 4) |
| **Laevateinn** | Nether | Fire Aspect-style burn, fire immunity while held | **Flame Wave**: 8-block arc of fire |
| **Caladbolg** | Outer End | Hits arc to 2 nearby enemies | **Rainbow Reach**: blade extends, one 10-block sweep |
| **Gram** | Deep Dark | Ignores 50% armor; +50% vs bosses and large mobs | **Sunder**: heavy hit that shreds armor and shield durability |
| **Hrunting** | Snowy peaks | Freezes and slows on hit | **Frost Lance**: thrown spear of ice that returns |

Each sword also gets one fitting vanilla enchant baked in at draw time (e.g. Laevateinn Fire Aspect II), so it shows the enchant glint.

---

## Track 3 — Chronokinesis (Time Flow)

### 3.1 Knockback every 4th hit, stun on hits 1–3
**File:** `ChronoFlow.land`

**What happens now:** Every landed blow pushes 0.35–1.1 and lifts the target, so it slides out of reach.

**Change:**
- Use the existing combo counter. Hits 1–3 of each set of 4 do **no push and no lift**. Each applies a **stun** of ~15 ticks, refreshed on every hit:
  - Mobs: navigation stopped, movement frozen
  - Players: reuse the input lock from `GravityStunInputMixin`
- Hit 4 is the finisher: the style's full push ×1.3, plus the lift (uppercut, kick).

### 3.2 Longer, varied counters
**Files:** `ChronoFlow.counter` / `Move`, `client/.../ChronoFlowPose`, `ChronoFlowClient`

**What happens now:** COUNTER is a single 10-tick pose.

**Change:** For single-target counters, pick one of 3 variants at random. Each is ~20–28 ticks, and the caster is invulnerable for the whole animation.
1. **Circle**: dash in an arc around the enemy to its back, then strike from behind.
2. **Slip**: lean or sidestep out of the attack, then a quick body hit.
3. **Deflect**: the arms knock the attack aside, the enemy staggers, then the riposte. It picks one of **5 canned arm animations** at random:
   1. **High forearm block**: lead forearm snaps up across the face, then pushes out.
   2. **Outside sweep**: lead arm sweeps outward from the centre line, turning the blow away.
   3. **Slap-down**: open hand drives the attack down and across the body.
   4. **Cross block**: both forearms cross in an X in front of the chest, then spread apart.
   5. **Elbow guard**: elbow raised to the side to catch the hit, then the forearm swings out.

This needs `variant` and `deflect` fields added to `ChronoFlow.Move`, including its stream codec. Counters against several attackers keep the current behavior.

---

## Track 4 — Web / Symbiote

### 4.1 Symbiote messages take over the screen
**Files:** `WebSymbiote.whisper` (every message goes through it, hints included), `client/power/wind/WindScreenFx`

**Change:** `whisper()` also sends a new screen effect kind for the length of the message (~60 ticks):
- Screen darkens (~35% black fill)
- Blackish-purple vignette (`#1A0A26`), pulsing
- A low ominous growl played only to that player (vanilla warden/ravager sound pitched down for now; a custom `.ogg` later)

### 4.2 Autonomous feeding: kill, grab, carry, then feed
**Files:** `WebSymbiote` (`selfFeed`, `tickTakeover`, `devour`, `startEating` / `tickEating`), `KitchenCompat`

**What happens now:**
- `selfFeed` only looks for corpses within 14 blocks and gives up if there are none.
- `devour()` **eats living mobs at ≤10 HP during a rampage**.
- `tickEating` drags a corpse straight to the mouth without it ever being carried.

**Change:** a new feeding state machine: **SEEK → KILL → GRAB → CARRY → FEED**.
1. **Seek:** look for corpses within 14 blocks first. If there are none, target the nearest living mob or animal (players only during a rampage).
2. **Kill:** autonomous attacks until the target dies and leaves a corpse.
3. **Grab:** tendrils reach out to the corpse (existing tendril visuals).
4. **Carry:** `KitchenCompat.take()`. The corpse is now carried.
5. **Feed:** eating starts **only once `carriedCorpse(player)` is that corpse**.

Also:
- **Remove `devour()`** and the eating path for corpses that aren't carried.
- The whole episode keeps the 4.1 screen effect running: darkening, vignette and growls.
- Corpses come from Jaymo's Kitchen (`CorpseEntity`), which is now required.

**Decided:** the carry-first rule also applies to the **manual Consume** ability. Consume on a living target finishes it off if it's at or below 10 HP (`CONSUME_MAX_HEALTH`), then grabs, carries and feeds on the corpse. Consume on a corpse grabs, carries and feeds on it. Nothing is eaten while alive.

**Decided:** **Jaymo's Kitchen is required.** Add `"jaymoskitchen": "*"` to `depends` in `fabric.mod.json` (moving it out of `suggests`). Keep `KitchenCompat` as the only place that touches Kitchen's classes.

### 4.3 Living Armor heavy debris strikes
**Files:** `WebSymbiote.tickStrikes`, `client/power/web/WebTendrilLayer`, `WebSymbioteClient`

**What happens now:** Every strike is the same whether or not a tendril holds something: range 5.5, 6 damage, push 0.6, 40-tick strike, 50-tick reset.

**Change:** When a tendril grip holds debris (a `BlockCluster` or falling block):
- Search range 5.5 → **8.5**, and the tendril visibly extends further (longer `STRIKE_OUT` stretch)
- Strike 40 → **60 ticks**, reset 50 → **90 ticks**, slower wind-up to sell the weight
- Damage 6 → **~11**, scaled by debris size
- Knockback of **about 10 blocks** (~1.0 horizontal + 0.45 up, tuned in game), plus a heavy thud and a small shake

---

**Strike variants (decided):** every tendril strike, heavy debris strikes and ordinary autonomous ones alike, randomly picks an **overhead slam** (rears up high and crashes straight down onto the target) or a **sideways swipe** (a wide horizontal sweep from either side). The variant is picked on the server and synced in the `Strike` attachment so everyone sees the same swing. The hit lands when the slam comes down or the swipe crosses the target; a slam knocks mostly down and back, a swipe knocks along the sweep. Debris strikes use the slower heavy timing; ordinary ones keep their normal speed.

### 4.4 Tendril combos with what you're holding
**Files:** `WebTendrils` (grips, throws), `WebSymbiote`, `client/power/web/WebTendrilLayer`, `WebSymbioteClient`

When you **attack** (left click) while tendrils hold things, the attack becomes a combo instead of a normal swing. Which combo depends on what each tendril holds:

| Holding | Combo |
|---|---|
| A creature in each tendril | **Clap**: both are slammed into each other in front of you. Both take heavy damage, then drop and bounce apart. |
| Debris in one tendril, and a creature directly in front of you (your crosshair target, within reach) | **Crush**: the debris is slammed down onto that creature. The clump collapses and shatters, and the creature takes heavy damage. |
| Debris in one tendril, a creature in the other | **Crush** onto the held creature, the same way. |
| Debris in both tendrils | **Shatter**: the two clumps are smashed together and burst into a **cone of debris** in front of you (about 6 blocks, about 60°), damaging everything in it. |

- Each combo uses the heavy timing (wind-up, impact, slow reset), a screen shake, block dust and heavy sounds.
- The debris used is spent: it collapses into falling debris, and like any thrown block it may drop.
- Soft debris (dirt, sand, gravel...) hits for much less, by the same soft-block rule as everywhere else.

## Track 5 — Wind, carrying and first person

### 5.1 Tempest Kick, varied every time
**Files:** `power/wind/WindKick` (`KICKING` attachment), `client/power/wind/WindPose` (the `KICK_KEY` branch)

**What happens now:** one fixed roundhouse. The right leg sweeps from side to front, the body twists the same way every time, and both arms are flung straight out.

**Reference (from the user):** a manga-style high roundhouse. The kick is so fast the leg is mostly the white crescent trail wrapping round to the target's head, and the body does the selling:
- **Whole body leans hard away from the kick**, tilted sideways about 25–35° and twisted, pivoting over the standing foot. This is the "offset torso".
- **Standing leg** straight and planted, up on the ball of the foot.
- **Kicking leg** swings up and round in a high arc to head height, then comes back down. It's the existing white crescent that sells it.
- **Arms thrown in opposite directions:** one flung up and back above the head, fist clenched, for balance; the other pulled down and across the hip.

**Change:** each kick rolls a random **side** (left or right) and an **arm variant**, and syncs them with the kick.
- **Side** mirrors the whole pose: which leg kicks, which way the body leans and twists, and which side the crescent sweeps from.
- **The lean** is done by tilting the whole model in the `PoseStack` around the standing foot, not by rotating `body` alone (that would leave the arms and head floating). The lean eases in over the wind-up and recovers after.
- **Arm variants** (random each kick): (1) the reference, high arm up and back, low arm across the hip; (2) mirrored, the other arm up; (3) both arms flung wide and high; (4) high arm up, low arm swung back behind. Plus a little random angle on each, so no two kicks look exactly alike.
- **Timing:** about 3 ticks of wind-up (lean starts, leg chambers), the snap through the arc at the hit tick, then about 4 ticks of recovery. Make `KICK_TICKS` 9 → ~11 if the extra recovery needs room.
- `KICKING` changes from a `Long` to a small record (start, side, swing seed). That's a new packet shape, covered by the 0.8.0 bump.

### 5.2 Carry players and small animals (everyone)
**New:** `core/Carry` (server) + `client/CarryPose` (both bodies). The carried player follows a carry point on the carrier each tick, the way Kitchen's corpse carry works. No vanilla riding, so there are no mount quirks.

- **Pick up (decided):** **everyone** can do it, with **sneak + right-click on a player or small animal** (`jaymospowers:carryable` entity tag: wolf, cat, ocelot, fox, chicken, rabbit, parrot, frog, armadillo, baby animals), empty hand or not. One player at a time. You can't pick someone up while carrying a corpse, or while already carrying someone.
- **Letting go (decided):** the same controls as Kitchen's corpses, so it feels the same. While carrying, **right-click drops** them and **sneak + right-click throws** them a short way ahead.
- **Thrown:** the thrown player goes into a **short ragdoll** (~1 s) while flying, with limbs flopping the way `WebRagdoll` does it for webbed creatures. Normal fall damage applies when they land (house rule).
- **Escaping (decided):** the carried player gets out with **one jump**, at any time. They can't while **stunned or otherwise unable to act**, e.g. Gravity's held stun, Chrono's new hit-stun, or a ragdoll still playing. Add a shared query `Carry.canStruggle(player)` that those powers' stun states feed into.
- **Safety:** the carry also ends when either player dies, logs out, changes dimension, or gets too far apart (teleport). The carrier moves a little slower (weight). The carried player can't attack, use items or break blocks.
- **Hold:** each pickup rolls a hold. Each hold has its own carrier pose, carried position and carried-body pose:
  1. **Fireman's carry**: over one shoulder, head and arms hanging down the back, legs in front. *(on foot and in flight)*
  2. **Bridal carry**: across both arms in front. *(on foot and in flight)*
  3. **Shoulder grab**: held up by the shoulder at your side, legs dangling below. *(flight only)*
  4. **Over the back**: slung behind, facing up. *(flight only)*
- **On foot** only the fireman's and bridal carry are rolled.
- **Wind flight (decided):** a Wind user carrying someone can take off. In flight, any of the 4 holds can be rolled: a new roll on take-off, and a switch back to fireman's or bridal on landing if the flight hold was a flight-only one. Flight is **slightly slower** while carrying (`CARRY_FLIGHT_SPEED` ≈ ×0.8 of top speed, plus a little extra hunger cost).
- **Carried body:** arms and legs drift loosely, swinging with the carrier's movement (sine sway plus inertia from turning and stopping), so they look like they're floating. In flight they trail in the wind.
- Both pose sets go through the existing pose path (`WebModelFeatureRendererMixin.prepareModel` + render-state extraction), like `WebBodyPose` and `ChronoFlowPose`.
- First person: the carrier's hands show the hold; the carried player's camera follows their body.

### 5.3 First-person Model, and living tendrils in first person
**Mods (decided):** add **First-person Model** by tr7zw (MIT) to the pack. It needs **Not Enough Animations** (tr7zw Protective License: fine to ship in a pack, no code reuse). Both have 26.3 Fabric builds on Modrinth:
- `maven.modrinth:first-person-model:Z05WZTy2` (2.7.3)
- `maven.modrinth:not-enough-animations:EhSk2OTD` (1.12.6)

Add both to `packMods` in `build.gradle`. Their CurseForge project and file ids still need filling in: CurseForge is blocked from the cloud session, so look them up on the home PC.

**Tendrils in first person:** Living Armor's tendrils, held debris, strikes and the feeding grab must be visible from your own view.
- **With First-person Model**, your body is drawn in first person, so `WebTendrilLayer` already renders. Check that FPM doesn't hide that layer; if it does, mark it visible through FPM's API through a small compat class (`FirstPersonCompat`, loaded only when FPM is present).
- In first person, tendrils **reach further forward**, curving over the shoulders into view. Idle tendrils sway at the edges of the screen. Strikes and grabs extend out in front, longer than in third person if that's what it takes to be seen.
- **Without FPM**, a fallback draws the tendrils in first person next to the hands, the way `WebSymbioteHand` draws the suit sleeve.

### 5.4 Cyclone (Wind)
**Key (decided by the lead, all of Wind's keys were taken):** **hold X**, the power skill key. A tap still does Tempest Kick; holding about 0.4 s forms the Cyclone instead. It's the same tap-or-hold split Gravity uses on V.

- **Stance:** the user drops into a **superhero landing** (the user's reference image):
  - one knee on the ground, the other leg bent forward with the foot planted
  - torso hunched forward, head down
  - one arm swept up and back over the shoulder, the other hand down near the ground
  You stay rooted in the stance while you hold.
- **Tornado:** a column of wind forms around you: particles, a funnel and a roaring loop sound. Creatures inside are caught, spun and lifted a little.
- **Damage to everything in it, and to you (suffocation from the vortex):**

| Tier | Damage to others | Damage to you |
|---|---|---|
| Base | 2 hearts per second (4 every 20 ticks) | ½ heart every 2 s (1 every 40 ticks) |
| Ascended | 2 hearts every 0.5 s (4 every 10 ticks) | ½ heart every 4 s (1 every 80 ticks) |
| Corrupted | the same as Ascended, as **shock** (lightning) damage, with sparks | the same as Ascended |

- **Levels with the player:** the tornado's radius and height grow with mastery level, from about 3 blocks at the first level to about 6 when fully mastered. The damage per tier is as in the table.
- Your own damage is what limits how long you hold it. Add a short cooldown after you let go, and a hunger cost.

## Before coding starts

- **Version:** bump to 0.8.0 (new packets: `ChronoFlow.Move` gains a field, plus a new screen-effect kind). Each track ends with a `./gradlew build`, plus the in-game checks listed in each section.
- **Usage limits:** the last 4-agent run stopped on usage limits. Consider running 2 tracks at a time.

## Answered
1. Skyfall + Weightless: the caster isn't slammed and takes no damage; they stay airborne for Weightless's duration (1.1).
2. Deflect counters: up to 5 canned arm animations (3.2).
3. Carry-before-feed applies to manual Consume too (4.2).
4. Jaymo's Kitchen becomes a required dependency (4.2).
5. The soft-block damage cap applies to every thrown or dropped soft block (1.4).
6. Everyone can carry players with sneak + right-click; drop and throw like corpses; a throw ragdolls the thrown player (5.2).
7. One jump frees a carried player, unless they're stunned or otherwise unable to act (5.2).
10. Wind users can fly while carrying, slightly slower, with all 4 holds; on foot only fireman's and bridal (5.2).
11. First-person Model joins the pack; living tendrils must be visible in first person (5.3).
16. Wind gets Cyclone on hold X, in a superhero-landing stance, with the damage table in 5.4.
15. Tendrils holding creatures or debris turn an attack into a combo: clap, crush or shatter (4.4).
14. Tendril strikes (heavy and autonomous) vary between overhead slams and sideways swipes (4.3).
13. Players **and small animals** (wolves, cats, chickens, foxes, rabbits, parrots and the like) can be carried. Animals don't jump free; they stay until dropped or thrown (5.2).
12. Tempest Kick reference received: a high roundhouse with the body leaning hard away and the arms thrown opposite ways (5.1).

## Open questions

None.
