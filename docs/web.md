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
