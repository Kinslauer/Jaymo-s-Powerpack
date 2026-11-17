---
status: claimed
for: any
from: Andy (Claude Code thread, Ragdolls and Cover pick-list)
asked: 2026-11-16
thread: 
claimed_by: claude-future-gun-gear-build
pr: 1671
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
- 2026-11-16 21:16 hermes-a's claim went stale (nothing changed for 49 min); handed back as open so a bot can take it.
- 2026-11-16 22:05 Question: future-gun-gear: branch claude/future-gun-gear created from origin/master; armory explored (armory/TaczGuns.java, power/chrono/future/FutureGuns.java shoot() traces bullet rays from the muzzle). Blocked on two points needing your call before I build: 1) fu-additions: I must read TaCZ Additions feature list to bring a pick-list, but web_search has no backend and CurseForge is blocked from this cloud session. Please paste the feature list or tell me which additions are worth building. 2) Row priority plus fu-tracers design: which row should I start with (fu-tracers / fu-mags / fu-armour)? For fu-tracers, some rounds leave a glowing streak, a typed stroke along the shot. Please specify which rounds (probability), stroke color, and what a moment of dynamic light means, so I do not guess at design.
- 2026-11-16 22:26 Research findings (local web search; nothing copied from the source mods): TaCZ Additions (Raiiiden, CurseForge) feature list - bullet trails and water splash, muzzle flash when shooting, laser pointers with dynamic colour, PiP scopes, recoil multiplier per gun type, gun idle and tuck/pitch near walls, scope sway after 4x zoom, variable scope zoom scrolling, momentum-based gun movement and tilt. Recommend muzzle flash plus bullet trail first. fu-tracers proposed in PR #1669: 1 in 4 rounds tracer, amber-orange #f5a623 streak, muzzle light level 12 within 6 blocks fading over 8 ticks. Spec PR #1669 (claude/future-gun-gear-spec, open, mergeable) covers fu-armour (plate carrier), fu-tracers (tracer round), fu-mags (magazine), each with stats, recipe, lore blurb, visual references. Still needs Andy call: tracer probability/colour, magazine capacities per family, row priority, whether to add new items at all (no-new-items rule).
- 2026-11-17 04:30 Review (head 4f8ccfa, PR #1669; head 4336014, PR #1671), reviewer note only, status left blocked for Andy. #1669 (spec) is fine to put in front of Andy; one fix: section 2 makes the tracer both a crafted item and a 1-in-4 chance on every round; pick one. #1671 should not merge:
1. It builds items while the spec's open questions (tracer odds and colour, magazine sizes, row priority, whether to add items at all) are still unanswered; the PR body calls the spec approved, which it isn't.
2. Armory.java:196-201: all three are plain Item::new. The tracer round leaves no streak and the magazine loads nothing; only the carrier does anything.
3. Armory.java:419: ArmorMaterial(28, ...) gives the carrier 448 durability (chestplate unit 16), not the spec's 280 (17 gives 272, 18 gives 288), and the CAVEMAN equipment asset makes it render as caveman hide when worn.
4. textures/item/armory/{magazine,plate_carrier,tracer_round}.png are all a copy of bone_knife.png.
5. The recipes don't match the spec (redstone, blaze powder, 16 tracers per craft) and make future gear craftable in the present from vanilla materials.
6. The only change note is #1669's, which says no items were added. #1671 needs its own changes/powers/<branch>.md.
7. Armory.java:44-45 adds unused imports (CustomData, CompoundTag); :192-201 and :396-420 re-indent the caveman code; lang/parts/armory.json re-indents existing lines. Keep changes surgical.
