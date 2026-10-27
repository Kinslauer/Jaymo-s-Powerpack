# Mana spirits (Kingdoms 0.7.245)

Andy (2026-10-26): "a mage hand type of ability that mimics the way stands work but not their presentation ... use of the
mage hands calls upon a mana spirit, each of which has a differing personality of 5, one of those being seemingly non
responsive at all ... these spirits should conjure as arms behind you with the same effects as your mana aura making up their
entire form ... some just give you buffs ... some have entire move sets but all can be upgraded and leveled and the ones
with personalities can kick in on their own to assist you at times similar to how the symbiote operates. make them speak like
wise wizards from ancient times looking after a young child or grumpy old wizards that are tired of looking after said young
child ... Make them give battle advice while in combat at higher bonding levels." Look: Nero's Devil Breaker (DMC5) to
Doppio's partial manifestation (JoJo), all different sizes. JCraft: Eyes of Ender is All Rights Reserved: ideas only, our own
art and code.

Andy's picks (the Mana Spirits doc): every ability but Work hands; Sense is the Quiet One's only. Unlock: 1 in 18 per brush
with death while attuned, then spirit sickness for up to 5 days (sooner with meditating, casting, nearly dying again);
waking cures it. One spirit for life; it can be released at a cost and no new one called. Key: a new keybind, L (no letter is free of
both mods and vanilla; vanilla's Advancements is unbound from L once per install by Kingdoms' `SpiritKey`, it stays on the pause menu). Attuned folk (court wizards, hermits) have spirits you can see and fight. The Quiet One speaks once at max
bond. **First patch: the shared mage hands plus Grimsby (blade) and Varro (siege).** Ambrose, Thessaly, the Quiet One,
Clash, Spirit armour, Spirit ride and Sense come in the next one.

## Where things live

Kingdoms, since mana lives there. Server: `kingdoms/src/main/java/com/jaymoskingdoms/spirits/`. Client:
`kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/`. Lang: Kingdoms has one flat `en_us.json`; while the tracks
run, each track writes its keys to `kingdoms/lang_parts/spirits_<track>.json` (plain JSON object, not loaded by the game) and
the lead merges them into `en_us.json` at the end. Tuning numbers are `static final` constants at the top of each class.

Shared helpers the lead added before the tracks (use these, don't re-make them):
- `Mana.pay(ServerPlayer, float cost, int intent)`: afford + take + ripple; false (and a word on screen) if they can't.
  `Mana.canPay(ServerPlayer, float)`. Both public.
- `Arcana.sighted(Player)` is public: the living entity in the player's sight within 24 blocks.
- `PowersCompat.helpless(LivingEntity)`: Powers' `Finishers.helpless` (downed/crippled) by reflection, false without Powers.
  `PowersCompat.shake(ServerPlayer, float)`: Powers' screen shake, no-op without Powers. `Downed.isDowned(entity)`
  (Kingdoms) reads Powers' downed tag.
- `Mana.attuned(entity)`, `Mana.current/max`, `Mana.ripple(entity, intent)`, `Mana.sendSeen(...)` (particles the attuned
  see), `ManaAlchemy.readsMagic(viewer)`, `ManaSickness.strain` (every `Mana.pay` already strains).

## Track A1: the core (server)

Files: `spirits/Spirits.java`, `spirits/SpiritState.java`, `spirits/Spirit.java`, `spirits/Move.java`,
`spirits/Moves.java`, `spirits/Poses.java`, `spirits/SpiritVoice.java`, `spirits/SpiritSickness.java`, stubs
`spirits/HandMoves.java`, `spirits/Grimsby.java`, `spirits/Varro.java` (name, colour, size, empty move list and hooks;
tracks B and C fill them). Hooks into existing code: `Classes` player tick calls `Spirits.tick(player)` after
`Mana.tick(player)`; `Spellcraft.register()` calls `Spirits.register()` after `Mana.register()`.

**SpiritState** (record, `CODEC` and `STREAM_CODEC`): `int kind` (`Spirits.GRIMSBY = 0, VARRO = 1, AMBROSE = 2,
THESSALY = 3, QUIET = 4`), `int level` (1-20), `int xp`, `int bond` (0-100), `boolean out`, `int move` (index into the
spirit's move list), `long show` (game time × 32 + a `Poses` id: the pose the arms hold right now, for viewers), `int arms`
(1, or 2 from bond 40). Attachment `Spirits.SPIRIT`: persistent, `copyOnDeath`, synced to all (viewers draw the arms).
`Spirits.SICK`: `SpiritSickness.State` record (`int kind, long sinceDay, int push`), persistent, copyOnDeath, synced to
the target only. `Spirits.RELEASED`: Boolean, persistent, copyOnDeath (never another). `Spirits.FLASH`: Long (game time
× 8 + a flash kind: 0 act, 1 parry, 2 ward, 3 warn), synced to the target only: the client's screen flash for every
autonomous act. Register all attachments in `Spirits.register()` (a debug log line that names them forces the classes to
load, as `Mana.register` does).

**Spirit** (abstract class, one instance per kind, `Spirits.of(kind)`):
- `int kind()`, `String id()` ("grimsby"), `int color()` (ARGB, blended into the aura's blue by the client), `float size()`
  (arm scale: Grimsby 1.0 long and bony, Varro 2.4 a gauntlet as tall as you, Ambrose 2.0, Thessaly 0.6, Quiet varies).
- `List<Move> moves()`: its own moves; the player's list is `HandMoves.SHARED` then these, filtered by
  `move.level() <= state.level`.
- Hooks, all default no-ops, called by `Spirits` only while the spirit is out and the player can pay:
  `void tick(ServerPlayer, SpiritState, @Nullable LivingEntity threat, long now)` (every 10 ticks; threat = the nearest mob
  targeting the player within 16, or null), `boolean hurt(ServerPlayer, SpiritState, DamageSource, float amount)` (from
  `ServerLivingEntityEvents.ALLOW_DAMAGE`; return true to cancel the hit: a parry, a ward), `void killed(ServerPlayer,
  SpiritState, LivingEntity)`, `void aimedAt(ServerPlayer, SpiritState, LivingEntity archer)` (a mob with a bow or
  crossbow targeting the player, every 10 ticks).
- `Spirits.act(player, kind, flashKind, cost)`: what every autonomous act goes through: checks bond ≥ `ACT_BOND` 20
  and `Mana.canPay(player, cost)` with a floor (never below 15 mana), pays, sets `FLASH`, says the `act` line sometimes.
  Each act keeps its own cooldown in the Spirit (a `Map<UUID, Long>`).

**Move** (interface): `String id()`, `float cost()`, `int cooldown()` (ticks), `int level()` (unlocks at), `boolean
charges()` (hold to charge: the javelin), `int pose()` (a `Poses` id shown when cast), `boolean cast(ServerPlayer player,
SpiritState state, int charge)` (charge = ticks held, 0 for a tap; return false if nothing happened so no mana is taken).
`Moves`: `register(Move)`, `of(String id)`, cooldown map `Map<UUID, Map<String, Long>>` with
`ready(player, move, now)` sending `message.jaymoskingdoms.spirit.wait` (seconds left) when not.

**Poses** (ints): `IDLE 0, REACH 1, GRAB 2, THROW 3, CATCH 4, SHOVE 5, PIN 6, HOLD 7, SWORD 8, GREATSWORD 9, SPEAR 10, AXE 11,
HAMMER 12, FLURRY 13, PARRY 14, SLAM 15, JAVELIN 16, VOLLEY 17, BOMBARD 18, WALL 19, MARK 20, WARD 21, FINISH 22`.
`Spirits.show(player, pose)` stamps `state.show` (the client holds a pose `POSE_TICKS` 16 then eases back to IDLE).

**Keys and payload**: `Spirits.Key(int action, int held, boolean sneak)` serverbound, `jaymoskingdoms:spirit_key`.
Actions: `SUMMON 0` (L tap, spirit not out), `CAST 1` (L released, spirit out; `held` = ticks held, for charging moves),
`NEXT 2` (sneak + L tap: select the next move, overlay message with its name), `DISMISS 3` (sneak + hold L 15 ticks).
Receiver in `Spirits.register()`. The client (track A2) sends them.

**Summon / out**: summoning costs `SUMMON_COST` 10 mana, ripples WILL, says `summon`; out, the spirit drains
`DRAIN_PER_SECOND` 0.5 mana a second; at mana < `TIRED_AT` 5 it fades back on its own (`tired` line). Dismiss says
`dismiss`. Dying, logging out or going spectator puts it away. Only the attuned with an awake spirit can summon.

**Bond**: +1 per `BOND_TICKS` 1200 out (×3 meditating with it out), +2 per fight won together (a kill while out), −1 when
the player burns out (ManaSickness stage 3) with it out. Bond 40 gives `arms = 2`. **Level**: xp +10 per kill while out,
+1 per move cast; level n needs `XP_PER_LEVEL` 100 × n; each level adds `LEVEL_DAMAGE` 5% to its moves' damage (`Spirits.
damageScale(state)`), up to level 20. Say `bond` at each 20 crossed and `level` on level up.

**Near death and sickness** (`SpiritSickness`): on `ServerLivingEntityEvents.AFTER_DAMAGE`, an attuned player without a
spirit, not released, not sick, left at ≤ `BRUSH_HEALTH` 20% of max health (or `Downed.isDowned`), at most once per
`BRUSH_EVERY` 2400 ticks, rolls 1 in `BRUSH_CHANCE` 18. Success: the kind is rolled (first patch: GRIMSBY or VARRO, by
life story if `Heritage`/`LifeStory` offers a lean, else at random), `SICK` is set with the current overworld day
(`Production.day(server)`) and the player reads `message.jaymoskingdoms.spirit.sick.begin`. Sick: mana regen ×
`SICK_REGEN` 0.5 (hook in `Mana.tick`: `SpiritSickness.regenTimes(player)`), a `sick.<day 1-5>` whisper from the
spirit-to-be once a day and at random (`SICK_WHISPER_EVERY` 2400 ticks, chance 0.3), `push` += 1 a second meditating, +=
cost/5 per cast (hook: `Mana.take` calls `SpiritSickness.pushed(player, price)`), += `PUSH_BRUSH` 60 per further brush
with death. It wakes when day ≥ sinceDay + 5 or push ≥ `PUSH_WAKES` 300: `SICK` removed, `SPIRIT` set (level 1, bond 10),
the `wake` line, a flare (`Mana.attune`-style motes, `Mana.ripple(WILL)`), `message.jaymoskingdoms.spirit.woke`.

**Release**: `/kingdom spirit release` twice within 30 s: the spirit is gone for good (`RELEASED` true, `SPIRIT` removed),
the player's mana is emptied and they fall into ManaSickness stage 3, and the `release` line is said. Also
`/kingdom spirit give <player> <kind>` and `set <player> bond|level <n>` for gamemasters.

**SpiritVoice**: `say(player, event)`: a chat line only they see, `Component.translatable("spirit.jaymoskingdoms.<id>.<event>.<n>")`
with n in 1..`LINES` 3, prefixed by the spirit's name in its colour, italic. One line per `SAY_GAP` 60 ticks at most. Events
every spirit must have 3 lines for: `wake, summon, dismiss, tired, kill, hurt, low, idle, bond, level, act, release,
sick.1 .. sick.5` (one line each for sick days). Bond gates: `idle` remarks (every 1200-2400 ticks out) from bond 40;
`advice.<foe>` from bond 60: `Spirits.advice(player, state, threat)` picks a key by the foe (`caster` for a Folk caster or
Sorcerer, `archer` for a bow or crossbow, `creeper`, `armoured` for ≥ 10 armour, `many` for 4+ foes within 8, `big` for
bounding box height ≥ 2.5, `foe` otherwise), once per foe type per `ADVICE_GAP` 600 ticks. From bond 80 the advice comes
before the fight (threat within 24 instead of 16).

## Track A2: the client

Files: `client/spirits/SpiritKey.java` (L, `key.jaymoskingdoms.spirit`, category `jaymoskingdoms:spirits`, the
tap/hold/sneak logic that sends `Spirits.Key`), `client/spirits/SpiritArms.java` (the render layer),
`client/spirits/SpiritHud.java`. Register from `JaymosKingdomsClient` after `ManaClient.register()`.

**Arms**: a `RenderLayer<AvatarRenderState, PlayerModel>` on `AvatarRenderer` and a `RenderLayer<S extends
HumanoidRenderState, HumanoidModel<S>>` for folk (same two-branch registration as `ManaClient.register`). Drawn only
when the viewer is attuned or `ManaAlchemy.readsMagic` and the wearer's `SPIRIT` state is `out` (or `show` is recent).
A model baked per kind and per shell: a `LayerDefinition` whose root holds `right_arm` (and `left_arm`) at the vanilla
offsets `(-5, 2, 0)`/`(5, 2, 0)`, each an arm box `(-3, -2, -2, 4, 12, 4)` with `CubeDeformation` of `ManaClient.SHELLS`
(0.4, 0.85, 1.4) and the kind's own hand on the end: Grimsby three long bony finger cubes (1×5×1) plus a blade cube
(1×10×3) below the wrist; Varro a gauntlet box (6×8×6) with knuckle studs; placeholders for the others. The arm part
copies the wearer's arm pose each frame (`loadPose(wearer.rightArm.storePose())`, as `WornModel` does), then the pose
offsets for `state.show`'s `Poses` id are added (a table of xRot/yRot/zRot per pose, lerped in over `POSE_IN` 4 ticks and
back out after `POSE_TICKS` 16). The arm grows out of the wearer's own arm: scaled about the shoulder pivot by `size ×
manifest`, where `manifest` eases 0→1 over `MANIFEST_TICKS` 10 after the state turns out (and 1→0 after), and pushed
behind the shoulder by `BEHIND` 2 px × size so a big spirit sits behind you. The second arm draws from `arms == 2`.
Drawn with `RenderTypes.energySwirl(ManaClient.TEXTURE, ...)` exactly as `ManaClient.Shells` does (three shells, light
`SHELL_LIGHT × glow strength × breathe`, tinted by the kind colour mixed 50/50 with `ManaClient.COLOR`), and the glow
strength comes from `ManaClient.KEY`'s `Glow` on the render state (null: don't draw). Wisps: every few frames a
`Mana.MOTE`-like dust particle at the hand (client-side `level.addParticle`).

**Sickness flickers**: while `SICK` is set on the local player, every `FLICKER_EVERY` 100-300 ticks a one-arm flicker: the
arm draws for 6 ticks at manifest 0.4 in the kind colour, and the screen edge tints (`ManaClient.overload`-style vignette,
alpha 0.15) for the same ticks. Each day of the sickness the flickers come more often.

**Flash**: when `FLASH` changes, a 10-tick vignette in the kind colour (alpha 0.25 for act, 0.35 parry, white for ward,
red edge for warn).

**HUD**: while out, a small line above the hotbar's right end: the spirit's name in its colour, the selected move's name,
and `level · bond`; the move's cooldown greys it. Hidden when Auto HUD would hide the hotbar (same check as the aim
crosshair in Powers: skip it if hard; just draw).

## Track B: the shared mage hands (`spirits/HandMoves.java`)

`HandMoves.SHARED` (a `List<Move>`), registered in `HandMoves.register()`. All cost mana via the Move contract; damage
scales with `Spirits.damageScale(state)`. The reach is `REACH` 16 blocks, `REACH_PER_LEVEL` 1 (so 32 at level 16+).
1. **Far hand** (`far_hand`, level 1, cost 4, cooldown 10): use the block in sight within reach: chests, barrels, doors,
   trapdoors, fence gates, levers, buttons, item frames, lecterns: call `state.useWithoutItem(level, player,
   BlockHitResult)` on it as if the player stood there (set the hit result from a ray from the eye); a lit `REACH` pose,
   a line of `Mana.MOTE` motes to it. If an item entity is in sight within reach: pull it to the player (`REACH` then
   `GRAB`) at 0.6 blocks/tick.
2. **Grab and throw** (`grab`, level 2, cost 12, cooldown 60, charges): tap: the arms seize the mob or item in sight
   (`Arcana.sighted`, mass by bounding box: nothing above 3 blocks tall; players only if they're smaller than the spirit's
   size × 2) and hold it 2 blocks in front of you (`GRAB` pose, the held entity set to no-gravity and moved each tick with
   you for up to `HOLD_TICKS` 60); tap again (or release a hold) to hurl it along your look at `THROW_SPEED` 1.4 + charge
   × 0.02 (`THROW` pose); it takes fall damage and deals `THROW_DAMAGE` 4 to what it lands on.
3. **Catch** (`catch`, level 3, cost 6, cooldown 40): for `CATCH_TICKS` 30 the arms snatch every arrow, trident, javelin,
   fireball or thrown potion within 3 blocks heading at the player: discard the projectile (arrows: drop the item in the
   player's hand slot or on the ground), `CATCH` pose, a chime. Also an autonomous version: `HandMoves.catchIfReady` for
   spirits with the `hurt` hook.
4. **Shove** (`shove`, level 1, cost 8, cooldown 80): every foe (not allies: use `Sorcery.foe`-style tests, never a player's
   own tamed animals or Workers of their faction) in a 90° cone within 5 blocks is knocked back
   (`knockback(SHOVE_POWER 1.6, ...)`, as `Sorcery.repulse`), `SHOVE` pose, `PowersCompat.shake` 0.3.
5. **Hold the line** (`pin`, level 4, cost 10, cooldown 120): the mob in sight within reach is pinned `PIN_TICKS` 60: held
   at its spot (no-gravity false; its position reset each tick; slowness 4, no attacking: `mob.setTarget(null)` each
   tick), `PIN` pose; players can't be pinned.
6. **Second pair of hands** (`hold`, level 5, cost 2/second while active, toggle): the spirit holds the player's shield
   or torch: moves the off-hand stack into a per-player `HELD` slot (a `Map<UUID, ItemStack>` saved on the SPIRIT? no:
   kept in the player's `Inventory` by leaving it in the off hand and setting a `HOLDING` flag): while holding, a shield in
   the off hand blocks every hit from the front (`ALLOW_DAMAGE`: cancel hits whose source is within 90° in front, `HOLD`
   pose, shield damage as a normal block) even while the player uses a two-handed weapon, and a torch in the off hand
   lights the way (place a light block: skip; just `MobEffects.NIGHT_VISION` 0 amplifier while active). Tap again to put
   the hands away.
Each move says the `move` line sometimes (`SpiritVoice.say(player, "move")` at chance `MOVE_SAY` 0.15).

## Track C: Grimsby and Varro (`spirits/Grimsby.java`, `spirits/Varro.java`)

**Grimsby the Weary** (`grimsby`, colour `0x9AB8FF` steel-blue-white, size 1.0): grumpy old wizard, four hundred years of
study, a fly swatter for a child, secretly fond. Blade.
- **Conjured arms** (`blade`, level 1, cost 6, cooldown 15, tap = strike, sneak-NEXT cycles the form): the hands form a
  weapon of aura: `FORMS` sword (fast, `SWORD` pose, 6 damage, 3-block reach), greatsword (`GREATSWORD`, 10, a sweep hits
  all in 120° within 3.5), spear (`SPEAR`, 7, 5-block thrust, pierces two in a line), axe (`AXE`, 9, breaks shields:
  `player.disableShield`-style: on a shield-blocking foe, `shieldBlockingDelay`/`disableShield()` where the API allows),
  hammer (`HAMMER`, 12, slow, knocks up). The form is kept in `state.move`'s neighbour: simplest: five separate Move ids
  `blade_sword .. blade_hammer`, all level 1, each its own combo of 3 taps (combo counter per player, resets after 20
  ticks; the third hit does ×1.5). Damage via `target.hurtServer(level, level.damageSources().indirectMagic(player, player),
  dmg × Spirits.damageScale)`. `Mana.sendSeen` slash motes along the arc.
- **Flurry** (`flurry`, level 3, cost 14, cooldown 100): 6 fast blows from both arms over 12 ticks at the foe in front
  (`FLURRY` pose), 3 damage each, the last knocks back.
- **Riposte** (`riposte`, level 5, cost 10, cooldown 160): for 20 ticks the next melee hit on the player is parried
  (`ALLOW_DAMAGE` cancel, `PARRY` pose, clang) and answered with a counter-cut for 8 on the attacker.
- **Guard break** (`guard_break`, level 7, cost 16, cooldown 200): a heavy two-handed slam (`SLAM` pose) for 10 on the
  foe in front that breaks its shield (disable 5 s) and stuns it (slowness 5 + no target for 30 ticks).
- **Finisher** (`finish`, level 9, cost 20, cooldown 300): on a `PowersCompat.helpless` foe in sight within 4: a single
  cut (`FINISH` pose) for 30 damage; it's the player's kill.
- Autonomous (`hurt` hook): at bond ≥ 20, a parry it didn't see coming: when a melee hit would land and the player isn't
  looking at the attacker (dot < 0.3) and its parry is ready (`PARRY_EVERY` 400 ticks): cancel the hit, `PARRY` pose,
  `Spirits.act(... FLASH parry, cost 8)`, then complains (`act` line). `tick`: at bond ≥ 60 advice.
- Lines (`spirit.jaymoskingdoms.grimsby.*`): grumpy, tired, dry; e.g. "Four hundred years of study and I'm a fly swatter
  for a child who walks into caves at night. Duck."

**Old Varro, the Siege-Mage** (`varro`, colour `0xFFB347` amber, size 2.4): gruff war veteran, barks orders, proud when
you listen. Siege.
- **Mana javelin** (`javelin`, level 1, cost 12, cooldown 40, charges): gather energy in the hands (`JAVELIN` pose while
  held; motes gather), release: a piercing bolt entity `SpiritJavelin` (register an `EntityType` as `Cannonball` does:
  `spirits/SpiritJavelin.java`, a `Projectile`-like entity, no gravity for 40 ticks, speed 2.2, pierces up to 3 foes,
  damage 8 + charge × 0.25 up to 20, `Mana.MOTE` trail seen by the attuned, a faint END_ROD trail for all). Client: no
  model needed if the entity renders nothing: the trail is the look (use `EntityRendererRegistry` with a `NoopRenderer`
  equivalent: `EntityRenderers.register(TYPE, NoopRenderer::new)` if 26.3 has it; else a tiny invisible renderer in
  `client/spirits/SpiritJavelinRenderer.java` on track A2's list: leave a note).
- **Volley** (`volley`, level 3, cost 18, cooldown 120): a fan of 5 small javelins (damage 4, spread 15°) (`VOLLEY`).
- **Bombard** (`bombard`, level 5, cost 24, cooldown 200): a slow heavy orb (speed 0.8, arcs) that bursts on impact: 12
  damage in 3 blocks, knockback, no block damage (`BOMBARD`).
- **Spear wall** (`spear_wall`, level 7, cost 20, cooldown 240): javelins rise from the ground in a 7-block line across
  the look direction 3 blocks ahead: each foe in it takes 8 and is launched up 0.6 (`WALL`), as `Sorcery.spikes` does.
- **Mark target** (`mark`, level 2, cost 6, cooldown 60): the mob in sight is marked (glowing 10 s, `MARK`); the player's
  attacks on it do ×1.3 (`ALLOW_DAMAGE`/`AFTER_DAMAGE`: scale via `hurt` amount in a `ModifyDamage`-style: simplest: on
  `AFTER_DAMAGE` by the player, deal the extra 30% again as magic).
- Autonomous: `aimedAt` hook, bond ≥ 20: fires a javelin at whoever is drawing a bow on the player (`JAVELIN_EVERY`
  300 ticks, `Spirits.act(... cost 12)`, the `act` line: "Archer, left! Mine."). `tick`: bond ≥ 60 advice, barked.
- Lines (`spirit.jaymoskingdoms.varro.*`): barked orders, "recruit", proud when the player wins.

## Track D: folk spirits (after B and C)

Court wizards and hermits (`CourtWizards.is`, `Hermits.is`) carry a spirit: `SPIRIT` on the entity (persistent, synced):
kind by `Math.floorMod(uuid.hashCode(), 2)` (Grimsby or Varro), level from their `Mana.power` (power 6 → level 6, hermits
8), bond 50, `out` while they have a target. Set in `ManaFolk.attuneIfMagic`. In `Sorcery.tick` for `WIZARD`, when a
target is within 12 and the spirit's cooldown is ready: Grimsby folk use `flurry` (or a blade strike) and parry the
player's blow one time in three (`ALLOW_DAMAGE` on the folk: cancel + `PARRY` pose + clang); Varro folk throw a `javelin` at
the target and `volley` at 3+ foes. The arms draw on them through the folk layer (A2). Moves must take a `LivingEntity
caster` rather than a `ServerPlayer` where they can: give `Move` a `castFrom(LivingEntity caster, LivingEntity target,
SpiritState)` default that the folk use (default: nothing), and implement it for `flurry`, `javelin`, `volley`, `riposte`.

## Checks

Compile with `/home/user/cc21/kcc.sh <repo root> <files>` (JDK 21, lowered 26.3 jars; the baseline's optional-mod errors are
filtered out). Check every 26.3 API name with `javap -cp /home/user/cc21/jars/26.3.jar <class>` before using it. No game run
in the cloud: Andy plays it.

# Second patch: Ambrose, Thessaly, the Quiet One, Clash, Spirit armour, Spirit ride

Same code, same conventions, same lang-parts rule (`kingdoms/lang_parts/spirits_<track>.json`, merged by the lead). Poses for
all of it already exist in `Poses` (AEGIS .. CLASH; `show` now packs 6 bits) with first-guess offsets in `SpiritArms.TABLE`.
The three placeholder spirits in `Spirits.ALL` become real classes; `SpiritSickness.roll` gives all five (heritage leans:
Tideborn/Verdant/Dawnborn → Ambrose, Sylvan/Nightborn → Thessaly, Stoneborn/Warriorborn → Varro, Emberborn/Earthling →
Grimsby, anyone else at random among the five; the Quiet One is 1 in 5 of the random ones and never a lean).

## Track E: Old Ambrose, the Warden (`spirits/Ambrose.java`) + Spirit armour and Spirit ride (`spirits/HandExtras.java`)
Ambrose (`ambrose`, colour `0xFF9BE08F`, size 2.0: a broad open hand twice yours behind the shoulder): warm grandfather
wizard, endlessly patient, calls you "little one". Ward: buffs, shields, healing; mostly acts on his own.
- **Aegis** (`aegis`, level 1, cost 12, cooldown 200): a shimmering ward on you for 10 s that soaks the next hit of 4+
  damage whole (`ALLOW_DAMAGE` cancel, `AEGIS` pose, a chime, `FLASH_WARD`), then fades.
- **Steady hands** (`steady`, level 2, cost 2/second toggle): while on, knockback on you is halved (`ALLOW_DAMAGE` can't
  do that: after the hit, scale the delta movement back by half in `AFTER_DAMAGE`) and your bow/crossbow draws aren't
  jittered: use `Mana`-free marker: set a `STEADY` map flag the client needn't know about; `HOLD`-like pose.
- **Battle blessing** (`bless`, level 3, cost 16, cooldown 400): strength, speed or resistance for 20 s by what you face
  (an armoured or big foe → strength; archers or many → speed; a caster or a creeper → resistance; use
  `Spirits.foeKey`), `BLESS` pose; from bond 20 he casts it himself when a fight starts (first hostile within 8).
- **Catch me** (`catch_me`, level 1, cost 10, cooldown 300): autonomous first: a fall of 8+ blocks is caught (fall damage
  cancelled, `CATCH_ME` pose, slow fall effect 3 s, "Easy now."), and standing in lava or fire he hauls you 3 blocks back
  the way you came and puts the fire out. Cast by key: the slow-fall for 10 s.
- **Mending touch** (`mend`, level 4, cost 3/second toggle; also autonomous below half health from bond 40): heals 1 every
  2 s through `PowersCompat.mend` (closes cuts, see Restoration), `MEND` pose; stops at full.
- `hurt` hook: Aegis on his own (bond ≥ 20, `AEGIS_EVERY` 600) against a hit that would take you below a third.
- Lines as the first two spirits have (all events, sick 1-5, advice.*, move names), warm and patient.
Spirit armour and Spirit ride are shared moves (every spirit) in `HandExtras.SHARED`, appended to `HandMoves.SHARED` from
`HandExtras.register()` (called from `Spirits.register()` after `HandMoves.register()`; track E adds that one line):
- **Spirit armour** (`armour`, level 6, cost 20, cooldown 300): the arms wrap round you like plate for 6 s: damage taken
  ×0.5 (`ALLOW_DAMAGE` can't scale; take the hit then heal back half in `AFTER_DAMAGE`, or cancel every other hit; pick
  the first), knockback immune, `ARMOUR` pose held (re-show every 15 ticks), `Spirits.show`.
- **Spirit ride** (`ride`, level 4, cost 14, cooldown 100): the hands carry you a short way in a leap: launch along your
  look (`RIDE` pose), 1.6 horizontal + 0.6 up, no fall damage from it (a `RIDING` map until you land), a whoosh.

## Track F: Thessaly the Learned (`spirits/Thessaly.java`)
Thessaly (`thessaly`, colour `0xFFFF9ED8`, size 0.6: a slim glove of light over your own hand): fussy scholar, lectures,
corrects your pronunciation of spells, "dear". Reach: things from far away, traps, utility.
- **Long reach** (`long_reach`, level 1, cost 6, cooldown 40): every item entity and experience orb within 24 blocks is
  pulled to you over 2 s (`PULL` pose), through walls.
- **Unseen hand** (`unseen`, level 3, cost 12, cooldown 160): the mob or player in sight within 16: a mob is disarmed (its
  main-hand item dropped at your feet, `STEAL` pose); a villager or Folk is pickpocketed (use `Crooks`/pickpocket code if
  Kingdoms has a pickpocket result to reuse, grep `Pickpocket`; else a random stack from an MCA villager's inventory or
  1-3 emeralds from Folk, and a `Theft`-style crime if seen: look at how `Theft.sorcery` reports crimes); a player loses
  the item in their off hand to your feet.
- **Rune trap** (`trap`, level 2, cost 10, cooldown 100): a glyph on the block in sight within 16 (`TRAP` pose, motes the
  attuned see, a faint END_ROD ring for all); the first foe to step on it (not allies, `HandMoves.ally`) is pinned 3 s as
  `HandGrip.pin` does and takes 4; lasts 60 s, at most 3 at once per player.
- **Unlock** (`unlock`, level 5, cost 16, cooldown 200): a locked loot chest in sight within 16 (`Lockpicking.locked`)
  opens if the spirit's level ≥ 2 × the lock's level (add a public `Lockpicking.pickBySpirit(ServerLevel, BlockPos,
  ServerPlayer, int spiritLevel)` that unlocks and gives the Stealth xp a pick would, or says the lock is beyond her),
  `UNLOCK` pose.
- **Juggle** (`juggle`, level 4, cost 8 per item, charges): tap: the arms lift up to 4 nearby item entities or small mobs
  (height < 1) into the air round you (`JUGGLE` pose, orbit them 2 blocks out, no gravity, as `HandGrip` holds); tap again
  to throw the next one along your look (`THROW_SPEED`), charge for speed. Uses `HandGrip`-style per-player state.
- Autonomous: `hurt` at bond ≥ 20: snatches an arrow (`HandMoves.catchIfReady` is already wired for every spirit); `tick`
  at bond ≥ 20: points out loot: once per `LOOT_EVERY` 1200 ticks, a chest within 16 you haven't opened (a `Set` of
  positions seen per player) gets motes and a line ("There's a chest behind that wall, dear. No, the other wall.").
- Lines, fussy and lecturing; move names.

## Track G: the Quiet One (`spirits/QuietOne.java`)
(`quiet`, colour `0xFFC8C8DC`, size: `size()` returns a different value each summon between 0.5 and 3.0, kept in a map by
player until the next summon). Says nothing: every `SpiritVoice.say` for kind QUIET is dropped (add the check in
`SpiritVoice`), except once at bond 100 (`quiet.word.1`: one word written in chat; a `SPOKEN` flag so it never repeats;
Andy: "Yes" to speaking once at max bond). Echo: repeats your own moves a beat later.
- **Echo strike** (`echo`, level 1, cost 6, passive toggle): while on, every melee hit you land is repeated 10 ticks later
  by the arms for 50% (`ECHO` pose), through `AFTER_DAMAGE` with a re-entry guard; costs 6 per echo.
- **Mirror step** (`mirror`, level 3, cost 10, cooldown 120): a dodge 3 blocks sideways/back along the sneak direction
  (or back), leaving a glowing double behind for 2 s that mobs target (a `Folk`-less decoy: an armour stand that's
  invisible with the arms drawn on it? Simplest: an `ArmorStand` marker tagged `jaymoskingdoms_mirror`, mobs targeting the
  player are retargeted to it, removed after 40 ticks), `MIRROR` pose.
- **Stillness** (`still`, level 6, cost 30, cooldown 1200; also on its own, rarely): for 3 s every mob within 8 is slowed
  (slowness 6, no attacking: target cleared each tick), projectiles in that sphere lose their speed, `STILL` pose; the
  screen flashes white (`FLASH_WARD`).
- **Silent end** (`silent`, level 8, cost 40, cooldown one in-game day: 24000): a single blow kills outright a foe in sight
  within 4 that's below 30% health (bosses excluded: `Enemy` with a boss bar, i.e. `EnderDragon`, `WitherBoss`, Kingdoms
  kings/giants: check `BossBars`), `SILENT` pose, then the arms go still.
- **Sense** (`sense`, level 2, cost 8, cooldown 200): for 10 s, hidden mobs within 24 glow for you (`setGlowingTag` is
  global; instead send motes at each one every 10 ticks, seen only by you: `level.sendParticles(player, ...)`), rune
  traps and locked chests within 16 get a mote ring, and ores within 8 (iron, gold, diamond, emerald, amethyst, copper)
  a single mote; `SENSE` pose.
- Autonomous: `hurt`/`tick` almost never: at bond ≥ 20, 1 in 40 chance per fight (first hostile within 8) that it finishes
  the fight: when a foe is below 30% within 6, `silent` on its own (its own cooldown), `FLASH_ACT`; and 1 in 60 per fight
  `still` on its own. Advice: none, ever. Idle remarks: none.
- Lines: only `quiet.word.1`, plus `sick.1 .. sick.5` for the Quiet One are blank whispers (" ..." style, five dots growing),
  and the move names. Every other event key may be omitted (SpiritVoice drops them).

## Track H: Clash (`spirits/SpiritClash.java`, `client/spirits/ClashCinematic.java`)
Andy: "two players' spirits lock arms; it uses the beam-clash film", All Might style. Built in Kingdoms alone (Powers'
`BeamClash` is beam geometry and its film reads Powers' attachment; not reachable cleanly).
- Two players with spirits out, within 4 blocks, who both cast **Grab** (or any strike move) at each other within 20 ticks
  of one another lock arms: both are rooted (position reset each tick, no input: the client ignores movement keys while
  `CLASH` is set), the `CLASH` pose on both, and a 5-second contest: every CAST tap (L) adds 1 + level/4 to your side,
  the spirit's level adds 0.2 a tick; at the end (or at a 40-point lead) the loser is thrown back 6 blocks and takes 8,
  the winner gets bond +3; a draw pushes both back 3. Server: `CLASH` attachment (synced to all: both ids, start time,
  the lead, −100..100) so viewers see it; a `Clash.Tap` payload isn't needed: reuse `Spirits.Key` CAST (the dispatcher
  routes CAST to the clash while one is on).
- Client `ClashCinematic` (model it on `HandshakeCinematic`): while the local player's `CLASH` is set: letterbox bars
  (12% top and bottom), the HUD hidden but for a centred tug bar showing the lead in both spirits' colours, a camera that
  swings side-on to the two (`view(...)` as the handshake's does; find how `HandshakeCinematic.view` is hooked into the
  camera and hook the same way), the screen shaking harder as the lead swings, and a flash in the winner's colour at the
  end. Nearby attuned viewers get the bars only.
- Lines: `clash.win.N`, `clash.lose.N` (3 each) for Grimsby, Varro, Ambrose, Thessaly (the Quiet One none) in
  `spirits_h.json`.

## Track I: the client (after E, F, G, H)
`SpiritArms`: hands for Ambrose (a broad open hand: a 6×6×2 palm with five 1×3×1 fingers spread), Thessaly (a slim glove:
the arm box deflated by 0.6 with three rings: 1-px-thick cube bands round the fingers), the Quiet One (smooth and
featureless: the plain arm box, no hand); tune `TABLE` for the new poses; the Quiet One's size from the synced state
(`SpiritState` gains nothing: the server sets `size` through a new `scale` field? No: the Quiet One's size is kept server
side only, so add `int scale` (×100) to `SpiritState` is track G's job: it adds the field (default 100) to the record,
CODEC and STREAM_CODEC, and `Spirit.size(SpiritState)` reads it; track I uses it). `SpiritHud`: the clash tug bar is H's;
the steady/armour/echo toggles show "on" after the move name. Lang keys in `spirits_i.json`.

## Leave a Hand (Kingdoms 0.7.277)

Andy (2026-10-27), linking Mage Hand (GPL-3.0, ideas only): "can this be implemented with our mana spirits?" A seventh
shared move, `leave_hand` (level 6, cost 15): the hand stays at a container (`spirits/TendedHand`, drawn by
`client/spirits/TendedHandRenderer`), gathers items (only the kinds inside if it holds any), guards it (monsters within 8),
and tends crops within 6 (`TendedHand.FARMS`). Hands: 1, 2 from bond 60, 3 from level 15. Tuning at the top of
`TendedHand`.
