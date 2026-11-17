---
status: open
for: any
from: Andy (Claude Code thread, Ragdolls and Cover pick-list)
asked: 2026-11-16
thread: 
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/power/chrono/future/
  - src/main/java/com/jaymospowers/armory/TaczGuns.java
---
# The future's gun gear: tracers, magazines, armour, TaCZ Additions

**Andy's words:** "These would be good for our future aesthetic" (Fracture Point, TaCZ Tracers & Flash, TaCZ
Additions, TaCZ Magazines, all by Raiiiden). Picked rows fu-tracers, fu-mags, fu-additions and fu-armour on the
Ragdolls and Cover pick-list (https://claude.ai/artifact/6v9SAjDsK47H9ay6MqgcPF). Then: "yeah put those on the board too."

All four are Forge/NeoForge 1.20.1 and 1.21.1 only, no Fabric build, so we build our own versions. Copy no code or
art. TaCZ's guns are the future's only (armory/TaczGuns), so all of this belongs there.

**What's wanted:**
- **fu-tracers:** some rounds leave a glowing streak and light up what they pass: a typed stroke along the shot and a
  moment of dynamic light, on future guns. Muzzle flash we already have (MuzzleFlash, TaczMuzzleFlashMixin).
- **fu-mags:** rounds go into magazines and a reload swaps a magazine. A magazine item per gun family, loaded by hand.
  Arsenal's handguns from the future already come "with only one magazine" (ArsenalFutureGun): make that a real item.
- **fu-additions:** read TaCZ Additions' feature list (https://www.curseforge.com/minecraft/mc-mods/tacz-additions; its
  summary only says "immersive and useful features") and bring Andy a short pick-list of the ones worth building
  before building any of them.
- **fu-armour:** tactical armour and kit (plate carriers, helmets, pouches, after Fracture Point): our own models, worn
  by the future's dead and echoes (FutureGuns) and lootable from them.

**Where to start:** docs/codemap/powers.md for TaczGuns, FutureGuns, MuzzleFlash, ArsenalFutureGun, TaczFirstPerson.
Effects only through typed FX (docs/MagicFx.md). Lore: the future (check Lore.md).

## Notes
- Not to be built in the Claude Code thread that made the pick-list.
- Builds go on the Deck or CI, never Andy's PC.
- Can be split into one PR per row.
- 2026-11-16 21:15 hermes-a's claim had no sign of work for 30 min: handed back as open.
