# Resonance

Sound and the Deep Dark's sculk (new in 0.8.28). Forms: **Resonance**, **Choir** (ascended), **Shrieker** (corrupted),
**Silent Choir** (both). Icon: the Tuning Fork. Colour `0xB48CF0`. Spec: `new-powers-table.md`, section 2.

## Keys

| Key | Ability | What it does | Unlocks |
|---|---|---|---|
| (passive) | Weak Echolocation | A faint ring round the crosshair; every creature within 12 blocks beats on it in its direction (double thump). Always on. | starter |
| V | Echolocation | Every creature within 32 blocks is outlined through walls for 4 s, **on your screen only**. Everyone nearby sees a ripple of sculk pops and hears a faint chime and tendril clicks. 8 s cooldown. | 1st |
| R | Pulse | A 35° cone, 8 blocks (12 with all 4 unlocked): 5 damage (through armour on `#jaymospowers:resonance_sensitive`, i.e. Wardens), knockback 1.5, stops a raised shield / meal / drink (a player can't raise it again for 1 s), shatters glass panes in the cone. Needs line of sight. 4 s cooldown. | 2nd |
| Z | Throw Voice | Tap: a sound at the block you look at (32 blocks). Players hear it from there; it's a vibration there (sculk and Wardens turn to it, not to you); creatures within 16 blocks with no target walk to it for 6 s. Hold Z and scroll: footstep / groan / door / your voice. 3 s cooldown. | with the 2nd |
| G (hold) | Shatter | Hum at what you look at (12 blocks) for up to 2 s. Blocks: glass (anything with glass sound) and ice at once; others take 20 ticks per 1.5 hardness (stone 1 s, so nothing harder than 3.0 in one hum; tuned low, up to 6.0). Never: hardness ≥ 50 (obsidian), unbreakable, block entities, fluids. Harder than iron ore (3.0): no drops. Creatures: every worn armour piece loses 10% of its max durability per 0.5 s; 2 damage when the hum ends. 10 s cooldown, only if the hum did something. | 3rd |
| X (hold + scroll) | Tune | Pitch -2..+2. Low: Pulse knockback +33%/step, Shatter on blocks 25% faster/step. High: Pulse damage +25%/step (+50% at +2), armour strips 2 ticks sooner/step. A quick tap of X: back to 0. | with the 3rd |
| B | Hush | A dome (6 blocks, **5 s**) where you stand. Inside: clients mute every sound in it and everything a listener inside would hear (music and UI excepted); no vibrations (sensors, shriekers, Wardens); other players' power keys do nothing (cooldowns keep ticking). Your own powers work; the Web symbiote's own actions aren't key presses, so it's untouched. 40 s cooldown. | 4th |
| C | Sonic Boom (Choir) | 1 s charge (Warden's charge sound), then the Warden's beam: 20 blocks, through walls, armour and shields, 14 damage, launches (2.5 along, 0.6+ up). `SONIC_BOOM` particles every block. 30 s cooldown. | ascended |
| C | Shriek (Shrieker) | Darkness 8 s on every creature within 16 blocks, you hear their heartbeats (ring + outline) for 8 s, Wardens in range get anger 150. 60 s cooldown. Silent Choir: C is Sonic Boom, sneak + C is Shriek. | corrupted |

Passives: **Footstep sense** (sneaking: your steps, landings, swims make no vibrations; Shrieker loses it).
**Heavy hearing** (a hit from a creature outside your 60° view or behind a wall flashes red on the ring in its direction).

## Items

- **Tuning Fork** `jaymospowers:resonance_tuning_fork`: `A A / _C_` (amethyst shards, copper ingot). Right click: hear
  (outline) every creature within 24 blocks for 3 s; 48 for a Resonance user. 5 s item cooldown. Anyone can use it.
- **Resonant Fork** `jaymospowers:resonance_resonant_fork`: smithing table, Tuning Fork + echo shard (no template).
  Strikes the same; it's the ascension item. Both in the Tools & Utilities tab. Textures: `tools/gen_resonance.py`.

## Fate

- **Ascend:** kill a Warden while holding a Resonant Fork (either hand), all 4 abilities unlocked.
- **Corrupt:** +20 per sculk shrieker you set off, +50 when a Warden emerges from a shrieker you set off (within 60 s).
  Shrieker: a vibration from you every 2 s even sneaking; Hush deafens you too (muffled screen, all sounds muted while
  it lasts); Pulse turns `#sculk_replaceable` ground to sculk where it lands (protection respected).
- **Cure:** stand within 4 blocks of a jukebox while a disc plays from its start (within 3 s) to its end, attacking
  nothing meanwhile.
- **Debug:** `/powerdebug resonance ready`, `/powerdebug resonance shrieks <n>` (meter = 20n; 5 corrupts; works in creative).

## Constants

All `static final` at the top of: `ResonancePower` (cooldowns, tune range, heartbeat radius, Z/X unlock levels),
`ResonancePulse`, `ResonanceShatter`, `ResonanceEcho`, `ResonanceHush`, `ResonanceVoice`, `ResonanceBoom`,
`ResonanceFate`, `TuningForkItem`.

## Files

- `power/resonance/`: `ResonancePower` (Power, keys, cooldown state), `ResonanceState` (synced HUD state, owner only),
  `ResonanceNet` (Sense/Heard to the client, Cycle/Tune to the server), `ResonancePulse`, `ResonanceShatter`,
  `ResonanceEcho` (V, ripples, Heavy hearing), `ResonanceHush` (domes, `DOME` attachment synced to all, vibrations),
  `ResonanceVoice`, `ResonanceBoom` (C), `ResonanceFate`, `ResonanceDamage` (damage types, protection rule),
  `TuningForkItem`, `ResonanceDebugCommand`.
- `power/resonance/mixin/`: `ResonanceShriekerMixin` (`SculkShriekerBlockEntity.shriek` / `trySummonWarden`),
  `ResonanceGameEventMixin` (`ServerLevel.gameEvent`).
- `client/power/resonance/`: `ResonancePowerClient` (ring, chips, bars), `ResonanceSenseClient`, `ResonanceHushClient`
  (muting, vignette, `silenced`), `ResonanceInputClient` (X and the scroll wheel); mixins `ResonanceGlowMixin`
  (`Minecraft.shouldEntityAppearGlowing`), `ResonanceScrollMixin` (`MouseHandler.onScroll`), `ResonanceSoundMixin`
  (`SoundEngine.play`).
- Shared: `core/Powers.java` (`addSilencer` / `silenced`: presses of R G V B, Z and C are dropped for a silenced
  player; releases pass), `JaymosPowersClient` (keys count as released while hushed), `JaymosPowers`, `fabric.mod.json`.
- Data: damage types `resonance_pulse`, `resonance_pulse_deep`, `resonance_sonic_boom` (`#bypasses_armor`;
  the boom also `#bypasses_shield`), entity tag `resonance_sensitive`, two recipes, a recipe advancement.

## Protection

Shatter, Pulse's panes and Shrieker's sculk break blocks only where the user `mayBuild()`, `mayInteract` (spawn
protection), isn't adventure-restricted, and Fabric's `PlayerBlockBreakEvents.BEFORE` passes (what claim mods hook).
Powers has no Kingdoms dependency, so a Kingdoms settlement blocks it only if Kingdoms listens to that event.

## Check first in game

1. Hush: sounds muted inside/for listeners inside; another player's R/G/V/B/Z/C dead inside; a Web user's symbiote
   still acts; held keys released on entering. The X key (Wind's kick, symbiote menu) is **not** gated (separate packets).
2. Echolocation outline shows only for the user (second client should see no glow).
3. Scroll with Z / X held doesn't move the hotbar and changes the sound / pitch.
4. Shriekers: corruption +20 per shriek, +50 on a Warden; the cure on a full disc.
5. Sonic Boom through a shield; the launch strength.
6. Throw Voice: mobs actually walk to the spot (goals may override the path for some mobs).
