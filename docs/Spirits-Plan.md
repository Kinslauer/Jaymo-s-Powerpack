# Mana spirits (Kingdoms 0.7.236)

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
waking cures it. One spirit for life; it can be released at a cost and no new one called. Key: a new keybind (L is the only
free letter). Attuned folk (court wizards, hermits) have spirits you can see and fight. The Quiet One speaks once at max
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
