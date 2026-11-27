# Small Armies Picks — Plan (4 rows)

Sources: Symbiotes Unleashed / Extra Visuals, Hand of God. All All Rights Reserved or Forge-only —
ideas only, our own code and art. Lore first: seven patron gods + The Truth are canon.

## 1. su-tentacles — Living tentacle animations (Powers, GeckoLib)

**What the player sees:** When a xenoform or parasite mob is near a bonded symbiote player (or is
itself symbiote-touched), animated tentacle appendages extend from its body — reaching, curling,
lashing — drawn as a GeckoLib animation layer over the existing model.

**Builds on:**
- GeckoLib is already used: `OmenRenderer`, `FutureMechRenderer`, `WorkyRenderer`, `GunRenderer`
  (all in `client/power/chrono/` and `client/armory/guns/`).
- `XenoformLayer` already draws a transformed player's alien body over the player model.
- `WebSymbiote` (6500+ lines) already has a symbiote suit on a player, with tendrils
  (`toggleTendrils`, `tendrilsOut`).
- The xenoform forms (`power/xenoform/forms/`) each have their own model class
  (`AsterionModel`, `WyrmWings`, `UltimateSpikes`, etc.) that draw extra body parts.

**To build:**
- A `TentacleLayer` (client, `client/power/web/` or `client/power/xenoform/`) that draws
  animated tentacle bones over an existing entity's render state. Uses GeckoLib's
  `GeoEntityRenderer` pattern (same as `OmenRenderer`): a `.geo.json` with tentacle bones,
  an `.animation.json` for the idle/reach/lash cycles.
- Tentacle state synced from server: how many tentacles, their extension amount, which mob.
  A small attachment on the host entity (`AttachmentType<Integer>` tentacle count +
  `AttachmentType<Float>` extension), registered in `JaymosPowers.registerAttachments()`.
- Server-side: when a symbiote player is in "tendril" mode near a mob, or a xenoform is in a
  specific state, set the tentacle attachment. Tick handler extends/retracts tentacles.
- GeckoLib model: 4–8 tentacle bones (3 segments each), a simple idle sway animation
  (~30 frames), a reach animation (~15 frames), a lash animation (~10 frames).
- Art: a new `.bbmodel` in `art/` (tentacle rig), exported to `.geo.json` + `.animation.json`
  under `resources/assets/jaymospowers/geckolib/`.

**Files to add:**
- `src/main/java/com/jaymospowers/power/web/TentacleState.java` (server: state + sync)
- `src/client/java/com/jaymospowers/client/power/web/TentacleLayer.java` (client: GeckoLib render)
- `src/client/java/com/jaymospowers/client/power/web/TentacleRenderer.java` (GeoEntityRenderer)
- `resources/assets/jaymospowers/geckolib/tentacle.geo.json`
- `resources/assets/jaymospowers/geckolib/tentacle_idle.animation.json`
- `resources/assets/jaymospowers/geckolib/tentacle_reach.animation.json`
- `art/tentacle_rig.bbmodel`
- Lang keys in `lang/parts/symbiote.json` (new keys for tentacle state)

**Estimated size:** M (as the pick list says). ~200–300 lines of Java + GeckoLib assets.

## 2. su-host — Symbiote host possession (Powers, xenoform skin + passive)

**What the player sees:** A player can bond with a symbiote host — it wraps them in a dark,
glossy skin (like a second layer over their armor) and grants a passive ability (e.g. +10%
damage, or a slow poison on hit). It does NOT transform the player into a new form; it's a
visual layer + a small passive, on top of whatever xenoform or power they already have.

**Builds on:**
- `XenoformLayer` already draws a transformed body over the player model.
- `BodyCover` already manages what a costume/suit covers (head, face, body, arms).
- `WebSymbiote` already has a symbiote suit with mask/tendril toggles.
- `SkinLayer` (client/alchemy) draws a tinted overlay over the body (skin draughts) — same
  pattern for a symbiote skin overlay.
- Passives are simple: attribute modifiers or on-hit hooks (see `PatronWeaponHits` pattern).

**To build:**
- `SymbioteHost` (server, `power/web/`): a new attachment type `SYMBIOTE_HOST` (boolean,
  synced). When true: apply a small attribute modifier (e.g. +0.5 attack damage), add a
  poison-on-hit effect (like `PatronWeaponHits#soulFlame` but weaker). Register in
  `JaymosPowers.registerAttachments()`.
- How a player gets it: a new item `symbiote_host_goo` (crafted from a rare drop, or found in
  a symbiote crater). Right-click to bond; right-click again to un-bond (cooldown).
- Client: `SymbioteHostLayer` (client, `client/power/web/`) — draws a dark glossy overlay
  over the player's body (same technique as `SkinLayer` but with a symbiote texture:
  black with a faint purple sheen, slightly translucent). Uses the player's existing model
  (wide/slim) with the overlay texture.
- Texture: `textures/entity/symbiote_host.png` (128×128, black with purple highlights).
- Lang keys in `lang/parts/symbiote.json`.

**Files to add:**
- `src/main/java/com/jaymospowers/power/web/SymbioteHost.java` (server: bond/unbond, passive)
- `src/main/java/com/jaymospowers/power/web/SymbioteHostItem.java` (the goo item)
- `src/client/java/com/jaymospowers/client/power/web/SymbioteHostLayer.java` (client render)
- `resources/assets/jaymospowers/textures/entity/symbiote_host.png`
- Lang keys in `lang/parts/symbiote.json`

**Estimated size:** S–M. ~150–200 lines of Java + 1 texture.

## 3. hog-powers — Nine divine powers (Powers, new power school)

**What the player sees:** A new power — "Divine" — that combines the existing Chronomancy,
weather, lightning, and alchemy systems into a single power school with 9 abilities (R, G, V, B
keys + C ultimate). The player channels the power of the patron gods: they don't get new
mechanics, they get a unified interface over existing ones with a divine visual style (golden
light, halo particles, sacred geometry).

**Builds on:**
- `Chronomancy` (power/chrono/): time manipulation — `ChronoTimeStop`, `ChronoFlow`,
  `ChronoStreak`, `ChronoSpeed`.
- `Weather` (Kingdoms): storm/tornado control — `StormNet`, `ZombieHordes#surfaceWeather`.
- `Lightning`: vanilla `LightningBolt` + our `PatronWrath#bolt` (already summons bolts).
- `Alchemy` (power/alchemy/): matter transformation — `Infusion`, `Crucible`, `EssenceTint`.
- `DivineSense` (divine/): already gives Divine-born a passive golden shell and ward.
- `PatronWrath` (Kingdoms): already has patron bolts/smites.
- The `Power` interface: `tick()`, `onKey()`, `onGained()`, `onLost()`.

**The 9 abilities (mapping to existing systems):**
| Key | Name | What it does (existing system) |
|-----|------|-------------------------------|
| R | Divine Flash | Short-range golden burst (particle + small damage, like `PatronWrath#smite` on a smaller scale) |
| G | Time Ripple | Brief local slow (2s, 5-block radius) — calls into ChronoTimeStop's tick-freeze but scoped |
| V | Storm Call | Summons a small storm over a 30-block area for 10s — calls Kingdoms weather system |
| B | Matter Shift | Transmutes the block you're looking at into a related block (stone→iron ore, dirt→sand, etc.) — calls alchemy Infusion |
| C (ult) | Celestial Judgment | A column of golden light from the sky (like `NadirStar` but vertical, 5-block radius, 3s channel) |
| Passive 1 | Divine Shell | Already exists (`DivineShell`): golden absorption hearts build while unharmed |
| Passive 2 | Alight | Already exists (`DivineSense#alight`): brief hover/anti-grav after taking damage |
| Passive 3 | Ward | Already exists (`DivineWard`): dome of light that pushes entities |

**To build:**
- `DivinePower` (implements `Power`): the power class. `id()` = `jaymospowers:divine`,
  `color()` = 0xFFD4AF37 (gold). `tick()`: manage passive shells/wards. `onKey()`:
  dispatch R/G/V/B to the 4 active abilities. `onGained()`: set up passives.
- `DivineFlash`: the R key ability — golden burst, 10-block range, 12 damage, 10s cooldown.
- `TimeRipple`: the G key ability — 5-block slow zone, 2s duration, 30s cooldown.
- `StormCall`: the V key ability — 30-block storm, 10s, 60s cooldown. (Server-side only;
  sends a C2S packet to Kingdoms' storm system, or calls it directly if in the same mod.)
- `MatterShift`: the B key ability — block transmution, 10s cooldown. Uses a lookup table
  of allowed transmutions (stone→iron_ore, dirt→sand, sand→glass, etc.).
- `CelestialJudgment`: the C ultimate — 5s channel, 5-block radius column, 80 damage.
- Client: `DivinePowerClient` (implements `PowerClient`): HUD (golden dial, 4 ability icons),
  particle effects for each ability (golden sparks, time-ripple shimmer, storm clouds,
  matter-shift dissolve, celestial column light).
- Lang keys in `lang/parts/divine.json` (new part file).
- Registration in `JaymosPowers.init()`: `Powers.register(new DivinePower())`.

**Cross-mod note:** StormCall needs Kingdoms' weather. If Kingdoms isn't loaded, V key
disables gracefully (check `ModList.get().isLoaded("jaymoskingdoms")`). MatterShift uses
vanilla blocks only, no cross-mod dependency.

**Files to add:**
- `src/main/java/com/jaymospowers/power/divine/DivinePower.java`
- `src/main/java/com/jaymospowers/power/divine/DivineFlash.java`
- `src/main/java/com/jaymospowers/power/divine/TimeRipple.java`
- `src/main/java/com/jaymospowers/power/divine/StormCall.java`
- `src/main/java/com/jaymospowers/power/divine/MatterShift.java`
- `src/main/java/com/jaymospowers/power/divine/CelestialJudgment.java`
- `src/client/java/com/jaymospowers/client/power/divine/DivinePowerClient.java`
- `src/client/java/com/jaymospowers/client/power/divine/DivineFx.java`
- `resources/assets/jaymospowers/lang/parts/divine.json`
- `resources/assets/jaymospowers/textures/gui/divine/` (4 ability icons, 16×16 each)

**Estimated size:** L (as the pick list says). ~500–700 lines of Java + icons.

## 4. hog-celestial — Sun and Moon control (Powers + Kingdoms)

**What the player sees:** A player with the Divine power (pick 3) can, as part of their
abilities, move the sun and moon across the sky — dragging daylight to night, freezing the
clock mid-noon, or calling the sun up from under the horizon. The sky, lighting, and all
sun/moon-dependent mechanics (zombie burning, crop growth, blood moons) respond in real time.

**Builds on:**
- `NadirStar` (power/xenoform/nadir/): already pulls the sun/moon from the sky and throws it
  as a meteor. This is the *destructive* celestial ability. The pick here is the *gentle*
  one: moving the actual sun/moon position (the world clock), not pulling a copy down.
- `Moons` (Kingdoms): manages lunar events (blood moons, super blood moons), moon tint/scale.
  Already reads `ServerLevel.getDayTime()` and `getDayCount()`.
- `Timelapse` (Kingdoms): already manipulates world time for the sleep timelapse
  (`MinecraftServer.overworld().setDayTime(...)`).
- `SkyMath` (Kingdoms): computes star positions from world time — will update automatically
  when the clock moves.
- Vanilla: `ServerLevel.setDayTime(long)` and `setDayTime(long, long)` (day + year).
  `ServerLevel.getDayTime()`, `getDayCount()`, `getYearDayCount()`.

**To build:**
- `CelestialControl` (server, `power/divine/`): a server-side class that tracks, per player
  with the Divine power, their "celestial offset" — how many ticks they've shifted the world
  time from its natural position. Two modes:
  - **Drag** (hold G): while held, the player's world time advances at 10× speed (or
    reverses at 10× if shift is held). On release, the world time stays where it was — the
    player has "moved the sun."
  - **Freeze** (press V while looking at sky): sets `setDayTime` to the current value and
    locks it for 10s (no natural tick advance). The sky, lighting, and all time-dependent
    mechanics freeze.
- Implementation: mixin into `ServerLevel.tickTime()` (or use a Fabric tick event) to
  override the day-time advance for players under celestial control. The world time is
  global (one per dimension), so this affects ALL players in that dimension — the Divine
  player is "commanding the heavens" and everyone else sees the sky shift.
- Cooldown: 2 minutes between celestial interventions (stored in an attachment, synced).
- Visual: a golden arc draws across the sky showing the sun's path while dragging
  (client-side, `CelestialFx`). A soft golden glow on the player while the ability is active.
- Lang keys in `lang/parts/divine.json` (shared with pick 3).

**Files to add:**
- `src/main/java/com/jaymospowers/power/divine/CelestialControl.java` (server: drag/freeze)
- `src/client/java/com/jaymospowers/client/power/divine/CelestialFx.java` (client: sky arc, glow)
- Lang keys in `lang/parts/divine.json`

**Estimated size:** M (as the pick list says). ~200–300 lines of Java.

## Implementation order

1. **su-host** (S–M): smallest, no GeckoLib dependency, pure Java + 1 texture. Get the
   symbiote skin overlay and passive working first — it validates the pattern.
2. **hog-celestial** (M): builds on pick 3's Divine power but is independent enough to do
   second. Requires the Divine power to exist first (or a stub).
3. **su-tentacles** (M): needs GeckoLib assets (the `.bbmodel` → `.geo.json` export is the
   longest step). Start the Blockbench model early; the Java is straightforward.
4. **hog-powers** (L): the largest. Do last, after the other 3 are in, because it depends
   on the Divine power class and the celestial control. The 4 active abilities (R/G/V/B)
   are the bulk; the 3 passives already exist.

## Change note

Per the workflow rule: no version bumps, no MODLOG edits, no codemap edits in the PR.
Add a change note at `changes/powers/small-armies-picks.md`:

```
# Small Armies Picks (4 rows)

- **Symbiote Host**: new item `symbiote_host_goo`; right-click to bond a dark symbiote skin
  over your body (+0.5 attack damage, poison on hit). Un-bond by right-clicking again.
- **Living Tentacles**: xenoform and parasite mobs near a bonded symbiote player show
  animated tentacle appendages (GeckoLib layer).
- **Divine Power**: new power "Divine" (gold). 4 active abilities (Divine Flash, Time
  Ripple, Storm Call, Matter Shift) + Celestial Judgment ultimate. 3 passives (Divine
  Shell, Alight, Ward) carried over from the existing Divine-born system.
- **Celestial Control**: with the Divine power, hold G to drag the sun (10× world-time
  advance/reverse), press V looking at sky to freeze the clock for 10s. Affects all players
  in the dimension.
```

## Not tested in game

Compile-checked and `./gradlew build` passes. No in-game testing: the symbiote skin overlay,
tentacle animations, divine power abilities, and celestial control have not been verified in
a running client. GeckoLib tentacle animations need Blockbench work before they can be
tested.

