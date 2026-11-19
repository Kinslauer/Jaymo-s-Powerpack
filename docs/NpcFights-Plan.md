# NPC fights: powered NPCs fight like anime fighters

**Andy (2026-11-19):** linked an Anime Clash video (YouTube `o-HULwH0OGc`, Minecraft anime NPCs fighting: Goku vs Broly,
Gogeta vs Saitama and so on): "i like the way these npcs fight, can our powered npcs be similar? id like to see a tyrant
warrior born and a galactic patrolman fighting this intensely later game". He ticked all 16 rows of the pick list
(https://claude.ai/artifact/RC1TV2nTdekjxETMhkp8og) and noted: **"they should be able to employ these techniques against
players as well."** Then: "send this to PC claude".

The video itself couldn't be watched from the cloud (YouTube blocked the download); the rows come from what that kind of
fight has: flight, dashes, vanishing, trading blows, beam struggles, big knockback. The video's mod is someone else's:
ideas only, our own code, sounds and art.

## Who this is for
- **Tyrants and raider-band men** (`ZFighterRogue`, kind `TYRANT`; raiders tagged `ZFighterRaiders.TAG` /
  `ZFighterHighSky.TAG`). Tournament champions get the moves too, scaled down by rank.
- **Galactic Patrolmen** (MCA villagers tagged `jaymospowers.patrolman`, `ZFighterPatrol`, also gifted `zfighter` BOLD).
- **Gifted villagers** (`core/gifted/GiftedAI` + each power's `*Gift` kit). The movement and defence rows (F2, F3, F6, F8,
  F16) apply to every power's gifted folk in that power's own flavour (a Wind user's dash is a gust, Smoke's vanish is a
  smoke step); the ki rows (F1 flight, F4, F5, F7, F9, F10, F13) are for Z Fighter gifted folk.
- **Against players as well as NPCs** (Andy's note): every move targets any `LivingEntity` foe, players included. A
  player's guard, parry and beam struggle must answer an NPC's blows and beams the same way they answer a player's.

## What we have today (read from master 2026-11-19, `3b16f0e7`)
- `ZFighterRogue`: blow strings from `ZFighterCombo.STRINGS`, finisher throw 2.2 + rush (`ZFighterImpact.rush`), 3-blast
  ki volleys every 70-100 ticks, a teleport step behind a foe 9+ blocks off every 100-140 ticks, Super 2 at half health
  (no build-up), flight only straight up to a foe above (`setNoGravity` when the foe is 2.5+ above) or in the high sky
  (`ZFighterHighSky.steer`). Targets: `Player`, `IronGolem`, `AbstractVillager` (tyrants only).
- `ZFighterPatrol#fight`: `moveTo(foe)`, one 7-damage punch every 16 ticks, 0.6 push. `foe()` = any `Enemy` not a player
  within 24, so patrolmen already hit tyrants they happen to be near. Never flies outside the pod.
- `ZFighterGift`: palm (100t), flurry (120t), blast (50t), flash step (110t, ground only), Wave Beam ultimate (12000t).
  `GiftedAI` thinks every 10 ticks, gap 20/30/45 by style, masters halve it.
- Player-only today: `ZFighterGuard` (guard/parry/counter), `BeamClash` (`ServerPlayer` API), `ZFighterKi` dash and
  air burst.

## The rows
| Row | Pick | Patch |
|---|---|---|
| F1 | Fight in the air: when either is airborne or knocked up, both take off; free 3D flight (hover, circle, dive), not just straight up | 1 |
| F2 | Dash and strafe: short ki dashes in, sidesteps, circling instead of walking straight at the foe | 1 |
| F3 | Vanish dodge: on an incoming blow or blast, blink behind the attacker leaving a fading afterimage, then strike; chance by rank, cooldown | 1 |
| F6 | Faster tempo for late-game fighters: think more often, smaller gaps with rank; weak early ones unchanged | 1 |
| F8 | Guard and parry for NPCs: crossed arms against a string, parry with sparks (`ClashFx`), counter; same rules as `ZFighterGuard` | 1 |
| F16 | Intensity grows with the game: everything scales with rank and world tier | 1 |
| F4 | Combo strings for patrolmen and Z Fighter gifted villagers (3-6 hits ending in a launcher or throw) | 2 |
| F5 | Launch and chase: finisher throws 8-15 blocks, attacker rushes after, follow-up mid-air or slam down | 2 |
| F7 | More ki attacks: short beam on a cooldown (not only the ultimate), seeking volleys, a visibly gathered charged blast | 2 |
| F9 | Trading blows: two fighters swinging in the same moment meet fist to fist: shockwave ring, sparks, both pushed back, again | 3 |
| F10 | Beam struggles with NPCs: an NPC beam meeting another beam (NPC or player) locks into `BeamClash`; NPCs push by rank/power level and surge | 3 |
| F11 | Impact you can see and feel: dust ring, ground cracks, a thrown body plows a trail, nearby players' cameras shake | 3 |
| F12 | Fights break soft blocks only (leaves, glass, dirt, sand, snow); never stone, wood or anything built; off near settlements and claimed land | 3 |
| F13 | Power up mid-fight: at half health back off, aura flare (rocks lift, ground shakes), come back stronger; patrolmen and Z Fighter gifted too | 3 |
| F14 | Tyrants and patrolmen are sworn foes: tyrant goes for any patrolman he sees, patrolmen hunt tyrants first | 4 |
| F15 | Late-game showdowns: from a high world tier a Patrol pod sometimes lands where a tyrant's raider band is | 4 |

## Patches (one PR each; 2 at a time)
Order: **1 and 4 together** (4 only touches targeting and spawning), then **2 and 3** once 1 has merged (both build on its
brain). Each patch: change note `changes/powers/<branch>.md` (and `changes/kingdoms/` if Kingdoms changes), never
`version=`/MODLOG/codemap, tuning in `static final` constants at the top of each class, compile-check, say in the PR it
has not been played.

### Patch 1: the fighter brain (F1, F2, F3, F6, F8, F16)
A shared brain every powered NPC runs, so tyrants, patrolmen and gifted folk fight the same way instead of three copies.
- New package `power/zfighter/brawl/` (or `core/brawl/` if the non-Z-Fighter gifted folk use it; pick one and say so in
  the PR). A `Brawler` state per fighting NPC (an `IdentityHashMap` or a non-synced attachment), ticked from
  `ZFighterRogue.customServerAiStep`, from `ZFighterPatrol`'s fight step, and from `GiftedAI` while it has a foe.
- **Rank** (F16): one number 0..1 per fighter: tyrant > raider > patrol rank (Rookie up) > gifted master > gifted >
  champion, raised by the nearest player's world tier (`ZFighterKingdoms.worldTier`). Every row below reads it: dash
  and vanish chance, guard and parry odds, think rate, gaps. At 0 a fighter behaves about as today.
- **Flight** (F1): a Z Fighter NPC (rogue, patrolman, Z Fighter gifted) whose foe is airborne, or who was knocked into the
  air, takes off: no gravity, steered with small accelerations (hover at the foe's height, circle at 3-6 blocks, dive in
  for a string), lands when the foe is grounded for a few seconds. Reuse `ZFighterHighSky.steer`'s idea, not its height
  rules. Never into the void or the build limit; `TerrainAhead`-style checks so they don't fly into walls. No fall damage
  while flying (they already skip it).
- **Dash and strafe** (F2): between strings, sidestep or circle (left/right at 0.4-0.6 b/t for a few ticks), dash in from
  6-12 blocks (`ZFighterKi.DASH_SPEED`-like burst with the cloud streak `ZFighterImpact.rush` draws). Gifted folk of other
  powers: their kit's own escape/approach move used more often, flavoured (a gust, a web zip, a smoke step).
- **Vanish dodge** (F3): when a player's or NPC's blow, ki blast or beam is about to land (hook the hurt event before
  damage, `ServerLivingEntityEvents.ALLOW_DAMAGE`, and cancel it), roll by rank (tyrant ~35%, rookie patrolman ~10%,
  cooldown 60-100 ticks): blink behind the attacker, an afterimage of the fighter left where they stood (a short-lived
  client effect: a translucent copy that fades over ~10 ticks; check how `ZFighterRogueRenderer` draws him), and the next
  string starts at once. Never against unblockable damage (fall, void, `/kill`).
- **Tempo** (F6): `GiftedAI.THINK_EVERY` and `GAP` scale down with rank (to 5 ticks and about half); rogues' `REST_TICKS`
  and `BLAST_EVERY` likewise. Rank 0 = today's numbers.
- **Guard and parry** (F8): an NPC under a string (its foe's `ZFighterCombo.MOVE` set, or a player swinging bare-handed
  within reach) raises crossed arms by rank: takes `ZFighterGuard.BLOCK_SHARE` of the damage from the front 150 degrees,
  drawn with the existing `ZFighterGuardPose` (it already draws "everyone who holds it up"; check what it reads). A timed
  parry (first blow inside the window) = `ClashFx.sparks`, the attacker staggered, the NPC counters. Guard drops after a
  few blows or a guard break, so it can't turtle forever. Players parry NPC blows exactly as today (their guard already
  works against any attacker; check it).

### Patch 2: strings, chases and ki (F4, F5, F7)
- **Combos** (F4): pull the rogue's string runner (`combat`, `beginBlow`, `strike`, `rushing`) out of `ZFighterRogue` into
  the brawl package so patrolmen and Z Fighter gifted folk throw the same strings (`ZFighterCombo.STRINGS`; a patrolman
  picks the longer ones as he ranks up). Replace `ZFighterPatrol#fight`'s single punch with it (keep his damage). Gifted
  Z Fighters get it as a new CLOSE move in `ZFighterGift` replacing palm+flurry, or alongside them.
- **Launch and chase** (F5): the finisher's throw scales with rank (2.2 today, up to about 1.5-2.0 b/t, so 8-15 blocks),
  then the rush (`ZFighterImpact.rush`); on arrival either a mid-air follow-up string (both flying, F1) or a hammer-blow
  down into the ground (`ZFighterImpact.launched` with downward push, the slam ring already exists).
- **Ki** (F7): tyrants and patrolmen get (a) a short beam on a 15-25 s cooldown: gather 10-16 ticks with a glow in the
  palms, a 20-block line (`ZFighterGift`'s Wave Beam code, shorter and weaker), registered for struggles in patch 3;
  (b) a seeking volley like the players' (`ZFighterKi` volley: 5 bolts that curve to the foe); (c) a charged blast:
  gathered visibly for ~20 ticks (a growing orb, `ZFighterOrbs`), big damage, dodgeable by a player who moves. All aim
  at players too.

### Patch 3: clashes and spectacle (F9, F10, F11, F12, F13)
- **Trading blows** (F9): when two fighters (NPC or player) land blows on each other within ~3 ticks, both hits are
  cancelled and turn into a clash: `ClashFx.sparks` at the midpoint, a shockwave ring (`ZFighterImpact`'s ring), a heavy
  boom, both pushed back 2-4 blocks (airborne in the air), screen shake near. Cap it (one clash per pair per ~2 s) so
  fights still progress. Players get it when their bare-handed blow meets an NPC's.
- **NPC beam struggles** (F10): `BeamClash` today takes `ServerPlayer`s (`BeamClash.java` ~164, 310, 327). Generalise the
  two sides to `LivingEntity` (players keep their tap-to-push and HUD; an NPC side pushes on its own: base push by rank
  and power level, random surges, a power-up jump near the end like `POWER_UP_JUMP`). Register NPC beams (patch 2's
  beam, `ZFighterGift`'s Wave Beam) the way `ZFighterWaveBeam` registers players'. NPC vs NPC and NPC vs player.
- **Impact** (F11): heavy blows and slams: dust ring, ground cracks (block-crack particles in a ring, or a short-lived
  decal if one exists), a thrown body leaves a dust trail along its flight, `Shakes.around` for players near. Reuse
  `ZFighterImpact`; don't add a second shake system (`core/Shakes` only).
- **Soft blocks** (F12): a body thrown by a finisher, or a slam, breaks the soft blocks in its path: a block tag
  `jaymospowers:brawl_breakable` (leaves, glass and panes, dirt, grass, sand, gravel, snow, ice, flowers, short plants);
  never stone, wood, anything else. Off within a settlement's land or a claim (check how other griefing features check
  Kingdoms' land; `PowersCompat`/tags) and when `mobGriefing` is off. Drop nothing for leaves/glass, normal drops for
  the rest.
- **Power-up** (F13): at half health (once per fight): back off 6-10 blocks, hover, 30-40 ticks of aura flare (rocks
  lift: falling-block-looking particles of the ground under him, ground shake, `Soundscape` hum rising), then the form
  goes up (tyrant: today's Super 2; patrolman and Z Fighter gifted: a +rank boost, faster tempo, brighter aura). A hit
  big enough during the build-up cuts it short (a player can interrupt it).

### Patch 4: rivals and showdowns (F14, F15) + lore
- **Sworn foes** (F14): tyrants (and raider-band men) target patrolmen: a `NearestAttackableTargetGoal` on `LivingEntity`
  filtered by the `jaymospowers.patrolman` tag, above villagers. Patrolmen pick a `ZFighterRogue` tyrant/raider over any
  other `Enemy` in `ZFighterPatrol#foe` (wider range for tyrants, ~48).
- **Showdowns** (F15): from world tier 3+ (constant; check what tiers mean in `WorldTiers` first and pick the late-game
  one), when a raider band lands (`ZFighterRaiders`) or a tyrant raid starts (`ZFighterRaids`), a chance (constant ~35%)
  that a Patrol pod comes down 40-80 blocks off a little later with one patrolman (a named one from the Patrol's list),
  who flies to the tyrant and fights him. Tidings/rumour line for nearby players (no screen text stating lore; see
  `lore-delivery`). The patrolman leaves in his pod once the tyrant falls; if the patrolman falls, the tyrant goes on as
  today. A player can join either side.
- **Lore**: add the paragraph below to Lore.md as `(new)`; Andy approves it in the thread.

## Lore placement (new, for Andy to approve)
> **Old enemies** (new; Claude's placement for Andy's NPC fight picks, 2026-11-19). The tyrants who land on kingdoms by
> night are Warrior-born, the same blood as the raider bands the Galactic Patrol chases from world to world, and the
> Patrol has a warrant out on every one of them. A tyrant who sees a patrolman goes for him before anyone else, and a
> patrolman near a raided town hunts the tyrant first. Once a world is strong enough to be talked about "out there",
> the Patrol sends officers after the raider bands themselves, and when one finds a tyrant their fight fills the sky:
> both trained in the same art, flying, trading blows and beams until one of them falls.

## Open questions (ask Andy only if they block)
- Does a dodge/guard/parry make tyrants too hard for mid-game players? Constants at rank 0 keep early fights as today;
  tune after he plays it.
- Soft-block breaking near player builds outside claims: off within 32 blocks of any bed/campfire? (Default in the
  plan: settlements, claims and `mobGriefing` only.)
