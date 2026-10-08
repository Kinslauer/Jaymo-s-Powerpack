# Web Slinger

Spider-Man-style traversal built around momentum. You swing on a real rope constraint, gain speed through the bottom
of each arc, and get a boost if you let go on the upswing. You keep that speed in the air until you land, and you can
fire again mid-air to chain swings. Three utility webs round out the kit. Accent colour `0xE0303A`; the icon is a
cobweb.

## Controls

| Key (default) | Ability | Cooldown |
|---|---|---|
| **R** (hold) | **Web Swing.** Sticks a web where you aim. If you aim at the sky or far below you, it auto-picks an anchor above and ahead of you. Hold to swing; release to let go. While swinging: **W** on the downswing pumps speed, **Jump** reels in, **Sneak** lets out rope. | none |
| **G** | **Web Zip.** Fires a web at the crosshair block (48 blocks) and reels you to it. Hitting a wall hops you up onto the ledge if there's one within 3 blocks. | 3 s |
| **V** | **Web Shot.** A fast, nearly flat web ball. A hit does 1 damage and gives Slowness IV and no jumping for 4 s. It also puts a temporary cobweb at the target's feet for 5 s (only in air). | 2 s |
| **B** | **Yank.** Webs the entity under the crosshair (24 blocks). Small things are flung to land about 2 blocks in front of you. Big things (bounding box over 2.6 m³, e.g. iron golems and horses) pull **you** to **them**. | 6 s |

**Passives:**
- You take no fall damage from the moment a swing web sticks, or a zip/yank-to starts, until you land. Landing means
  ground, water, lava, a ladder or vine, a vehicle, or creative flight. It also ends after 60 s at most.
- +3 safe fall distance at all times, through a permanent attribute modifier `jaymospowers:web_safe_fall`. It is
  removed when you lose the power.

**HUD:**
- A reticle of four corner ticks around the crosshair:
  - **White:** the crosshair is on an anchor.
  - **Pink:** an assisted anchor was found.
  - **Faint:** nothing in range.
  - **Red:** you're on a web.
- Three key chips under the crosshair for G, V and B. A red bar drains while each one cools down.

## How it works

- **Swing (client).** `WebSwing.tickSwing` runs at the end of every client tick, after vanilla has already moved the
  player.
  - It undoes vanilla's air drag (0.91 horizontal, 0.98 vertical) and applies its own lighter drag, plus a little extra
    gravity.
  - The rope is a distance constraint from the anchor to hand height (+1.2). If you're past the rope length, the
    outward velocity is removed and replaced by a springy pull back (rope elasticity). That turns falling into a
    pendulum arc, and the arc naturally speeds up at the bottom.
  - Release (`detach`) checks how steeply you're moving up. At 35° you get the full forward and up boost; the boost
    falls to 0 at ±35°. A good release plays a sweep sound and a cloud puff.
  - After any release, `tickFlight` keeps horizontal drag at 0.985 instead of 0.91 until you land.
- **Server role.**
  - The client sends `jaymospowers:web_swing` (attach + anchor, or detach).
  - The server checks that the anchor is within 52 blocks, next to a block with collision, and in line of sight (with
    a tolerance of 2.5 blocks, because the server position lags). If it fails, the server replies with
    `web_swing_reject` and the client lets go.
  - On a valid attach, the server sets the synced `jaymospowers:web_line` attachment so everyone can draw the line. It
    plays the thwip to everyone except the swinger and puffs particles at the anchor.
  - During the swing, `allowsFloating` and `skipsMovementCheck` return true. Fall distance is reset every tick, and
    `ServerLivingEntityEvents.ALLOW_DAMAGE` cancels fall damage until you land.
  - If the anchor block is broken, both the client and the server drop the web.
- **Zip / Yank-to.**
  - The server raycasts, then sends `jaymospowers:web_pull` (line anchor, foot target or entity id, max ticks, block
    face).
  - The client steers your velocity onto the line at up to 1.8 blocks/tick. It stops when you arrive, the time runs
    out, or you're stuck for 3 ticks.
  - At the end it hops: onto the ledge for a wall hit, a small settle for a top face, or a soft stop otherwise.
- **Yank-in (server).** Sets the target's velocity on a rough ballistic arc toward you and flags `syncVelocity`, so
  players get the push as well. It also stops the mob's pathing.
- **Web Shot.**
  - `WebBall` is a `ThrowableItemProjectile` drawn with `ThrownItemRenderer` using the `jaymospowers:web_ball` item
    sprite (drawn by `tools/gen_web.py`).
  - On hit: Slowness IV, plus a transient `JUMP_STRENGTH ×0` modifier. Neither is saved, and the modifier is removed
    after 4 s.
  - It also places a cobweb at the target's feet if that block is air. The cobweb is removed after 5 s. All
    remaining temporary cobwebs are removed on server stop. Breaking one by hand drops nothing (no string farming).
- **Line rendering.**
  - `WebLineRenderer` hooks Fabric's `LevelRenderEvents.COLLECT_SUBMITS`. It submits `RenderTypes.lines()` custom
    geometry, which is the same pipeline the vanilla fishing line uses.
  - Each line runs from the hand to the anchor in 12 segments, 1.4× vanilla line width, near-white. Six short
    strands form a splat at the anchor.
  - Your own line comes from local state, so it appears the same frame you fire. Other players' lines come from the
    synced attachment; yank lines follow the entity.

## Tuning constants

**Swing feel** (`client/power/web/WebSwing.java`, top of the file):

| Constant | Value | What it does |
|---|---|---|
| `ATTACH_SLACK` | 0.94 | The rope starts at this fraction of the anchor distance. Below 1 gives a tug on attach. |
| `ROPE_MIN` | 3.0 | The shortest you can reel in to. |
| `ROPE_SPRING` / `ROPE_MAX_PULL` | 0.3 / 0.7 | Rope elasticity: how hard an over-stretched rope pulls back (per block of stretch, per tick), and the cap on that pull. |
| `SWING_GRAVITY` | 0.025 | Extra gravity while swinging, on top of vanilla's 0.08. More gives faster, heavier arcs. |
| `SWING_DRAG` | 0.993 | Velocity kept per tick on the web. |
| `PUMP` | 0.03 | Speed added per tick by holding W on the downswing. |
| `REEL_IN` / `LET_OUT` | 0.45 / 0.35 | Rope change per tick with Jump / Sneak. |
| `MAX_SPEED` | 2.6 | Speed cap in blocks/tick (sprinting is about 0.28). |
| `RELEASE_SWEET_ANGLE` / `RELEASE_WINDOW` | 35° / 35° | The perfect release angle, and the window either side of it. |
| `RELEASE_FORWARD` / `RELEASE_UP` | 0.35 / 0.5 | The boost from a perfect release. |
| `FLIGHT_DRAG` | 0.985 | Horizontal velocity kept per tick after a release (vanilla is 0.91). Lower it if flight feels too floaty. |
| `ZIP_STEER` / `PERCH_FORWARD` | 0.55 / 0.22 | How tightly a zip follows its line, and the push onto a ledge. |

**Anchors** (`power/web/WebAnchors.java`):
- Ranges: `SWING_RANGE` 48, `ASSIST_RANGE` 40.
- `DIRECT_MAX_DROP` (4): a crosshair anchor further than this below your eyes is skipped in favour of an assisted one.
- The assisted search fan (`ASSIST_ELEVATIONS`, `ASSIST_YAWS`) and its preferences: 52° elevation, 20 blocks away,
  at least 3 blocks up and 6 blocks out.

**Server** (`power/web/WebPower.java`):
- Cooldowns: zip 60, shot 40, yank 120 ticks.
- `ZIP_RANGE` 48, `ZIP_SPEED` 1.8.
- `YANK_RANGE` 24, `YANK_HEAVY_VOLUME` 2.6.
- `SHOT_SPEED` 2.4.
- `SAFE_FALL_BONUS` 3.

**Web Shot** (`WebTraps`, `WebBall`):
- Snare: 80 ticks at Slowness IV (amplifier 3); the cobweb lasts 100 ticks.
- Damage 1, gravity 0.012.

## Testing

1. Run `/power set @s web`.
2. Survival, in a forest or a village with tall buildings:
   1. Jump off something, hold **R** while aiming at a tree or wall above, and let go on the way up. Chain swings.
   2. Look straight ahead over open ground near tall terrain and hold R. The reticle should turn pink and you should
      swing from an assisted anchor.
   3. Land after a long swing and check that you took no fall damage.
3. Watch the reticle: white on a block, pink when assisted, faint over open sky.
4. **G** at a wall top: you should zip and hop onto the ledge. **G** at the sky should only "fwip", with no cooldown.
5. **V** at a zombie: it should get Slowness, be unable to jump, and have a cobweb at its feet that vanishes after
   5 s. Break that cobweb by hand: no string should drop.
6. **B** at a pig: it flies to you. **B** at an iron golem (`/summon iron_golem`): you fly to it.
7. Multiplayer (dedicated server, second client):
   - The other player sees your web line while you swing, zip or yank.
   - Nobody gets kicked for flying or rubber-banded during long swings.

`/give @s jaymospowers:web_ball` exists only as the projectile's sprite; the item does nothing.

## Known limitations / upgrade ideas

- The line is a screen-space line from the `lines` pipeline: constant pixel width, unlit, and it doesn't sag. A
  textured ribbon would look better up close.
- There's no swing animation. The arm doesn't rise; only the line shows the swing.
- The assisted search uses the look direction only. It could favour the direction you're travelling at high speed.
- A temporary cobweb could outlive a server **crash** (a normal stop removes them).
- Yank-in on another player relies on the knockback-style velocity packet, so it works only as well as knockback does.
- Upgrade ideas:
  - Web-swing tricks (flips on release).
  - A wall-crawl passive.
  - Web zip onto entities.
  - A web "net" area shot.
  - A charged double-web launch, like the video's slingshot.

## Two webs (0.1.1)
- **R** is the main-hand web. **G** is the off-hand web while the off hand is **empty**; with something in the off
  hand, G is Web Zip as before.
- **Ropes:** each web has its own rope and key, so you can hang from two anchors at once or alternate hands.
  - Jump reels in, and sneak lets out, every web that's out.
  - With two webs the constraints run twice per tick, so they settle against each other.
  - The release boost only fires when the last web lets go.
- **Off hand:** putting something in the off hand drops the off-hand web. The client and the server both check this.
- **Sync:** the off-hand line is synced as `jaymospowers:web_line_off`. The `web_swing` packet carries the hand
  (0 main, 1 off), and `web_swing_reject` says which hand to drop.
- **Body (`WebArms`):** posed the same way as Jaymo's Kitchen's corpse ragdolls: spring state per player, then render
  state, then model parts by name after `setupAnim`.
  - Each arm springs toward its anchor (`ARM_SPRING` 0.35, `ARM_DAMPING` 0.55), so a fresh web snaps the arm up from
    the side.
  - The legs hang limp, trailing the swing and flopping sideways (`LEG_SPRING` 0.22, `LEG_DAMPING` 0.78,
    `LEG_TRAIL_PER_SPEED` 0.9).
  - The web line starts at the posed hand: shoulder + arm direction × 10 px. In first person it still comes from the
    screen corner of that hand.

## Slingshot and web wings (0.1.2)
- **Slingshot:** with both webs stuck, hold **Sneak** to brace in place, then hold **Jump** to draw back.
  - A full draw takes `SLING_DRAW_TICKS` 30 and pulls you back `SLING_PULLBACK` 2.5 blocks from the webs.
  - Letting Jump go fires you between the webs, blended `SLING_AIM` 0.4 toward where you look.
  - Launch speed is `SLING_MIN_SPEED` 0.9 at no draw and `SLING_MAX_SPEED` 3.4 at a full draw. The extra speed bleeds
    back to the normal cap (1%/tick).
  - Releasing Sneak first cancels the shot. A draw below `SLING_MIN_DRAW` doesn't fire.
- **Web wings:** hold **Jump** in mid-air while falling with no web out, for `WINGS_HOLD_TICKS` 6.
  - The wings spread for `WebPower.WINGS_TICKS` 80 (4 s), then a 3 s cooldown (`WINGS_COOLDOWN` 60). They fold early if
    you land, swim, or shoot a web.
  - Glide: sink `WINGS_SINK` 0.07 b/t. Looking down dives (`WINGS_DIVE`) and turns height into speed
    (`WINGS_DIVE_GAIN`), between 0.45 and 1.8 b/t. The path steers toward your look (`WINGS_STEER`).
  - No fall damage until you land.
  - Others see your arms spread and a web membrane from hand to hip. The `web_wings` packet and attachment carry this.

## Fall damage (0.1.2, by request: fall damage always applies outside creative)
- Removed: the "no fall damage until you land" grace and the +3 safe-fall passive. The passive is cleared from anyone
  who still has it.
- Falls during and after swings count from your highest point (the core rule).
- Web wings follow the elytra rule: gliding slower than 0.5 b/t down isn't a fall.

## 0.1.4
- **Web blast (V):**
  - Hold to charge for up to `SHOT_CHARGE_TICKS` 60 (3 s), release to fire. It bursts like a splash potion wherever it
    lands.
  - Damage: direct hit 1 → 10 by charge; things in the splash take up to half that, scaled by distance.
  - Splash radius 1.75 → 4.5. Everything in it gets Slowness III (amplifier 2) for up to 60 → 220 ticks, plus a
    temporary cobweb at its feet and the jump lock.
  - Cooldown 2 → 8 s by charge. The chip shows a charge bar.
- **Glide lean:** the body leans forward up to 70° over the first 1.5 s of a glide (smoothstep), with the head up.
  It's drawn by `WebLivingEntityRendererMixin`.
- **Wing rules:**
  - The wings only open after a fall of more than `WINGS_MIN_DROP` 2 blocks from the apex.
  - There's no cooldown, but each fold needs Jump released and another 2-block fall.
- **Slingshot:**
  - The aim is now 80% look direction.
  - After a launch, held web keys are dropped and new webs are blocked for `SLING_LOCKOUT` 12 ticks, so the launch
    can't be snagged.
- **Unlock order (natural awakening):** R, G, B, then V. The charged blast comes last.

## Yank tether (0.1.7)
- **Holding B:** after the yank, the web stays stuck to the target like a lead (`jaymospowers:web_tether`, seen by
  everyone).
  - Past `TETHER_LENGTH` 4 it's pulled back toward you. The spring is `TETHER_SPRING` 0.25, capped at
    `TETHER_MAX_PULL` 0.9 b/t a tick, so you can drag it or let it hang under you while you swing.
  - The line is drawn from the main hand when that hand has no swing web.
- **It lets go when:** you release B.
- **It snaps when:**
  - it's stretched past `TETHER_BREAK` 16;
  - the target damages anything;
  - a tethered player swings their arm.
- **Big targets** (volume > 2.6) still pull you to them, as before.

## Hitching and ragdolls (0.1.8)
- **Hitching:** releasing B while aiming at a block within `HITCH_REACH` 24 ties the held creature to that block
  (`WebHitches`).
  - The line keeps the length it had at release, clamped to 1.5–16.
  - The creature's own movement drops to 15% (a transient attribute modifier) and its pathfinding is cancelled. Past
    the line's length it's pulled back (spring 0.3, max 0.9 b/t).
  - The line breaks when the block goes, the creature hits anything, it's wrenched more than `SNAP_OVER` 6 past the
    line, or after 60 s.
  - It's synced as `web_hitch` (the anchor) so everyone sees the line.
- **Ragdoll (`WebRagdoll`):** creatures held by Yank (`web_held`) or hitched go limp.
  - The body tips along the drag direction, up to 55°.
  - Every limb the model has flops on its own beat, the same approach as the kitchen's corpse Jolt. It's stronger the
    faster they're dragged, and the walk cycle is suppressed.
  - It's drawn by a `LivingEntityRenderer` mixin, so it works on any mob.
- **Client smoke test:** 78 mods load and the client joins a world with no mixin errors.

## Swing feel (0.1.9)
- **No tug on cast:** `ATTACH_SLACK` is 1.0, so a new web holds you at the distance it stuck at. Jump reels in; Sneak
  still lets line out.
- **Jump during a swing:** reels in and thrusts along the arc (`JUMP_THRUST` 0.07 b/t per tick, square to the webs,
  up to the 2.6 cap). Holding it rockets you round the swing; releasing flings you out with the speed.
- **Swing sway (`WebArms`):** hanging from a web, the body swings from the shoulders like a pendulum, with the legs
  trailing behind the motion.
  - Same spring as Wind's hover: `SWAY_SPRING` 0.16, `SWAY_DAMPING` 0.86, up to `SWAY_MAX` 0.6 rad.
  - It's driven by position deltas, so it works for other players too.
- **Posed geometry:** `WebArms.posed()` moves shoulder, hand and wing points through the same lean and sway as the
  rendered body. The web lines and the wing membrane follow the leaning, swinging body, and the arms aim within it.

## Web Zip on its own key (0.1.10)
- Web Zip moved to **Z**: `key.jaymospowers.web_zip`, under Controls → Powers, sent as the `web_zip` packet. It
  unlocks with the second ability (G).
- G is now only the off-hand web (it needs an empty off hand).
- **HUD row:** wings · G · V · B · Z. G's chip dims while the off hand is full.

## 0.2.0: Ascension & Corruption

The web power doesn't use the Fate corruption meter. Its dark path is the **symbiote**, a separate system with its
own bond, hunger and rejection. Nothing below is explained in game; the only hints are one-line whispers in the
action bar, each heard once.

### The symbiote

**Where it's found: meteor craters** (`WebCraterFeature`, feature type `jaymospowers:web_crater`).
- Data files: `data/jaymospowers/worldgen/feature/web_crater.json` and `.../placed_feature/web_crater.json`. Both
  are written by `tools/gen_web.py`.
- Placement: `rarity_filter` 1 in `CRATER_RARITY` = 500 chunks, then `in_square`, `heightmap WORLD_SURFACE_WG` and
  `biome`. The feature refuses many sites, so the effective rate is roughly 1 in 600 or more.
- Added with `BiomeModifications` (VEGETAL_DECORATION) to `foundInOverworld()` biomes, except oceans, deep oceans,
  rivers and beaches.
- Shape: a bowl of radius `MIN_RADIUS` 3.5 to `MAX_RADIUS` 5.5 (7�11 across), `DEPTH` 3 at the middle, with ragged
  edges.
  - The floor is blackstone and coarse dirt, with magma and obsidian toward the centre.
  - It has a `RIM_WIDTH` 1.5 lip of debris and a `SCORCH_WIDTH` 2.5 ring of coarse dirt fading out.
  - A 3�3 seat of magma and obsidian sits at the middle, with the goo on it. Sneaking (which bonding needs anyway)
    keeps the magma from burning you.
- The site is refused if:
  - any column has fluid at or just above the ground;
  - the ground under the bowl or lip varies more than `MAX_SLOPE` 3 from the centre;
  - anything in the dig volume isn't natural ground (dirt, sand, stone, terracotta, snow, gravel, clay, ice, plants
    or leaves). Logs, planks, paths, cobblestone and block entities all refuse it, which keeps craters out of
    villages, ruins and the middle of trees.
- **The goo** (`jaymospowers:web_symbiote_goo`, "Black Ooze"):
  - Unbreakable, can't be pushed, and drops nothing. It's an animated 8-frame black texture with a drifting purple
    sheen.
  - Its client `animateTick` gives off dark motes, plus a little smoke and ash around the crater.
  - It has no item; use `/powerdebug web crater` to make one.

**Bonding:** a web slinger **sneaks and right-clicks** the goo with any hand contents (`UseBlockCallback`).
- The block is consumed. Over `BOND_TICKS` 50 the goo spirals up your body (black dust, squelches), with Darkness and
  Nausea, ending on a heartbeat and a sculk bloom. Whisper: "Something answers."
- Anyone else (or a web slinger who isn't sneaking, or one already bonded) makes it **recoil**: a squelch, ink, and
  nothing else.
- State is the `jaymospowers:web_symbiote` attachment (`WebSymbioteState`): bonded, satiety, stress, rejections, flags
  (suit, armor, crawl) and hints. It's persistent, kept through death, and synced to everyone.
- Cooldowns are in `web_symbiote_cd`, synced to the owner only.
- Losing the web power drops the bond.

**While bonded:**
- **Suit:** `WebSymbioteLayer` draws an original 64�64 texture over the whole player model: mottled black, dark purple
  vein walks, and white angular eye-patches. It covers the skin's outer layers too.
  - Everyone sees it. It hides while invisible.
  - The little mask in the middle of the radial menu toggles it (`SUIT_TOGGLE`). With the suit off, every ability
    and enhancement still works, but you and your webs look normal.
- **Black webs:** swing, zip and yank lines and the wings render near-black, with a purple sheen running along them
  (`WebLineRenderer.sheen`).
- **Enhanced:** �`REACH_SCALE` 1.5 reach for swing, the assist, zip, yank and hitch; �`PULL_SCALE` 1.25 for the rope
  spring and max pull, reel-in, jump thrust and pump; Zip cooldown �`ZIP_COOLDOWN_SCALE` 0.5.
- A faint black drip of particles every 30 ticks.

**Radial menu:** hold **X** (`key.jaymospowers.web_symbiote`), point, and release (or click).
- `WebSymbioteScreen` is a non-pausing screen. It releases on key-up (`keyReleased`, plus polling the physical key).
- It has six tiles clockwise from the top, each with an icon, a cooldown shade with seconds, and a lit strip for
  toggles that are on.
- The middle shows the pick's name and a satiety bar, which flashes red when starving. Under it is the suit mask
  toggle. The dead zone (`DEAD_ZONE` 24) picks nothing.
- The server checks everything: bond, cooldown and satiety cost (`WebSymbiote.use`).

| # | Ability | Cooldown | Cost | What it does |
|---|---|---|---|---|
| 0 | Tendril Grab | 100 | 2 | Pulls the creature under the crosshair within `TENDRIL_RANGE` 20 to you. Things over `TENDRIL_HEAVY_VOLUME` 8 pull you to them instead. Aimed at a block, it zips you there. |
| 1 | Living Armor (toggle) | 40 | 0 | +`ARMOR_ABSORPTION` 16 max absorption (8 hearts), regrowing `ARMOR_REGEN` 1 every `ARMOR_REGEN_TICKS` 40, and +0.6 knockback resistance. Satiety drains �`ARMOR_DRAIN_MULT` 3. |
| 2 | Wall Crawl (toggle) | 20 | 0 | Client movement (`WebSymbioteClient.tickCrawl`). Next to a wall in the air, you stick; Jump or forward climbs at `CRAWL_CLIMB` 0.2, Sneak goes down. Under a ceiling, Jump sticks you to it, you move with the movement keys, and Sneak drops you. Climbing over a top edge hops you onto the ledge. The server allows floating while it's on and resets fall distance while you're touching a wall or ceiling. |
| 3 | Consume | 160 | 0 | Swallows a non-player creature with max health = `CONSUME_MAX_HEALTH` 10 within `CONSUME_RANGE` 3, preferring the one you're looking at. It's removed with no drops. Heals 6, feeds +30, calms -10. Tamed and name-tagged creatures are never eaten. |
| 4 | Spike Burst | 240 | 8 | Everything within `SPIKE_RADIUS` 5 takes `SPIKE_DAMAGE` 8 (player attack) and is thrown `SPIKE_KNOCKBACK` 1.3 outward and 0.45 up. Rays of black dust. |
| 5 | Shroud | 600 | 5 | Invisibility (no particles) until you damage anything, or `SHROUD_TICKS` 400. |

**Hunger:**
- Satiety runs 0�`SATIETY_MAX` 100 and starts at 70. It loses 1 point every `SATIETY_DRAIN_TICKS` 240 (about 20
  minutes from full), and not in creative.
- **Raw meat** (beef, pork, chicken, mutton, rabbit, cod, salmon, rotten flesh) feeds it `MEAT_FEED_PER_NUTRITION` 5
  � nutrition instead of you (`WebFoodPropertiesMixin`). Its other effects, such as raw chicken's Hunger, still
  apply.
- At or below `STARVING` 15, it eats your hunger bar: 1.2 exhaustion every 40 ticks, doubled at 0. Stress climbs 1
  every 80 ticks while starving and calms 1 every 100 ticks while fed.

**Takeover:**
- When starving with stress = `TAKEOVER_STRESS` 50, every `TAKEOVER_CHECK_TICKS` 200 there's a
  `TAKEOVER_CHANCE` 35% chance. It's at most once per `TAKEOVER_COOLDOWN` 1200, and never in creative.
- It picks the nearest mob within 12 (not tamed or name-tagged; never players) and takes over for 60�100 ticks.
- The client wrenches your view and drags you at the mob (`TAKEOVER_SPEED` 0.34, hopping over steps), with a pulsing
  dark vignette.
- The server swings and attacks with `player.attack` every 10 ticks when within reach 3.
- Afterwards stress drops by 20.

**Weaknesses ? rejection:**
- **Fire:** being on fire or in lava counts as **one** exposure per burning episode. An episode ends after
  `FIRE_EPISODE_GAP` 60 ticks without fire.
- **Noise** (`WebServerLevelMixin` on `ServerLevel.gameEvent`):
  - a bell ringing (`block_change` at a bell) within 16;
  - `note_block_play` within 8;
  - `instrument_play` (goat horn) within 24;
  - `explode` within 16 (this includes firework bursts).
  - After one noise counts, more noise doesn't for `NOISE_GRACE_TICKS` 100.
- **Each exposure:** 3 magic damage, Nausea 4 s, Slowness II 2 s, +20 stress, a jolt, ink, a squelch and a warden
  growl. +1 rejection.
- At `REJECT_AT` 6 rejections it **rejects you**:
  - an ink and black-dust explosion, a death squelch, the elder-guardian curse sound, 3 damage, Nausea and Weakness 30 s;
  - then a black blot slithers away across the ground for 2.5 s and vanishes in smoke.
  - The bond is gone. You need another crater's goo, and any crater's goo works for a rejected player, since bonding
    only needs "not bonded".
- **Whispers** (each once per player): first noise "It shudders at the sound.", first fire "It writhes from the
  heat.", one exposure from rejection "It's had almost enough of you.", first starving "It's hungry.", first meat
  "It took that for itself.", takeover "Not you. It.", rejection "It tears itself away.".

### Ascension: Spider-Sense
- **Trigger:**
  - The server adds up distance travelled while airborne with a swing web stuck, from per-tick position deltas.
    Steps over 6 blocks (teleports) are ignored.
  - The total is kept in `jaymospowers:web_swung` (persistent, kept through death) and flushed every 20 ticks.
  - At `ASCEND_DISTANCE` 2000, if `Fate.canAscend`, it calls `Fate.ascend`.
  - Whispers at 50% and 85% (not once ascended).
- **Spider-Sense:**
  - Every `SENSE_TICKS` 10, the server sends the client the ids of mobs within 16 that are targeting you (`web_sense`).
  - The client (`WebEntityRendererMixin`, at the TAIL of `EntityRenderer.extractRenderState`) gives those a red
    outline, plus swelling creepers and skeletons drawing a bow within 16. The outline shows through walls.
- **Dodge:** the first hit from an entity (melee or projectile; nothing that bypasses invulnerability) every
  `DODGE_COOLDOWN` 400 ticks is cancelled (`ALLOW_DAMAGE`). You get a sidestep nudge square to the attack
  (`web_nudge`), an amethyst tingle, and sparkles.
- **Web Strike (C, `onUltimate`):**
  - It zips you to the living entity under the crosshair within `STRIKE_RANGE` 32, as a Yank-to pull.
  - When you're within 2.2 of the target's edge, the kick deals `max(STRIKE_MIN_DAMAGE 10, best speed of the last 3
    ticks � STRIKE_DAMAGE_PER_SPEED 8)`, with `STRIKE_KNOCKBACK` 2.2 out and 0.55 up. You rebound off it.
  - Cooldown `STRIKE_COOLDOWN` 160 (8 s), started on cast. It shows as a gold-rimmed chip with the bound key at the
    end of the bottom-right row.
- Lang: `.ascended` "Spider-Sense", `.key.ultimate` "Web Strike".

### Testing
- `/power set @s web`
- **Crater:** `/powerdebug web crater` carves one where you stand. Sneak and right-click the goo: you bond. Try again
  without sneaking, or as another power: it recoils.
  - Worldgen: make a new world, then `/locate` won't find it (it's a feature). Fly around plains or desert, or use
    `/place feature jaymospowers:web_crater` (that bypasses rarity but not the site checks).
- **Bond directly:** `/powerdebug web symbiote bond`. Check the suit in F5, the black webs, and longer reach.
- **Radial:** hold X, point, release. Test each ability on pigs, chickens and a zombie (Consume needs a chicken,
  rabbit or similar).
- **Hunger:** `/powerdebug web symbiote` prints the state. Eat raw beef: satiety rises and your hunger doesn't.
- **Noise:** ring a bell, play a note block, blow a goat horn, light TNT. Each counts once per 5 s. The 6th exposure
  rejects you. `/powerdebug web symbiote reject` forces it.
- `/powerdebug web symbiote feed` fills satiety and clears stress.
- **Takeover:** stay starving with stress = 50 (exposures add 20 each), then wait near a mob.
- **Ascension:** `/powerdebug web swung 2000` (needs all 4 abilities mastered; `/power level @s 4`). Or
  `/power ascend @s`.
  - Then let a zombie target you: red outline. The first zombie hit misses with a sidestep.
  - Press C at a mob: zip and kick.

### Risky / untested at runtime
- **Mixins:** `ServerLevel.gameEvent`, `FoodProperties.onConsume` and `EntityRenderer.extractRenderState` all have
  `require 1`. They were checked against the decompiled 26.3 sources but never loaded in game.
- **Worldgen:**
  - How rare craters really are after site refusals, and whether the `WORLD_SURFACE_WG`/`OCEAN_FLOOR_WG` ground
    finder behaves at chunk edges.
  - The feature only writes within �9 blocks of a point inside its chunk, which should stay inside the
    neighbouring-chunk write window.
  - Craters on snowy slopes or in badlands may look odd.
- **Rendering:**
  - The suit's look on slim (Alex) models, and whether the outer-layer copy z-fights.
  - The suit isn't drawn on the first-person arm.
  - Whether the cutout layer respects armor (it draws under armour, like a skin).
- **Spider-Sense outline:** relies on the vanilla outline pass turning on when any submitted model carries an outline
  colour.
- **Wall Crawl:** the feel of the climb, ceiling clinging, and corner cases (fences, slabs, trapdoors) are unknown.
  It can also make wall-adjacent jumps "sticky" while it's on.
- **Radial:** the key-up poll uses `InputConstants.isKeyDown(scancode)`. A mouse-button binding falls back to
  click-to-choose.
- **Takeover:** steering fights the player's own mouse each tick. It may feel jerky on high-FPS clients.

### Known gaps / upgrade ideas
- Feeding when your own hunger bar is full isn't possible: vanilla won't start eating.
