# Plan for 0.8: the user's playtest changes

Based on a full read of the 0.7.1 jar. Class names are the real ones in `src/`. Player-facing text goes in `lang/parts/<power>.json`. Default keys: R = Lift / Ability 1, G = Ability 2, V = Ability 3, B = Ability 4, C = Ultimate, Z = Mode, H = Menu.

Work is split into 4 tracks so 4 agents can run in parallel without touching the same files.

---

## Track 1 — Gravity (Collapsing Horizon)

### 1.1 Skyfall + Weightless synergy
**Files:** `GravitySkyfall`, `GravityPower` (`toggleWeightless`, `setWeightless`)

**What happens now:** Skyfall only lifts *other* entities. The caster isn't in the lifted list. So if the caster is weightless (gravity −0.7), they float up with everyone else, but the slam phase never touches them. They just drift down.

**Change:**
- If the caster has Weightless on during the rise (cast while weightless, or toggled on in the first 60 ticks), add them to the Skyfall as a **rider**.
- At slam time, the rider gets slammed too. Weightless turns off, they get the downward velocity, and their landing becomes a **ground-pound**:
  - AoE damage scaled by fall height
  - Debris ring
  - The caster takes normal fall damage (house rule: fall damage always applies outside creative); see question 1
- Add a small public query, `GravitySkyfall.airborne(Entity)` / `riding(ServerPlayer)`. Other powers can use it later for synergies, for example Wind or Web hitting someone mid-Skyfall.

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
**File:** `core/BlockCluster.serverTick` (thrown-hit damage, currently 2.5 + 2.5×speed, up to 12)

**Change:** If a cluster is made of falling-type blocks (sand, gravel, concrete powder, i.e. `instanceof FallingBlock`), cap its hit damage at **1.0** (half a heart). This affects orbit, throws and Skyfall blocks. *(See question 5 if you want it limited to the orbit only.)*

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
3. **Parry**: lead arm swings across to deflect, the enemy staggers back, then the riposte.

This needs a `variant` field added to `ChronoFlow.Move`, including its stream codec. Counters against several attackers keep the current behavior.

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
- Corpses come from Jaymo's Kitchen (`CorpseEntity`). See question 4.

### 4.3 Living Armor heavy debris strikes
**Files:** `WebSymbiote.tickStrikes`, `client/power/web/WebTendrilLayer`, `WebSymbioteClient`

**What happens now:** Every strike is the same whether or not a tendril holds something: range 5.5, 6 damage, push 0.6, 40-tick strike, 50-tick reset.

**Change:** When a tendril grip holds debris (a `BlockCluster` or falling block):
- Search range 5.5 → **8.5**, and the tendril visibly extends further (longer `STRIKE_OUT` stretch)
- Strike 40 → **60 ticks**, reset 50 → **90 ticks**, slower wind-up to sell the weight
- Damage 6 → **~11**, scaled by debris size
- Knockback of **about 10 blocks** (~1.0 horizontal + 0.45 up, tuned in game), plus a heavy thud and a small shake

---

## Before coding starts

- **Version:** bump to 0.8.0 (new packets: `ChronoFlow.Move` gains a field, plus a new screen-effect kind). Each track ends with a `./gradlew build`, plus the in-game checks listed in each section.
- **Usage limits:** the last 4-agent run stopped on usage limits. Consider running 2 tracks at a time.

## Open questions

1. **Skyfall + Weightless:** is 1.1 right, that *you* ride up and get slammed down as a ground-pound? Or did you mean the lifted *enemies* weren't being slammed? And should the caster take fall damage from that landing?
2. **Parry counter:** your note ended with "arm animate s]=[". What should the arm do? I assumed it swings across to deflect.
3. **Manual Consume** (the symbiote's Consume ability, which eats small living mobs up to 10 HP): should it also follow carry-before-feed, or is the rule only for autonomous feeding?
4. **Jaymo's Kitchen:** corpses only exist with Kitchen installed. Should Powers *require* Kitchen? If it doesn't, autonomous feeding can't happen at all under the carry rule.
5. **Soft-block damage:** should the half-heart cap apply to every thrown or dropped block, or only to Collapsing Horizon's orbit?
