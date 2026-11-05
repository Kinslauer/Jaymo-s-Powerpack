# Ultimates: each one its own evolved alien

Andy (2026-11-04), on XenoBand-Plan patch 2: "this is how ultimates should work". Each Ultimate is its own evolved
alien, not a buff on the base one. It gets a new model and skin (bigger, armoured, with spikes, plates and glowing seams) that grows out of the
alien's current body, and its own R G V B. Today's Ultimate move mostly becomes C, and the rest are stronger takes on the base moves.
C as an ascended alien changes into it with a short film. It lasts until the charge runs out, and it has its own health on top of
the base alien's. Nadir gets none. Every other alien gets one (40, Ooze and Magma Ooze sharing one with a molten variant).

What was built in 0.18.93 (an armoured skin and spikes on the base body, +50% health and fists, halved cooldowns, the Ultimate
move on B) stays as the fallback until each alien's patch lands. Patch 6 removes it.

Each alien entry below gives what its Ultimate does **today** (read from the code), then the new body, kit, health and film.
Before the band is reforged, an Ultimate only comes on by itself as a last stand, when a boss or great foe is about to kill you (item 5). The change-in film letterboxes and zooms on each changing part, longer the first time, and others nearby see it unless they're fighting (item 2).
Every entry was checked against the code by a second pass. Drop-back is written as the recommended key (open question 2).

## Patch 0: the framework

**1. How an Ultimate is represented and how keys reach it**
- Add an abstract `XenoUltimate extends XenoForm` in its own file, `power/xenoform/XenoUltimate.java`. Each Ultimate is one class in `forms/ultimate/<Name>.java`. Because it extends `XenoForm`, each Ultimate gets its own body fields for free (`scale`, `health`, `damage`, `speed`, `knockbackResist`, `stepUp`, `icon`, `color`), along with the protected static helpers (`ready`, `cooldown`, `aimed`, `around`, `summon`, `dismiss`, `shove`, `burst`, `beam`, `ring`).
  - Its constructor takes the base `XenoForm base`. It passes `base.id` to `super(...)`, so anything keyed on the id (the models map, the colour lookup in `XenoformLayer`, the lang) still finds the base alien. It overrides `name()` to return `base.ultimateName()`.
  - `scans(Entity)` returns false and `hidden()` returns true.
  - It is never registered in `XenoForms` (`XenoForms.register` is private). So it is never on the dial and never counted by `XenoForms.scannable()` toward a complete band.
- New hooks on it:
  - `onBigMove(player, live)` for C.
  - `Item[] icons()` for the R G V B C chips, so tracks don't edit `XenoformHud.ICONS`. The client can reach it through `XenoformState.form(player).evolved()`, because `XenoForm` is common code.
  - `String[] morphParts()`, the parts the change-in film zooms on, in order (see item 2).
  - It keeps `onEnter`, `onExit`, `tick`, `onKey`, `flight`, `allowsFloating`, `skipsMovementCheck`, `allowDamage`, `onDealtDamage` and `onModeTap` (which returns boolean) from `XenoForm`.
- On `XenoForm`, add `public @Nullable XenoUltimate evolved()`, null by default.
  - `Ooze` is registered twice (`OOZE = new Ooze(false)`, `MAGMA_OOZE = new Ooze(true)`; its field is `magma`). Each instance builds its own `UltimateOoze(boolean magma)` from the one class.
  - `Dud` gets a new one. Dud is the only form with no `onUltimate` today, and the only one with no `xenoform.jaymospowers.dud.ultimate` lang name.
  - `Nadir` keeps `ultimateIsMove()`, and its C path stays exactly as it is.
- `Live`: replace `public boolean ultimate` with `@Nullable XenoUltimate ult`.
  - Keep `ultimate` as a mirror (`= ult != null`) only until the four forms that read it are migrated: Bulwark, Pyre, Wyrm and Duplex. `XenoForm.cooldown` reads it too.
  - Add `Live.body()`, which returns `ult != null ? ult : form`.
- Use `body()` instead of `live.form` in these places:
  - `XenoformPower.tick`: the `tick(...)` and `flight(...)` calls.
  - `onKey`.
  - `onMode`: the `onModeTap` call.
  - `allowsFloating()` and `skipsMovementCheck()` (lines 626 and 632).
  - `ALLOW_DAMAGE`: the `allowDamage` call.
  - The second `AFTER_DAMAGE` hook: the `onDealtDamage` call. The first `AFTER_DAMAGE` hook only drains the charge and stays on `live.form != null`.
  - `corrupt(...)` and `syncAct(...)` (which switches on `live.form.id`) stay on the base alien unless an Ultimate needs its own act poses.
- `live.form` stays the base alien. `formOf()`, the `FORM` attachment, `XenoformState.form(player)`, `statusSuffix`, the Kingdoms tags, `XenoVoices`, `CryoLent.lender` and `NadirNet` all keep seeing the base.
- Fix the Dud check in `ALLOW_DAMAGE` (`live(attacker).form == XenoForms.DUD`) to use `body()`. Otherwise the new Dud Ultimate can't hurt anything either.
- Cooldowns:
  - Raise `XenoformState.Hud.SLOTS` from 4 to 5 and add `XenoForm.C = 4`. The `Hud` codec and `NONE` already loop over `SLOTS`.
  - `live.cooldownUntil` and `cooldownLength` are `final` arrays. When the Ultimate comes on, copy them into `Live.baseCooldownUntil/baseCooldownLength` and `Arrays.fill` them with 0. Copy the saved values back on drop-back. They are game times, so they kept running.
  - `XenoformHud` draws a fifth chip (C): `chipsOrigin(width, height, 5)`. Its key label comes from `JaymosPowersClient.ultimateKey()`, because `moveChip` labels with `PowerKey.values()[slot]` and `PowerKey` has only 4 values. The chip shows while `ULTIMATE` is on, and also while the `ULT_REENTER` cooldown runs (see item 4).
  - `live.ultimateNext`, `ultimateReady` and `ultimateCooldown` are not dropped in patch 0:
    - Nadir's C path uses them (`Nadir.onUltimate` → `ultimateReady`/`ultimateCooldown(NadirFilm.COOLDOWN)`) and has to stay as it is, so Nadir keeps them for good.
    - Duplex, Gremlin, Wisp, Prism, Velox, Knell and Rime also use them on the fallback path.
    - Each batch moves its form's Ultimate-move cooldown to slot `C`. The last batch can then reduce them to Nadir-only.
- The current buff (added in 0.18.93) stays as a fallback for any form whose `evolved()` is still null, so nothing breaks while the batches roll out. The fallback is:
  - `ultimateBody` with `ULT_HEALTH`, `ULT_DAMAGE` and `ULT_SCALE`.
  - The `ULT_COOLDOWN` halving in `XenoForm.cooldown`, applied only on that path.
  - `form.onUltimate` as the entry check.
  - `tickUltimate`.
  - `onUltimateAgain`/`ultimateHasMove` on B.
- The last batch deletes all of it:
  - `ULT_*` and their modifier ids (`ULT_HEALTH_ID`, `ULT_DAMAGE_ID`, `ULT_SCALE_ID`).
  - `ultimateBody`, `onUltimate` (except Nadir's), `onUltimateAgain`, `ultimateHasMove`, `tickUltimate` and `onUltimateEnd`.
  - `XenoformLayer.ultimate()`, `UltimateSpikes.java`, and `gen_xenoform.ultimate()`/`ultimate_spikes()`.
  - `UltimateSpikes` only fits humanoid bodies (`AlienBodies-Plan`), so its `STYLES` table (`record Style`) is only a reference for where each model's spikes go.

**2. The change-in (C as the base alien)**
- `XenoformPower.onUltimate`, new path: run `XenoformPower.evolve(player, live)` when `form.evolved() != null`. The fallback path keeps today's code.
  - It checks the entry rules in items 4 and 5. Then it calls `form.onExit` to turn the base alien's extras off (`clearForm` is not called), and saves `live.baseHealth = player.getHealth()` and the base cooldowns.
  - It sets `live.ult` and calls `apply(player, ult)`. That is the same attribute modifiers: `apply` calls `unapply` first, then sets each one relative to the human's attributes, now with the Ultimate's own body. Then it calls `player.setHealth(ult.health)`, sets `XenoformState.ULTIMATE` true, and calls `ult.onEnter`.
  - `dropUltimate` is the reverse:
    - `ult.onExit`
    - `apply(player, live.form)`
    - `setHealth(live.baseHealth)`
    - the cooldowns put back
    - `ULTIMATE` removed
    - `form.onEnter`, so the base alien's extras come back
  - `revert` with `live.ult != null` calls `ult.onExit` and then continues with today's steps, including `form.onExit`. It does not run the drop-back's `form.onEnter`. `form.onExit` was already called at change-in, so it now runs twice. Each base `onExit` must be safe to run twice, or `revert` skips it when an Ultimate was on.
- **The change-in film** (Andy, 2026-11-04: "the film should letterbox and zoom on the parts of the user that are changing, meaning the first ultimate transformation can be a bit longer and exaggerated, and the cutscene shows for other players unless they are in combat, if not they can just see the transformation in world").
  - New shot `UltimateFilm.MORPH` (10): letterboxed, it zooms in on each changing part in turn (each Ultimate lists its parts in order, `XenoUltimate.morphParts()`, e.g. Bulwark: arms, shoulders, back plates, head), holds while that part grows from the base body into the Ultimate's, then pulls back to show the whole new body. The `Play` packet gains the part list and the per-part beat.
  - The body changes part by part on the client in step with the film (`XenoformClient` lerps each part from the base model's size and place to the Ultimate's over its beat, its seams lighting as it lands). Everyone sees this, film or not.
  - **First time** each player becomes a given Ultimate: the long cut, `ULT_FILM_FIRST` (100 ticks, `MAX_TICKS`), with bigger zooms, a slow-down on each part, the band crackling, a shake and a roar on the last beat. After that: the short cut, `ULT_FILM_TICKS` (40). The seen Ultimates are kept on `BandUpgrades` (a new `seenUltimates` set, saved).
  - **Other players** within `ULT_FILM_WITNESS` (32 blocks) get the same film with their own camera, unless they are in combat (hurt by or hitting a mob in the last `ULT_FILM_COMBAT` 100 ticks, as `ZFighterBattle`'s `IN_FIGHT_TICKS` reads it). Those in combat get no film and no bars and just see the change happen in the world. A new `UltimateFilm.shared(...)` sends the caster's film to each witness (today's `witnessed` only sends bars).
  - Skipping works as today (skip key, an attack, a hit) for the caster and each witness alone. Nothing on the server is timed to the film, so a knockout or a revert during it has no race to handle. The base alien's stats switch to the Ultimate's at the start; only the look grows in.
- Client: keep `ULTIMATE` as a synced Boolean (`AttachmentSyncPredicate.all()`, not persistent). `XenoformClient` already copies it into the `XenoformClient.ULTIMATE` render-state key. The Ultimate model is found from the base id in `FORM`.
  - Add one resolver, `XenoformClient.model(String form, boolean ultimate)`. It tries `XenoFormModels.ultimate(id)` and falls back to `of(id)`.
  - Use it in the three places that resolve the model a player is wearing:
    - `XenoformLayer.submit`
    - `XenoformHand.render`
    - `XenoformClient.submitGhost`
  - Leave these on the base models:
    - `XenoBand.silhouette`, which shows the dialled alien, not the one worn.
    - `WyrmWings.setupAnim`, which always reads `of("wyrm")` for the dragon torso. An Ultimate Wyrm draws its own wings in `submitExtras`.
    - `XenoDoubleRenderer`, `RivalAlienRenderer`, `XenoStalkerRenderer` and `XenoHologram`.
- Models: one file per Ultimate, `client/power/xenoform/ultimate/Ultimate<Name>.java`, each extending `XenoFormModels.FormModel` (a `HumanoidModel<AvatarRenderState>`).
  - This keeps tracks out of the 9,331-line `XenoFormModels.java`. Each batch only adds lines to a new `ULTIMATES` map in it, filled lazily like `MODELS` in `of()`.
  - To allow this, make the `FormModel(ModelPart, String)` constructor and the package-private `renderScale`, `insigniaY/insigniaZ/insigniaSize` fields protected. `ownsTap` and `ownsLegs` are already public.
  - Add `submitExtras(...)`, a no-op by default, for each model's own extra passes (glow, wings, comet).
  - `XenoformLayer`'s `instanceof Wisp/Lumen/Pyre/Wyrm` branches won't match an Ultimate model. Their effects move into that model's `submitExtras`.
- Each model is an evolved version of the base alien's current body (`AlienBodies-Plan`). It keeps the inherited hidden humanoid parts:
  - `body`, posed where the insignia sits (`XenoBand.submitInsignia`, `XenoformSlap`).
  - `rightArm`/`leftArm`, posed for the first-person hand (`XenoformHand`).
- `XenoformLayer`, when the model is an Ultimate model:
  1. Draw `skin(model, state, true)`, which is `model.ultimateTexture` through `XenoLooks.skin` (look `PLAIN`, band glow). It is not `model.texture`: built with the base id, `texture` would be the base skin.
  2. Draw `model.ultimateGlow` fullbright (`entityTranslucentEmissive`, `FULL_BRIGHT`) with the existing `SEAM_PULSE`.
  3. Call `submitExtras`.

  Drop the `UltimateSpikes` pass for it.
- Skins keep today's names. `FormModel` already sets these from the id:
  - `ultimateTexture` = `textures/entity/xenoform_<baseId>_ultimate.png`
  - `ultimateGlow` = `_ultimate_glow.png`

  An Ultimate's own UV layout is 64x128, or larger where it needs it (set in its `bake`).
- The generator is `tools/gen_xenoform.py`. `ultimate(form)` at line 4308 recolours the base skin, and `ULTIMATE_COLORS` lists the forms; Dud and Nadir are not in it.
  - Put the new painters in a new `tools/gen_xeno_ultimates.py`. It imports the helpers from `gen_xenoform` (`box`, `parts`, `noise_fill`, `eyes`, `chest_insignia`, `save`); `gen_xenoform`'s generation only runs under `if __name__ == "__main__"`.
  - The helpers draw from the module-level `gen_xenoform.rng = random.Random(2603)`, not from the global `random`. So each painter calls `gen_xenoform.rng.seed("<id>_ultimate")`; `random.seed` would change nothing.
  - That way tracks don't edit one script, and the older skins' random speckle doesn't shift.
  - When an alien's new skin lands, take it out of `gen_xenoform`'s `ultimate()` loops in `__main__`.
- Lang (`lang/parts/xenoform.json`):
  - The name is `xenoform.jaymospowers.<id>.ultimate`. It exists for every form except Dud, which needs one.
  - Key descriptions follow the existing convention: `power.jaymospowers.xenoform.key.<slot>.<suffix>` plus `.name`, where the suffix comes from `statusSuffix`. Rivet's shells already work this way (`"." + form.id + "_" + shell`).
  - So `statusSuffix` returns `"." + form.id + "_ultimate"` while `ULTIMATE` is on, and each Ultimate adds `power.jaymospowers.xenoform.key.ability_1`-`4.<id>_ultimate` (+ `.name`) and `power.jaymospowers.xenoform.key.ultimate.<id>_ultimate` (+ `.name`).
  - The existing `key.ultimate.<id>` entries (for example `key.ultimate.bulwark` = "Titan") now describe the change-in from the base alien.
  - Rewrite the general `power.jaymospowers.xenoform.key.ultimate` text, which describes today's buff.
  - Add `xenoform.jaymospowers.<id>.ultimate.desc` if the band screen shows one.

**3. Drop-back key: hold C (recommended)**
- This mirrors Z, where holding Z for `HOLD_TICKS` as an alien turns you back.
  - `Powers.ultimate` already passes both press and release. `XenoformPower.onUltimate` returns on `!down` today, so that early return goes.
  - The Z+C wild-dial toggle and the Nadir path stay on press.
- Store `live.ultDownAt` on press. On release:
  - held at least `ULT_DROP_HOLD` (10 ticks, the same as `HOLD_TICKS`) → `dropUltimate`
  - shorter → `ult.onBigMove`

  So every C move is a tap; none is held. A tap from the powers screen (`TAP_ULTIMATE`) lasts 3 ticks, so it counts as a tap.
- Holding Z as an Ultimate still goes straight to human through `revert`, which undoes the Ultimate first.
- Quick change (`quickChange` → `swap`) leaves the Ultimate through `revert`. The wild dial's `swap` in `tick` waits while `live.ult != null`.
- Sneak+C was the other option. I didn't pick it because Ultimates want sneak variants (Gremlin's swap already uses sneak) and sneak is "descend" for the fliers.

**4. Its own health pool**
- The Ultimate's `health` is its own max-health bar (the vanilla hearts, set by `apply`). The base alien's health is frozen in `live.baseHealth`, the same way `humanHealth` sits under the base alien today.
- Order of damage:
  1. Hits drain the Ultimate's bar.
  2. When it empties, you are back on the base alien's bar.
  3. When that empties, you are knocked out as today: `revert(knockout = true)` puts you back at `humanHealth`, and nothing carries over.
- In `ALLOW_DEATH`, after the existing `BYPASSES_INVULNERABILITY` check: if `live.ult != null`, the Ultimate breaks. That is:
  - `dropUltimate(player, live, BROKEN)`
  - `setHealth(live.baseHealth)`
  - Nausea, plus Slowness III (amplifier 2) for `ULT_BREAK_STUN` (30 ticks)
  - a glass-break sound
  - `return false`

  Damage left over from the killing hit is lost. A break is not a knockout: it doesn't go through `revert`, so it adds no `KNOCKOUT_CORRUPTION` and `XenoVoices.formEnded` doesn't count it toward `KNOCKOUTS`.
- No free heal from going in and out:
  - On a voluntary drop, save the bar's health in `live.ultHealth` and give it back on the next change-in during the same change.
  - After a break, `live.ultBroken` blocks C for the rest of the change.
  - Both reset in `clearForm`.
- Re-entry has a short cooldown, `ULT_REENTER` (200 ticks), kept in slot `C` of the base cooldowns while you are the base alien. So the C chip also shows while that cooldown runs.
- HUD: add `under` and `underMax` (the base alien's held health) to the `Hud` record, its `STREAM_CODEC`, `NONE` and `same()`. `XenoformHud` draws them as a thin bar in the alien's colour between the timer and the chips, only while the Ultimate is on.
- Relog: nothing new to store.
  - `DISCONNECT` → `revert(player, false, false)` already undoes the Ultimate.
  - `ULTIMATE` is not persistent.
  - `KEPT` holds the band's recharge and charge as they stand after that revert. Base alien and Ultimate share `ticksLeft`, so nothing differs.

**5. The charge**
- Ascension (`tickAscension`) needs these two things:
  - The band has unlocked as many non-hidden aliens as `XenoForms.scannable()`.
  - `Fate.canAscend` passes, which means full mastery (`Mastery.unlocked >= Mastery.ALL`).

  It does not check `BandUpgrades.get(player).reforged()`, so an ascended player can still have an unforged band. `charged()` is also true in creative.
- **Unforged band: the last stand** (Andy, 2026-11-04: "they are activated if you are in a situation where you are about to die, usually to a boss or great foe"). Before the reforge, C does not change you. Instead the Ultimate comes on by itself:
  - When a hit would take an ascended alien below `ULT_LAST_STAND` (25%) health, or kill it, while it is fighting a great foe: a vanilla boss (Ender Dragon, Wither, Elder Guardian, Warden) or anything showing a boss bar (Kingdoms' giants, monster kings, knowledge bosses; the same test as Kingdoms' `WorldTiers`, read through a `KingdomsCompat` check, with the vanilla list as the fallback).
  - The hit is cancelled, the film plays, and you are the Ultimate with its full health. Once per change.
  - It then lasts for the rest of the random timer. When the timer ends, you go back to human as today.
  - C as the Ultimate is its big move as usual; hold C drops back (and the last stand is spent for that change).
- **Reforged band** (Andy picked "C at will once reforged", 2026-11-04): C changes you in at will. It lasts until the charge runs out: each tick as the Ultimate also takes `ULT_DRAIN` off `ticksLeft` (`ULT_DRAIN = 1` doubles the burn).
  - Do it directly in `tick` with the same `charged()`/creative guard, not through `drain()`. `drain` → `spend` sets `hudDirty` on every call, which would re-sync the HUD every tick.
  - Hits (`HURT_DRAIN`) and moves (`MOVE_DRAIN`) drain as usual.
  - The last stand still works on a reforged band too, so a player caught by a boss with C on cooldown is saved once.
  - Change-in by C needs `ticksLeft >= ULT_MIN_LEFT` (400 ticks), or C shows the `ultimate_not_now` hint. A move with its own charge price uses `spend()`, which costs charge whether the band is forged or not (never in creative, and never below 1 tick).
- When the time runs out as the Ultimate, `revert` sends you straight back to human.

**6. Everything else every batch needs**
- Nadir: the `ultimateIsMove` path is untouched. It plays `NadirFilm`, fires on press, and keeps `live.ultimateNext`/`ultimateReady`/`ultimateCooldown`.
- Fuse: the Ultimate's version of Self-Destruct (V, hold) keeps paying with `XenoformPower.spend(player, live, CHARGE_COST)`. While Fuse is invisible during its re-form, it stays the Ultimate.
- Gifted villagers: no Xenoform `GiftKit` exists, so nothing changes.
- `RivalAlien`/`RivalHunter` and `XenoDouble` keep the base aliens in patch 0.
- Knockouts: `revert(knockout = true)` from base-alien death works as today and undoes the Ultimate first. `XenoJam` reverts too, from `tick`.
- Kingdoms: no change. The tags are still `jaymospowers_xenoform` and `jaymospowers_xenoform:<id>` (`XenoformState.TAG`).
- Insignia and first-person hand: covered by the model rules in item 2.
- Tuning: all new numbers are `static final` constants at the top of each class:
  - In `XenoformPower`: `ULT_DROP_HOLD`, `ULT_BREAK_STUN`, `ULT_REENTER`, `ULT_DRAIN`, `ULT_MIN_LEFT`, `ULT_LAST_STAND`, `ULT_FILM_TICKS` (today's `ULTIMATE_FILM_TICKS`), `ULT_FILM_FIRST`, `ULT_FILM_WITNESS` and `ULT_FILM_COMBAT`.
  - In each `XenoUltimate`: that Ultimate's own numbers.
- Debug: add `/powerdebug xenoform ultimate <on|off|break>` next to the existing `band`/`bands`/`hunter`/`star`/`unlock`/`form` literals in `XenoformDebugCommand`.
- Docs: rewrite the Stage 3 section of `docs/xenoform.md` ("## Stage 3: ascension, the Ultimates, the twists, the kingdoms"). Bump the version (currently 0.19.85 in `gradle.properties`).
- Patch 0 ships with one Ultimate migrated end to end, for example Bulwark's Titan, to prove the framework. The other batches run 2 tracks at a time. Each touches only its own `forms/ultimate/*`, `client/.../ultimate/*` and painter, plus one line each in the `ULTIMATES` map, `evolved()` and the lang.

## Patch 1: the first Ultimates (Bulwark, Pyre, Velox, Prism, Wisp, Gremlin, Knell) (done, 0.20.21)

### Bulwark -> Titan
- **Today:** Titan makes you twice your size (`TITAN_EXTRA_SCALE`), gives you +12 armour (`TITAN_ARMOR`) and +20 health (`TITAN_HEALTH`), and heals that 20 when it starts, with a ring of `Debris` and a heavy shake. While it lasts, every Ground Clap also smashes walls 3 deep, 3 wide and 3 tall ahead (`smashWalls`, hardness up to `TITAN_BREAK_MAX_HARDNESS` 6), and you puff dust (POOF) as you move (`tickUltimate`). It has no move of its own (`ultimateHasMove` is false), so B stays Leap Slam.
- **Body:** It is still the four-armed thin-waisted biped, 2.0x your size (about 1.35x Bulwark). Its arms keep the swapping raised-pair guard. Iron-grey stone plates cover the shoulders, forearms and shins. Knuckle spikes sit on all four fists, and a ridge of short spikes runs down the spine. Molten orange seams glow between the plates. The lower pair of arms is thicker and plated like gauntlets. It lumbers with a heavier sway and puffs dust at each step (POOF, as `tickUltimate` does now).
- **R:** Quake Combo, a stronger Fist Combo. It throws four blows, one per arm, at 7 each, then a double-fisted hammer that launches the target and breaks the block behind it (hardness up to 6, the same check as `smashWalls`).
- **G:** Wallbreaker Clap, a stronger Ground Clap. The shockwave reaches 7 blocks and hits for 9. Today's Titan wall smash (3x3x3 ahead) comes with every clap.
- **V:** Boulder Hurl, a stronger Grab. Each pair of arms holds a creature, so V grabs two and V again throws both. Aimed at the ground, it rips out an 8-block chunk (`BlockCluster`, today 4) that bursts into `Debris` on landing and hits everything within 3 for 8.
- **B:** Meteor Slam, a stronger Leap Slam. The leap goes higher. The landing hits everything within 6 for 12, shakes the screen at Titan strength (`TITAN_SHAKE`) and shoves everything outward.
- **C:** Earthsplitter. All four fists hammer the ground. A fissure runs 12 blocks along your look and tears up soft blocks as `Debris`. Everything on the line takes 14 and is flung up. Every 30 s. Holding C for 1 s drops you back to Bulwark.
- **Health:** +15 hearts (30).
- **Film:** MORPH on right_lower_arm, left_lower_arm, pauldron, spine, then head, then the whole body. First time: each gauntlet arm slams down in slow motion, the band crackling as stone plates lock on and the ground shakes.

### Pyre -> Sunheart
- **Today:** Sunheart makes the Flame Jet streak faster (`FLY_SUN`: top cruise `CRUISE_TOP_SUN` 2.0 instead of 1.4) with a longer, wider comet (`PyreComet`). R becomes a held beam of fire, 12 blocks long (`BEAM_RANGE`). Every 4 ticks it deals 2 to the creature it hits and sets it alight for 3 s, or, if it hits no creature, it lights the block it ends on. It has no move of its own (`ultimateHasMove` is false), so B stays Magma Trail.
- **Body:** It stays humanoid (Andy's rule), 1.25x your size, still a body of living flame with fire hair streaming up off the head. Plates of cooled black basalt crust the shoulders, chest, forearms and shins. Their cracks glow white-yellow, and those seams are drawn fullbright in the same second pass Pyre's skin already gets (`GLOW`). A crown of flame-tipped spikes rises from the head and shoulder blades, and the fire hair streams higher. It hovers a hand's width off the ground as it walks, shedding SMALL_FLAME.
- **R:** Sunray, a stronger Fire Bolt. This is today's Sunheart beam, held, now 16 blocks long and dealing 3 every 4 ticks. Its end sets blocks alight.
- **G:** Sun Jet, a stronger Flame Jet. It flies with today's `FLY_SUN` speed. The comet (`PyreComet`) is wider and sets alight every creature it passes within 2 blocks.
- **V:** Blaze Nova, a stronger Fire Nova. Everything within 7 takes 8, is knocked back and burns for 8 s (today: 5 blocks, 6 damage, 5 s).
- **B:** Lava Wake, a stronger Magma Trail. For 8 s (today 6 s), every step leaves fire and temporary magma blocks, which turn back into the old block after 5 s. Anything standing in the wake burns.
- **C:** Corona. You flare like a small sun. Everything within 10 takes 10 fire damage and burns for 8 s. Players get 2 s of Blindness. Snow and ice within 6 melt, and a flat ring of 12 `SmallFireball`s flies outward. Every 30 s. Holding C for 1 s drops you back to Pyre.
- **Health:** +10 hearts (20).
- **Film:** MORPH on right_arm, left_arm, core, hair, crown, then the whole body. First time: the basalt crust creeps over each limb in slow motion and splits white-hot, then the flame crown flares skyward with a roar.

### Velox -> Standstill
- **Today:** Pressing C to ascend casts Standstill straight away, and while ascended B casts it again once the 30 s cooldown is over (`STANDSTILL_COOLDOWN`). For 6 s, everything living within 24 gets Slowness VII and its motion cut to 10% each tick. Projectiles within 24 hang at 5% speed and fly on at full speed, with their gravity back, when it ends or when they leave the range (`XenoformPower.Held`). You get Speed II, and any Speed you already had comes back afterwards.
- **Body:** It is still the forward-leaning two-legged raptor with the S-neck, tucked clawed forearms and the long balancing tail, now 1.15x your size (about 1.3x Velox). Blue-black plates run along the spine and down the tail. The swept-back crest becomes a fan of blade quills, the tail ends in a blade, the sliding visor is plated, and each foot has a sickle claw. Its seams glow the light blue of Velocity's streak (`ChronoSpeed.SKY`) and brighten with speed. At a run the tail already straightens; now the quills also flatten back.
- **R:** Overdrive, a stronger Velocity. It is the same toggle at 1.5x `VELOCITY_SPEED`. Creatures you run into take 3 and are shouldered aside.
- **G:** Thousand Blows, a stronger Rapid Punches. It lands 12 blows (today 6), one every tick, at 2.5 each. The last one knocks the target back.
- **V:** Vortex, a stronger Cyclone. The sweep covers 9 blocks (today 6) and lifts up to 6. When it ends, everything caught is thrown outward. Projectiles inside are caught and held the way Standstill holds them (`XenoformPower.Held`), then flung outward with the rest.
- **B:** Flicker Storm, a stronger Chain Strike. It flickers between up to 8 foes within 14 blocks for 7 each (today 5 within 8 for 5), then returns you to where you started.
- **C:** Standstill, today's move. It now lasts 8 s, and the Speed it gives you is Speed III. Light blue after-images trail your path (`ChronoSpeed` streak) while it runs. Every 30 s. Holding C for 1 s drops you back to Velox.
- **Health:** +8 hearts (16).
- **Film:** MORPH on sickle, tail4, crest, visor, then the whole body. First time: the world slows to grey as each plate snaps on, and the crest fans into blade quills with a shriek.

### Prism -> Geode
- **Today:** Pressing C to ascend casts Geode straight away, and while ascended B casts it again once the 20 s cooldown is over (`GEODE_COOLDOWN`). Everything within 6 (`GEODE_RADIUS`) gets Slowness, Weakness and Mining Fatigue for 4 s and has amethyst blocks grown round its feet and head. It is held still and loses its target. Projectiles within 6 shatter. You get Resistance III. The amethyst is removed when it ends.
- **Body:** It is still the faceted crystal core on six thin shard legs with its tripod crab gait, now 1.5x your size (about 1.35x Prism). The core is cased in dark amethyst plates with glowing violet-white seams, and its two front facets glow brighter. The crystals on top are taller, faceted spires with sharp tips. Its walk is slower and heavier, and the spires jiggle and chime (amethyst sounds) at each step.
- **R:** Shard Storm, a stronger Crystal Shards. Seven shards fan out wider (today 3), each dealing 6 and piercing up to 2 creatures.
- **G:** Rampart, a stronger Crystal Wall. A curved amethyst wall 7 wide and 4 tall wraps your front for 15 s (today a straight wall 5 wide and 3 tall, 3 blocks ahead, for 10 s).
- **V:** Mirror Root, a stronger Reflect (hold). You root in crystal as now. Today only arrows fly back and other projectiles shatter. Now every projectile that hits you, fireballs included, flies back at its shooter at `REFLECT_SPEED`, and melee blows return 30% of their damage to the striker.
- **B:** Spike Forest, a stronger Spike Field. It reaches 7 blocks (today 4.5) and deals 7, with the same slow. Temporary amethyst clusters stay up for 3 s and are removed the way Geode's crystals are. Anything stepping in them takes 2.
- **C:** Geode, today's move, now reaching 8 blocks and lasting 5 s. Every 20 s. Holding C for 1 s drops you back to Prism.
- **Health:** +12 hearts (24).
- **Film:** MORPH on leg, core, crystal, then the whole body. First time: amethyst crawls up each leg in slow motion and the core cracks, then the spires burst up chiming as the ground shakes.

### Wisp -> Revenant
- **Today:** Pressing C to ascend slips you into the mob you look at (8 blocks, not a player or a minion) straight away. While ascended, B does it again once the 15 s cooldown is over (`POSSESS_COOLDOWN`), or lets go while you wear one. You wear the mob for up to 10 s with its AI off. It walks where you look and jumps at walls, you are invisible, and mobs stop targeting you. When you let go it wakes with Weakness II, Glowing and Slowness III.
- **Body:** It is still the hooded legless ghost of see-through fullbright skin over ki, 1.2x your size. The hood is tattered, with a bone-pale mask and collar plates. The hem frays into long trailing tendrils, the eyes glow ice blue, and thin ice-blue seams run through the cloth. It has more ki shells, set farther out (`XenoFormModels.Wisp.KI_SHELLS`, drawn by `WispKi`). It floats and bobs as now, swaying as it moves.
- **R:** Wraithwalk, a stronger Phase. It goes through walls up to 5 thick (today 2). Creatures in the way are passed through, take 3 and get Slowness II for 3 s.
- **G:** Veil, a stronger Shroud. You fade from sight as now. The first strike out of it deals +6 and breaks it.
- **V:** Banshee Wail, a stronger Dread Wail. It reaches 16 blocks (today 10) and deals 4. Everything gets Weakness for 6 s, creatures flee as now, and players get darkness.
- **B:** Drift, a stronger Glide (hold). It becomes slow true flight: you float forward along your look, Jump rises gently.
- **C:** Revenant, today's move. You wear the host for 15 s, and the host's own attack strikes what you aim at (`doHurtTarget`). C again while worn lets go. Every 15 s. Holding C for 1 s drops you back to Wisp.
- **Health:** +6 hearts (12).
- **Film:** MORPH on tatters, body, hood, head, then the whole body. First time: the ghost frays to mist in slow motion and reforms larger, and the bone mask slams onto the face with an icy wail.

### Gremlin -> Swarm
- **Today:** Pressing C to ascend splits off three glowing silverfish clones that follow you and never attack players or minions. When you Bite (V), any clone within 4 blocks of the target bites it too. While ascended, B swaps you with the farthest clone (the nearest while sneaking), at most once a second (`SWAP_COOLDOWN`). The clones are dismissed when it ends.
- **Body:** It is still the hunched imp on its knuckles with the big bobbing skull and floppy ears. It grows from 0.3x to 0.45x, still under one block so it fits one-block gaps. The skull gets a plated brow with a crest of small horns, and the knuckles get plates. Its thin tail grows barbs and curls up over the back. Green seams (0x9ADC5A) glow along the plates, and the eyes glow brighter. It scurries faster.
- **R:** Pounce, a stronger Spring. It is the same huge, soft-landing jump. Landing on or beside a creature bites it for 5.
- **G:** Haywire, a stronger Sabotage. Every lever, button, door, trapdoor and gate within 4 blocks of the aimed block flips at once, from up to 24 blocks (today one block from up to 16).
- **V:** Leech Bite, a stronger Bite. It deals 5, steals 6 food, heals you 4 and gives Hunger for 15 s (today 3 damage, heals 2, Hunger 10 s).
- **B:** Scuttle, a stronger Climb (hold). You climb at 0.3 (today 0.18) and keep going along ceilings, reusing the ceiling crawl (`SkitterCeiling`, synced `XenoformState.CLIMB`).
- **C:** Swarm, today's move. The first C splits off 5 glowing gremlin clones (silverfish, as today). Each C after that swaps you with the farthest clone (the nearest while sneaking), at most once a second. Holding C for 1 s drops you back to Gremlin.
- **Health:** +5 hearts (10).
- **Film:** MORPH on fist, tail, right_ear, head, then the whole body. First time: the knuckle plates clack on one by one, the barbed tail curls up overhead, and the horned brow bursts out with a shrill screech.

### Knell -> The Tolling
- **Today:** Pressing C to ascend starts the Tolling straight away, but only on the ground. While ascended, B starts it again once the last round is over and the 30 s cooldown has passed (`TOLL_COOLDOWN`, counted from the last toll). You are planted for 5 tolls, 2 s apart, with Resistance II. Each toll deals 6 sonic damage to everything within 8, and whatever it hurts is shoved away, with 3 s of darkness for players.
- **Body:** It is still the hunched horned beast that knuckle-walks on fists and legs, now 1.6x your size (about 1.25x Knell). Its horns are longer and curl further. The back hump and forearms carry sculk-black plates, and a row of sculk spikes runs along the spine. The rib plates are wider and armoured, and they swing open on every toll. Teal seams (0x1FB8C8) pulse in time with a heartbeat. It moves with a slower, heavier lumber.
- **R:** Sonic Lance, a stronger Sonic Boom. It reaches 24 blocks at 2.5 wide and deals 14 (today 15 blocks, 1.5 wide, 10 damage), and still goes through walls.
- **G:** Deep Sense, a stronger Tremor Sense (hold). It reaches 40 blocks (today 24), and invisible or shrouded creatures glow too.
- **V:** Deep Shriek, a stronger Shriek. It reaches 16 blocks and deals 6 (today 12 blocks, 4 damage). Everything gets darkness and Slowness II for 6 s.
- **B:** Undertow, a stronger Burrow. You surface up to 24 blocks away (today 16). Coming up throws everything within 4 into the air and deals 8.
- **C:** The Tolling, today's move, now 7 tolls 2 s apart, hitting everything within 10 for 7 each, with the same shove and darkness and with Resistance II. Again 30 s after the last toll. Holding C for 1 s drops you back to Knell.
- **Health:** +18 hearts (36).
- **Film:** MORPH on forearm, hump, right_rib, right_horn, then the whole body. First time: the sculk spikes erupt down the spine in slow motion, the ribs swing open on a heartbeat, and the horns curl out as the first toll shakes the screen.

## Patch 2: Wyrm, Duplex, Rivet, Gorger, Slipfin, Polestar, Lurk

### Wyrm -> Elder Wyrm
- **Today:** Adds +0.8 scale (`ELDER_EXTRA_SCALE`) to the 1.2x drake, and the general +15% comes on top, so it ends about 2.15x. It also gets +8 armour (`ELDER_ARMOR`). The wings already cost nothing. Dragon's Breath has `ELDER_BREATH_COOLDOWN` 40, and the general halving cuts that to 20 ticks, so it is back every 1 s. Wing Gust reaches 1.5x as far (radius 9). It throws portal and dragon's-breath particles, roars (`ENDER_DRAGON_GROWL`) and shakes the screen (`ELDER_SHAKE`). `tickUltimate` trails dragon's-breath particles. There is no move of its own (no `ultimateHasMove`). On top come the general +50% health and fists and every base cooldown halved.
- **Body:** The same small four-legged dragon with the ender wings on its shoulders, grown from 1.2x to 2.0x. Black scale plates run along its back and flanks. The spikes on its back, neck and tail grow long, and the spade at the tail's tip widens into a barbed blade. The two horns grow into a swept-back crown. Its seams and the veins of the wing membranes glow void purple (0xC060E0). It walks heavily on four legs and flies with slow, deep wingbeats (`WyrmWings`, larger).
- **R:** Void Breath, a stronger take on Dragon's Breath. It lays a line of three `AreaEffectCloud`s, 3, 7 and 11 blocks ahead, instead of one cloud at 5. Each has radius 3 and Instant Damage II and lasts 5 s. Cooldown 2 s.
- **G:** Elder Wings, a stronger take on Wings. Hold to fly where you look at 1.3/tick (today 0.9), free. Sneak while flying to dive at 2.0/tick. Landing from a dive throws everything within 4 upward and deals 4.
- **V:** Tempest Beat, a stronger take on Wing Gust. The radius is 9 (today 6), with 6 damage and a 2.4 shove out and up. Arrows and other projectiles in the radius are swatted away. The screen shakes with `ELDER_SHAKE`.
- **B:** Void Rift, a stronger take on Rift. It teleports up to 24 blocks where you look (today 12). A small dragon's-breath cloud (radius 2, 3 s) is left where you stood and another where you land. Creatures within 2 of the landing take 4.
- **C:** Endfire Volley (new, since today has no move). Five vanilla `DragonFireball`s fly out in a fan where you look. Each bursts into its own dragon's-breath cloud. Cooldown 20 s.
- **Health:** +24 (12 hearts).
- **Film:** MORPH on tail3 and spade, torso, neck1 and neck2, then right_horn and left_horn, then the whole body. First time: the spade splits into its barbed blade with a crack and the horns sweep back into the crown on a slowed beat, then it roars (`ENDER_DRAGON_GROWL`) as the void purple wing veins flare.

### Duplex -> Copper Storm
- **Today:** Spawns doubles until 3 are loyal (no more than 6 in all), then calls visual-only lightning on each. Everything within 3 of a double takes 6 lightning damage, never you or yours. The doubles are charged (Speed II, Strength, Glowing) for 20 s. While it lasts Split's cap is 6 doubles, and the extras pop when it ends. The storm sits on B (`onUltimateAgain`) every 20 s (`STORM_COOLDOWN` 400). On top come the general +50% health and fists, 15% bigger, and every base cooldown halved.
- **Body:** The same copper golem build (kept humanoid): the wide flat head, short body, long arms hanging near the ground, stubby legs and belt. It grows from 0.55x to 0.9x, about 1.6 blocks tall. Patina-green plates sit over the bright copper on its head, shoulders and forearms. The lightning rod grows a copper coil under its ball tip, and sparks crawl along the belt. Its seams glow electric blue-white. Its doubles wear the same storm armour (`XenoDoubleRenderer`).
- **R:** Storm Split, a stronger take on Split. Two doubles step out at once, up to 6 (`STORM_MAX_DOUBLES`). Each has 10 health instead of 6 (`XenoDouble.MAX_HEALTH`) and comes out charged for 10 s.
- **G:** Bolt Rally, a stronger take on Rally. Your doubles swarm the creature you aim at, up to 24 blocks away (today 16). The first double to reach it deals 4 lightning damage with a spark burst. Sneak + G is still Fetch.
- **V:** Arc Switch, a stronger take on Switch. You trade places with the aimed double (reach 32), and a line of `ELECTRIC_SPARK` runs between the two spots. Everything on that line takes 5. Sneak + V (the double's eyes) stays as it is.
- **B:** Overcharge Merge, a stronger take on Merge. Doubles within 24 run back into you and heal 4 each (today 3). Each merged double also gives you 3 s of Speed II and Strength. If 3 or more merge, a shock ring deals 3 to everything within 4.
- **C:** Copper Storm, today's move moved off B. Visual-only lightning strikes every double. Everything within 3 of a double takes 6, and the doubles are charged for 20 s. Cooldown 20 s (`STORM_COOLDOWN`).
- **Health:** +12 (6 hearts).
- **Film:** MORPH on belt, right_arm and left_arm, head, then rod and knob, then the whole body. First time: a visual-only bolt hits the knob and the coil winds down the rod in a flash of blue-white sparks.

### Rivet -> Ironpour
- **Today:** Only the general buff: +50% health and fists, 15% bigger, and every cooldown halved (the shells' moves too), with a spark burst and an anvil clang (`ANVIL_LAND`). There is no move of its own.
- **Body:** The same liquid-metal puddle that rises into a hunched figure to fight, grown from 0.6x to 1.0x. Riveted steel plates float on the metal, and gear-tooth spikes rise along its back. Both arms can stretch into whips (today only the lash arm does). Its seams glow its steel-blue (0x9FB4C0) and turn white-hot at the joints. A melded shell is drawn 1.5x larger with plates bolted on. It still melts flat to pour.
- **R:** Overmeld, a stronger take on Meld. The reach is 8 (today 5). In the 1.5x shell every shell move is 50% stronger: longer fire breath, a harder piston Shove, a heavier anvil Drop, more arrows a volley. Sneak + R still pours you out.
- **G:** Twin Lash, a stronger take on Lash. Two whips reach 10 blocks (today 7), each hitting a different creature for 7 (today 5) and dragging it to you.
- **V:** Molten Pour, a stronger take on Pour. The dash is 2.2 (today 1.4), and nothing lands on you during it. Creatures you pour through take 4 and get Slowness II for 3 s, with `LAVA` and `DRIPPING_LAVA` in the wake.
- **B:** Overdrive Ride, a stronger take on Ride. The reach is 6 (today 4). The minecart, iron or clockwork golem, or android mech you drive also gets Resistance II and bolted plates. Running into a creature rams it for 6. B again climbs out.
- **C:** Foundry Slam (new). Rivet surges up into a tall metal pillar and slams both whip-arms down. Everything within 5 takes 10 and is thrown up, with a ring of `LAVA` and `ELECTRIC_SPARK` particles. Cooldown 20 s.
- **Health:** +14 (7 hearts).
- **Film:** MORPH on pool, mound, right_arm and left_arm, then head, then the whole body. First time: the pool boils up into a tall column, each plate slams on with an anvil clang (`ANVIL_LAND`), and the joints flash white-hot as the head rises.

### Gorger -> Worldmaw
- **Today:** The general buff (+50% health and fists, 15% bigger, cooldowns halved), an explosion-particle ring and a deep roll sound. The gut starts full (`gorge = GORGE_MAX`, 12). There is no move of its own.
- **Body:** The same armadillo on four legs, grown from 1.1x to 1.6x. The six shell bands are thickened with stone-grey plates, each with a row of short spikes. The snout's horn is forked and the maw is wider. Its seams glow ember orange through the cracks between bands. It scurries the same way, and Roll Up closes into a larger spiked six-band ball.
- **R:** Deep Gulp, a stronger take on Gulp. It eats the aimed block and the eight blocks round it on that face (same rules for each), with reach 6 (today 4.5). The gut holds 24 (today 12).
- **G:** Rubble Cannon, a stronger take on Gut Blast. The cone is 14 blocks (today 9) and deals 3 a bite (today 2) with a harder shove. Up to 3 bites also fly as real `FallingBlockEntity`s of what was eaten (the gut now remembers its last blocks). They hurt what they land on, as Polestar's throw does, then break apart and leave no block.
- **V:** Quake Charge, a stronger take on Horn Charge. It runs for 32 ticks at 0.9 (today 22 at 0.75) and gores every creature in the way (not only the first) for 9 with a toss. Running off a ledge becomes a slam with radius 6 and up to 24 damage (today 4 and 18).
- **B:** Spiked Roll, a stronger take on Roll Up. Hold to curl into the spiked ball, which rolls at walking speed (Slowness I instead of IV). Shots still glance off. Anything you roll into takes 4 and bounces off.
- **C:** Worldmaw (new). Gorger unhinges its jaw and inhales for 2 s. Blocks in a cone 5 wide and 6 deep are gulped by Gulp's rules. Creatures in the cone are dragged in and bitten for 4 every half second. Then the whole gut comes out as one Gut Blast at double damage. Cooldown 30 s.
- **Health:** +24 (12 hearts).
- **Film:** MORPH on shell, tail and tail2, snout, then horn, then the whole body. First time: the shell bands crack with ember light and grow spikes one band at a time, then the horn splits in two and it stamps (`MACE_SMASH_GROUND_HEAVY`).

### Slipfin -> Brinecrown
- **Today:** The general buff (+50% health and fists, 15% bigger, cooldowns halved), a splash burst and the axolotl bucket sound (`BUCKET_EMPTY_AXOLOTL`). You are soaked through (`live.dry = 0`). There is no move of its own.
- **Body:** The same axolotl, grown from 0.8x to 1.3x and longer, with the same belly crawl and undulating swim. Pearly scale plates run down the back. The six gill stalks grow into long frilled crowns with glowing tips, and a new spiked fin sail runs along the spine and tail. Its seams glow coral pink (0xF08CB8). It swims faster.
- **R:** Tide Jet, a stronger take on Bubble Jet. It reaches 18 blocks (today 12) and is 2.0 wide (today 1.2). It deals 5 (10 under water; today 3 and 6), pushes harder, gives Slowness I for 2 s and puts out fire.
- **G:** Undertow Slip, a stronger take on Slip. You get out of any hold, and slowness and tangles are shaken off. A water burst throws everything within 3 away from you, and you get Speed III for 3 s (today Speed II).
- **V:** Torrent Dart, a stronger take on Dart. The lunge is 3.0 in water and 1.6 on land (today 2.0 and 0.9), with a splash wake. It bites every creature on the way for 8 (today the first one, for 6).
- **B:** Bloom Regrow, a stronger take on Regrow. Every lost limb grows back and every break knits. You get Regeneration III for 6 s (today II for 5 s), and allies within 6 get Regeneration I for 6 s. Cooldown 30 s (today 45 s).
- **C:** Brinecrown (new). A local downpour of `SPLASH` and `FALLING_WATER` particles falls within 10 blocks for 12 s. You stay wet even on land, fire inside it goes out, and enemies inside get Slowness II. Every 2 s a wave pushes them 1.0 away from you. Cooldown 30 s.
- **Health:** +12 (6 hearts).
- **Film:** MORPH on tail_0 to tail_3, rear, jaw, then the gill_ stalks, then the whole body. First time: the fin sail rises along the spine in a spray of water, then the gills unfurl into glowing coral crowns (`BUCKET_EMPTY_AXOLOTL`).

### Polestar -> Lodestar
- **Today:** The general buff (+50% health and fists, 15% bigger, cooldowns halved), with a spark ring and the lodestone lock sound. There is no move of its own.
- **Body:** The same broad, red-and-grey banded humanoid (kept humanoid), grown from 1.2x to 1.5x. Its chest plates thicken, and dark lodestone plates cover the shoulders and forearms. The horseshoe prongs grow long and curved, with `ELECTRIC_SPARK` jumping between the tips. Four iron shards orbit the shoulders as model parts. Its seams glow magnet red (0xD84A4A), with blue at the prong tips. It walks with a heavy, planted stride.
- **R:** Great Pull, a stronger take on Pull. It reaches 24 blocks (today 16) in a wider cone, with 0.6 + 0.4 a piece of iron (today 0.4 + 0.25). Pulled creatures take 2 a piece when they reach you. Loose iron still flies in.
- **G:** Shock Repel, a stronger take on Repel. The radius is 12 (today 8), dealing 2.5 a piece of iron (today 1.5). Creatures with no iron still get a flat shove, and arrows in the air turn back.
- **V:** Triple Lift, a stronger take on Lift. You tear up to three iron blocks out of the world (today one) and they orbit you. Each V throws one where you aim, dealing 3 a block fallen (today 2), max 30.
- **B:** Bastion Field, a stronger take on Field. Hold for radius 9 (today 6). Arrows fly back at their shooter at 1.5x speed (today 1.1x), and iron-wearers that step inside are pushed out.
- **C:** Lodestar (new). You become a magnetic pole for 6 s. Every iron-wearer and iron golem within 16 is dragged in and held still against you, and loose iron items fly in. At the end, or on C again, all of them are thrown out at 2.0, taking 2 a piece of iron. Cooldown 30 s.
- **Health:** +24 (12 hearts).
- **Film:** MORPH on body, plates, then right_prong and left_prong, then the whole body. First time: iron dust swirls in and locks on as lodestone plates, then the prongs curl long and an arc jumps red to blue between the tips (`LODESTONE_COMPASS_LOCK`).

### Lurk -> Nightfall
- **Today:** The general buff (+50% health and fists, 15% bigger, cooldowns halved), with a squid-ink burst and the elder guardian curse sound. There is no move of its own.
- **Body:** The same tall, thin stalker that drops onto all fours, grown from 1.05x to 1.3x. Its arms are even longer, with bone-black plates on the forearms and spine. A ridge of thin spikes runs down its back, and its tall bulb head has two more eye domes (four in all). Its seams glow violet (0x6E52B0). On all fours it is longer and lower, and the "fade" stance is unchanged.
- **R:** Mark of Night, a stronger take on Watch. You can mark up to 3 creatures within 64 blocks (today one). Each glows for 30 s (today 20 s) and loses track of you.
- **G:** Nightstep, a stronger take on Behind. It reaches 32 (today 24) and strikes for 10 (today 7), doubled if the target wasn't looking your way. G again within 2 s steps behind the next marked creature.
- **V:** Terror, a stronger take on Dread. The radius is 24 (today 16), and it hits everything looking at you and every marked creature. They get Darkness, Slowness II and Weakness II for 8 s (today Weakness I for 6 s) plus the 2 s of Blindness, and monsters drop their target and are shoved back.
- **B:** Deep Fade, a stronger take on Fade. You are unseen (invisible, and hunters forget you) with Speed I. The first strike from Fade does not break it. The second strike or B again does.
- **C:** Nightfall (new). For 8 s everything else within 16 gets Darkness and Blindness, and every creature inside glows on your screen alone (client-side outline). A ring of `SQUID_INK` marks the edge. Cooldown 40 s.
- **Health:** +12 (6 hearts).
- **Film:** MORPH on forearm and hand, torso, neck, then head with right_eye and left_eye, then the whole body. First time: the light drains to black (`ELDER_GUARDIAN_CURSE`), the spine spikes rise, and the eye domes open one pair at a time, the new pair last.

## Patch 3: Gyre, Cortex, Locust, Croaker, Rime, Marrow, Swathe

### Gyre -> Tempest Shell
- **Today:** C turns on the generic Ultimate buff: half again Gyre's health on top, healed in full; fists +50%; 15% bigger; the armoured Ultimate skin, with no spikes because four-legged bodies don't wear `UltimateSpikes`. All four base moves' cooldowns are halved (`XenoForm.cooldown`), including B's 1-tick Shell. It comes on with a gust burst and a low breeze whirl. Gyre has no Ultimate move: `onUltimate` only plays the effect, and there is no `ultimateHasMove`.
- **Body:** The same turtle on four flippers (`XenoFormModels.Gyre`), 1.5x bigger, with a taller dome. The shell is ridged with dark scute plates and has spiral vents glowing pale green (0x5FA37A). A ring of short spikes runs round its rim, and two vent funnels on its back trail small gusts. The flippers get scute armour and claws. It waddles heavier and rocks more, and in a spin the rim spikes flare out.
- **R:** Cyclone Bowl, a stronger take on Shell Spin. It bowls for 50 ticks instead of 30, at 0.9 instead of 0.6. Each creature takes 8 instead of 5 and a toss of 1.6 instead of 1.1. The hit list clears every 20 ticks, so it can hit the same creature again. As in Shell Spin, shots glance off while it spins.
- **G:** Gale Cannon, a stronger take on Gust. A narrower cone (0.85 instead of 0.7) reaches 14 blocks instead of 8 and shoves at 2.6 instead of 1.6, with more lift. Like Gust, it puts out burning creatures and fires in the cone. The push ends in a vanilla wind charge burst at the cone's tip.
- **V:** Twin Spiral, a stronger take on Whirlwind. Two whirlwinds last 4 s instead of 2: one where you look, one that follows you. Each has radius 5 instead of 4 and deals 4 damage a second instead of 3.
- **B:** Storm Shell, a stronger take on Shell (hold). You pull in as before. Shots are turned round and sent back at the shooter instead of glancing off. You crawl at walking pace instead of Slowness IV, and creatures that touch the shell are shoved away.
- **C:** Tempest. Gyre spins in place as the eye of a storm for 6 s. Everything within 10 blocks is pulled round and lifted, and every fire in that area goes out. When it ends, everything caught is flung outward for 10 damage. The visuals borrow the column from Wind's `WindCyclone`.
- **Health:** Its own pool of 14 health (7 hearts), on top of Gyre's 28. Damage takes it first.
- **Film:** MORPH on the flippers, the plastron, the shell, the shell_top dome and its rim spikes, then the whole body. First time: the shell slows to a crawl as each scute plate snaps on, then the rim spikes flare out all at once with a gust that shakes the shot.

### Cortex -> Mindcrown
- **Today:** C turns on the generic Ultimate buff: half again Cortex's health on top, fists +50%, 15% bigger, and the armoured skin with no spikes. All four base moves' cooldowns are halved. It comes on with a spark burst and thunder. Cortex has no Ultimate move: `onUltimate` only plays the effect.
- **Body:** The same floating eye fish (`XenoFormModels.Cortex`), 1.5x bigger, hanging higher. The hull is plated in dark armour with teal seams (0x4FB8B0). The big eye gets a second ring of lens. The skull-dome cracks wider so a larger brain shows, drawn fullbright and sparking. It has four spikes a side instead of three, longer, with sparks jumping between them, and a fourth tail segment. It drifts slower and steadier, and sparks crawl over the hull.
- **R:** Storm Arc, a stronger take on Arc. The first target takes 7 instead of 5. It then makes 4 jumps instead of 2, for 5 each instead of 3, with a jump range of 7 instead of 5. Every creature struck is slowed for 1 s.
- **G:** Mind Ward, a stronger take on Bubble. The bubble lasts 8 s instead of 6 and also covers spared allies within 4 blocks. Shots that hit it are sent back along their path instead of being stopped.
- **V:** Mind Slam, a stronger take on Mind Lift. Instead of lifting one target for 1.5 s, it lifts the target and up to 2 creatures near it for 2 s, then slams them down for 8 damage. A 3-block shove ring goes out where each one lands.
- **B:** Spine Storm, a stronger take on Spine Snap. Instead of one creature within 3.5 taking 7, the spikes shoot out on every side and hit everything within 4 for 8. Each one hit gets Slowness III for 3 s, as Spine Snap does.
- **C:** Mind Riot. For 6 s, every creature within 16 blocks that Cortex can see gets Glowing. Hostile mobs within 12 lose you as a target and are set on the nearest other hostile mob (`Mob.setTarget`). Once a second, an arc from the skull hits one glowing creature for 6.
- **Health:** Its own pool of 12 health (6 hearts), on top of Cortex's 24. Damage takes it first.
- **Film:** MORPH on the tail, the spikes, the dome, the brain, the eye, then the whole body. First time: the dome cracks open in slow motion and the brain flares with sparks before the second lens ring rolls shut over the eye.

### Locust -> Swarmking
- **Today:** C turns on the generic Ultimate buff: half again Locust's health on top, fists +50%, 15% bigger, and the armoured skin with no spikes. All four base moves' cooldowns are halved. It comes on with a cloud burst and a goat scream. Locust has no Ultimate move: `onUltimate` only plays the effect.
- **Body:** The same six-legged grasshopper (`XenoFormModels.Locust`), 1.5x bigger. Dark chitin plates have lime-yellow glowing seams (0x8DB33A) along the abdomen segments. The spurs on the big hind legs become blades, and a horned crest sits on the head. The folded wings split open to show a second pair, and the feelers grow longer with glowing tips. It runs a faster tripod gait, and in the air its wings buzz and let it glide.
- **R:** Meteor Leap, a stronger take on Crash Leap (hold to charge). It charges fully in 20 ticks instead of 30 and goes farther: top speed 3.2 instead of 2.4. The landing ring is 4 to 8 blocks instead of 3 to 6 and deals 6 to 13 instead of 4 to 9. It tosses creatures up as well as out.
- **G:** Piston Kick, a stronger take on Kick. It deals 10 instead of 6 and shoves at 3.0 instead of 1.8. A creature kicked into a wall takes 4 more, and you spring back farther (1.0 instead of 0.6).
- **V:** Ricochet, a stronger take on Wall Bounce. You can kick off a creature as well as a wall. A creature you kick off takes 5 and is shoved.
- **B:** Swarm Hops, a stronger take on Hops: five hops instead of three. Each landing makes a 2-block shock ring for 3 damage.
- **C:** Plague Dive. You spring 20 blocks up in a cloud of buzzing particles, hang and steer for up to 3 s, then dive where you look. The impact ring is 8 blocks wide, deals 14 and launches creatures. Locust takes no fall damage already, so the dive needs no extra guard.
- **Health:** Its own pool of 11 health (5.5 hearts), on top of Locust's 22. Damage takes it first.
- **Film:** MORPH on the hind legs, the abdomen, the thorax wings, the head, then the whole body. First time: the wings split open in slow motion to show the second pair, and the crest rises with a screaming buzz.

### Croaker -> Marshmaw
- **Today:** C turns on the generic Ultimate buff: half again Croaker's health on top, fists +50%, 15% bigger, and the armoured skin with no spikes. All four base moves' cooldowns are halved. It comes on with a splash burst and a deep croak. Croaker has no Ultimate move: `onUltimate` only plays the effect.
- **Body:** The same squat hopping frog (`XenoFormModels.Croaker`), 1.7x bigger. It is still low, but too big for the low box now: `XenoformState.LOW_BOX` must skip Croaker while it is the Ultimate. Warty armour plates cover its back, with moss-green glowing seams (0x6E9E3C). A crown of horn nubs sits over the eyes, and there are spurs on the hind legs. The throat sac is bigger and glows when it swells. It hops heavier, and every landing kicks up block particles.
- **R:** Grapple Tongue, a stronger take on Tongue. It reaches 16 instead of 10, deals 5 instead of 2, and hauls the target all the way to you, even big creatures. If it hits a block instead, it reels you to that block. A dropped item on the line is still fetched, as before.
- **G:** Great Gulp, a stronger take on Gulp. It can swallow creatures up to 1.4 wide and 2.0 tall, such as a horse or a villager; the base limit is 1.0 by 1.5. It still never swallows a player. It digests for 2 every 2 s instead of 1. The spit hits for 8 instead of 4 and knocks down whatever it lands on.
- **V:** Quake Bound, a stronger take on Bound. A longer leap ends in a 4-block splash ring. Everything in the ring takes 5 and gets Slowness II for 3 s. As before, it takes no fall damage.
- **B:** Thunder Croak, a stronger take on Croak. Radius 10 instead of 6, and 4 damage instead of 2. The stun is the same: Slowness IV, Nausea for players, and monsters lose their target.
- **C:** Bog Boom. The throat sac balloons and bursts in one croak. Everything within 10 is launched up for 8 damage, then mired for 6 s (Slowness III, Mining Fatigue, Weakness), with splash and mud particles.
- **Health:** Its own pool of 13 health (6.5 hearts), on top of Croaker's 26. Damage takes it first.
- **Film:** MORPH on the thighs, the torso back plates, the sac, the head, then the whole body. First time: the throat sac swells far past full size and glows before the horn crown punches up with a croak that shakes the shot.

### Rime -> Hoarfrost
- **Today:** C turns on the generic Ultimate buff: half again Rime's health on top, fists +50%, 15% bigger, and the armoured skin with no spikes. It comes on with a snowflake burst. R G V cooldowns are halved. Its Ultimate move takes B: the Blizzard. `onUltimateAgain` calls `CryoLent.blizzard` with cooldown `CryoBlizzard.DURATION_TICKS + 600` (1000 ticks), so Ice Wall can't be used.
- **Body:** The same stooped ice brute (`XenoFormModels.Rime`), 1.5x bigger. Thick ice plates cover the hump and forearms, and the fists become glacier blocks. The icicle spines grow longer, and the frost crown rises into a tall crest. Seams glow pale blue (0x9ED8F0), and frost fogs from its mouth. It walks the same heavy stride, and snowflake particles burst where each foot plants. The parts keep the man's names, so Cryo's poses still work.
- **R:** Whiteout Breath, a stronger take on Frost Breath (hold). It is still Cryo's breath through `CryoLent.breath`. Any creature that stays in it for 2 s is flash-frozen, as Flash Freeze does, if it fits in the ice.
- **G:** Icicle Volley, a stronger take on Ice Shards. It fires three of `CryoShards`' five-shard fans at once: left, centre and right. This needs a `CryoShards.fire` overload that takes a yaw offset.
- **V:** Deep Freeze, a stronger take on Flash Freeze. It freezes the target and every creature within 3 of it that fits in the ice.
- **B:** Glacier Ring, a stronger take on Ice Wall. Instead of one 5-wide wall where you aim, it raises four walls round you as a closed ring that melts as Ice Wall does.
- **C:** Blizzard. Today's Ultimate move, moved from B to C, unchanged: `CryoLent.blizzard`, a 20 s whiteout, radius 16, the same 1000-tick cooldown.
- **Health:** Its own pool of 15 health (7.5 hearts), on top of Rime's 30. Damage takes it first.
- **Film:** MORPH on the fists, the trunk, the spine, the crown, then the whole body. First time: each fist freezes over into a glacier block in slow motion, and the crown spikes up into its crest as snow bursts out.

### Marrow -> Ossuary
- **Today:** C turns on the generic Ultimate buff: half again Marrow's health on top, fists +50%, 15% bigger, and the armoured skin with its `UltimateSpikes` style. All four base moves' cooldowns are halved. It comes on with a bone burst. It also grows back the rise (`live.risen = false`): one more killing blow drops it into a heap, and it stands up at half health. It has no Ultimate move.
- **Body:** The same humanoid skeleton (`XenoFormModels.Marrow`, kept humanoid), 1.4x taller. Bone plates close the open ribcage into a breastplate. It has four horns curling back instead of two, and a longer spine with spikes along it. Two thin new bone arms reach from the ribcage. The joint seams glow bone-white (0xE6E0CC). It stands upright, and its joints clatter as it walks.
- **R:** Bone Forest, a stronger take on Bone Spikes. The line is 14 blocks long instead of 9 and 2.5 wide instead of 1.2. It deals 9 instead of 6, with a toss. The spears stay up for 3 s as a hedge that deals 2 to anything walking into it.
- **G:** Twin Ribs, a stronger take on Rib Throw. It throws two ribs at once, one curving left and one right. Each cuts for 6 going out and 6 coming back, instead of 4 and 4.
- **V:** Ossuary Plate, a stronger take on Bone Shield. It lasts 8 s instead of 5. As before, shots glance off and blows land softer (Resistance II). Melee attackers also take 2 back.
- **B:** Scatter, a stronger take on Fall Apart. You drop into a heap as before, then put yourself back together up to 8 blocks away, where you look. Whatever was hunting you loses interest.
- **C:** Bone Cage. Bone spears burst up round the creature you look at (up to 16 blocks) and cage it for 4 s. It is held still and takes 3 a second, and every creature within 3 of it is tossed out. The Ultimate keeps its own rise, once per Ultimate, as `live.risen` does today.
- **Health:** Its own pool of 12 health (6 hearts), on top of Marrow's 24. Damage takes it first. It also has the Ultimate's own rise.
- **Film:** MORPH on the spine, the ribs, the new rib arms, the horns, then the whole body. First time: the bones clatter shut over the ribcage one by one, then the rib arms unfold and the extra horns curl out with a roar.

### Swathe -> Unbound
- **Today:** C turns on the generic Ultimate buff: half again Swathe's health on top, fists +50%, 15% bigger, and the armoured skin with its `UltimateSpikes` style. All four base moves' cooldowns are halved. It comes on with a sand burst. It has no Ultimate move. As always, it never drowns or starves.
- **Body:** The same wrapped humanoid (`XenoFormModels.Swathe`, kept humanoid), 1.4x taller. The wrappings are layered with dark bronze bands at the chest, shoulders and forearms. The arm ends and the back trail become six long loose lashes that move like tendrils. Its eyes and the gaps in the wrap glow pale sand (0xB8A890), and sand trickles from the gaps. It glides slightly as it walks, with the lashes trailing.
- **R:** Grave Bind, a stronger take on Lash Bind. It wraps the target and up to 2 more creatures within 4 of it, bringing each down for 6 s instead of 4 (`Tripped.bind`). As before, anything that can't be tripped is held fast instead.
- **G:** Reel, a stronger take on Lash Pull. Instead of one creature within 12, it drags up to 3 creatures in a 16-block cone to you at once. Aimed at a block, it reels you there instead.
- **V:** Dune Storm, a stronger take on Sandstorm. Radius 10 instead of 6 (14 instead of 9 in deserts and badlands), and 2 damage a second instead of 1 (3 instead of 2 in sand). Like Sandstorm, it moves with you. Creatures hunting you also lose you while you are inside it.
- **B:** Rewrap, a stronger take on Wrap. Bleeding stops, as before, and you heal faster: Regeneration III instead of II. You also get +4 armour for 10 s, and spared allies within 4 have their bleeding stopped.
- **C:** Unravelling. The wrappings shoot out as lashes to every creature within 12 and bind them (`Tripped.bind`, or held fast). Then they are dragged into a ring round you, and a burst of sand hits them for 8.
- **Health:** Its own pool of 13 health (6.5 hearts), on top of Swathe's 26. Damage takes it first.
- **Film:** MORPH on the arm loose ends, the trail, the body bands, the head, then the whole body. First time: the wrappings unwind and lash out wide in slow motion, then snap back tight under the bronze bands as the eyes ignite.

## Patch 4: Blight, Howl, Prowl, Tusk, Talon, Murk, Gnasher

### Blight -> Withering
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Blight's own soul burst and the Wither's spawn sound. It has no Ultimate move of its own (no `ultimateHasMove`). The dark shield at half health and the immunity to Wither are base Blight traits, not part of the Ultimate.
- **Body:** The same floating Wither (three skulls on a crossbar over a ribbed chest and a three-joint tail, no legs), 30% bigger. Blackened bone plates cover the crossbar and ribs, bone spikes run down the spine, and the middle skull wears a crown of horns. The seams and eye sockets glow soul-blue. The tail grows a fourth joint that ends in a barbed spike. It floats a block higher with a slower, heavier bob.
- **R:** Skull Barrage, a stronger Skull: three skulls from the middle head, 4 ticks apart. The third is the blue, block-breaking kind (`WitherSkull.setDangerous`).
- **G:** Grave Volley, a stronger Volley (today one skull from each head, only the middle one blue): five skulls in a wider fan, the shoulder skulls firing two each. All five are blue.
- **V:** Blight Grasp, a stronger Rot Touch: reach 6 instead of 4. The target takes 10 instead of 6 and gets Wither III for 8 s (today Wither II for 6 s). Every creature within 3 of it also withers, and the rot radius grows from 2 to 4.
- **B:** Dark Tide, a stronger Dark Pulse: two rings of decay 10 ticks apart, radius 10 instead of 6. Each ring throws back and gives Wither II (today Wither I). It uses `Shakes.heavy`. The dark shield (shots can't touch it) rises for 5 s whatever your health.
- **C:** Ashfall (new, since today has no move): it rises 3 blocks and hangs there for 4 s. Every half second it drops a wither skull on a random creature within 16, sparing you and your pets. It ends with one blue skull where you look. A soul-fire ring (`ring`, `SOUL_FIRE_FLAME`) burns below it.
- **Health:** +15 hearts (30) on top of its 60.
- **Film:** MORPH on tail2, rib, bar, mid_skull, then the whole body. First time: the camera crawls up from the barbed fourth tail joint, plates crack shut over each rib one by one, and the horn crown bursts from the middle skull in a gout of soul flame.

### Howl -> Alpha
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Howl's own cloud burst and a low howl. It has no Ultimate move of its own. The Strength and Speed at night are a base Howl trait.
- **Body:** The same four-legged wolf, 30% bigger, with a deeper chest. The ruff thickens into bony collar plates. Iron-grey plates cover the shoulders and spine, with a row of short back spikes, and the fangs are longer. The seams glow amber and the eyes are gold. It has the same trot with a longer stride, and its bushy tail streams like the base wolf's.
- **R:** Rending Pounce, a stronger Pounce: the leap is longer (1.6 out instead of 1.2) and hits for 9 instead of 6. Every creature within 2 of the landing is knocked down (`Tripped.bind`), not just the first.
- **G:** Pack Call, a stronger Howl: wild wolves within 48 answer instead of 32, and they get Strength I and Speed I while the pack lasts. If none answer, two vanilla `Wolf`s spawn tamed to you, and they are removed when the Ultimate ends.
- **V:** Blood Hunt, a stronger Scent: range 64 instead of 48. Every bleeder it shows glows and also gets Slowness I for 10 s.
- **B:** Crushing Jaw, a stronger Lockjaw: the bite does 6 instead of 4 and holds for 6 s instead of 4, gnawing for 3 every second instead of 2. B again throws the held creature forward (1.5) instead of just letting go.
- **C:** Dread Howl: a howl you hear far off. Every mob within 16 gets Weakness II and Slowness II for 8 s. You and your pack get Strength II and Speed II for 10 s, night or day. It throws out a cloud ring and uses `Shakes.heavy`.
- **Health:** +9 hearts (18) on top of its 26.
- **Film:** MORPH on torso, chest, ruff, head, then the whole body. First time: the wolf throws its head back in a long howl as the ruff bristles into bone plates, the fangs stretch out and the gold eyes flare.

### Prowl -> Apex
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Prowl's own crit burst and a high roar. It has no Ultimate move of its own. Moving silently, being hard to see while sneaking, and safe falls under 12 blocks are base Prowl traits.
- **Body:** The same long, low cat, 30% bigger. Its longer jaw carries two long sabre fangs. The dark stripes harden into armour bands down the flanks, bony plates sit on the shoulders and haunches, and short spikes run along the tail. The seams glow hot orange. It keeps the cat's walk, bounding gallop and sneaking stalk, and moves just as silently.
- **R:** Killing Leap, a stronger Long Pounce: 2.2 out instead of 1.7, and 9 damage instead of 6. It pins for 4 s instead of 3 and adds a bleeding cut (`Bleeding.open`).
- **G:** Triple Rake, a stronger Rake (today one creature in reach 3.5, 5 and a bleed): three sweeps over 10 ticks, 4 each. They hit everything in a 100-degree arc within 4, and every hit bleeds.
- **V:** Death Mark, a stronger Stalk: the mark lasts 30 s instead of 20. Near it you get Strength III instead of today's Strength II. The mark glows through walls as today.
- **B:** Thunder Roar, a stronger Roar: radius 16 instead of 12. Mobs break off, run, and get Weakness I for 6 s. Players caught in it get Darkness for 3 s.
- **C:** Sabre Rush: it blurs between up to 5 creatures within 12, teleporting to a clear spot behind each in turn. Each takes 8 and a bleeding cut, with a `SWEEP_ATTACK` puff at each one. It ends crouched behind the last.
- **Health:** +8 hearts (16) on top of its 24.
- **Film:** MORPH on tail1, haunch, chest, cat_head, then the whole body. First time: the cat sinks low as the stripes harden into bands, then its jaw stretches long and the sabre fangs slide out slowly in a close-up.

### Tusk -> Warhog
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Tusk's own smoke burst and an angry hoglin grunt. It has no Ultimate move of its own.
- **Body:** The same barrel boar on four short legs, 40% bigger, the heaviest of the beasts. The two curved tusks grow long and fork into four. The mane becomes a ridge of bone spikes. Rust-red plates armour the flanks, with a battering plate over the brow. The seams glow ember red. Its trot is heavier and the head stays low.
- **R:** Ram, a stronger Charge: the run is 40 ticks instead of 25. It hits every creature in the way, not just the first, for 10 instead of 7, and tosses each one. With mob griefing on, it smashes leaves and glass in its path.
- **G:** Skewer, a stronger Gore: the tusks hit for 9 instead of 6 with a bleeding cut, then fling the creature up and over your back (1.2 up) instead of shoving it.
- **V:** Upheaval, a stronger Root Out: it heaves the ground in a 6-block line ahead, shown with `BlockParticleOption`. Everything on the line is tossed up 0.8 and slowed for 3 s. It still digs up one roots-and-truffles find, at your feet or where you look.
- **B:** Iron Hide, a stronger Thick Hide: 15 s instead of 10, with no knockback and Resistance II instead of I. Melee attackers take 2 back.
- **C:** Quake Slam: it rears and slams down its front legs. Every creature within 8 takes 10, is tossed up 1.0 and gets Slowness III for 4 s. A ring of ground particles spreads out and `Shakes.heavy` shakes the screen.
- **Health:** +12 hearts (24) on top of its 34.
- **Film:** MORPH on body, mane, head, snout, then the whole body. First time: the boar paws the ground and snorts steam as the mane rises into spikes, the brow plate slams down like a visor and the tusks fork into four with a crack.

### Talon -> Sky Tyrant
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Talon's own cloud burst and a phantom swoop sound. It has no Ultimate move of its own. Flight and safe falls are base Talon traits.
- **Body:** The same hawk, 35% bigger, with a much wider wingspan. The four flight feathers on each wing and the tail fan are edged with steel-grey quill blades. The beak and talons are sheathed in dark plates, and a crest of swept-back spikes runs down the head. The seams glow pale gold. It still perches upright, hops, flies and glides like the base hawk, with slower, stronger wingbeats. Falls never hurt it.
- **R:** Tyrant Stoop, a stronger Stoop: a faster dive (1.8 instead of 1.4) that hits for 7 instead of 4. It can snatch creatures up to 2.0 wide and 3.0 tall instead of 1.4 by 2.2, and carry them for 12 s instead of 8.
- **G:** Sky Drop, a stronger Drop: what you let go is flung down at 1.5, and its landing deals 4 to everything within 2. With nothing held, G is a double talon strike, two hits of 6 (today one hit of 5).
- **V:** Hunter's Eye, a stronger Far Sight: range 128 instead of 96. Players show too as well as monsters, bleeders glow, and you see in the dark.
- **B:** Quill Storm, a stronger Feather Darts: two fans of 9 hard quills (`Arrow`, pickup disallowed, as today) at speed 3.0 for 4 each. Today it fires one fan of 5 at 2.2 for 2.5.
- **C:** Gale Beat: it hovers and gives one huge wingbeat. Everything in a 10-block cone ahead takes 6 and is blown back 2.0 and up. Arrows and other projectiles in the cone are turned back.
- **Health:** +6 hearts (12) on top of its 20.
- **Film:** MORPH on tail, feather, foot, skull, beak, then the whole body. First time: the hawk spreads its wings full as quill blades slide out of every feather one after another, and the head crest flares as the beak plate snaps shut.

### Murk -> Kraken
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Murk's own glow-ink burst and a squirt sound. It has no Ultimate move of its own. Fast swimming, water breathing and night vision in water are base Murk traits.
- **Body:** The same squid, 40% bigger. The mantle is armoured with overlapping dark violet plates and a ridge of hooked spines, and its fins are edged with bone. It keeps the eight jointed tentacles round the beak, longer and hooked underneath, and grows two new long hunting arms that end in toothed clubs. The seams and eye-rings glow cyan, like a glow squid's. It swims mantle-first and faster, and drags itself along on land like the base squid, only heavier.
- **R:** Abyss Ink, a stronger Ink: radius 8 instead of 5. Everything in it gets Blindness and Darkness for 7 s (today Blindness for 5 s) and loses you. You get Invisibility for 3 s inside the cloud.
- **G:** Crushing Grip, a stronger Tentacles: it seizes up to 6 creatures within 8 instead of 4 within 6, and holds them for 6 s instead of 4. It squeezes for 2 every second instead of 1. G again throws them all outward instead of just letting go.
- **V:** Torrent Jet, a stronger Jet: the dash goes farther (2.4 in water instead of 1.6, 1.2 on land instead of 0.8). Anything it passes through takes 5 and is shoved aside.
- **B:** Deep Lure, a stronger Lure: radius 32 instead of 24. Lured creatures also get Slowness II and Glowing.
- **C:** Maelstrom: in water, a whirlpool of radius 10 spins for 6 s. Everything in it is pulled round and in toward you, taking 2 a second, shown with bubble particles. On land, the two hunting arms slam down instead: 8 to everything in a 6-block line, with an ink burst.
- **Health:** +8 hearts (16) on top of its 22.
- **Film:** MORPH on arm_, fin_0, mantle_0, mantle_3, eye_0, then the whole body. First time: the squid curls in dark ink while the mantle plates shutter closed one by one, two toothed hunting arms uncoil from beneath, and the eyes flare cyan.

### Gnasher -> Megalodon
- **Today:** C turns on the shared buff: +50% health and fists, 15% bigger, an armoured skin with glowing seams, and base cooldowns halved. It comes with the band's flash and a short orbit film, plus Gnasher's own bubble burst and a guardian attack sound. It has no Ultimate move of its own. Fast swimming and seeing bleeders in water within 48 are base Gnasher traits.
- **Body:** The same three-segment shark, 50% bigger. The jaw is wider, with a second row of teeth. Slate armour plates run down the back, and the dorsal, pectoral and crescent tail fins get a serrated bone edge. The seams and gill slits glow blood red. It swims level and faster, and drags itself on its pectoral fins on land.
- **R:** Iron Jaw, a stronger Clamp: reach 4 instead of 3. The bite does 9 instead of 6 with a bleeding cut, holds for 6 s instead of 4, and the held creature takes 2 every second (today it only holds).
- **G:** Breach, a stronger Lunge: a farther leap (2.0 out and 0.9 up, instead of 1.5 and 0.6). Everything met takes 8, not just the first creature for 5. Splashing back into water shoves everything within 3.
- **V:** Blood Frenzy, a stronger Frenzy: it counts up to 5 bleeders instead of 3, and while it lasts every hit you land opens a bleed (`Bleeding.open`).
- **B:** Drowning Roll, a stronger Death Roll: it rolls for 3 s instead of 2, dealing 5 every 10 ticks instead of 3. Then it throws the creature down, knocked over for 4 s instead of 3 (`Tripped.bind`). In water, the creature is dragged 3 blocks down.
- **C:** Abyss Charge: a straight 16-block rush, in water or on land, with the jaw wide open. Everything in the path takes 12 and bleeds. The last creature hit is clamped in the jaws at the end.
- **Health:** +10 hearts (20) on top of its 30.
- **Film:** MORPH on caudal, pec_0, dorsal, fore, jaw, then the whole body. First time: the shark circles low as plates slam down its back, then the jaw gapes wide on the second row of teeth and it lunges at the camera.

## Patch 5: Lumen, Keen, Hive, Skitter, Shade, Mirage

### Lumen -> Radiance
- **Today:** Only the generic Ultimate buff. Health goes up by half of Lumen's 16 (+8, healed in full), fists hit 50% harder, it is 15% bigger, it gets the armoured skin, and every cooldown is halved. The shared C plays a flash, END_ROD and totem particles, the `UltimateFilm.ORBIT` film, and shows the name. Lumen has no `ultimateHasMove`, so B stays Fetch. Lumen's own `onUltimate` only adds an END_ROD burst and the beacon sound. Lumen has no `UltimateSpikes` entry. C again drops back.
- **Body:** Still the hovering orb of light with no limbs, 1.4x Lumen's size. The core gets a second inner shell. The dithered halo shell breaks into six faceted crystal plates with gaps between them, held apart by white-gold seams that pulse. The front stays clear so the core and the insignia show. Three long light-spike rays turn slowly round the core like a star. The orbiting motes double and stay on their tilted rings. It hovers higher (about 1.5 blocks instead of about 1) and drifts down even slower. An attack swing flares every plate outward.
- **R:** Sanctum, stronger take on Mend. Heals you, players and pets within 12 (Mend: 8) for 6 (Mend: 4). It then leaves a glow ring that follows you for 6 s and gives Regeneration I to the same group inside it.
- **G:** Searing Flare, stronger take on Flare. Every creature within 12 (Flare: 8) is blinded for 6 s (Flare: 4 s), and anything targeting you drops you, as Flare does today. Undead (`EntityTypeTags.UNDEAD`, as the rest of the mod checks it) also take 4 and catch fire.
- **V:** Sunbeam, stronger take on Light. A beam of END_ROD particles goes out to `LIGHT_REACH` (24). The first creature it hits takes 6 and gets Glowing for 5 s. Up to 3 Light blocks are left along the path, using the same `live.lights` bookkeeping and the `LIGHT_MAX` cap (oldest out first). That way one beam doesn't put out every light you already placed.
- **B:** Gather, stronger take on Fetch. For 10 s (Fetch's `FETCH_TICKS`), items of the held kind (any item with an empty hand) and every experience orb within 32 (Fetch: 24) fly to you.
- **C:** Nova. The core swells for 1 s, then bursts. Within 14, players and pets heal 10 and get Absorption II for 10 s. Hostiles are knocked back 2 blocks, blinded for 4 s and get Glowing for 10 s. Undead also take 8. Cooldown 30 s (`live.ultimateNext`).
- **Health:** +6 hearts (12 health) on top.
- **Film:** MORPH on core_x, core_y, mote, halo, then the whole body. First time: the halo shell cracks apart into six crystal plates with a white-gold flash and the three light-spike rays snap out like a star.

### Keen -> Vesperwing
- **Today:** Only the generic Ultimate buff. Health goes up by half of Keen's 14 (+7, healed in full), fists hit 50% harder, it is 15% bigger, it gets the armoured skin, and every cooldown is halved. The shared C plays a flash, particles, the `UltimateFilm.ORBIT` film, and shows the name (today's name is "Nightwing"). No `ultimateHasMove`, so B stays Echo. Keen's own `onUltimate` adds a smoke burst and the bat takeoff sound. No `UltimateSpikes` entry. C again drops back.
- **Body:** The same furry bat, 1.4x Keen's size. On the ground it crawls on its wrists and feet; in the air it beats or glides; it hangs head-down from ceilings. Dark chitin plates cover the skull and the back of the round body, with a ridge of short spikes down the spine. The huge ears grow longer and notched, each of the three finger bones ends in a hooked spur, the thumb claw is bigger, and the membranes get torn edges. The red eyes and the seams along the wing bones glow red. It flies, glides and hangs like Keen, with a slower, heavier wingbeat.
- **R:** Deep Sonar, stronger take on Sonar. Range 40 (Sonar: 24). Hostiles caught in the pulse also get Glowing for 6 s, so allies see them too. Sonar's outline is for you alone.
- **G:** Shriek, stronger take on Screech. Hits everything within 16, not only fliers, for 5 (Screech: 3) and shoves it away. Fliers are still knocked out of the air as today, and players also get Nausea for 3 s.
- **V:** Deep Drain, stronger take on Drain. The bite (reach 3, as Drain) latches for 2 s and takes 2 every 10 ticks, healing you by the same amount. It breaks if the target gets more than 4 blocks away.
- **B:** Night Dash, stronger take on Echo. The dash is 1.5x Echo's `ECHO_SPEED`. It always makes you invisible for 1.5 s (Echo's `ECHO_HIDE`), not only in the dark. Creatures you pass through take 4.
- **C:** Blackout. 12 vanilla bats (`EntityTypes.BAT`, through the `summon`/`dismiss` minion helpers) burst out and swirl round you for 10 s. Every hostile within 20 gets Darkness and Blindness and loses its target, and the Sonar outline runs for the whole 10 s. Cooldown 30 s (`live.ultimateNext`).
- **Health:** +5 hearts (10 health) on top.
- **Film:** MORPH on finger, thumb, body, right_ear, bat_head, then the whole body. First time: the wings flare wide as the hooked spurs and torn membranes tear out, then the chitin skull plates clamp shut over glowing red eyes.

### Hive -> Queen of the Hive
- **Today:** Only the generic Ultimate buff. Health goes up by half of Hive's 16 (+8, healed in full), fists hit 50% harder, it is 15% bigger, it gets the armoured skin, and every cooldown is halved. The shared C plays a flash, particles, the `UltimateFilm.ORBIT` film, and shows the name. No `ultimateHasMove`, so B stays Swarm. Hive's own `onUltimate` adds an amber dust burst and the angry bee loop. No `UltimateSpikes` entry. C again drops back.
- **Body:** Still the hovering big bee, 1.5x Hive's size. The striped abdomen is longer and heavier, with overlapping amber carapace plates. A crown of five short spikes rings the back of the head. A third pair of wings sits behind today's two pairs, all buzzing. The stinger under the abdomen grows long and barbed. The plate seams and compound eyes glow amber. It hovers a little higher, and its six dangling legs are thicker and spiked.
- **R:** Swarmform, stronger take on Scatter. 6 s as the cloud (Scatter: 4 s), with blows still passing through and you still unseen. Holding jump flies faster and higher than Scatter's lift. Creatures the cloud passes through take 1.5 and get Poison.
- **G:** Royal Sting, stronger take on Sting. A stab of 6 (Sting: 3) with Poison II for 8 s (Sting: Poison II for 5 s) and Slowness I.
- **V:** Honey Flood, stronger take on Honey Glue. Every creature within 4 of the aimed creature or block (reach 12, as Honey Glue) is glued as Honey Glue does today, for 5 s (Honey Glue: 4 s).
- **B:** Split Swarm, stronger take on Swarm. Swarms latch onto the aimed creature (reach 24) and up to 2 more hostiles near it. Each one stings as today for 6 s. This needs a list of latched ids in place of the single `live.latched`.
- **C:** The Hive. A humming swarm cloud fills 8 blocks round you for 8 s. Everything inside except you and yours takes 1.5 every 10 ticks and gets Poison and Slowness. You get Regeneration I while you stay in it. Cooldown 30 s (`live.ultimateNext`).
- **Health:** +6 hearts (12 health) on top.
- **Film:** MORPH on abdomen, stinger, right_hind_wing, bee_head, then the whole body. First time: the abdomen swells plate by amber plate and the third pair of wings bursts out buzzing before the spiked crown rises on the head.

### Skitter -> Silkmother
- **Today:** Only the generic Ultimate buff. Health goes up by half of Skitter's 22 (+11, healed in full), fists hit 50% harder, it is 15% bigger, it gets the armoured skin, and every cooldown is halved. It also gets the `UltimateSpikes` "skitter" style on the upright body. The shared C plays a flash, particles, the `UltimateFilm.ORBIT` film, and shows the name (today's name is "Broodmother"). No `ultimateHasMove`, so B stays Egg Sac. Skitter's own `onUltimate` adds a cobweb burst and the spider sound. C again drops back.
- **Body:** The same two stances, 1.35x Skitter's size.
  - Standing (the upright man with four spider legs off its back and an abdomen behind): black chitin plates cover the chest and shoulders, the four back legs are thicker with spiked joints, and the small abdomen swells with egg-sac bulges.
  - Prone (the eight-legged spider): longer spiked legs and a huge, pulsing abdomen.
  - In both, the red eye cluster and the seams between the abdomen plates glow red. It climbs walls and ceilings as today (`SkitterCeiling`, `XenoformState.CLIMB`).
- **R:** Hauling Line, stronger take on Web Line. Reach 48 (Web Line: 32). At a block you swing as today, and R again lets go. At a creature, the line yanks it to arm's length and makes your next melee hit land at full strength.
- **G:** Web Net, stronger take on Web Tie. Every creature within 4 of the aimed one (reach 12) is bound (`Tripped.bind`, with Slowness as the fallback) and weakened, as Web Tie does today, for 4 s. Temporary cobwebs go at their feet and are removed after 6 s. They are kept in Skitter's own timed block map (built like Lumen's `live.lights`) and cleared in `onExit`.
- **V:** Venom Fang, stronger take on Bite. A bite of 7 (Bite: 4) with Poison III for 5 s (Bite: Poison II for 5 s) and Slowness II for 3 s.
- **B:** Brood Wave, stronger take on Egg Sac. 6 cave spiders hatch (Egg Sac: 3) and fight for you for 45 s (Egg Sac: 30 s).
- **C:** The Nest. A ring of temporary cobwebs, 3 high, forms 6 blocks out round you, only where you may build, and lasts 12 s. A cave spider hatches from the webs every 2 s, up to 8 alive. Spiders aren't slowed by cobwebs. Hostiles inside the ring get Slowness II. The webs and spiders are cleared when it ends. Cooldown 40 s (`live.ultimateNext`).
- **Health:** +9 hearts (18 health) on top.
- **Film:** MORPH on back_leg, back_abdomen, thorax, spider_head, then the whole body. First time: the abdomen balloons with pulsing egg sacs while the legs crack and sprout spiked joints, then the eye cluster lights up red one eye at a time.

### Shade -> Endshade
- **Today:** Only the generic Ultimate buff. Health goes up by half of Shade's 30 (+15, healed in full), fists hit 50% harder, it is 15% bigger, it gets the armoured skin, and every cooldown is halved. The shared C plays a flash, particles, the `UltimateFilm.ORBIT` film, and shows the name. No `ultimateHasMove`, so B stays Wary. Shade's own `onUltimate` adds a reverse-portal burst and the enderman scream. No `UltimateSpikes` entry. Water and rain still burn it. C again drops back.
- **Body:** The same thin shadow, 1.3x Shade's size, with its limbs even longer. It goes low on all fours when moving and unfolds to its full height standing still, attacking, staring or holding a block. Narrow black plates cover the shoulders, forearms and shins, and a row of thin spines runs down the back. Two long swept-back horns grow from the narrow head. The violet seams and eyes glow, and REVERSE_PORTAL motes fall off it. It folds and unfolds as today, only taller.
- **R:** Grand Theft, stronger take on Steal. Pulls out the aimed block (reach 6) and up to 2 more behind it, all held over your head. Each R press throws one. A thrown block's damage per block fallen doubles (`THROW_DAMAGE` 2 to 4 in `FallingBlockEntity.setHurtsEntities`), and the cap rises from 20 to 30. The hardness limit rises from 10 to 20, so obsidian still stays put.
- **G:** Dread Stare, stronger take on Stare. Every creature in a 30-degree cone out to 24 is held still while you keep looking, up to 6 s (Stare: one creature, 5 s). Each takes 1 a second, and players also get Darkness.
- **V:** Rend Blink, stronger take on Blink. Reach 40 (Blink: 24). Everything within 2 of where you leave and where you land takes 4 and is shoved away.
- **B:** Afterimage, stronger take on Wary. For 15 s (Wary: 10 s), up to 3 blows blink you away instead of landing (Wary: one). Each one leaves a portal burst that blinds the attacker for 2 s.
- **C:** Night Snatch. You blink behind the aimed creature (out to 24) and lift it over your head, as Steal lifts a block. It is helpless for 2 s, then you hurl it where you look. It takes 8 on impact, and you drop it if you are hurt first. Cooldown 25 s (`live.ultimateNext`).
- **Health:** +12 hearts (24 health) on top.
- **Film:** MORPH on shin, forearm, torso, head, then the whole body. First time: the limbs stretch out impossibly long and the back spines ripple up the torso before the swept-back horns slowly grow from the head.

### Mirage -> Hall of Mirrors
- **Today:** Only the generic Ultimate buff. Health goes up by half of Mirage's 20 (+10, healed in full), fists hit 50% harder, it is 15% bigger, it gets the armoured skin, and every cooldown is halved. It also gets the `UltimateSpikes` "mirage" style. The shared C plays a flash, particles, the `UltimateFilm.ORBIT` film, and shows the name. No `ultimateHasMove`, so B stays Swap. Mirage's own `onUltimate` adds a witch burst and the illusioner cast sound. C again drops back.
- **Body:** Still the lean hooded figure in the deep blue, gold-trimmed robe (one of the 7 kept humanoid), 1.2x Mirage's size. Mirror-glass plates sit on the shoulders, chest and forearms, and spikes ring the hood's rim. The gold trim becomes glowing gold seams, and a halo of six small mirror shards circles the hood. It walks as today, with the robe skirt drawn longer and the shimmer stronger.
- **R:** Mirror Legion, stronger take on Mirror Images. 5 copies (Mirror Images: 3), lasting 20 s (Mirror Images: 15 s), made with `XenoDouble.makeFake` and the Ultimate's look. A copy popped by a blow blinds whoever hit it for 3 s.
- **G:** Mass Blind, stronger take on Blind. Every creature within 5 of the aimed one (reach 16) gets Blindness and Darkness and loses you, as Blind does today, for 6 s (Blind: 5 s).
- **V:** Vanish, stronger take on Unseen. 8 s unseen (Unseen: 5 s), with whatever was after you within 24 losing you. A fresh copy steps out where you stood to draw off whatever hunted you.
- **B:** Shatter Swap, stronger take on Swap. You trade places with a copy as today (the farthest; the nearest when sneaking), and then the copy shatters. Everything within 3 of it takes 4 and is blinded for 2 s.
- **C:** Mirror Ring. 8 copies stand in a ring 6 blocks round the spot you look at (out to 12) for 10 s. Every mob within 24 that was after you goes after a copy. You stay invisible while any copy stands, until you strike. Cooldown 30 s (`live.ultimateNext`).
- **Health:** +7 hearts (14 health) on top.
- **Film:** MORPH on robe, robe, hood, hood, then the whole body. First time: mirror plates spread across the robe like frost and the six-shard halo shatters into place around the hood with a gold flash.

## Patch 6: Ooze and Magma Ooze, Fuse, Thornwild, Billow, Towerback, Dud (and the old buff removed)

### Ooze -> Gelatinous King (Magma Ooze -> Molten King)
- **Today:** C turns on the generic Ultimate buff (+50% health and damage, 15% bigger, halved cooldowns) with a burst of goo (flame for Magma Ooze) and a squelch. `ultimateHasMove` is false, so B stays Split.
- **Body:** The same jelly cube, 2x the size (about 1.2 blocks), so it loses the low box. Its thick outer wall has hardened gel plates on the corners and a ring of crystallised gel spikes along the top edge like a crown. The core glows lime. Molten King keeps the three magma slabs but with a black crust plated over them and orange-gold seams glowing between the slabs. It moves in heavier hops, and each landing thuds and throws up goo.
- **R:** Devour, a stronger take on Engulf: it holds up to 3 creatures at once. The size limit goes up from 1 wide and 1.6 tall to 2 wide and 2.2 tall, so zombies and skeletons fit. It digests 2 a second (Engulf does 1), and each one digested to death heals 2. This needs a list of held ids on `Live` in place of the single `latched`.
- **G:** Gob Cannon, a stronger take on Spit: anything held is launched at 2.5 speed (Spit uses 1.6) and bursts where it lands. When empty, it fires a big glob that splashes everything within 3 for 5 damage and Slowness II for 4 s (Spit's glob hits one creature for 3 with Slowness II for 3 s). Magma's glob sets them alight, as Magma Ooze's glob does today.
- **V:** Flood, a stronger take on Squeeze: it melts into a fast puddle (scaled down as Squeeze is, so it fits one-block gaps). Creatures standing within 2 of the puddle get Slowness IV. V again, with room, rises back up.
- **B:** Royal Split, a stronger take on Split: four size-2 slimes (magma cubes for Molten) drop off at once. When one dies, two size-1 blobs of its own drop in its place and count toward the cap. This does not use vanilla's split on death, because those children would be wild slimes outside `live.minions` and would attack you. The cap rises from 4 to 6, and each still heals 3 when it rejoins.
- **C:** King's Crush (new, since there is no Ultimate move today): it springs high and comes down flat. Everything within 5 takes 10 and gets Slowness IV for 3 s, and the smallest creature under it is engulfed. Molten King also ignites them and rings the spot with flame. It reuses `GroundCracks` and `Shakes`. Every 30 s.
- **Health:** +10 hearts (20).
- **Film:** MORPH on the shell as it swells to twice its size, the gel corner plates (black crust over the slabs for Molten), the spike crown pushing up along the top edge, then the glowing core, then the whole body. First time: the cube bloats past its new size and wobbles back down before the crown spikes crack up one by one with a heavy squelch.

### Fuse -> Chain Reaction
- **Today:** C turns on the generic buff (+50% health and damage, 15% bigger, halved cooldowns) with a puff of smoke and a creeper hiss. There is no Ultimate move, so B stays Defuse.
- **Body:** The same armless creeper build (a blocky head on a tall narrow body on four stubby legs), 1.5x bigger. Its head and back get dark scorched plates, and a row of short smoking vent spikes runs down its spine. Hot white-green seams glow between the plates and pulse faster as it swells. The diagonal-pair shuffle and waddle get heavier, and every footfall puffs smoke from the vents.
- **R:** Cluster Pod, a stronger take on Blast Pod: three pods stick in a spread round what you look at and go off 1 s apart in a chain (power 2.5 each; today's pod is 2.0). It uses the same `level.explode(..., ExplosionInteraction.MOB)` call as today's pod.
- **G:** Detonation Dash, a stronger take on Fuse Dash: the dash runs twice as long (24 ticks). It pops every creature it passes, not just the first: 6 damage (today 5) and a small blast each (power 1, no block damage).
- **V:** Overload, a stronger take on Self-Destruct: hold it and it fills in 1 s (20 ticks) instead of 1.5 s (30). The blast is power 5 (today 3.5), then a ring of 6 small ones (today 4). Chain Reaction keeps its shape, so there is no 20-tick re-form. It still costs band charge.
- **B:** Blast Shell, a stronger take on Defuse: it defuses primed TNT and swelling creepers within 16 (today 10). For 8 s after (today 5 s), every blast that reaches you heals 2 instead of only doing nothing.
- **C:** Cascade: every creature within 12 gets a primed pod. They go off one by one, nearest first, 4 ticks apart (power 2 each, block damage by mobGriefing as the pods are). Every 30 s.
- **Health:** +8 hearts (16).
- **Film:** MORPH on the legs as they thicken, the body as scorched plates slam onto its back, the vent spikes punching up its spine, then the head as its seams flare white-green, then the whole body. First time: it swells until hairline cracks split it, hissing louder, and each plate lands with a smoke puff as the seams pulse ever faster.

### Thornwild -> Elder Grove
- **Today:** C turns on the generic buff (+50% health and damage, 15% bigger, halved cooldowns) with a spore-blossom burst and a creaking sound. There is no Ultimate move, so B stays Bloom.
- **Body:** The same treant on root feet, 1.6x taller (about 3 blocks). Its bark thickens into overlapping armour plates, and long thorn spikes run along its arms and shoulders. Its twig crown grows into a wide crown of leafy branches like antlers, and a second pair of thin branch arms sprouts from its back hump. Amber sap glows in the seams between the bark plates. It walks slower and heavier, and rooted-dirt particles sprout round each planted foot.
- **R:** Strangleroots, a stronger take on Roots: the aimed creature and up to 4 others within 6 of it are held for 5 s (today one creature for 4 s), bitten for 2 a second (today 1), and pinned to the ground with no jumping, as Roots' Slowness and jump lock do. This needs a list of held ids in place of the single `latched`.
- **G:** Thorn Storm, a stronger take on Thorn Volley: three fans of 7 thorns, 4 ticks apart, each thorn doing 3.5 (today one fan of 5 at 2.5). They are `Arrow`s as today, built from a poison tipped-arrow stack so they poison.
- **V:** Bramble Wall, a stronger take on Hedge: a wall of persistent leaves 7 wide and 3 tall (the hedge is 5 wide and 2 tall). A row of full-grown sweet berry bushes grows in front of it where the ground will hold them, and they hurt whatever pushes through. Both are removed after 30 s, as the hedge is.
- **B:** Grove Bloom, a stronger take on Bloom: radius 6 (today 3) for 15 s (today 10 s), and you and friends inside get Regeneration I.
- **C:** Wildwood Rising (new, since there is no Ultimate move today): it drives its arms into the ground, as in the "roots" act, and a ring of roots erupts out to 10. Every creature caught takes 6, is thrown up a little and is then held for 4 s. It reuses `GroundCracks` and rooted-dirt `BlockParticleOption`. Every 30 s.
- **Health:** +12 hearts (24).
- **Film:** MORPH on the root feet as they grow taller, the trunk as bark plates overlap with amber sap in the seams, the arms as thorn spikes run along them and the back branch arms sprout, then the crown as it spreads into leafy antlers, then the whole body. First time: the camera creeps up its trunk in slow motion as each bark plate creaks shut and the antler crown unfurls with a burst of leaves.

### Billow -> Stormfront
- **Today:** C turns on the generic buff (+50% health and damage, 15% bigger, halved cooldowns) with a cloud burst and a happy-ghast sound. There is no Ultimate move, so B stays Fog.
- **Body:** The same cloud mass, 1.6x broader and darker: a storm-grey underside, heaped thunderhead puffs on top flattening into an anvil crown, and dense dark storm-cell plates under its belly. Blue-white seams flicker inside the puffs like lightning, and rain streaks fall from its underside. Its box stays the low swimming-pose box, so it still floats and seeps like wholly-smoke. It still carries 3 riders, and its top trails further behind as it drifts.
- **R:** Deluge, a stronger take on Downpour: radius 8 for 10 s (today 4 for 6 s). It puts out fire, slows creatures in it (Slowness I), and does 1 a second to water-sensitive mobs (blazes, endermen, striders), as rain hurts them.
- **G:** Chain Lightning, a stronger take on Thunderhead: a visual-only `LightningBolt` plus a direct hit, as today, so it starts no fire. It does 10 to the aimed creature (today 8), then jumps to up to 3 more within 6 for 6 each.
- **V:** Gale, a stronger take on Gust: 12 blocks deep (today 8) in a wider cone, with a push of 2.4 (today 1.6). It also flips incoming arrows and projectiles back the way they came.
- **B:** Whiteout, a stronger take on Fog: radius 12 for 12 s (today 8 for 10 s). Creatures inside get Blindness and Slowness and lose you and your riders.
- **C:** Tempest (new, since there is no Ultimate move today): for 8 s it is a live storm. Every second a no-fire bolt strikes a random enemy within 16 for 8. Rain particles fall round it (the weather does not change), and a wind drags everything within 10 slowly towards it. Every 30 s.
- **Health:** +15 hearts (30).
- **Film:** MORPH on the underside as it darkens to storm grey with dense storm-cell plates, the rain streaks starting to fall, the puffs heaping up into a thunderhead, then the anvil crown flattening on top as its seams flicker, then the whole body. First time: the cloud boils up far taller than it ends, then flattens into the anvil with one blinding blue-white flash and a thunderclap.

### Towerback -> Colossus
- **Today:** C turns on the generic buff (+50% health and damage, 15% bigger, halved cooldowns) with campfire smoke and a deep warden roar. There is no Ultimate move, so B stays Roar.
- **Body:** The same stone giant (one of the aliens kept humanoid), 6.5x instead of 5x (about 12 blocks). Z still raises it to monolith size; at 16x, the scale attribute's limit, it is no bigger than the base's monolith. Dark slate-blue slab armour covers its chest and forearms, and its boulder pauldrons sprout jutting stone shards. Pale blue-white light glows through cracks in its stone, the same colour as its eyes. The broken watchtower on its back grows into a whole keep with a lit beacon glow on top. Its strides stay long, and each step's cracks spread wider.
- **R:** Crushing Grip, a stronger take on Grab: a faster wind-up (20 ticks instead of 30), squeezes of 6 (today 4), and a slam of 40 (today 30) whose impact sends out a Stomp shockwave.
- **G:** Quake, a stronger take on Stomp: the wave runs 3x as far as a step's (today 2x) for 14 (today 10), and a second wave follows 10 ticks later.
- **V:** Sweep, a stronger take on Swat: the same slow coil, then both arms sweep the whole front, reaching 1.6 per scale (today 1.2). It does 16 (today 12) and flings at 3.0 (today 2.4). It breaks soft blocks in its path (hardness 1 or less: leaves, glass, dirt), as `Towerback.trample` breaks blocks, and trees in its path fall as `GiantTrample` fells them.
- **B:** Warcry, a stronger take on Roar: 4 per scale of reach (today 3). Monsters get Weakness III and Slowness IV for 10 s and lose their target (today Weakness II and Slowness III for 8 s), and friends near get Strength I and Resistance I for 10 s.
- **C:** Landfall (new, since there is no Ultimate move today): it heaves up and crashes down as a triple stomp. It does 20 close in, sends `GroundCracks` 12 out, gives everyone within 64 a `Shakes` jolt, and throws everything up. Every 30 s.
- **Health:** +40 hearts (80).
- **Film:** MORPH on the forearms and chest as slate-blue slab armour slams on, the boulder pauldrons as stone shards jut out, the tower as it rises into a whole keep with a beacon glow, then the head as its eyes and cracks flare pale blue, then the whole body. First time: each plate lands with a ground shake, the keep stacks up course by course, and the beacon flares on top before the eyes ignite.

### Dud -> Bouncer
- **Today:** Dud has no Ultimate. It doesn't override `onUltimate`, so C returns false and the band only shows the "Not here, not now." hint. The key tooltip says "Dud has no Ultimate: nothing happens".
- **Body:** The same soft round ball on tiny stubby feet, 1.5x bigger. Quilted rubbery plates cover its front and top like a bumper, it is studded all over with blunt round rubber nubs, and soft pink seams glow between the quilting. The worried face gets a set brow, and its nubby hands swell into mitts. It moves in springy bounces instead of a waddle.
- **R:** Big Taunt, a stronger take on Taunt: radius 32 (today 24), and it pulls in monsters even without line of sight. Every monster drawn in gets Glowing for 8 s so friends can see them.
- **G:** Mega Boing, a stronger take on Boing: a bounce of 1.6 up (today 1.0). On landing, a squash wave flings everything within 4 up and away (no damage).
- **V:** Fake Pop, a stronger take on Play Dead: it bursts in a pink puff and gets Invisibility for 5 s (Play Dead lasts 3 s), and every monster within 32 drops it for that time.
- **B:** Bowling Roll, a stronger take on Roll: twice the speed for 1.5 s (Roll is one push of 1.1 for 15 ticks), steering where you look. Every creature it hits is bowled over and thrown hard (no damage).
- **C:** Bounce House (new): for 6 s every monster within 10 is pulled to it and flung off it each time it touches. Bouncer hurts nothing itself, but the falls do. Every 30 s.
- **Health:** +10 hearts (20). This is a shell: while it holds, blows drain it instead of flinging Bouncer away, so Bouncer stands its ground. Falls never drain it. When the shell breaks, you are back to the unhurtable Dud.
- **Film:** MORPH on the body as it puffs up and the quilted bumper plates pop on, the rubber nubs studding out all over, the nub hands swelling into mitts, then the face as its brow sets, then the whole body. First time: it inflates like a balloon past its new size with a squeak, the nubs pop out in a ripple, and the worried face hardens into a glare on the final beat.

## Open questions

1. ~~Should an unforged band allow Ultimates?~~ Andy (2026-11-04): they come on when you are about to die, usually to a boss or great foe. Planned as the last stand in item 5: before the reforge only that way, after it C at will as well.
2. **Drop-back key: hold C (0.5 s) or sneak+C?** Recommended: hold C, the same as holding Z to turn back. C moves then fire on release of a tap.
3. **When the Ultimate's health runs out: back to the base alien with a 1.5 s stun and no Ultimate again this change, or allowed again after a cooldown?** Recommended: stun, and locked for the rest of the change.
4. **When the band's time runs out as an Ultimate: straight to human, or first back to the base alien for a grace period?** Recommended: straight to human ("lasts until the charge runs out").
5. **Should the rival band's darkened aliens (RivalAlien) also use Ultimates, for example a low-health last stand?** Recommended: not now; a later patch once the 40 Ultimates exist (41 aliens without Nadir, with Ooze and Magma Ooze sharing one class).
6. ~~Should other players near the change-in see the film?~~ Andy (2026-11-04): yes, the whole film, unless they are in combat; then they just see the change in the world. The film letterboxes and zooms on each changing part, and the first time is longer and exaggerated (item 2).
7. **Should Dud's Ultimate deal damage, or stay a no-damage clown that only taunts and throws?** Recommended: it deals light damage, as the one payoff for the joke alien. This needs the `body()` fix to the Dud check in `ALLOW_DAMAGE`.
8. **Renames.** Some of today's Ultimate names match characters or names in other games, shows and films, so the plan gives them new ones: Rivet Ironclad to Ironpour, Slipfin Tidecaller to Brinecrown, Cortex Overmind to Mindcrown, Croaker Bog King to Marshmaw, Keen Nightwing to Vesperwing, and Skitter Broodmother to Silkmother. A few move names were changed for the same reason. Recommended: take the new names (lang only, nothing saved uses them).
