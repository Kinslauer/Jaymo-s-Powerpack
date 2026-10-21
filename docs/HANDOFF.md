# Handoff: picking up Jaymo's Powers (and Kingdoms, Kitchen) in a new session

Written 2026-10-10 at the end of a long local session, so a cloud session can carry on.

## What's in this repository
- **The root: Jaymo's Powers** (`jaymospowers`, Fabric, Minecraft 26.3), now **v0.7.1**. Powers: Gravity, Wind Control,
  Web Slinger (with the symbiote), Pocket Arsenal, Chronokinesis, Resonance, Darktide, Xenoform (the Xeno-Band
  from falling stars; `docs/xenoform.md`). Each power owns `power/<name>` (+ client),
  `lang/parts/<name>.json` and its mixin configs. Core shared code is in `core/`, `api/`.
  - Screen shake: every power goes through `core/Shakes` (server: `send`, `around`, `heavy`, each with the spot it
    came from) and `client/ScreenShake` (directional kick + rattle, stacking, explosions shake too). Don't add another.
  - Held sounds: every power's sustained sound goes through `core/Soundscape` (layers of variants with jittered
    volume/pitch, `due` for uneven timing instead of `tickCount % N`, echoes when enclosed) and, on the player's own
    side, `client/sound/PowerLoop` (a looping bed that follows speed/charge) plus `client/sound/Accents`. Don't add
    a bare `% N == 0` sound loop.
  - `docs/Abilities.md`: the table of every ability (the user edits the "Change to" column; "Line" = the row's own line
    number in the file). `docs/Plan-0.7.md`: the 0.7 plan and the user's answers (all built).
  - `docs/<power>.md`: per-power design notes. `MODLOG.md`: the design log.
  - `tools/*.py`: asset generators (sprites, models, worldgen JSON); run `python -I tools/<x>.py` from the root.
- **`kingdoms/`: Jaymo's Kingdoms** (`jaymoskingdoms`, its own Gradle project; history merged in). An *original*
  mod (Tale of Kingdoms' code is GPL-3.0 and its art all rights reserved, so nothing is taken from it).
  `kingdoms/MODLOG.md` is its log; `docs/Kingdoms-Plan.md` the plan and the user's answers.
- **`kitchen/`: Jaymo's Kitchen** source (`jaymoskitchen`, its own Gradle project, v0.4.22; not under git before, so no
  history). It requires Cooking for Blockheads and Balm. Its built jar is committed in `libs/` (the pack ships it).

## Building (in a cloud session)
- JDK 25 and Gradle wrapper 9.7.1. `./gradlew build` in the root (or in `kingdoms/`, `kitchen/`). Build output goes
  to `build/` (on the home PC it goes to `E:\...`, kept out of OneDrive).
- 26.3 ships unobfuscated (Mojang names, no mappings). There are no decompiled sources in the repo; on the home PC
  they were at `E:\mcsrc-26.3`. In a cloud session, `./gradlew genSources` (Loom) gives them; **always check an API
  in the 26.3 sources before using it**: much has changed since older versions.
- `packageModpack` / `serverMods` / `profileMods` / `runClient` / `runServer` only make sense on the home PC (they
  write to the CurseForge profile and the friends' server folder, `../Serverfiles`, which isn't in the repo).

## 26.3 API facts learned the hard way
`ModelPart` has no `copyFrom`: use `part.loadPose(other.storePose())`; `EntityTypes.PLAYER` (not `EntityType.PLAYER`); Kitchen's jar builds to `E:\jaymos-kitchen-build\libs\`;
`Entity.syncVelocity = true` (no hurtMarked); `ServerPlayer.drop(stack, false, Prediction.SERVER_ONLY)`;
`new ItemStack(item).getHoverName()`; `Vec3.STREAM_CODEC`; `EntityType.builtInRegistryHolder().is(tag)`;
`PushReaction.IMMOVEABLE`; `collector.submitMovingBlock(poseStack, MovingBlockRenderState, outlineColor)`;
`ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF)` (FLASH needs a colour); owner-only particles:
`level.sendParticles(ServerPlayer, particle, true, false, x,y,z,count,dx,dy,dz,speed)`; `ServerLivingEntityEvents.ALLOW_DAMAGE`;
`Feature` is an interface (`codec()`, `place()`); `ResourceLocation` is `Identifier`; `ResourceKey.identifier()`;
`ChunkPos` is a record with `pack()`/`unpack()`/`containing(BlockPos)`; `player.swing(hand, SwingAnimation.DEFAULT, true)`;
`entity.setInvulnerableTime(0)` (the field is private); `Entity.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false)`;
coloured blocks (terracotta, beds, banners, carpets) have no `Blocks.X` fields: look them up by name in
`BuiltInRegistries.BLOCK`; GUI: `Screen.extractRenderState(GuiGraphicsExtractor, ...)`, `graphics.text/centeredText/fill`,
`minecraft.gui.setScreen(...)`; `Font.split(text, width)`.
- **Attachments** must be class-loaded at init on both sides or dedicated servers break ("Client does not support the
  syncable attachments"): every class holding an `AttachmentType` is listed in `JaymosPowers.registerAttachments()`.
- Poses go through `WebModelFeatureRendererMixin.prepareModel` (after `setupAnim`) and render-state extraction via
  mixins on `LivingEntityRenderer`/`AvatarRenderer` (see `WebBodyPose`, `FreefallPose`, `ChronoFlowPose`). (0.15.32) Ease
  a pose in and out with `client/PoseBlend` (a `Channel` per pose, a `Frame` of offsets from vanilla, `Track.apply`).
- Never write Java with PowerShell `Set-Content` (it adds a BOM). Edits were done with small Python scripts
  asserting each replaced snippet occurs exactly once.

## The user and how they like things (from the session's memory)
- They playtest with friends on a server and send many feature and tuning requests, often mid-task.
- **Fall damage always applies outside creative**; no power waives it (except controlled descents slower than
  0.5 b/t, and Gravity's Weightless which they asked for).
- Movement powers feel physical: hunger cost, inertia, crash damage scaling with speed².
- Power HUDs sit bottom-right, right of the hotbar, in Wind's chip style; never mid-screen.
- Natural awakenings start with one ability; the rest unlock through Mastery: 3 in-game days an ability from time alone, ability use and meditating (I) bring it down to 1 day, training in someone else's Immense Field to half a day (the field's owner only while shadowboxing in it beside another shadowboxer; a Gravity user's shadowboxing pays only with a partner). Creative gets all.
- Ascension/corruption is discovered in play; messages stay vague; cures are circumstances in the world.
- Bump `version` on every release (new packets break old clients). After a release on the home PC:
  `packageModpack`, the friends get `modpack/Jaymos-Powers-Modpack-<v>.zip`, the server restarts.
- Don't launch long test runs without asking: the user closed the slow dev client/server consoles. Build only.
- The user reads the `docs/Abilities.md` table to request changes.

## Where things stand
- **Wounds** (Powers 0.15.1, `core/wounds/`, plan `docs/Wounds-Plan.md`, specs `docs/Wounds-Phase2.md` to
  `Phase5.md`; all five phases written without a compiler, not yet built or tested on the Deck): every living thing's
  health spread over body parts, the downed state (moved from Kingdoms), broken limbs, bleeding and festering, pain,
  the medieval kit, and the client's HUD, poses and effects. Player-facing rules in `docs/Abilities.md` (Wounds).
  26.3 / Fabric facts it was built on:
  - `ServerLivingEntityEvents.AFTER_DAMAGE` **never fires for a lethal hit**. Whatever must see the killing blow (a
    head or limb severed onto the corpse) notes it in `ALLOW_DEATH` and acts in `AFTER_DEATH`.
  - The order is `ALLOW_DAMAGE` → the hit → `ALLOW_DEATH` (every phase, in order; any `false` cancels the death) →
    `AFTER_DEATH` (same tick, only once the death has gone through). Downed saves from death in a phase `LAST`, after
    the default phase (so Darktide's shade, Xenoform, sparring and Kingdoms' Last Stand go first); Wounds notes the
    killing blow in a phase after that (`KILLING_PHASE`), so only deaths nothing saved are noted.
  - **Scoreboard tags shared across the mods** (Kingdoms and Kitchen never compile against Powers; they read and write
    these): `jaymospowers_downed` (on everyone downed), `jaymospowers_downable` (Kingdoms puts it on its folk),
    `jaymospowers_revive` (Kingdoms' tending gets the downed up), `jaymospowers_bandaged` (Kingdoms' tending stops the
    bleeding), `jaymospowers_fights_on` (an undead fighting on without its body), `jaymospowers_crippled` (a
    broken leg; not read by Kingdoms yet), `jaymospowers_freerun` (Kingdoms' Stealth experience), and Kitchen's
    corpse tags `jaymoskitchen.missing.<part>` and `jaymoskitchen.broken.<part>`.
- **Powers 0.8.0** is built on branch `plan-0.8` (not merged to master, not packaged, not playtested): everything in
  `docs/Plan-0.8.md`, built by agent tracks (briefed by `docs/AGENT_BRIEF_0.8.md`) and merged by the lead. The jar went
  to the user's tester. Jaymo's Kitchen is now a required dependency; First-person Model and Not Enough Animations are
  in `packModsNoCf` in `build.gradle` until their CurseForge ids are filled in. Each power's doc has a 0.8 section with
  what to check first in game.
- **Powers 0.7.1** is built and packaged (not yet on the server: the user must stop it and run Build Modpack).
  The changelog was given in chat; add "Wind users dive head-first instead of flailing, and pull out of it with
  their flight" under Wind.
- **Kingdoms 0.2.0** (not in the pack yet): kingdoms found at village bells (or `/kingdom found [keep|castle|tower]`
  for ops); three kinds of seat built in the village's own materials (`Palette`), watchtowers with archers, kings
  (bosses, rally at 40%), guards, two-layer reputation, ranks, treasury, a fallen king's three random fates,
  `/kingdom` commands. Epic Structures: Villages (all rights reserved, but the author invites modpack use; CurseForge
  1308486 / file 8830175; Modrinth `epic-structures-villages:hCRa4eFr`) is in Kingdoms' dev runtime; it joins the pack
  with Kingdoms. The three seat designs were built and the keep verified block by block; the castle and tower designs
  haven't been seen in game yet.

## Next, in the user's order
1. **Conversations with the kingdom's people** (in progress, not started in code): right-click a king/guard/archer
   opens a talk screen (server-authoritative: an OpenTalk packet with the folk, role, kingdom, reputation, a line and
   the options; buttons send Talk(folkId, option)). Options: talk (role/reputation-flavoured lines; a little
   reputation, once a day), ask about the kingdom (king, treasury, rivals, your rank), rumours (other kingdoms' and
   barbarian camps' directions), gift the held item (emeralds, gold, diamonds, food → reputation), ask for work
   (bounties: slay N monsters on their land; deliveries: bring wheat, iron, logs → emeralds, reputation, standing),
   and for the king: pledge, pay tribute, leave. Hostile reputation: they refuse ("Begone!").
2. **Barbarians**: camps in the wild 150-300 blocks from kingdoms (tents of wool, a log palisade, a campfire, the
   chief's tent with loot); barbarian and chief roles (hostile to players, kingdom folk, villagers, golems; the chief
   a mini-boss with a boss bar); camps grow daily; war parties raid the nearest kingdom when someone is near it
   (announced, marching on the capital); clearing a camp (the chief dies) earns reputation with every kingdom.
   Folk's HurtByTarget must only alert its own side (barbarians vs kingdom folk).
3. Later: war between kingdoms and land claiming, kingdoms' own battles (intervening shifts reputation), the
   member-kingdom politics; **Better Mob Combat** rewrite (MIT/CC0, an add-on so humanoid mobs use Better Combat's
   combos; good for guards and kings); **MCA Reborn** (GPL-3.0) is in the pack since Powers 0.8.21 / Kingdoms 0.4.2,
   built from its authors' dev/26.3 branch (`libs/mca-fabric-*.jar`) until a 26.3 release: swap in the CurseForge
   file then (see `mcaJar` in `build.gradle`). Kingdoms' tie-in is `McaCompat`/`McaBridge`/`mixin/mca`.
