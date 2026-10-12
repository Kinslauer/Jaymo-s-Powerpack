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

## Fewer takeovers (0.8.34)
The symbiote takes the wheel only for these reasons (plus the near-death last-ditch mode, unchanged):
- **Starving** (satiety 15 or less): it gorges, taking over to hunt and kill, then eating, kill after kill, until it's
  back to `GORGE_FULL` (80). Then it's sated: no hunger drain for `SATED_TICKS` (5 minutes). Only starving hunts;
  merely hungry, it still eats a corpse in reach or in your arms without taking over.
- **A look** (`CURIOUS_CHANCE`, now 3% per check): grabs something with a tendril, stares, decides, gives control back.
- **Neglect** (bond under `NEGLECT_BOND`, 25): up to `NEGLECT_CHANCE` (5% at bond 0, falling to none at 25) it says
  one of four lines about being ignored and goes off tearing into nearby creatures (not players).
- Gone: the armour-worn-too-long rampage, the old weak-bond takeovers (12% per check up to bond 60), and the starving +
  stressed random grab.
- Full bond: Living Armor (the big body) no longer withdraws after 5 s unused; it stays until you put it away.
- Death: the respawned player is reset to no bond, no suit, no armour (`AFTER_RESPAWN`), on top of the
  death-time withdraw.

## Two-handed Zip (0.8.16)
A Web Zip shoots from both hands at the one target (two lines, both arms aimed). Once it catches
(`WebArms.ZIP_SHOOT_TICKS`), both hands haul back on it and sweep behind you, trailing opposite your flight and a little
down, while the lines still run from them to the target. Everyone sees it; in first person both lines leave the screen
corners. Works off the `WebLine.ZIP` line, so a Yank's pull is unchanged.

## Swing tricks (0.8.9)
The slinger's client knows when a web lets go and when the feet land, so it picks a trick and asks the server
(`WebTricks.Trick`), which checks it and starts a `WebPose` everyone sees. Your own tricks are hidden in first person.
Tuning is at the top of `client/power/web/WebTrickClient`; the shapes are in `WebBodyPose`.
- **Off a released web** (moving 0.5+ b/t): tucked **front flip**, laid-out **corkscrew**, aerial **cartwheel**
  (thrown upward favours the front flip). Never the same one twice running.
- **On the web:** through the bottom of a fast arc (30%) a **twirl** round the line or a **giant swing** over the
  hand; when the next web catches mid-air (40%) a twirl.
- **Landing** (after 8+ ticks in the air off a web or the wings): a 2.5+ block drop lands **three-point** (superhero
  landing); fast across the ground, a **forward roll**; off the wings, the **flare and spin** into a crouch (a roll if
  very fast). A landing cuts into a flip still going; dodges, ragdolls and takeovers are never interrupted.

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
| 2 | Wall Crawl (toggle) | 20 | 0 | Client movement (`WebSymbioteClient.tickCrawl`). Next to a wall in the air, tendrils latch on and drag you the way you're moving (0.8.6: see below); Sneak goes down. Under a ceiling, Jump sticks you to it, you move with the movement keys, and Sneak drops you. Climbing over a top edge hops you onto the ledge. The server allows floating while it's on and resets fall distance while you're touching a wall or ceiling. |
| 3 | Consume | 160 | 0 | Nothing is eaten alive (0.8). A corpse you carry is eaten. Else a free corpse within `CONSUME_CORPSE_RANGE` 6 (the one you look at first) is grabbed by a tendril, carried, then eaten. Else a non-player creature at **current** health <= `CONSUME_MAX_HEALTH` 10 within `CONSUME_RANGE` 3 is finished off by a lash (`CONSUME_FINISH_LAG` 8 ticks), and its corpse grabbed, carried and eaten. Tamed, name-tagged, inedible and corpse-less creatures are never taken. A corpse heals 6-16 and feeds 30-80 by size. |
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
- The client wrenches your view and drags you at the mob (`TAKEOVER_SPEED` 0.34), with a pulsing dark vignette.
  Since 0.8.x it walks a real path round walls, or rips through them (see **Takeover pathing** below).
- The server swings and attacks with `player.attack` every 10 ticks when within reach 3.
- Afterwards stress drops by 20.

**Takeover pathing (0.8.x, `WebTakeoverPath`, client `WebSymbioteClient.followPath`).** Everywhere the symbiote
drives you (rampage and hunger takeovers, a curious one on its way to look, the feeding hunt, the run for its life)
it no longer lurches in a straight line into walls.
- **The path (server):** a bounded A* over block positions sized for a player (feet + head clear, something to stand
  on; steps up of 1 with headroom, charged leaps up taller ledges (below), drops of up to `MAX_DROP` 3; no
  corner-cutting diagonals; never through or onto
  fire, lava, campfires, cactus, magma, berry bushes, powder snow, wither roses or cobwebs; slabs/carpets count as
  floor). Budget `MAX_NODES` 1600 within `SEARCH_RADIUS` 32. The goal is the ground under the target (searched down
  `GROUND_SEARCH` 6), reached within `GOAL_REACH` 1.6 and 2 blocks of height. Re-pathed every `REPATH_TICKS` 10, or at
  once when the target has moved `REPATH_TARGET_MOVE` 3. A partial path is walked only if it ends
  `PARTIAL_MIN_GAIN` 2.5 blocks closer. Waypoints go to the client in `WebNet.TakeoverPath` (`web_takeover_path`; at
  most `PATH_SEND_MAX` 48) with a mode: PATH, DIRECT, RIP or FALLBACK.
- **Straight line** only when the target is within `DIRECT_RANGE` 7, within `DIRECT_MAX_DY` 1.2 of your height, in
  clear sight at the eyes and the feet, and there's ground all along the line (no lurching into a ravine).
- **Running:** a flood fill (`FLEE_NODES` 1400, cost up to `FLEE_MAX_COST` 40) for the reachable spot that scores best:
  distance from the threat − `FLEE_COST_WEIGHT` 0.35 × path cost, + `FLEE_SKY_BONUS` 3 under open sky. A spot must
  gain `FLEE_MIN_GAIN` 4 blocks of distance, else it's trapped and rips away from the threat.
- **Walking it (client):** faces and moves toward the next waypoint (reached within `WAYPOINT_REACH` 0.7, or
  `CORNER_REACH` 0.45 at a corner; skips up to 2 ahead), hops (a plain jump, `JUMP_SPEED` 0.42) for a waypoint
  `STEP_JUMP` 0.4+ higher or on bumping a wall, swims up, and creeps (`LEDGE_CREEP` 0.25) at an edge the path doesn't
  drop down. Chasing, the eyes stay on the prey. The old straight lurch (with its detours) is the fallback.
- **Sprinting (0.8.3):** it sprints whenever it can, in every mode (chasing, hunting, curious, running): the sprint
  flag is on (so the gallop shows and the server sees it; `WebSprintMixin` keeps vanilla from cancelling a sprint with
  no forward key held) at `TAKEOVER_SPEED` 0.38 blocks/tick, × `FLEE_SPEED_SCALE` 1.2 running. It drops to a walk
  (`WALK_SPEED` 0.24) on a tight corner (within `CORNER_SLOW_RANGE` 1.2 of a turn sharper than `CORNER_COS`), on a
  step up, at an edge, before and during a leap, in water, and when you're too hungry to sprint (vanilla's rule: food
  above 6, or flight allowed). It costs hunger as any sprint does.
- **Steps and leaps (0.8.3):** while a takeover has the legs, the suit's charged jump stands aside
  (`WebSymbioteClient.driving()`: `WebSwing.holdingCharge` is false so `WebJumpMixin` lets plain jumps through, and
  `tickChargeJump` ignores your Jump key), so 1-block steps are plain hops. A ledge taller than that is climbed with a
  **charged leap**: the A* has a leap move up `LEAP_MIN_RISE` 2 to `LEAP_MAX_RISE` blocks onto the lowest ledge on that
  side, with headroom above where it coils (up to the ledge's height + 2) and over the ledge; it costs `COST_LEAP` 3 +
  `COST_LEAP_PER_BLOCK` 0.5 a block on top of the step, so walking round wins unless the leap saves real distance or is
  the only way. `LEAP_MAX_RISE` is `WebLeap.TAKEOVER_MAX_RISE`: the height a **3/4 charge** (`TAKEOVER_MAX_CHARGE`)
  clears with `CLEARANCE` 0.6 to spare, worked out from the leap's own physics (up speed `CHARGE_UP_MIN` 0.45 →
  `CHARGE_UP_MAX` 1.65 blocks/tick, gravity 0.08, drag 0.98): 9 blocks. Too high even for that, and it walks round or
  rips as before. On the client, a waypoint more than `LEAP_MIN_RISE` 1.4 above your feet is a ledge: it walks to its
  foot (`LEAP_START_RANGE` 1.3, or until it bumps the wall), coils only as much charge as the rise needs
  (`WebLeap.chargeFor`, at your gravity; e.g. about 0.15 for 2 blocks, 0.26 for 3, 0.35 for 4, never above 0.75) for that share
  of `CHARGE_FULL` 30 ticks, crouching (`ChargeHold`, so everyone sees the coil; the server accepts it during a
  takeover even with the suit hidden) and squelching, then leaps, nudged on at `LEAP_FORWARD` 0.12 and steered onto the
  ledge in the air. Coiling counts as progress for the stuck check. The leap numbers live in `power/web/WebLeap`,
  shared with the suit's own charged jump (`WebSwing`).
- **Stuck:** moved less than `STUCK_MOVE` 0.9 in `STUCK_TICKS` 30 (and not in reach of the prey): it re-paths at once
  (and stays off the straight line for `DIRECT_BAN_TICKS` 60). Stuck `STUCK_RIP_AFTER` 2 times running: it rips.
- **Ripping through (debris-style since 0.8.3):** no path within the budget, a sealed room, or stuck: the tendrils
  tear out the blocks in the first blocked cell toward the target (or away from the threat), up to `RIP_LOOKAHEAD` 3
  cells along the main axis (then the other): a 1×2 slot, or a step (the block above the step and over your head) when
  the target is higher, a step down when it's lower. The whole slot comes out as **one clump**, the way Living Armor's
  tendrils tear a chunk loose: the tendrils dig in (the `web_symbiote_ripping` attachment, synced, draws every tendril
  reaching into the block nearest your eyes) and work it loose over `RIP_LASH_TICKS` 8 + `RIP_TICKS_PER_HARDNESS` 2 ×
  the hardest block's hardness + `RIP_TICKS_PER_BLOCK` 3 per extra block (at most `RIP_LASH_MAX_TICKS` 30), every
  block cracking, with hit sounds, small debris puffs (`RIP_DEBRIS` 0.3) and the black `trail`. Then the blocks are
  torn out through the shared code path, `BlockCluster.of` (the same puffs and wrench sounds as Living Armor's
  sneak-rip; no item drops at the tear), and a free tendril takes the clump with `WebTendrils.grab` (it reaches, hauls
  it up out of the hole and holds it, cords drawn as for any grab). After `RIP_HOLD_TICKS` 6 held (or
  `RIP_HOLD_TIMEOUT` 40 after the tear) it's **flung aside** with `WebTendrils.fling`: square to the way through, to
  the side (level, or up and over) with the most open room within `RIP_FLING_LOOK` 4 blocks, at `RIP_FLING_SPEED` 1.2
  with `RIP_FLING_LIFT` 0.25, so never ahead into your path and never back at you. It's a launched clump: it batters
  whatever it hits (never you) and shatters into dust, a few blocks dropping as items (`BlockCluster.DROP_CHANCE`). In a
  tight tunnel it shatters against the wall beside you. Both tendrils full: the clump is shoved aside at once. Then it
  re-paths. You're held still facing the block, and the dread overlay is refreshed every `RIP_FX_EVERY` 40. The first
  rip in a takeover whispers "Walls won't stop it. It tears through." A takeover ending with a clump held flings it
  aside.
- **What it won't break:** unbreakable blocks, anything harder than `RIP_MAX_HARDNESS` 20 (obsidian and harder),
  blocks with block entities (chests, furnaces, spawners...), fluids and waterlogged blocks, blocks you may not interact with
  (`ServerPlayer.mayInteract`: spawn protection, world border) or act on in your game mode (`blockActionRestricted`),
  and anything a protection mod refuses through Fabric's `PlayerBlockBreakEvents.BEFORE` (it fires `AFTER` too). At most
  `RIP_MAX_BLOCKS` 32 per takeover (a clump is trimmed to what's left), within `RIP_REACH` 4.5. Nothing breakable in the way (a ravine, bedrock, a chest
  wall): the blind fallback, and no new rip for `RIP_RETRY_TICKS` 40.

**Feeding (0.8): kill, grab, carry, then feed.** Jaymo's Kitchen is now a required dependency (corpses).
- The symbiote never feeds on anything living. Hungry (`SELF_FEED_BELOW` 50, checked every `SELF_FEED_EVERY` 20) it
  runs a state machine (`WebSymbiote.tickFeeding`, server-side `Live.feedStage`):
  1. **Seek:** a corpse you're carrying (edible) goes straight to Feed; else the nearest free corpse within
     `SELF_FEED_RANGE` 14 (edible, nobody carrying it, no tendril holding it); else the nearest creature there that
     leaves a corpse (no pets, no named ones; players only in a rampage).
  2. **Kill:** it takes the wheel (a "hunt" takeover: no big body) and attacks; the tendril strikes go for the prey
     first. Gives up after `KILL_TICKS` 400 or if the prey escapes. A hungry or rampage takeover that kills its own prey
     also goes on into feeding.
  3. Once the prey is dead, its Kitchen corpse must appear within `CORPSE_WAIT_TICKS` 60, within `CORPSE_FIND_RADIUS`
     3 of where it fell.
  4. **Grab:** a tendril (`WebTendrils.grab`) reaches for it and hauls it in (reach + `GRAB_HAUL_TICKS` 8, at most
     `GRAB_TIMEOUT` 80).
  5. **Carry:** `KitchenCompat.carry` puts it in your arms (Kitchen's private arms-carry, reached by reflection, since
     its `takeBy` pockets small corpses; falls back to `takeBy`).
  6. **Feed:** eating starts only once `KitchenCompat.carriedCorpse(player)` is that corpse. If the corpse leaves your
     arms mid-meal, the maw closes and nothing is eaten.
- `devour()` (eating weak creatures alive in a rampage) is gone, and a rampage bite on a player no longer heals or
  feeds it. Player corpses (they hold belongings) are never eaten.
- The whole episode keeps the dread screen effect up (refreshed every `FEED_FX_EVERY` 40 ticks).
- Debug: `/powerdebug web symbiote` now prints `feeding[stage=... target=...]` (1 kill, 2 grab, 3 carry, 4 feed).

**Its voice (0.8):** every `whisper` (hints included) also sends `web_symbiote_dread` (`WebNet.Dread`): for
`WHISPER_FX_TICKS` 60 the screen darkens 35%, a pulsing blackish-purple (#1A0A26) vignette closes in, and a low growl
(warden ambient + ravager ambient at pitch 0.5, at most once per 2 s) plays as a UI sound for that player only
(`client/.../WebSymbioteDread`, its own HUD element after the misc overlays).

**Tendril strikes (0.8):**
- Each strike rolls a variant, synced in the `Strike` attachment (`variant`, plus `heavy`): a **slam** (reared high
  over the head, crashing straight down; little push, and an airborne target is driven down) or a **swipe** from the
  left or the right (a wide sweep through the target; knocks it sideways along the sweep). The hit lands at
  `STRIKE_HIT_SHARE` 0.5 of the strike (rear until `STRIKE_REAR` 0.4).
- **Heavy debris strikes:** while a tendril grip holds a `BlockCluster` or a falling block, strikes search
  `HEAVY_STRIKE_RANGE` 8.5, last `HEAVY_STRIKE_TICKS` 60 (rear until 0.5, hit at 0.58), reset `HEAVY_STRIKE_EVERY` 90,
  and deal `HEAVY_STRIKE_DAMAGE` 11 × (0.6 + 0.1 per block, 0.7-1.5). Knockback ~10 blocks (`HEAVY_KNOCK` 1.0 +
  `HEAVY_LIFT` 0.45; a slam 0.75 + 0.2), a mace thud and anvil, block dust, and a shake for you and a struck player.
  The held debris itself is swung along (`WebSymbiote.debrisStrikePoint`, steered in `WebTendrils.tick`): reared above
  you or out to the side, then brought down or across onto the target.
- Client (`WebTendrilLayer`): the strike curve follows the variant; a heavy strike stretches the striking tendrils far
  further. The aim math was fixed: in model space a cord built along +Z points at relative yaw `a` (+ right) and
  elevation `p` (+ up) with `yRot = PI + a`, `xRot = p` (the old `-PI/2 - 1 - p` pointed strikes back and down).

**Tendril combos (0.8, `WebTendrilCombos`):** attack (left click) while the tendrils hold things and it's a combo
instead of a swing. The client (`WebPowerClient`'s `ClientPreAttackCallback`) works out whether one fits
(`WebTendrilCombos.kind`, plus `crosshairTarget`: the living thing under the crosshair within `CRUSH_REACH` 6, seen past
held things) and sends `web_tendril_combo_attack` (`Attack(target)`); the server re-checks it (target within reach +
`REACH_SLACK` 2 and in front), else falls back to the old chunk swing (`WebTendrils.smash`). Both tendrils must have
reached what they hold.

| Holding | Combo |
|---|---|
| A creature in each tendril | **Clap**: hauled out `CLAP_WIDE` 2.6 to each side, slammed together `CLAP_FORWARD` 2.3 ahead; `CLAP_DAMAGE` 12 each, then dropped and bounced apart (`CLAP_BOUNCE` 0.75 out, `CLAP_LIFT` 0.4 up). |
| Debris + a creature in the other tendril, or under the crosshair | **Crush**: the debris reared `CRUSH_REAR_UP` 2.8 over your head, carried over the target and brought straight down on it. `CRUSH_DAMAGE` 15 × size (0.6 + 0.1 a block, 0.7-1.5), driven down if airborne, Slowness II 1.5 s. A held victim is pinned low in front for it. |
| Debris in both | **Shatter**: hauled out wide and up, smashed together `SHATTER_FORWARD` 2.4 ahead, bursting into a cone (`SHATTER_RANGE` 6, half-angle 30°) of their blocks. Everything in it (line of sight from the burst) takes `SHATTER_DAMAGE` 10 × size (to 1.8), falling off 40% at the far end, and is knocked along the cone. |

- Timing: hauled out until `REAR_TICKS` 12 (a sweep and mace whoosh at the turn), impact at `HIT_TICKS` 18, slow reset to
  `TICKS` 50 (no new combo, no autonomous strike; clicks are swallowed while the tendrils still hold something).
- Synced in `WebTendrils.COMBO` (`Combo(kind, start, first, second)`) to everyone. The server steers the held things
  through it (`WebTendrilCombos.point`, steered hard in `WebTendrils.tick`), so their cords move with them; in
  `WebTendrilLayer` every upper tendril joins in (clap: both sides hauled wide, then clapped in; crush and shatter: a
  heavy two-sided slam).
- Used debris is spent at the impact: `BlockCluster.shatter` (dust, the usual 30% drops) plus falling grit; a held
  falling block puffs, may drop (`SPENT_DROP_CHANCE` 0.3) and is gone. Soft debris (`BlockCluster.allSoft` / `soft`)
  hits for no more than `BlockCluster.SOFT_HIT_DAMAGE`; a shatter's damage scales by its share of hard blocks.
- Impact: heavy sounds (mace, anvil, crit, a blast for the shatter), block dust from the ground, a screen shake for you
  (`SHAKE` 1.8, shatter 2.2) and for a struck player (`TARGET_SHAKE` 2.5). You're never hurt by your own combo; other
  players are.

**First person (0.8):** First-person Model (`firstperson`) and Not Enough Animations join the pack (`packModsNoCf`
in `build.gradle`, CurseForge ids still to fill in).
- With FPM drawing your body (`FirstPersonCompat.bodyShown`: loaded, first-person camera, `FirstPersonAPI.isEnabled()`
  by reflection), `WebTendrilLayer` renders as usual (FPM hides no layer unless its `hiddenLayers` config names it);
  your own upper tendrils take a first-person pose: out to the side, rising forward over the shoulders and curling in
  at the screen edges, longer, swaying; strikes stretch further still.
- Without it, `WebFirstPersonHandsMixin` (TAIL of `FirstPersonHandsAndItemsRenderer.submitHandsWithItems`) draws the
  same model in camera space next to the hands (`WebTendrilLayer.submitFirstPerson`), with the strike and wall aim
  turned from body-relative to view-relative.
- Held things (grabs, debris, the feeding grab) already showed their cords in first person (`WebLineRenderer.tentacle`).

**Weaknesses ? rejection:**
- **Fire:** being on fire or in lava counts as **one** exposure per burning episode. An episode ends after
  `FIRE_EPISODE_GAP` 60 ticks without fire.
  - While the suit or Living Armor is out, a power's fire effect within 4 blocks (`Flames.SYMBIOTE_RANGE`: Laevateinn,
    Sunfang, Soul Saber, the Meteor) counts the same, even if you aren't burning (`WebSymbiote.nearFlames`, called
    from `core/Flames`).
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

### Symbiote rework (0.8.9)
**Bond** (`WebSymbioteState.bond`, 0-`BOND_MAX` 100, synced; old saves load at 50):
- A new bond starts at a random 0-`BOND_START_MAX` 50. A rejection resets it to 0.
- It grows 1 every `BOND_GAIN_TICKS` 2400 while fed and calm (stress under `BOND_CALM` 50) and drops 1 as often
  while starving. +`BOND_MEAL` 2 per meal; each exposure costs `BOND_EXPOSURE` 3, breaking a takeover
  `BOND_BREAK_FREE` 2.
- Abilities unlock at `UNLOCK_BOND` (tendril 15, armor 60, crawl 30, consume 45, spikes 75, shroud 0). Each unlock
  is whispered. The radial menu darkens locked tiles with the bond they need and shows the bond under the hunger
  bar. Creative gets everything.
- A low bond means it ignores you (up to `IGNORE_MAX` 45% of uses below `IGNORE_BOND` 60; putting a toggle away is
  never ignored) and takes over on a whim (`FERAL_CHANCE`, below `FERAL_BOND`; whisper "feral"). At full bond it no
  longer rampages when restless, and Living Armor wears the big bulk body with you in control.
- Its tendrils look wilder at low bond: quicker sway, sudden jerks, a stronger bulge travelling along each one
  (`WebTendrilLayer.FERAL_*`).

**Narration:** `WebSymbiote.narrate(player, act)` speaks `say.<act>.1-3` with a chance from `NARRATE_MIN` 0.1 to
`NARRATE_MAX` 0.9 by bond, at most every `NARRATE_GAP_MAX` 600 to `NARRATE_GAP_MIN` 100 ticks. Every line is a
whisper, so the vignette, dark screen and growl play each time.

**Takeovers can't be interrupted.** `WebTakeoverInputMixin` zeroes movement while it drives, and clicks, power keys,
mode, ultimate and the menu key are swallowed. Each fresh press is a struggle (`WebNet.Struggle`): once you've
earned `BREAK_FREE_ABILITIES` 3 abilities, `BREAK_FREE_BASE` 36 - 4 per ability presses (12 with all six; the
count fades by `STRUGGLE_FADE` per tick) break its hold, which adds `BREAK_FREE_STRESS` 10 stress. A meter shows on
the HUD.

**Takeover combat:** the tendrils do the fighting: `FRENZY_TICKS` 18 strikes every `FRENZY_EVERY` 4 ticks for
`FRENZY_DAMAGE` 9, with a `FRENZY_SNATCH` 18% chance to snatch and hurl the prey. Its own punches slowed to
`TAKEOVER_ATTACK_TICKS` 20.

**Charged cross (click Smash with a chunk, `WebTendrils.smash`):** `CROSS_WINDUP` 22 ticks drawing the chunk back and
across, `CROSS_SWING` 5 whipping it through, `CROSS_FOLLOW` 8, then `CROSS_RECOVER` 18 before anything else. Damage
6 + 1.5 per piece (max 16).

**Debris pull (Sneak+R/G at a wall):** the tendril reaches in, then hesitates and struggles (`TUG_MIN` 20 to `TUG_MAX`
60 ticks, longer for harder blocks), cracking the blocks, before it tears the chunk out and hauls it in. Letting go
of the key first cancels it. The cord to the wall shows (`WebTendrils.TUG`).

**Corpse thrash (`WebTendrilCombos.THRASH`):** click while a tendril holds a corpse (one or two). Each slam is a
`THRASH_CYCLE` 14-tick cycle: hauled overhead, smashed down `THRASH_FORWARD` 2 ahead with blood and ground debris,
splash damage 5. 1-4 slams; a second corpse runs `THRASH_OFFSET` 7 behind. Then it's dropped and butchered as by
hand with no block (`KitchenCompat.butcherRough`, Jaymo's Kitchen only).

**Wall Crawl:** the tendrils latch onto the wall (`WebNet.ClimbLatch`, drawn from `WebTendrils.CLIMB`) up to
`CLIMB_REACH` 2.3 ahead in the direction you're moving, then drag you there, alternating hands (`REACH_TICKS` 3,
`PULL_TICKS` 9, `PULL_MAX` 0.42). With no keys it hangs and sags slightly. At a ledge one bites over the top and
hauls you up.

**Takeover digging:** each block in the way is torn out by one tendril in `RIP_LASH_TICKS` 3 + hardness ticks
(max 10), thrown straight ahead, and the other arm starts on the next block at once, alternating R and G.

**Slingshot:** no more pulling back. Holding the draw cracks the blocks under and behind you, up to `WebStrain.ALMOST`
90%. In Living Armor, holding `SLING_ARMOR_BREAK_TICKS` 100 (5 s) breaks them and fires you as if you'd let go.

**Death (`WebSymbiote.withdraw`):** when a bonded player dies the bond ends for good and the symbiote pours out as a
`jaymospowers:web_symbiote_blob` (`WebSymbioteBlob`): a black blob that hauls itself around on tendrils
(`WebBlobRenderer`). It roams `ROAM_RADIUS` 10 around where you fell, moving its home onto your body (Kitchen) if
one is within `CORPSE_RANGE` 24. Every `SCAN_EVERY` 20 ticks it looks within `SEEK_RANGE` 16 for, first, a Kitchen
zombie player, then any web slinger without a symbiote (chased up to `LEASH` 32 from home), and leaps on at
`BOND_REACH`. A player gets a fresh bond (`bond`). Only fire, lava, explosions and `/kill` hurt it (20 health); other
blows splash it back. It never despawns.
A Gravity meteor (`GravityMeteor.impact`) carries one `METEOR_CHANCE` 20% of the time, unless a symbiote (a blob, a
behemoth or a bonded player) is already within `METEOR_VICINITY` 128. `METEOR_ALIVE` 30% of those crawl out of
the crater as a blob with `FIRE_GRACE` 300 ticks of immunity to the fire and lava there; the rest lie on the crater
floor as goo (Black Ooze) on obsidian, bonded like a natural crater's (Sneak + right-click). Goo blocks nearby don't
count toward the vicinity check.

**Behemoth (`WebBehemoth`):** a zombie player it takes gets the `web_behemoth` attachment and permanent modifiers
(+80 health, +6 damage, x1.7 size, x1.3 speed, 0.8 knockback resistance), the big body and veins drawn over it, and
six back tendrils. It targets anything living it can see within `SIGHT` 24 (not creative players, other behemoths
or blobs) and lashes with tendrils (`STRIKE_TICKS` 16, landing at 8, 12 damage, every 12 ticks, reach 6.5). The sun
doesn't burn it and it never turns into a drowned (`WebHostZombieMixin`). When it dies the blob crawls out again.

**Testing:** `/powerdebug web symbiote level <0-100>` sets the bond (unlocks and the bulk follow). Check: a fresh bond
has only Shroud; a low bond ignores the menu sometimes; spam keys during a curious takeover (nothing happens until 3
abilities, then the meter fills and you break free); takeover fights are tendril-heavy; a chunk cross winds up and
recovers slowly; Sneak+R at stone hesitates before pulling; thrash a held corpse (with Kitchen) for the drops; Wall
Crawl drags rather than floats; a takeover through stone throws clumps forward; hold a slingshot draw on stone (with
and without Living Armor); die bonded (a blob crawls out,
roams, bonds a nearby web user, or with Kitchen waits for the body to rise and turns it into the behemoth). None of this has been run in game yet.

### Symbiote rework, part 2 (0.8.19)

- **Suit at bonding:** the suit shows itself when the symbiote bonds (`SUIT_DEBUT_TICKS` after the bonding), then
  withdraws ("suit_withdraw"). The mask won't bring it back until `SUIT_ABILITIES` (4) abilities are unlocked
  (`suitAllowed`; creative always can). The menu shades the mask and shows "n/4" until then.
- **Engulf on every takeover:** each takeover (low-health last-ditch included) forces the suit and the big body on,
  then puts the suit back the way it was when the takeover ends.
- **Alarms:** recoiling from fire or noise whispers an alarming line every time (`alarm_fire.N`, `alarm_noise.N`)
  with the dread vignette; the edge-of-rejection warning still comes once.
- **Cooldowns:** every radial ability runs its own cooldown. Shroud's starts when it ends (`COOLDOWN + SHROUD_TICKS`).
  Living Armor goes on cooldown (`COOLDOWN[ARMOR]`, 10 s) when it ends, and switches off after `ARMOR_IDLE_TICKS`
  (5 s) with no tendril use while the suit is off, or after `ARMOR_MAX_TICKS` (60 s) regardless. When it ends the
  tendrils sink back into the body over `WITHDRAW_TICKS` (client), the growth run backwards.
- **Feeding:** it only feeds itself below `SELF_FEED_BELOW` (30) satiety. Once it starts wanting a meal (`craving`)
  it keeps hunting until it has actually eaten, even if satiety climbs back over the line.
- **No fall damage** while the suit shows or Living Armor is up.
- **Last-ditch mode** (low health): a different panicked line each time (`panic.1-8`), then in order: Spike Burst
  if mobs are within `SPIKE_RADIUS`, Shroud, a leap away from the threat (`LEAP_SPEED`, `LEAP_UP`), and the takeover
  flee. Fleeing into a wall, the tendrils climb it (`FLEE_CLIMB`, client); dead ends are ripped through as before
  (`WebTakeoverPath`). If it takes `EMERGENCY_HITS` hits while fleeing, or is stuck `STUCK_TICKS` with the threat
  within `STUCK_NEAR`, it hardens: `EMERGENCY_ABSORPTION` absorption, once per flee ("harden").

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
- **Takeover pathing:** the path is computed from the server's view of your position (client-authoritative movement),
  so lag can make the first waypoint lag a little (the client picks the nearest of the first four). Check: a mob
  behind a wall (it should walk round), a mob in a sealed stone room (it should rip a 1×2 slot in, never through
  chests or obsidian, and not inside spawn protection), a mob up a hill or down a pit (steps), and a run for its life
  in a cave (it heads for the best open spot, ripping only if walled in). The A* runs every 10 ticks per driven player
  (bounded at 1600 nodes).
- **0.8.3 takeover checks:** it sprints (gallop, speed, hunger drain) chasing, hunting and running, walks at corners,
  steps and edges, and walks with an empty belly (food 6 or less). A 1-block step is a plain hop, never a coil, even
  holding Jump. A 2-4 block ledge with no stairs nearby: it walks to the wall, crouches briefly (longer for taller) and
  leaps just over it; with stairs a few blocks off it should prefer them. A 10+ block cliff: walks round or rips. A
  sealed room: each 1×2 slot is worked loose (all blocks cracking), torn out as one clump, hauled up by a tendril and
  flung sideways (not down the tunnel, not at you); it should shatter nearby with some drops. Check that a leap that
  misses its ledge (laggy server) only falls the clearance, and that protection mods still stop the tear.

### Known gaps / upgrade ideas
- Feeding when your own hunger bar is full isn't possible: vanilla won't start eating.
