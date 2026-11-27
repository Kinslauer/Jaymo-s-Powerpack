# Plan: Small Armies picks (Andy, 2026-11-26)

Andy picked 4 of the 10 rows on `Picklists/Small-armies pick-list.md`. All sources are All Rights Reserved or
Forge-only, so this is our own code and art, with nothing copied. Not picked: clay soldiers, Clay Warfare
colours/buffs/remote/book.

## Lore

- **Divine powers** (Hand of God): granted by the patron gods. The seven patron gods + The Truth are canon
  (see `docs/Patrons-Plan.md`, `docs/Patrons-B-Plan.md`, `docs/Patrons-C-Plan.md`). A player who earns a god's
  favour (a quest, a ritual, a debt paid) gains one of the god's powers. The Hand of God is not a single power
  but a school: each power is a fragment of a god's domain, usable by a mortal who has been chosen.
- **Symbiote hosts**: the symbiotes (Web, the xenoforms' alien strains) bond to a host — player or mob — and
  change their look and abilities. Who carries them: the xenoforms' hunters (the Band), the Web's hosts (players
  who bonded a symbiote), and in the deep dark, the parasites that infest the native fauna.

## Pick 1: su-tentacles — Living tentacle animations

**Source:** Symbiotes Unleashed / Extra Visuals — animated tentacles, organic armour movement, dynamic symbiote
visuals on a host entity.

**For us:** GeckoLib animation layer on an existing mob. Tentacle bones on our xeno or parasite mobs, not a new
entity. The xenoforms already have GeckoLib models (`XenoForm`, `forms/`). The parasites (the xenoform's hostile
mobs, `nadir/`) can carry animated tentacles that sway, reach and retract.

**Plan:**
- New GeckoLib model: a tentacle overlay (4-6 tendrils, each 3-4 bones, swaying with a sine wave keyed to
  `tickCount`). No new entity — it's a render layer on an existing mob.
- The layer reads the mob's health, target and movement state to drive the animation (tentacles reach toward the
  target, retract when idle, flare when the mob is hurt).
- Applied to: the xenoform's parasite mobs (the `nadir` package's hostile creatures) and any future symbiote-hosted
  mob.
- Client only. Server sends no extra data (the mob's existing state drives it).

**Files (when built):**
- `src/client/java/com/jaymospowers/client/power/xenoform/TentacleLayer.java` — the GeckoLib render layer
- `src/client/java/com/jaymospowers/client/power/xenoform/TentacleModel.java` — the tentacle model
- `src/main/resources/assets/jaymospowers/models/geckolib/tentacle.animation.json` — the animation
- `src/main/resources/assets/jaymospowers/models/geckolib/tentacle.geo.json` — the geometry

## Pick 2: su-host — Symbiote host possession

**Source:** Symbiotes Unleashed / Extra Visuals — a symbiote bonds to a player or mob, changes their look and
abilities.

**For us:** Powers already has xenoforms and ultimates. This is a skin and a passive on top, not a new system.
The Web symbiote already does host possession (WebSymbiote, WebHostBody, WebSuitGrowth). The xenoforms already
transform the player (XenoForm, forms/). This pick extends the idea to **mobs**: a symbiote bonds to a mob (not a
player) and changes its look and behaviour.

**Plan:**
- A passive effect: a xenoform's parasite (or a Web symbiote in its free state) bonds to a nearby mob.
- The bonded mob gets: a visual overlay (the symbiote's skin, a GeckoLib layer), a small stat boost (speed,
  toughness), and a behavioural change (it follows the symbiote's host, attacks the host's enemies).
- The bond lasts a duration, or until the mob dies. It breaks if the host is too far or dies.
- One symbiote can bond one mob at a time (the player's xenoform's "pet" slot, or the Web's free blob latching
  onto a mob).
- Server-side: the bond state (which mob, since when), the stat modifiers, the follow AI.
- Client-side: the symbiote skin overlay on the mob, a faint tendril connection to the host.

**Files (when built):**
- `src/main/java/com/jaymospowers/power/xenoform/HostBond.java` — the bond state, stat modifiers, follow AI
- `src/client/java/com/jaymospowers/client/power/xenoform/HostBondLayer.java` — the client render layer

## Pick 3: hog-powers — Nine divine powers

**Source:** Hand of God — control Sun and Moon, time, matter, reality and lightning; god-like player abilities
with visual effects.

**For us:** Powers already has time (Chronomancy), weather, lightning, matter (alchemy). This is a power school,
not new mechanics. The "Hand of God" is a power (like Wind, Chrono, Gravity) that, when awakened, gives access to
nine divine abilities drawn from the existing systems. Each ability is a wrapper around an existing mechanic,
re-skinned with divine VFX and granted by a patron god.

**Plan:**
- A new power: `handofgod` (the Hand of God), a power school with nine abilities.
- The nine abilities (each maps to an existing system):
  1. **Sun and Moon** — move or freeze the celestial bodies (Pick 4, implemented first)
  2. **Time's Hand** — a localised slow-time (Chrono's slow time, but only in a radius around the player, not
     the whole server)
  3. **Storm Caller** — summon lightning at a target (Wind's lightning, on demand, with a cooldown)
  4. **Matter's Word** — transmute one block in hand into another (alchemy's transmutation, simplified to a
     direct conversion)
  5. **Reality's Seam** — a short intangibility phase (Chrono's phase, but shorter and on its own cooldown)
  6. **God's Gaze** — a brief all-seeing vision (see all enemies within a radius, highlighted, for a few seconds)
  7. **Creation's Breath** — a burst of healing that also mends nearby friendly entities
  8. **Void's Claim** — a pull that draws nearby enemies to the player (gravity's pull, inverted: enemies come
     to you, not you to them)
  9. **The Truth's Silence** — a short silence: no sound, no particle effects, no VFX in a radius (a
     "quiet zone" that also slightly reduces enemy mob detection)
- Each ability has its own cooldown, a divine VFX (golden/white particles, a brief screen flash), and a sound.
- The power is granted by completing a patron god's quest (or a ritual). It's a one-power choice like the others.
- No slash commands: the abilities are on the power's key bar (the standard four ability keys + the ultimate).

**Files (when built):**
- `src/main/java/com/jaymospowers/power/handofgod/HandOfGodPower.java` — the power definition
- `src/main/java/com/jaymospowers/power/handofgod/CelestialCommand.java` — ability 1 (Sun and Moon)
- `src/main/java/com/jaymospowers/power/handofgod/TimesHand.java` — ability 2
- ... (one class per ability)
- `src/client/java/com/jaymospowers/client/power/handofgod/HandOfGodPowerClient.java` — the client half

## Pick 4: hog-celestial — Sun and Moon control (implemented first)

**Source:** Hand of God — move or freeze the Sun and Moon.

**For us:** Kingdoms' world time or Powers' existing celestial hooks. The `EnvironmentAttributeSystem` already
drives the sky (SUN_ANGLE, MOON_ANGLE) from the level's game time. Freezing the sun means holding the overworld
clock still; moving it means setting the clock to a specific time.

**Plan (implemented):**
- A new power in `power/handofgod/`: `CelestialCommand`.
- The player, with the Hand of God power, presses an ability key to:
  - **Freeze** (ability 1): hold the overworld's day time for a duration (the sun and moon stop moving; everything
    else continues). Duration: 10 seconds, cooldown: 60 seconds.
  - **Set** (ability 2): jump the overworld's day time to where the player is looking (the sun/moon snap to the
    player's gaze direction). Cooldown: 30 seconds.
- Server-side: the tick handler that freezes or sets the time.
- Client-side: a brief VFX flash when the power is used, a small HUD indicator while frozen.
- This is a global effect (the sky is one per level), so it affects everyone on the server.

**Files:**
- `src/main/java/com/jaymospowers/power/handofgod/CelestialCommand.java` — the server-side power
- `src/main/java/com/jaymospowers/power/handofgod/HandOfGodPower.java` — the power definition (registered)
- `src/client/java/com/jaymospowers/client/power/handofgod/HandOfGodPowerClient.java` — the client half
- `lang/parts/handofgod.json` — lang keys
- `changes/powers/small-armies-picks-4.md` — the change note

## Build order

1. **This PR:** Pick 4 (CelestialCommand) + the plan doc. The simplest, most self-contained feature.
2. **Next PR:** Pick 3 (the full Hand of God power school, building on Pick 4's celestial ability).
3. **Next PR:** Pick 2 (symbiote host possession on mobs).
4. **Next PR:** Pick 1 (tentacle animations, client-only, no server changes).
