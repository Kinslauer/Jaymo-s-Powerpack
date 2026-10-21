# Cryokinesis (0.15.22)

Andy (2026-10-17) asked for an ice power whose synergy is "with smoke instead of fire". The plan is
`docs/Ember-Ice-Plan.md` ("Track B" and "Ice into smoke: freezing fog"). Power id `cryo`, display name
"Cryokinesis" (the plan's open question 2: "Cryokinesis" or plain "Ice"); colour `0x8FD3F5`; icon blue ice.
Server `power/cryo`, client `client/power/cryo`, lang `lang/parts/cryo.json`, textures from `tools/gen_cryo.py`.
No mixins.

## The kit
- **R, Ice Slide** (`CryoSlide`, client `CryoSlideClient`, the starter): a toggle (`cryo_slide`, synced to all). The
  client skates you like Smoke's and Wind's flight: its own sideways velocity, W pushes off along your look (0.03 a
  tick, Sprint 0.045), top speed 0.55 (Sprint 0.85; a fresh awakening 0.4 / 0.6), S brakes 0.04 a tick, nothing
  pressed glides on (0.993 a tick), the heading turns toward your look at 10 degrees a tick standing to 2.2 at top
  speed. Up and down stay vanilla's: you jump and fall as ever, and falls hurt. A wall at more than 0.45 b/t is a
  crash: `9 x (speed^2 - 0.45^2)` damage (`CryoNet.Crash`, server-judged, none in creative). Hunger 0.006 a tick plus
  0.014 a block; it ends at 6 food, R, in deep water, riding, gliding or dead (20-tick cooldown after).
  On land the lane is drawn flat on the ground for everyone (`CryoLaneClient`, 10 s, fading at the end); nothing solid
  is replaced. Water under you and 3 blocks ahead (3 wide) freezes into frosted ice that melts back to water 10 s
  later: a real bridge.
- **G (hold), Frost Breath** (`CryoBreath`): a cone 8 blocks long (0.3 wide at the mouth, +0.3 a block; a wall stops
  it), up to 5 s. What it catches: Slowness II, +5 freezing a tick (powder snow's `ticksFrozen`, up to 80 past frozen
  through); frozen through, it's held stiff (Slowness VII, Mining Fatigue, Weakness). Fire on creatures goes out.
  Every 4 ticks water surfaces along it freeze to ice (melting back after 30 s) and smoke along it turns to freezing
  fog. Hunger 0.02 a tick; cooldown 1.5-7 s by how long it blew.
- **V, Ice Shards** (`CryoShards`): five shards 8 degrees apart at 1.7 b/t. Each is a snowball wearing the
  `cryo_ice_shard` item (a look only, no recipe), tracked like Smoke's bomb; the path ahead is checked each tick so a
  creature on it takes 4 damage (thrown), Slowness II 3 s and +70 freezing, and the shard shatters. Smoke a shard
  passes freezes. Cooldown 4 s (3 s mastered).
- **B, Flash Freeze** (`CryoFlashFreeze`): the creature you look at (16 blocks, not through walls, at most 2 wide and
  3.2 tall) is shut in packed ice for 3 s, frozen through and held still. Built like Kingdoms' `FrostPrison`: the
  shell goes only into air and always melts, early if the prisoner dies. Smoke round it freezes. Cooldown 12 s (9 s
  mastered).
- **X, Ice Wall** (`CryoWall`, the shared power skill key, taken for a Cryokinesis user in `CryoPowerClient`): a wall
  of packed ice 5 wide and 3 high where you look (24 blocks), square across your look, standing on the ground there.
  It only goes into air, water sources, snow layers and plants (not double plants, not block entities), never into a
  creature, and melts after 15 s. Cooldown 10 s. Unlocks with the third ability.
- **Z, Snow mode** (`cryo_snow`, saved): Frost Breath and Ice Shards turn to snow: Slowness and Blindness, no
  damage, no freezing, no ice. Unlocks with Frost Breath.
- **C, Blizzard** (`CryoBlizzard`): 20 s, 16 blocks round you, moving with you. Snow drives through it (particles) and
  lies (three tries a tick at a snow layer on the top block, only into air on a full top face); every 10 ticks
  everything in it but you (and creative players) gets Slowness I and +20 freezing, burning things go out, and smoke
  in it freezes; mobs hunting you lose you unless within 5 blocks. Others inside see a whiteout
  (`CryoBlizzardClient`, from the synced `cryo_blizzard`), you a faint veil. Cooldown 2 min.
  **C answers once every ability is mastered**, not on ascension: no ascension exists yet (the plan's open question
  3 about fates is still open), so `Power` gained `ultimateAnswers(Player)` (default: ascended, or corrupted for
  `ultimateWhenCorrupted`), used by `Powers.ultimate`, `JaymosPowersClient` and `KeyGuide`; Cryokinesis overrides it.
- **Frost-born** (`ZFighterLineage.of(player) == ZFighterLineage.FROST`): freezing damage never hurts a Frost-born
  Cryokinesis user (`ALLOW_DAMAGE`); where it would snow (`WindCold.isCold`) the power is 25% stronger (breath reach
  and freezing, shard damage and freezing, Flash Freeze's hold and reach, the wall's time, the breath's ice); and the
  calling comes sooner.
- **The calling** (`core/Callings`): in powder snow, or under the open sky in a snowfall, freezing (a Frost-born, whom
  the cold can't freeze, just standing in it), for 60 s in a row (Frost-born 30 s): a day off the wait (Frost-born
  two), once a day.
- **No blocks left behind** (`CryoIce`): every block the power places is recorded with what goes back (water to water,
  anything else to air) and when; it melts then if it's still our ice (broken or built over, it's left alone), and a
  server stopping melts it all. Snow layers from the fog and the Blizzard are frost, not placed ice: they stay (and
  melt by light as any snow).

## Freezing fog (`power/smoke/SmokeField`)
- `SmokeField.freeze(level, at, radius)` marks the smoke cells there (anyone's) frozen for 20 s (`Field.frozen`,
  cell -> until). Called by Frost Breath (along the cone, every 4 ticks), each Ice Shard in flight (1.2), Flash Freeze
  (2.5 round the prisoner) and the Blizzard (its whole radius, every 10 ticks).
- In the step a frozen cell neither rises, spreads, drifts nor is drawn by a draught; it keeps its smoke (losing only
  0.08% a step); other smoke can still flow into it. `blow` (Wind users scattering smoke) leaves it be. A cell whose
  time is up, or whose smoke is gone, thaws.
- Every 10 ticks whatever has its feet or eyes in a frozen cell gets Slowness II and +30 freezing (up to 60 past
  frozen through), sparing the smoke's maker, every Cryokinesis user and creative players: so a Smoke user's cloud
  and an Ice user's breath together make a killing fog for everyone else.
- Every 20 ticks six random frozen cells drop a snow layer up to 4 blocks down onto the first ground: only into air
  over a full top face, nothing removed.
- Thawing: every 5 ticks a fire, lava, magma, a lit campfire or furnace in or right under a frozen cell (`SmokeHeat.hot`),
  or a burning creature in one (thawing 1.5 round it), thaws it at once with a hiss and steam. `SmokeField.thaw(level,
  at, radius)` is public: Emberborn's Kindle thaws along its flame every 5 ticks and the fireball's burst thaws its
  radius.
- Sync: the 6th byte of each synced cell got a second flag, `SmokeNet.Field.FROZEN` (2). `SmokeFieldClient` draws a
  frozen cell still (no bobbing or turning), pale ice-blue, each puff glinting white now and then (drawn at full
  light), drops a few snowflakes out of the fog near you, and tints your view frost-blue instead of grey in it.

## What to check first in game
1. Ice Slide on grass: W/Sprint/S feel, the lane drawn behind you (and on a friend's screen), hunger going down, a
   crash into a wall at full Sprint hurting (and not in creative), a fall off a cliff still hurting.
2. Skate onto a lake: the frosted-ice bridge forms ahead fast enough at full Sprint, and turns back into water 10 s
   later.
3. Frost Breath on a zombie until it's frozen stiff; on a pond (ice, then water again after 30 s); in Snow mode on a
   villager (slowed and blinded, never hurt).
4. Ice Shards: five in a fan, hitting, slowing; in Snow mode plain snowballs.
5. Flash Freeze on a mob: the shell holds 3 s and disappears; on a mob that dies in it, it melts at once.
6. Ice Wall on flat ground, against a hillside, over water and over tall grass; it never cuts into blocks and is gone
   after 15 s. Stop the server with a wall standing: no ice after the restart.
7. Freezing fog: a Smoke user's Smokescreen, then Frost Breath into it: the cloud stops moving, goes pale blue and
   glitters, mobs in it slow and freeze, snow settles under it, it thaws after 20 s. An Emberborn's Kindle (or a lit
   campfire under it, or a burning zombie walking in) thaws it at once.
8. Blizzard (fully mastered, or creative): the whiteout on a friend's screen, snow lying, mobs losing you.
9. A Frost-born Cryokinesis user in powder snow: no freezing damage.

## Left for later
- Fates (the plan's open question 3): no corruption or ascension yet; the Blizzard comes with mastery instead.
- No `docs/Abilities.md` rows yet. (Body poses for every move came in 0.15.40: `CryoPose`, from `CryoMoves`.)
- Other players walking on the land lane don't skate (only the slider does); it's a look.
