# Rig style: the Awakening picks (2026-11-14)

Andy linked The Awakening (TryHarderrr; All Rights Reserved, NeoForge 1.21.1 / 1.21.11, no source): "this second mod has
serious steez i want added to the rig". Its powered-suit power is the nearest thing to our Rig (`rig/`, `client/rig/`).
Ideas only: our own code, poses, names and art. Pick page: https://claude.ai/artifact/H4HQaRL59tnyCWmbktc9yV (copy in
project files `awakening-rig-study/`). Andy picked 30 of 32 rows. Left out: the suit hammer, and the assembly (ours stays
as it is). His one note, on hit-stop: "yes bettercombat heavies need this".

Rules that bind every patch:
- Rig poses stay in `RigPose`, worked out from the synced `RigMoves`, so everyone sees the same thing.
- Shakes through `core/Shakes`, held sounds through `PowerLoop` / `Soundscape`, effects through `MagicFx`.
- HUD pieces through `HudZones`; numbers in `static final` constants at the top of each class.
- Nothing here adds a thing to the world, so there is no new lore; the Rig's makers and wrecks are unchanged.

## Patches (one branch, one PR, a commit per patch)

### 1. How the suit moves (mv-beats, mv-body, mv-recoil, mv-takeoff, mv-gestures, mv-hover)
- `client/rig/RigBeats`: keyed tracks with an ease per key (out-cubic, out-back overshoot, in-out sine), the way a
  hand-keyed clip reads, but written as constants.
- `RigPose`: palm blast, launcher, Unibeam, kick and the one-knee landing retimed as wind-up, release, settle; the hips,
  both legs and the head join in; recoil runs arm, then chest, then head, bigger on the Siege frame.
- New synced moves in `RigMoves`: take-off (crouch, then the climb), scan (two fingers to the helm), call (arms out and
  feet planted while the suit builds), table (a wrist glance).
- Check: each move's pose returns to rest by its own length; compile.

### 2. Flight feel (fl-switch, fl-fov, fl-sound, fl-flames, fl-camera, fl-boom, fl-lowpower, fl-instruments)
- Hover and full flight flip either way in the air (double-tap Jump); the Rig menu picks which one a take-off starts in.
- `client/rig/RigFlightFeel`: view widens with speed, a layered thruster loop (`PowerLoop`), the F5 camera settling low
  and behind at cruise, streaks and a crack at sprint speed, drawn thruster cones on boots and palms.
- Core empty in the air: the thrusters sputter and you sink slowly for a few seconds instead of dropping.
- Instruments are already on the visor; nothing to build.

### 3. Impact (im-hitstop, im-glow, im-duck, im-flash, im-slowmo, im-shake)
- `core/HitStop` + `client/HitStopClient`: attacker and target hold their pose for 2 to 4 ticks, the knockback waits for
  it. Used by Rig punches and the dash ram, and by every heavy melee blow (slow, hard-hitting weapons: Better Combat
  heavies).
- The limb that charges brightens through the wind-up and flashes for a tick on release (the suit's glow pass).
- A short dip in other sounds under the Unibeam and a barrage; a half-second slow beat on your own screen only.
- Shake strength is already `/shake`.

### 4. Weapons (fl-dash, wp-tagged, wp-barrage, wp-punch, wp-firstperson, wp-beam)
- Thruster dash (a Rig pick), tagged targets from a scan, artillery barrage (a Rig pick at Mind level 5), punch flare
  and lunge, the gauntlet's glow and kick on your own screen, a scorched line behind the Unibeam (`BeamScars`).

### 5. Suit presence (su-skins, su-failsafe, su-dossier, su-hudmove)
- Finishes earned by deeds, offered at the Rig Forge; the parked suit coming on its own at a near-fatal hit (Mind
  level 4, a Rig menu switch); a target card beside the bracket; an arrange screen for HUD pieces (`HudZones` offsets).

## Open questions
- Which deeds and looks the finishes get (three first picks are mine).
- Whether the slow beat stays once it has been seen on the server.
