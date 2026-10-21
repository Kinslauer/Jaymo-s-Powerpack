# Wounds phase 5: polish (Powers 0.14.5)

The last phase of `docs/Wounds-Plan.md` ("5. Polish": client effects, limps and hanging arms, quadruped downed pose,
tuning, docs, JEI pages). Andy (2026-10-20): "last phase". Already done in earlier phases, so not in scope: hanging
arms (`client/Dismembered` reads `Wounds.brokenLimbs`), quadrupeds lying on their side (`client/wounds/DownedPose`),
grey downed screen and heartbeat while passed out (`DownedHud`), poppy haze, salt flash and fever shimmer
(`MedicineHud`).

Two tracks, side by side, touching different files. Powers only; no Kingdoms or Kitchen changes.

## Track A: client feel (`client/wounds/` and client mixins only)

- **Limp:** a broken leg (or sprained foot) gives a limp on the walk: that leg's swing shortened and the body dipping
  on its step (copy how `Dismembered` already poses a broken leg dragging straight, and extend it with a small
  vertical bob or a shortened swing on the other leg; keep to the model-pose hooks `Dismembered` already uses). A
  splinted leg (`Breaks.SPLINTED`, synced) limps half as much. Seen by everyone (the broken bitmask is synced to all).
- **Pain blur:** while the local player's HEAD or BODY part is under `PAIN_FROM` (0.35) and pain isn't dulled (read
  whatever Pain syncs to the owner; if numbness isn't synced, sync a boolean the same way `Pain` syncs its poppy
  attachment, which is a Track A edit to `Pain.java` allowed here), a soft dark vignette at the screen edges that
  pulses slowly, stronger the lower the part. Drawn like `DownedHud`'s overlay (fill rectangles / gradient the repo
  already uses), no shaders.
- **Red edge pulse on hits:** when any of the local player's parts drops (`WoundsHud` already compares frames for its
  white flash: reuse that), a red edge flash for `HIT_FLASH_TICKS` (6), stronger for a bigger drop.
- **Heartbeat when critical:** HEAD or BODY under `CRITICAL_FROM` (0.15) while not downed: the heartbeat sound
  `DownedHud` already plays while passed out, at a faster pace, plus a faint red pulse in time with it.
- **Gasp on smelling salts:** when `MedicineHud` sees the salt jolt start, play a short gasp on the client (a vanilla
  sound already used in the repo that suits it, e.g. the player's breath/hurt or a drowned's surface gasp; pick one the
  repo already references).
- All new numbers as `static final` constants at the top of the class. Nothing shows in creative or spectator.

## Track B: tuning, rules, docs, JEI (server side + docs + `client/jei/`)

- **Freerunning respects breaks** (from PR #339, `freerun/Freerun.canMove`): no freerunning moves with a broken,
  unsplinted leg or with both arms broken; a sprained foot or one broken arm blocks only climbing and bar swings
  (pick the move kinds in `Freerun` that use the arms). Through a small public query on `Breaks` (e.g.
  `Breaks.legsUsable(Player)`, `Breaks.armsUsable(Player)`), server and client both (the bitmasks are synced).
- **Hidden blade and Wounds:** Kingdoms' hidden blade does 10x on "crippled" foes (Kingdoms' Crippled effect, PR
  #339). Do not touch Kingdoms. If Powers can cheaply mark a foe with a broken leg so Kingdoms could read it, add the
  scoreboard tag `jaymospowers_crippled` while any leg is broken (added/removed in `Breaks`' 20-tick refresh) and
  note it in the MODLOG for a later Kingdoms patch; nothing more.
- **Tuning pass:** read every phase 1-4 constant (`Wounds`, `HitRouting`, `Downed`, `Breaks`, `Bleeding`, `Pain`,
  `Treatment`, `Dismember`'s sever numbers) and fix anything plainly off against the plan (e.g. a bleed that kills a
  full-health player faster than a minute, a bandage that heals less than the bleed it stops). List every change with
  old → new and why. Keep changes small; when unsure leave it and list it as a question.
- **Docs:** a "Wounds" section in `docs/Abilities.md` in its style: body parts and where hits land, downed and
  reviving, breaks and splints, bleeding and festering, every medicine item with its recipe and what it does,
  addiction, the Kingdoms clerics and taverns, and the debug command. Also a short Wounds entry in
  `docs/HANDOFF.md` with the 26.3 facts learned (AFTER_DAMAGE not firing on lethal hits; the ALLOW_DEATH →
  AFTER_DEATH order; the scoreboard tags shared with Kingdoms and Kitchen).
- **JEI:** a "Medicine" page in `client/jei/` using the repo's own `SimpleCategory` pattern (as `TinkeringJei`
  does), showing each medicine item with a line on what it treats. Register it in the existing `TinkeringJei` plugin
  (or a second plugin listed in `fabric.mod.json`'s `jei_mod_plugin`, as the repo does). Use only JEI API calls that
  `TinkeringJei`/`SimpleCategory` or Kitchen's `MillJei` already use.

## Release

Powers 0.14.5 (Track B bumps `gradle.properties`), one MODLOG entry covering both tracks (Track B writes it; Track A
reports its part for it), mark phase 5 built in `docs/Wounds-Plan.md`, new lang keys in `lang/parts/wounds.json`
(Track A adds under `wounds.jaymospowers.client.*`, Track B under `jei.jaymospowers.medicine.*`, to avoid edit
clashes: if both must edit that file, Track A adds its keys at the end).

Both tracks: copy every API shape from code that already compiles in this repo; no invented APIs; constants at the
top of each class.
