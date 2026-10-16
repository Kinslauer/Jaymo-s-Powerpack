# Web Slinger

Spider-Man-style traversal built around momentum. You swing on a real rope constraint, gain speed through the bottom
of each arc, and get a boost if you let go on the upswing. You keep that speed in the air until you land, and you can
fire again mid-air to chain swings. Three utility webs round out the kit. Accent colour `0xE0303A`; the icon is a
cobweb.

## Rapid Web Shot from the shooters, longer fists, levels that build (`web-shot`)
Andy: "make the web shot tap fire as fast as you can tap the button, so it can be more rapid and web an enemy up after
5 hits, but you can only use it when you have web shooters equipped and spare web in the canisters. organic web
disables V, but webbed fists last 30s instead of 10. you also now only get webbed fists and web wings before level 1.
but you keep everything and build on it unlike before where you lost B."
- **Rapid pellets (`WebPower.shoot`):** a tap of V (let go under `SHOT_TAP_TICKS` 20) fires one pellet per tap, as
  fast as you tap: `PELLET_COOLDOWN` is now a 2-tick anti-spam floor (was 4) kept in the server state
  (`State.pelletReady`), and pellets no longer set V's cooldown at all, so the V chip doesn't flicker. The charged blast
  and the Sneak + tap bomb keep their cooldowns, which still hold pellets back too (V's shared cooldown).
- **Five hits web it up (`WebStrength.shotHit`):** each pellet that hits a living thing adds a stack (per shooter):
  a puff of web gathering on it (4 particles a stack), a squish rising in pitch, and Slowness one level higher a stack
  for `SHOT_STACK_SLOW_TICKS` 30. A stack is forgotten `SHOT_STACK_TICKS` 60 (3 s) after its last hit. The
  `SHOT_WRAP_HITS` 5th hit cocoons it for `SHOT_WRAP_TICKS` 80 (the same cocoon as the fists' third hit and the web
  bomb: held where it stands, Slowness VII, no jumping, a cobweb at its feet).
- **Shooters only; organic web disables V:** V is Web Shot (ABILITY_3), so "shooters only" and "organic web disables
  V" are one rule. Every pellet, bomb and blast leaves a web shooter and uses one web of fluid from it: the main
  hand's wrist if it has any, else the other (`WebShooters.shotHand`). With no shooters, or both wrists dry, V doesn't
  charge or fire: a dry click (dispenser fail) and an action-bar line (`message.jaymospowers.web_shot.no_shooters` /
  `.dry`); the HUD's V chip is shaded out and shows no charge bar. Swing webs, Zip and Yank still fall back to organic
  web as before (`WebShooters.REQUIRED` is still false). The symbiote suit's V (its black mass; suit shown or Living
  Armor) isn't web, so it needs no shooters, as the suit ignores them everywhere else. V while Yank holds something is
  still the taser shock down the line (not a shot, no fluid).
- **Webbed fists 30 s (`WebStrength.FISTS_TICKS` 600, was 200).** The cooldown is unchanged (`FISTS_COOLDOWN` 600
  from when they're coated), so they can be coated again as soon as they dry up.
- **Levels that build (`WebPower.starterKey`, `usableEarly`):** before level 1 (no ability unlocked) a web slinger has
  only webbed fists (X) and web wings (hold Jump while falling); there's no starter key any more (it was weblines on B).
  Both count as practice (`Mastery.used`). From level 1 B strings weblines (`WEBLINE_LEVEL` 1), and keeps doing so:
  when Yank unlocks at its level (third, unlock order R, G, B, V unchanged), B aimed at a block (no creature) still
  starts a webline as it always did, so nothing is lost on levelling. Before, the webline starter vanished at level 1
  and B did nothing until Yank. The key guide (H) shows "Wings & Fists" at level 0 and B's "Webline" line
  (`key.ability_4.early`, a generic `KeyGuide` hook for a key that works before its ability unlocks) until Yank.
- **Not gated:** wall runs and parkour (passive movement, `WebWallRun`/`WebParkour`) still work at level 0, as before.
- **Check first in game:** tapping V as fast as possible with shooters on (one pellet a tap, fluid going down one a
  pellet); five pellets on a zombie cocooning it, and the stack dropping after 3 s without a hit; V with no shooters,
  empty shooters, and in the suit; a level-0 player (`/power level 0`) having only wings and fists, then B's weblines
  at level 1 and Yank (still stringing weblines at blocks) at level 3.

## The symbiote's removal fight, dormancy, and tendrils on every line (`WebSymbioteBond`)
Andy: "symbiote's removal is different, its activation is added to the radial menu, the first few removals at low
bond animate like a fight or struggle, requiring the same takeover inputs to regain control, eventually showing just
the face, for a short variable duration until mastery increases. Upon first acquisition the suit lay dormant, doesn't
allow you use any of its abilities (it will still takeover to survive) for up to two days, whispering to you and
conforming itself to its new home. Every message should pulse with tendrils along the vignette drawn on screen,
intensifying with glow and intensity to a maximum at low health takeover alerts." Built on the tear-off (`WebSuitTear`).
- **Removal (the radial menu's mask, the same suit-off toggle):** under `FIGHT_BOND` 70, the first `FIGHTS` 3 times
  with each new bond (`FOUGHT`, saved), taking the suit off is a fight: your keys are swallowed and every press is a
  heave against it, as against a takeover (the same struggle packet, the same `STRUGGLE` bar and its fade; a bar at
  the bottom of the screen; `FIGHT_PRESSES_MAX` 30 at no bond down to `FIGHT_PRESSES_MIN` 16). Everyone sees the
  tear-off driven back and forth by the bar (`WebSuitGrowth.FIGHT_TEAR_MIN` 0.06 to `FIGHT_TEAR_MAX` 0.3 of the way
  through: clawing at the chest, the mask sliding off the face and back as it tugs); past `FIGHT_SHOVE_FROM` of the
  bar it heaves back now and then (`FIGHT_SHOVE` of the bar, a shake). Win: only the face comes free (`FACE`, synced),
  for a random `FACE_MIN_*`-`FACE_MAX_*` ticks (3-8 s at no bond up to 20-80 s at a whole one), the skin's hat layer
  showing; then the suit flows back up over the face (`FACE_FLOW_TICKS`). Lose (`FIGHT_TICKS` 20 s up): it keeps you
  covered. A takeover, the roar or the big body ends the fight or the bare face. Past the fights, or at bond 70+, the
  toggle is the plain tear-off. The symbiote isn't a secret identity, so nothing changes in `SecretIdentity`.
- **Dormancy:** the first time a player ever bonds (`MET`, saved through death; never in creative) it lies dormant for
  a random `DORMANT_MIN_SHARE` 35%-100% of up to `DORMANT_MAX_TICKS` 48000 (two days; at a whole starting bond the cap
  is `DORMANT_FULL_BOND_SHARE` a quarter of that), counted while online and saved (`DORMANT`, synced to the owner). No
  radial pick answers (a whisper why; the menu shades every tile and says how many minutes are left), no reach/pull/Zip
  bonus, no hunger, no hunting, instinct, curiosity, tours, neglect, climate walks or gifts, and the suit withdraws
  after its debut. It still saves itself near death (the roar, the run, the desperate lashing). It whispers every
  `DORMANT_WHISPER_MIN`-`MAX` ticks (10 lines), ripples up your body in a slow black spiral every
  `DORMANT_RIPPLE_TICKS`, and wakes with a heartbeat. `/powerdebug web symbiote dormant <ticks>` sets it (0 wakes it).
- **Tendrils on every line (`WebSymbioteDread`):** every whisper sends `WebNet.Dread` an urgency
  (`WebSymbiote.urgency`: a plain line 0.2, moods, gifts and unlocks 0.4, struggles, lockouts and the fight 0.55,
  takeovers, frights, the climate's last warning 0.75, hardening 0.9, the near-death panic 1.0). The client draws that
  many tendrils (`TENDRILS_MIN` 6 to `MAX` 26) pulsing in from the screen's edges, reaching further, wider, faster and
  with a brighter violet glow the more urgent; at the top a glowing rim lights the whole edge. They stay at full while
  it runs for its life, and up while it fights you for the suit, then fade. Plain dread with no line (feeding, the
  roar) has none.
- **Check first in game:** a fresh bond's dormancy (whispers, ripples, refusals, a defend roar still firing at low
  health); the fight (the tear pose back and forth for you and others, the bar, winning to a bare face and it flowing
  back); a takeover starting mid-fight; the tendrils at each urgency, and the panic at full.

## Braided webs, a true rope, web shooters (0.12.3)
Andy, on the swinging video: "I like the web design, and web shooter idea", and earlier "remake as much as possible,
while retaining our tethers, it should NOT attach to the air". That mod is someone else's: these are our own code and
art after its ideas. Webs still only stick to real blocks and creatures (`WebAnchors`' rules are unchanged).
- **Braided lines (`WebLineRenderer.braid`):** a web line is a white rope of strands twisting round each other (a turn
  every `BRAID_TURN` 0.9 blocks), each a camera-facing ribbon of `textures/entity/web_rope.png` (solid down the middle,
  see-through at its edges, lit by the world so it isn't glowing at night), gathered into one at the hand and at the
  splat (`BRAID_GATHER`), with fine fibres fraying off it (two-segment translucent wisps placed by a hash, swaying).
  A web shooter's line is the full rope: `SHOOTER_STRANDS` 3, `SHOOTER_STRAND_WIDTH` 0.05, `SHOOTER_TWIST` 0.032, a
  fibre every `SHOOTER_FIBRE_EVERY` 0.5 blocks, a 1.3× splat. The power's own organic web (no shooters, or dry) is
  thinner and plainer: 2 strands, 0.03 wide, a fibre every 1.4 blocks. Hitch lines and weblines are organic braids.
  The rope sim, the snaking shot, twang, hum and thinning all still shape it (a stretched line's strands thin). Past
  `BRAID_FAR` 72 blocks a line falls back to the old flat line; each line is at most `BRAID_MAX_PIECES` 160 pieces
  per strand, so several swingers stay cheap. The symbiote's black corded ropes were already a 3-strand twist and are
  unchanged; spent (let-go) lines still fall as thin fading lines.
- **A true rope (`WebSwing`):** the rope is no longer a spring. Slack (closer than its length) it does nothing at all;
  taut (|body - anchor| >= rope length) the body is put back on the sphere of that length round the anchor and only the
  velocity outward along the line is removed (v -= (v·n)n with n pointing away from the anchor, only when v·n > 0), so
  the arc keeps all its speed. On the web nothing but gravity acts otherwise: vanilla's air drag is undone (÷0.91 and
  ÷0.98) and `SWING_DRAG` is now 1.0 (was 0.993); the taut swing still loses its little through `PENDULUM_DRAG` 0.996.
  The correction is a real move (so walls and floors stop it), at most `ROPE_MAX_CATCH` 1.5 blocks a tick (an anchor
  on something running off, or a hard reel, catches up smoothly), over `ROPE_PASSES` 2 passes for two webs. The
  height you gain reeling in doesn't cost swing speed, and snapping taut from slack loses the speed it took
  off along the line (the swing is measured again from there). Gone: `ROPE_SPRING` 0.3 / `ROPE_MAX_PULL` 0.7.
  Momentum build-up, pumping, Jump thrust and tug, reel in/out, hanging still, two-web swings, the slingshot, jump-off,
  the release boost and the active-swing pose are untouched.
- **Web shooters (`WebShooters`, `WebShooterItem`, `WebCartridgeItem`, client `WebShootersClient`):** built as the
  *optional upgrade* (the recommended default while Andy chooses; `WebShooters.REQUIRED` = true makes them needed:
  a dry wrist then clicks and fires nothing, the suit excepted).
  - **Items:** Web Shooters (one item is the pair; iron ×4, redstone ×2, string: `I I / RSR / I I`) and Web Cartridge
    (string ×2, slime ball, glass bottle, shapeless; stacks to 16 while full). Art by `tools/gen_web_shooters.py`.
  - **Wear:** right-click the pair: strapped on both wrists (`jaymospowers:web_shooters` player attachment, saved,
    synced to everyone). **Take off:** Sneak + right-click with an empty main hand, aiming at nothing (the client sends
    `web_shooters_off`): the pair comes back into your hand, cartridges in it. **Death:** dropped where you fall like
    armour (kept on with keepInventory). Smoke users can't wear them, as with the backpack.
  - **Load:** right-click a cartridge while wearing them: it goes into the emptier wrist and the old one comes back (a
    part-used cartridge, or the glass bottle of a spent one). Or Sneak + right-click the pair in hand: its empty
    wrists fill from the fullest cartridges you carry.
  - **Fluid:** a cartridge holds `CARTRIDGE_SHOTS` 64 webs. Every web the power fires from a hand uses one shot from
    that wrist: swing webs (R/G, their own wrist), and on the main hand Zip, Yank, weblines, hitches and torch rips
    (`WebPower.thwip`). A loaded shot clicks (lever click, pitch 1.9) under the thwip; a dry wrist clicks empty
    (dispenser fail) and the organic web goes out as before.
  - **Boosts:** a loaded wrist's web reaches `RANGE_SCALE` 1.25× further (swing anchors 48 → 60, assisted 40 → 50,
    Zip, Yank; the server allows it for anyone wearing them) and pulls `PULL_SCALE` 1.15× harder (reeling in on a
    swing, Zip's haul). Lines leave the nozzle on the posed wrist (`WebArms.WRIST_LENGTH` 8.5 px down the arm) and
    are the full braid; the symbiote's suit ignores the shooters entirely (its own black webs, shooters hidden).
  - **Looks:** a silver cuff round each wrist with a red trigger pad, the nozzle on the palm side and the cartridge on
    the back while one is in, on everyone in third person (`WebShootersClient.Layer`, on the posed arms) and on your
    own first-person hands (`WebAvatarRendererMixin.renderHand`). Knobs: `CUFF_*`, `*_ARM_MIDDLE`.
  - **HUD:** a gauge chip at the end of the Web chip row (above its start on narrow screens): a tank per wrist (left on
    the left), white, amber under `LOW_SHOTS` 12, dark red when dry; the number is the main hand's shots.
- **Check first in game:** swing feel on the new rope (it should whip round the bottom with no bounce at the end of
  the line); reeling in hard against a ceiling; a two-web hang; the braid at night and up close in first person;
  others seeing your braided line and cuffs; wear / load / take off / die with shooters.

## Slower momentum (0.8.93)
Speed has to be built. A swing chain starts capped at 0.55 b/t (was 0.8) and the cap only climbs, 0.003 a tick (was
0.011 regardless), while you're on a web moving at 85% of it or more: about half a minute of unbroken, well-driven
swinging to the 2.6 top speed. A clean release (quality over 0.6) adds up to 0.08 to the cap. Jump thrust on the web
is halved (0.035) and the forward pump too (0.015). Landing still starts the chain over. Tuning: top of `WebSwing`.
- **Jump on a web:** holding it (still reels in) braces you: no fall damage while you hold it on a web (server reads
  the client's input). Letting it go (after 3+ ticks) lets go of the webs and springs you up by 0.2 + 0.45 × your
  speed, at most 1.4 b/t: the faster the swing, the higher the jump. Web keys must be pressed again after.
- **Airbrake and freedive (S while gliding):** holding **S** (Sneak still works) flares the wings: the glide stops
  carrying you, forward speed bleeds off at `BRAKE_DRAG` 0.94 a tick (about 3 s from top speed) and the fall eases to
  `BRAKE_SINK` 0.2. Once you're nearly stopped (`DIVE_AT` 0.06 b/t) you tip head-first into a Wind-style freedive with
  the wings still out but swept back, falling up to `DIVE_MAX_SPEED` 2.5. Let go of S and the glide takes over again,
  so pitching up turns the dive's speed forward. Flaring at speed throws you up (0.8.101): the flare eases in over
  `BRAKE_EASE_IN` 12 ticks, and `BRAKE_LIFT` 1.4 × the speed it takes off each tick comes back as lift (climb held
  under `BRAKE_MAX_RISE` 0.9), so the climb builds gently, swells and fades as the speed runs out. The wings are a faint
  cobweb skin that ripples in the wind (faster, more), and billows out and inflates by a third when you brake
  (`WebLineRenderer` `WING_*`, `FLUTTER*`, `BILLOW`, `INFLATE`). Landing from a fast dive hurts. Others see it from the brake flag and
  your fall speed (`WebArms` `DIVE_*`).
- **Zip while gliding** is a boost: the wings stay out and the web tows you toward where you aimed for
  `GLIDE_ZIP_TICKS` 30 (1.5 s), then lets go with the usual kick up (or sooner, near the target) and the line falls
  limp. You carry on gliding with the speed and height.

## Active swing (0.10.100)
- Swinging along on a swing web (not hanging still, not a zip, yank or whip), the body drives the swing instead of
  hanging limp: legs back and straight on the drop, knees tucked up and apart through the bottom of the arc, kicked out
  ahead on the upswing (the lead leg a little further). Swinging from one hand, the free arm flares out for balance. The
  body banks into turns. It fades in with speed (`WebArms.DRIVE_SPEED` 0.45 b/t) and only going forward; backwards or
  slow, the old limp hang. Tuning: the "active swing" block at the top of `WebArms`.
- Tendrils stay supple on the move: they stream back behind you, ripple out to the tips and slither with your stride
  (`WebTendrilLayer.DRAG_*`); only the walkers planted on a wall are rigid.
- Wall cling needs webbed fists or the suit, climbs on W, and has new perch / tendril poses (see Parkour below).

## Web Whip, and webs that fall limp (0.8.100)
- **Let-go webs fall limp:** every web line that lets go (swing webs on R/G, zips, whips, the Yank's lead) is left
  behind like a spent zip: stuck where it hit, sagging, falling, then fading (`WebSpentLines`). A line on something that
  moves falls free at once. Recasting to a new spot counts as letting go of the old one.
  The Yank's lead waits: let go of it and only when it breaks or the creature is lost does a spent line fall (one line,
  not two); tied off to a block (`WebSpentLines.LEAD_WAIT` 4 ticks to tell) nothing falls until the hitch itself goes.
- **Taut lines:** a line with no more than `WebRopeSim.TAUT_SLACK` 0.3 blocks of slack is drawn dead straight, easing
  into a free hang by `LOOSE_SLACK` 2.0; a line stuck to something moving (a whip's catch) keeps its growth, so whips
  and the midair lasso draw to both hands through the spin.
- **Web Whip (Sneak + R or G, no web out):** aimed within `WebWhip.RANGE` 16 (× symbiote reach) at a creature, a corpse
  (not a heavy one) or a block. A block tears out a small chunk (`PIECES` 4, nothing harder than `HARDEST` 3). The web
  catches it and you whip it about, one of three ways at random: **overhead** (up over your head, down in front),
  **seesaw** (down in front, then back over into the ground behind) or **lasso** (round you once, then overhead and
  down). Each slam hits for `DAMAGE` 9 (5 to anything else where it lands) and breaks a crater under it, bigger for
  bigger creatures (`CRATER_*`, from about 1.5 blocks for a chicken to over 3 for a golem). A chunk of ground bursts on
  its last slam into a creeper-sized crater (`CHUNK_CRATER`). Every swing whooshes, trails dust and jolts your screen;
  every slam throws block dust, smoke and a heavy mace thud, and shakes you, the one slammed and anyone within 12.
  Bosses can't be whipped; Living Armor's tendrils keep their own Sneak grab. `COOLDOWN` 20 ticks after one ends.

## Tendril slams, the surge and the tear-off (Andy's PS5 / Spider-Man 3 references)
Andy: "I'd also like the symbiote to animate its counters and applicable abilities more like these" (three contact
sheets in `/mnt/project-files/web-shooters/refs/`). Then: "enemies mobs affected by the symbiote should also get the
living armor tendrils, and it helps them attack". No blood anywhere in it.
- **Tendril bundles (`WebTendrilBundle`, client):** counters, Tendril Grab and the combos are drawn as a thick bundle
  of glossy strands (`STRANDS` 14, `GRAB_STRANDS` 6, `COMBO_STRANDS` 12) from the forearm and the back over the lead
  shoulder. Each strand roots on its own and gathers into the bundle (`ROOT_SPREAD`, `SPREAD`). The last `WRAP_SHARE`
  coils round the target `WRAP_TURNS` times, and the bundle lashes while it reaches and while it recoils (`LASH*`). In
  your own first person it comes from the lower corner of the view (`FP_*`). It is drawn from the synced
  `WebCounter.ACTIVE` and `WebCounter.bundle(kind)` timings, so everyone sees the same thing.
- **Counters as tendril slams (`WebCounter`, server; `WebCounterPose`, client):** each counter now runs reach, wrap,
  haul, slam and recoil (`Bundle(reached, wrapped, haulEnd, release, spear)`, `RECOIL_TICKS` 6).
  - Impale: lifts the target `IMPALE_LIFT` and slams it down where it stood.
  - Hurl: hauls it over your head (`HURL_APEX`) to the ground `HURL_BEHIND` behind you.
  - Whiplash: swings it round to your side (`WHIP_*`).
  - Constrict: holds it up in front (`CONSTRICT_*`), squeezes on `CONSTRICT_SQUEEZES`, then drops it.
  - Spear Flurry: stabs on `FLURRY_STABS`.
  - Jaw Snap, Maul and the Maw Roar: reel it to your face (`JAWS_AHEAD`).

  A slam does its damage, `SLAM_STUN_TICKS` of Slowness IV and a burst of black mass, dust and ground debris
  (`WebHaul.burst`, `SLAM_BURST`). It shakes your screen (`SLAM_SHAKE`) and a slammed player's (`SLAMMED_SHAKE`).
  Durations grew a little: Jaw Snap 18, Impale and Whiplash 20, Drag to Jaws and the Roar 24, Maul and Hurl 28,
  Flurry 30 (Constrict unchanged at 34). The body poses wind up, cast, heave and follow through.

  The back tendrils no longer strike during a counter (the bundle is the strike). A symbiote counter's bundle and pose
  play out even if the target dies. Hauling rules are in `WebHaul`: a target wider than `MAX_WIDTH` or taller than
  `MAX_HEIGHT` isn't hauled, nor is a dragon or wither.
- **Combos (`WebTendrilCombos`):** the thrash slam, clap, crush and shatter now land with the same burst (`BURST` 0.8).
- **Spike Burst as the surge (`WebSymbiote`, `WebBodyPose`, `WebTendrilBundle.surge`):**
  - It gathers first for `SPIKE_GATHER_TICKS` 6: crouched, arms crossed and trembling, with a heartbeat and sculk
    charge.
  - Then it bursts. The damage lands now, so it is 6 ticks later than before. Arms fling wide and the head is thrown
    back. `SURGE_TENDRILS` 18 long thick tendrils shoot out (`SURGE_SHOOT`), whip (`SURGE_WHIP`, `SURGE_SWING`) and
    dissolve into smoke from `SURGE_DISSOLVE`.
  - A dark shockwave rolls out (`WebSymbiote.surgeWave`, with `SURGE_RINGS` rings and `SURGE_EMBERS` embers) and your
    screen kicks by `SPIKE_SHAKE`.
  - The roar's blast uses the same wave and tendrils.
  - The spike hits spray black dust instead of blood.
- **The suit torn off (`WebSuitTear`):** rejection, the death pour-out, the radial suit toggle (off) and the debut
  withdraw set the synced `WebSymbiote.TORN`. The client sees it within `TORN_MATCH_TICKS` of the suit change and plays
  the tear instead of the plain withdraw, over `TEAR_TICKS` 46 (the withdraw was 36).
  - The wearer claws at the chest (`CLAW_END`), then wrenches it outward (`RIP_END`), then flings it off with the head
    thrown back, leaning by `LEAN_*`.
  - The suit splits from the middle of the chest outward (`CENTER_FIRST`, `PULL_SPAN`) in `COLUMNS` × `ROWS` strips
    (`STRIP_*`). The strips' ends cling longest and are pulled into the hands, and the sleeves slide off over the hands.
  - `STICKY` sticky strands stretch between the strips and the body and snap between `SNAP_MIN` and `SNAP_MAX`.
  - `THRASH_TENDRILS` tendrils thrash out of each hand's clump.
  - The mask peels by `MASK_END`.
- **Ridden mobs get Living Armor's tendrils (`WebBehemothClient`, `WebTendrilLayer.submitOnMob`):**
  - Every behemoth and every mob a loose symbiote inhabits wears the player's own back tendrils, drawn on its own model
    and sized to it.
    - Upright mobs root `UPRIGHT_ROOT` of their height up and `BACK_DEPTH` of their width back. Four-legged ones (wider
      than `QUADRUPED_WIDE` of their height) root on top of the back (`QUADRUPED_ROOT`).
    - Size is height / `SIZE_HEIGHT`, kept between `SIZE_MIN` and `SIZE_MAX`. A behemoth's sit out `BEHEMOTH_DEPTH`
      on its big body.
    - They grow out over `GROW_TICKS`. They are fully wild on hostile hosts and behemoths (`FERAL_HOSTILE`) and half
      so on peaceful ones (`FERAL_PEACEFUL`).
  - Each lash swings the tendrils on the target's side, and a short spear bundle (`LASH_STRANDS`) shoots from the back
    to the target.
  - The old world-space thrashing cords (`WebBehemothClient.render`) are gone, replaced by these tendrils.
  - **Tendril slam (`WebBehemoth.SLAM`, synced):** hostile hosts and behemoths now also grab-and-slam.
    - Every `SLAM_EVERY_MIN`-`SLAM_EVERY_MAX` ticks a `MOB_SLAM_STRANDS` bundle snatches prey within `SLAM_REACH`. It
      wraps the prey by `SLAM_WRAPPED`, hauls it over the mob's head (`SLAM_APEX`) and slams it `SLAM_LAND` in front at
      `SLAM_HIT`. All the back tendrils rear and crash down with it.
    - The slam deals `SLAM_DAMAGE` 7 (less than a lash's 10), `SLAM_STUN_TICKS` of slowness and a burst
      (`SLAM_BURST`), and shakes nearby screens (`SLAM_SHAKE`).
    - Lashes are unchanged in rate and damage.
  - Peaceful hosts show the tendrils but never attack.

## Weapon arm, counter animations, hulking big body (0.11.62)
- **Weapon arm (`WebMorph`):** in the suit or big body, with an empty main hand, left click (held since 0.11.91, see below) morphs the arm into a
  random weapon (never the same twice running, arms alternate) and strikes what you aim at within `REACH` 6 (with Living
  Armor up it forms on a tendril instead, and there are three more weapons: see "Weapon tendrils" below):
  - Mace (blunt): `DAMAGE` 10, `hurtArmor` x`ARMOR_WEAR` 4, breaks a bone `MACE_BREAK` 35% (shatters skeletons), slams back.
  - Scythe (slashing): 8, severs a limb `SCYTHE_SEVER` 45% (`Dismember.cut`), sweeps others within 2.5 for 60% at half the chance.
  - Spear (piercing): 9, `SPEAR_PIERCE` 35% of it ignores armour, armour wear x4, drives straight back.
  - Timing: `TICKS` 16, forms at `FORM` 4, lands at `HIT` 8, `COOLDOWN` 12 ticks; holding the button chains strikes.
    A block under the crosshair is still mined. Drawn by `WebMorphLayer` (cord + weapon head, arm pose).
- **Counter animations (`WebCounterPose`):** all 18 counters (9 web, 9 symbiote) have their own body animation,
  driven by the synced `WebCounter.ACTIVE`; the side is picked from game time so server and client agree.
- **Hulking big body (`WebHulk`, mesh from `tools/gen_web_hulk.py` → `WebHulkMesh` + `web_symbiote_hulk.png`):**
  since 0.11.99 built like a towering green-giant hero (Andy's pick, the TG Heroes Hulk as reference only: that pack is
  All Rights Reserved) but all symbiote: 46 boxes, upright, a broad chest over deep lats, huge shoulders, thick arms
  ending in big fists with bone spurs out of the forearms, tree-trunk legs with clawed toes, the head up on the traps
  (`HEAD_DOWN` -0.5, `HEAD_FORWARD` 2), arms out `ARM_OUT` 5, legs `LEG_OUT` 2.2. Living Armor swells you 1.5x
  (`WebSymbiote.ARMOR_SCALE` 0.5, was 0.25) and stands leaning, not hunched (`WebFeral.IDLE_BEND` 0.35, was 0.75). A hinged fanged jaw breathes and
  snarls (`JAW_REST` 0.22); a red tongue (12 pieces; it used to loll out and lash every 70 ticks, but Andy: "the tounge
  should only lash out periodically") stays in the mouth and flicks out quickly every `TONGUE_EVERY` 140 ticks (7 s,
  `FLICK_TICKS` 12, the jaw opening `JAW_FLICK` wider), and once just after a meal ends (`WebHulk.noteMeal`). No emblem (earlier ruling).
  - **Elbows and knees (Andy: "it should animate swinging at the elbows and knees"):** the mesh hangs each forearm (fist,
    knuckles, spurs) off an `elbow` part (`ELBOW` (0, 8.5, 2), toward the back) and each shin (calf, foot, toes) off a
    `knee` (`KNEE` (0, 5.5, -2.5), toward the front), so no gap opens as they bend. `WebHulk.Model.limbs` (after the
    poses, from `WebSuitGrowth.applyModel`) bends them: elbows `ELBOW_REST` 0.25 plus up to `ELBOW_SWING` 0.75 trailing
    the arm's swing by `ELBOW_LAG` 0.6; knees `KNEE_WALK` 0.15 plus up to `KNEE_SWING` 1.1 as each leg swings through
    (walk and gallop); a raised thigh folds its shin back `KNEE_CROUCH` 2x (feet stay under the hips in the hunch, the
    leap's coil, the slam's drawn-up knees), sitting `KNEE_SIT` 1x. Standing, the elbows breathe (`ELBOW_IDLE`) and the
    arms rock (`IDLE_SWAY`). A hand holding something, punching or forming a weapon keeps `ELBOW_HOLDING` 25% of the bend.
  - **The maw's tongue (Consume, `WebMouthLayer.tongue`, Andy: "consume large head should use the animated tongue"):**
    the same tongue (`WebHulk.tongue`, 14 pieces, wider) tastes the air as the maw swells out (half length), then
    `TONGUE_LEAD` 7 ticks before the last bite lashes out at the meal and is reeled back into the mouth as the jaws snap shut.
  The behemoth (zombie-player host) still uses the old inflated body.
- **Equip animation (`WebSuitGrowth` + `WebSuitWrapLayer`):** tendrils leech out and coil round the body, arms, head,
  then legs from each joint (`WRAP_SPAN` 0.3); raw goo folds over each part (`GOO_AFTER` 0.28, `LAYER_SPAN` 0.26), then
  the suit rolls over it (`SUIT_AFTER` 0.14) with a band of tendril at its edge, as the strands sink in. Building the big
  body, thicker tendrils wrap the new frame, then each muscle is slapped on in turn (`WebHulk.BUILD_*`). Off runs backward.

- **Swinging in the suit:** the whole suit arm streams into the line: `WebLineRenderer.armSheath` peels
  `SHEATH_STRANDS` 7 tendrils off the arm from the shoulder, flares them past the hand (`SHEATH_FLARE` 0.32) and braids
  them into the cord `SHEATH_MERGE` 2.6 blocks out. Not in your own first person, nor on two-handed catches.
- **Cracks linger (`WebCracks`):** Wall Crawl bites (`WebTendrils.CLIMB_CRACK` 5), walking tips (`WebTendrilWalk.PLANT_CRACK`
  3) and the wall tethers crack the block they grab; the crack holds `HOLD_TICKS` 100 after the last bite, then fades a
  stage at a time over `FADE_TICKS` 200. Server cracks reach everyone; walking-tip cracks are worked out on each client.
- **Doc Ock Wall Crawl:** off the wall, with Wall Crawl on, four tendrils carry you (`WebSymbioteClient.stride`,
  `WebTendrilWalk.striding`): hoisted `STRIDE_HEIGHT` 1.2 off the ground, walking at `STRIDE_SPEED` 0.2 (sprint 0.3),
  each tip reaching `STRIDE_REACH` 4.5 for whatever is near (ground, walls, ceilings, trees) and stepping when it trails
  `STRIDE_STEP_FAR` 1.1. Jump climbs (needs `STRIDE_RISE_ANCHORS` 4 ways to grab), Sneak lowers, Sneak on the ground
  stands you on your feet; nothing to grab within reach and you fall. Walls and ceilings still climb as before. While the
  four walk, four spare tendrils (`WebTendrilLayer.FIRST_SPARE`) grow, so strikes, grabs and the barrage keep theirs.
- **Ceilings and withdrawing (0.11.83):** striding with a ceiling within `STRIDE_REACH` overhead, the front pair
  (`WebTendrilWalk.CEILING_DIRS`) bite the ceiling and walk along it while the back pair walk the ground; you still only
  walk. When the tendrils draw back in (Wall Crawl or Living Armor ending), they go the suit's way: strands pour over the
  body and coil round each part, then unwind into the chest (`WebSuitWrapLayer.retract`, `RETRACT_WRAP` 0.45, over
  `WebSymbioteClient.WITHDRAW_TICKS` 40).
- **Hunger takeovers (0.11.83):** hungry (satiety under `WebSymbiote.HUNGRY_HUNT_BELOW` 45), each feeding check (every
  `SELF_FEED_EVERY` 20 ticks, off the takeover cooldown) has `HUNGRY_HUNT_CHANCE` 8% that it takes the wheel, hunts the
  nearest creature, eats it and lets go: strictly to feed, never a player. In a low mood (stress at `LOW_MOOD_STRESS` 60+)
  it's a spree instead: after eating it keeps killing creature after creature (`spreeOn`) until you break free or nothing
  is left near. A spree can always be fought (`SPREE_BREAK_FREE` 80 presses if you couldn't break a hold yet; synced
  `SPREE` attachment).
- **Slam latch (0.11.83):** with Wall Crawl on, pressing Sneak against a wall slams both tendrils into it at once (no
  reach first; the wall cracks at both bites), throws you at it (`WebSymbioteClient.LATCH_SLAM` 0.35, lifted
  `LATCH_LIFT` 0.3 off the ground) and pins you for `LATCH_HOLD_TICKS` 8. Once a tendril has bitten the wall, the
  tendrils are fully out at once.
- **Ceilings like walls (0.11.91):** pressing Sneak with a ceiling within `CEILING_LATCH_REACH` 1.8 over your head
  (`CEILING_LATCH_GROUND` 0.9 standing; by a wall only when looking up past `CEILING_LATCH_LOOK` 30°) slams both
  tendrils up into it (cracking it at both bites), yanks you up at `LATCH_SLAM` + `CEILING_SLAM_EXTRA` and pins you
  `LATCH_HOLD_TICKS`; then the keys drag you along it upside down as before. Another Sneak press drops you (holding
  Sneak no longer does). Jump against a ceiling still sticks you too.
- **Hold-to-form weapon arm (0.11.91):** left click now gathers while held (`WebMorph.Gather`, synced `CHARGE`):
  lumps course over the suit, front and back, to the striking shoulder (`COURSE_TICKS` 8), run down the arm and swell
  the forearm (`COLLECT_TICKS` 13), and the weapon grows out of the hand (`FORMED_TICKS` 18). Letting go strikes
  (`WebMorph.Strike`), at once if formed, else as soon as it forms; held, the weapon stays ready (dropped after
  `CHARGE_MAX_TICKS` 600). One strike per press now. Drawn by `WebMorphLayer.submitGather`.
- **Bladed barrage (0.11.91):** stabbing in a barrage, each tendril's tip is a blade: a barbed spear point or a
  hooked claw blade, turn about (`WebTendrilLayer.blades`, sizes `BLADE_*`).
- **Tendrils stay out (0.11.99):** with Wall Crawl on, the tendrils are drawn the whole time (`WebSymbioteClient.extract`), not only while they hold you; holding Jump (a coiled leap, nothing to grab) used to make them vanish. They go only when Wall Crawl is switched off.
- **Charged leap on Wall Crawl (0.11.99):** in the suit, holding Jump while the tendrils carry you coils them (you sink to `WebSymbioteClient.STRIDE_COIL` 0.55 of the stride height) instead of climbing; let go and they fling you with the charged leap, as from the ground (`launchable`), letting you fly for `LEAP_GRACE_TICKS` 8 before they grab again. On a wall or ceiling Jump still climbs. Without the suit, Jump still climbs the stride.
- **Tendril Grab looks like Wall Crawl (0.11.107):** hung off a wall, the six tethered tendrils are drawn as Wall Crawl's jointed walking limbs (knees out from the wall, tips bitten into their cracked spots, `WebTendrilLayer.tetherLegs` into `bend`) instead of straight taut cords; they reach out one after another as before. The hold is unchanged: still pinned to where you bit in, rubber-banding back and forth.
- **The big body's charged leap (fix):** "large suit charge jump doesnt seem to work". The charged jump was only for
  `suitShown` (the suit flag), and Living Armor at a whole bond wears the big body without that flag, so Jump was a
  plain hop. `WebSymbiote.leapBody` (suit or big body) now gates it (`WebSwing.holdingCharge`/`tick`, the server's
  `ChargeHold`). In the big body, Jump held on the ground coils the leap; held in the air it's still the slam.
- **Meals by size:** Consume's meal goes by what the corpse was in life, standing (`WebSymbiote.mealSize`:
  sqrt(width² × height / `MEAL_FULL_VOLUME` 8) of its type; the corpse's own box is low and narrow, so a chicken and a
  ravager used to be almost the same meal). Your hunger: `MEAL_FOOD_MIN` 1 to `MEAL_FOOD_MAX` 16 points (chicken 3,
  cow 7, horse 10, ravager 16) at saturation 0.3-0.8; what a full bar can't take heals you, `MEAL_OVERFLOW_HEAL` 1
  health a point, on top of `CORPSE_HEAL_MIN` 2 to `CORPSE_HEAL_MAX` 10. The symbiote: 30-80 satiety, as before.
- **Climate takeover:** "Spending too long in hot or cold climates will make the suit takeover and pathfind until he
  finds a new one." In a biome with base temperature at least `CLIMATE_HOT` 1.5 (desert, savanna, badlands, the
  Nether) or at most `CLIMATE_COLD` 0.15 (snowy and frozen biomes, peaks), time adds up (`CLIMATE_TICKS` 2400, 2 min;
  it drains twice as fast anywhere temperate). At half it complains (`climate.<hot|cold>.uneasy.*`), 20 s before
  (`CLIMATE_WARN_TICKS` 400) it warns you with a full dread, a shake and a heartbeat (`.warn.*`), then it takes the
  wheel (`startMigrate`: dread, shake, `.go.*`). It finds the nearest temperate land biome (not sea or river) within
  `MIGRATE_SEARCH` 640 at your ground's height (`findClosestBiome3d`) and walks you there as a sightseeing tour
  (`tourSight` "climate"), in legs of up to `MIGRATE_LEG` 20 blocks (a `TOUR_TAG` marker each, veering round water and
  never onto lava), each allowed `MIGRATE_LEG_TICKS` 600. Once your feet are in a temperate biome it lets go
  (`.there.*`). Stuck on a leg, past `MIGRATE_MAX_LEGS` 48, or nowhere to go (the Nether: `.lost`), it gives up and
  tries again after `MIGRATE_RETRY` 1200. Not in creative; struggling breaks it as any takeover (then the usual
  takeover cooldown).
- **Wall Crawl and Living Armor (0.11.66):** the tendrils carry you whenever Wall Crawl is on, flat ground included.
  When Living Armor withdraws or is put away, Wall Crawl switches off with it (`WebSymbiote.set`). At full bond
  (`BOND_MAX`) Living Armor no longer times out at `ARMOR_MAX_TICKS`: it stays until you put it away.

## Weapon tendrils, and three more weapons (after 0.11.98)
Andy: "the weapon on left click should be adapted to the living armor tendrils ... with 3 more variants."
- **On a tendril:** while Living Armor is up (`WebSymbiote.armored`: its tendrils out, or the big body), the held left
  click forms the weapon on a tendril instead of the arm (`WebMorph.onTendril`; synced as `tendril` on `CHARGE` and
  `MORPH`). Living Armor without the suit now counts too. Gathering, the striking side's shoulder tendril (0 right,
  1 left) rears into a coil out to its side and shudders, its tip swelling into the weapon head; the mace and the
  hammer-fist are two tendrils braided (with 2 or 3, just inside). Let go and it winds up its own way (mace reared over
  the head, hammer-fist dropped low, scythe drawn wide, spear coiled short like a spring, whip-blade thrown far back,
  hook claw raised), lashes out root first with the tip trailing, a ripple runs back up it at the impact (the hook reels
  in), and it eases back to rest. The arm stays put (no swing). Same timing, reach, damage and effects as the arm; the
  server doesn't care which. Drawn by `WebTendrilLayer` (`TendrilModel.morph`, `heads`; tuning `MORPH_*` at the top),
  with the heads from `WebMorphLayer.weapon`. Outside Living Armor the plain suit keeps the arm weapon unchanged.
- **Whip-blade** (`WHIP`): reaches `WHIP_REACH` 9, cuts up to `WHIP_HITS` 3 along the line through the target (within
  `WHIP_WIDTH` 0.9; each after the first `WHIP_FALLOFF` x0.8 of the one before, from 7), flicks the near ones aside and
  drags the last toward you (`WHIP_PULL` 1.1). A lash of barbed links to a thin blade; the arm swings back low, then
  up over the shoulder and cracks it out, the cord bowing and waving (`WebMorphLayer.WHIP_LAG`, `WHIP_WAVE`).
- **Hammer-fist** (`HAMMER`): 9, thrown up `HAMMER_LIFT` 1.15 (back 0.45), stunned `HAMMER_STUN_TICKS` 30 (Slowness IV,
  Weakness II), the block under it bursts into dust. A knuckled block of a club; an uppercut from low and back.
- **Hook claw** (`HOOK`): reaches `HOOK_REACH` 8, 6 damage, reels the target in to just short of you (`HOOK_YANK`,
  at most 1.6 b/t), then it bleeds `BLEED_DAMAGE` 1.5 (through armour) every `BLEED_EVERY` 10 ticks, `BLEED_HITS` 4
  times, dripping red. Three curved bone hooks, a grapple; the arm raised, flung out, yanked back as it bites.
- Never the same weapon twice running still holds (six now). The client aims with the forming weapon's reach
  (`WebMorph.reachNow`, from the synced charge); the server checks it against `reach(weapon)`.

## Andy's symbiote and whip changes (0.9.8)
- **Rigid tendrils:** Wall Crawl's walking tendrils are jointed limbs (two straight sections, a knee out from the
  surface, fixed length `WebTendrilWalk.LIMB` 32 px), Doc Ock style. Walking, running or climbing, every other tendril
  stops swaying and holds its curl in two joints. Since 0.10.100 (Andy: too stiff) only the planted walkers stay rigid;
  the rest stream back and ripple on the move (`WebTendrilLayer.DRAG_*`).
- **Whip needs both webs:** the first Sneak + R/G only catches (a creature dragged nearly still, a chunk hanging where
  it tore); Sneak + the other key within `WebWhip.CATCH_TICKS` 60 puts the second web on it and starts the whip. In
  midair it's always the lasso and you hang in place for it (no gravity, held still); the seesaw only on a living
  creature; anything else is the overhead.
- **Barrage combo:** one creature held by a tendril, the other empty, hold left click: it's pinned in front and the
  outermost pair of tendrils (`BARRAGE_FREE`; walking, the outer spares) stab it left and right by turns (the rest held poised), a stab every `BARRAGE_EVERY` 10 ticks (half a second) (i-frames ignored), 3 damage rising to 9 over
  `BARRAGE_RAMP_TICKS` 60; letting go of the button (or `BARRAGE_MAX_TICKS` 160) ends it.
- **Behemoth (symbiote in a zombie player):** all 6 back tendrils thrash wildly and each lashes on its own at a random
  living thing within `STRIKE_REACH` 7.5 (10 damage, 1-5 ticks between a tendril's lashes; normal i-frames).
- **Shroud** lasts 5 s (`SHROUD_TICKS` 100); the suit's debut after bonding is 1.5 s (`SUIT_DEBUT_TICKS` 30).
- **Suit after death:** the suit's grow/withdraw animation now runs on game time; on the player's own age it held a
  respawned player in the suit until their new age caught up with the old one.
- **The blob** walks on rigid two-section tendrils (`WebBlobRenderer.LEG_LENGTH`), and reads its movement from its
  position change (clients don't keep a mob's velocity, so it used to slide with its legs idle).
- **Blob inhabits mobs (`WebInhabit`):** with no web slinger or risen body in reach, a loose blob takes the nearest mob
  it can (`takeable`: cow, chicken, sheep, pig; zombies of every kind, skeletons, illagers, witches, piglins). It wears the suit over its own model (`web_symbiote_goo.png`, drawn like
  glowing eyes). A peaceful host is spat out after `CALM_MIN`-`CALM_MAX` 20-40 s (reels with Nausea, Slowness,
  Weakness; lives). A hostile one (`Enemy`) keeps it `HOSTILE_MIN`-`HOSTILE_MAX` 60-90 s: +20 health, +4 damage, +25%
  speed, lashing with behemoth tendrils at everything near, losing `DECAY` 1.2% of max health a second (up to x3 near
  the end), and killed when its time is up. Whenever a host dies or is rejected the blob drops out there, rests
  `MOB_REST_TICKS` 100, and never takes that mob again.
- **Behemoth (your risen body):** never lets go until killed; +160 health, +10 damage, and the near-death takeover moves:
  a roar every `ROAR_EVERY` 300 ticks with prey near and at half and quarter health (10 damage, hurled, Resistance III
  while it roars), a frenzy under half health (lashes twice as often, x1.4, 18% snatch and hurl), and under a quarter
  the Shroud (5 s unseen, Regeneration III, Speed, a leap away) once a minute.
- **Suits under Fresh Animations:** while the plain suit, the big body or its growth shows, `PoseTakeover.posed` pauses
  EMF/FA so the suit models line up with the body. Since 0.11.62 the big body is its own hulking model
  (`WebHulk`, see below); MCA player models hide their own skin, clothes and hair layers under it.
- **Into the big body:** the bulk takes 3 s (`WebSuitGrowth.BULK_TICKS` 60): doubled over in pain, clutching the
  middle, spasming, knees buckling, head hanging and jerking (`WebFeral.agony`), then up into a deeper feral idle
  (`IDLE_BEND` 0.35, arms low and wide, knees bent, head twitching).
- **Overhead slam (`WebSlam`, big body):** jump and keep holding Space in the air: over `RAISE_TICKS` 10 it rears up,
  both fists over its head. Land still holding it (held at least `MIN_TICKS` 5) and it slams: `DAMAGE` 12 at the
  centre (half at the edge, +0.8 per block fallen, up to +10) to everything within `RADIUS` 4.5, hurled out and up, a
  shallow crater (`Debris.smash` at 1.5+, so dirt and sand go, stone mostly holds), a ring of debris, an explosion
  burst and shaking screens within 16. Letting go in the air calls it off. `COOLDOWN` 20 ticks.
- **Villagers too:** the loose blob also takes villagers (always rejected in the end, like the farm animals).
- **Settles:** a blob with no host for 5 minutes (`WebSymbioteBlob.SETTLE_TICKS` 6000) sinks into the ground as a goo
  block where it is.
- **Quick dive:** Shift + Space while gliding on the wings tips you straight into the dive; slowing to `DIVE_AT` still
  does it too.
- **Dive slam (0.10.12):** coming down straight out of the freedive (at least `WebTrickClient.DIVE_SLAM_SPEED` 1 b/t,
  sideways drift under half the fall) lands in the superhero landing and breaks the ground: a `Debris.smash` at the
  dive's speed, a debris ring, and everything within `WebSlam.DIVE_RADIUS` 4 hurt (6 + 4 per b/t, half at the edge)
  and thrown out. The server checks its own record of the fall (6+ blocks, 1+ b/t, landed within 8 ticks).
- **Jump cuts a zip (0.10.12):** pressing Jump at any point of a Web Zip or Yank-to lets go of the line; you keep the
  speed it gave you. A Jump already held when the zip starts doesn't count until it's pressed again.
- **Web block catch:** the whip's first web on a block now pries for 1.5 s (`WebWhip.PRY_TICKS` 30), the blocks
  cracking, before the chunk tears out and hangs there for the second web.
- **Tendril debris pull** takes a flat 0.5 s (`TUG_MIN`/`TUG_MAX` 10) and can wreck spawners (monster and trial): aimed
  at one, or in its crater, it's broken as if mined when the chunk tears out.
- **Takeover wall ripping** tears a small crater each pull: the blocks in the way plus the breakable ones round them,
  up to `WebTakeoverPath.RIP_CRATER` 9 within 1.8 of the middle, never below the gap's floor or the block under you
  (`RIP_MAX_BLOCKS` 96 per takeover).
- **It talks more:** narration is likelier and closer together (see Narration). On top of that it **remarks on your
  surroundings** every `WebSymbiote.REMARK_GAP_MAX` 1400 (weak bond) to `REMARK_GAP_MIN` 500 ticks (+ up to 300):
  `see.<topic>.1-3` for storm, rain, night, deep, high, hot, cold, water, village, monsters, beasts, player, corpse,
  lava, or quiet when there's nothing (one picked at random from what applies).
- **Sightseeing takeovers:** a calm, fed bond has `TOUR_CHANCE` 4% each takeover check (after the neglect roll, before
  curiosity) to walk you somewhere it wants to see, 10-28 blocks off: a high point (6+ up), the shore of some water,
  flowers or a villager, never toward lava, only with open sky above. It says why (`tour.<sight>.go.1-3`), drives you
  there on the takeover path (an invisible `Interaction` marker tagged `TOUR_TAG` is the target; leftovers are
  discarded on load), and once within `TOUR_REACH` 2.5 turns your head out over it, says so (`tour.<sight>.there.1-3`)
  and lets go. It doesn't fight on the way; it gives up after `TOUR_TICKS` 600; struggling breaks it as usual.
- **Landings:** fast across the ground (`ROLL_SPEED` 0.55) is always a roll, however far the drop; slower, a 2.5+ block
  drop eases down into the superhero landing (over `WebBodyPose.HERO_IN` 30% of a 28-tick pose).

## Controls

| Key (default) | Ability | Cooldown |
|---|---|---|
| **R** (hold) | **Web Swing.** Sticks a web where you aim. If you aim at the sky or far below you, it auto-picks an anchor above and ahead of you. Hold to swing; release to let go. While swinging: **W** on the downswing pumps speed, **Jump** reels in, **Sneak** lets out rope. | none |
| **G** | **Web Zip.** Fires a web at the crosshair block (48 blocks) and reels you to it. Hitting a wall hops you up onto the ledge if there's one within 3 blocks. | 3 s |
| **V** | **Web Shot** (web shooters with fluid only; one web a shot). Tap as fast as you like: pellets, five hits in a row cocoon the target. Hold: the charged blast. Sneak + tap: web bomb. | pellets 0.1 s; blast 1.5-5 s; bomb 6 s |
| **B** | **Yank.** Webs the entity under the crosshair (24 blocks). Small things are flung to land about 2 blocks in front of you. Things heavier than you can haul (bounding box over 4 m³, more in the suit or with friends holding it on their lines too, e.g. iron golems) pull **you** to **them**; at a torch, lantern or bell it tears it off. | 6 s |

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
  - The rope is a distance constraint from the anchor to hand height (+1.2). If you're at or past the rope length,
    you're put back on its sphere and only the outward velocity is removed (a true rope since 0.12.3; it was a springy
    pull back before). That turns falling into a pendulum arc, and the arc naturally speeds up at the bottom.
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
    out, you're stuck for 3 ticks, or you press Jump (it tells the server with a main-hand `web_swing` let-go, which
    drops the line).
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
| `ROPE_PASSES` / `ROPE_MAX_CATCH` | 2 / 1.5 | The true rope (0.12.3): passes a tick (two webs settle), and the most it moves you back onto its length in a tick. |
| `SWING_GRAVITY` | 0.025 | Extra gravity while swinging, on top of vanilla's 0.08. More gives faster, heavier arcs. |
| `SWING_DRAG` | 1.0 | Velocity kept per tick on the web, after vanilla's drag is undone (1 = gravity only; was 0.993). |
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

## Fewer takeovers (0.8.37)
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
- **Landing** (after 8+ ticks in the air off a web or the wings): fast across the ground, a **forward roll**; slower,
  a 2.5+ block drop eases into a **three-point** superhero landing; off the wings, the **flare and spin** into a crouch (a roll if
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
- **Suit (0.10.105):** `WebSymbioteLayer` draws an original 64x64 texture over the whole player model: the classic
  black suit (flat matte black, faint blue-grey sheen, big angular white eyes with black borders, no emblem). Veins
  (`web_symbiote_veins_*`) show only while feral (bond under `VEIN_BOND` 40), while bulking, and on the big body. It covers the skin's outer layers too.
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
| 2 | Wall Crawl (toggle) | 20 | 0 | Client movement (`WebSymbioteClient.tickCrawl`). Next to a wall in the air, tendrils latch on and drag you the way you're moving (0.8.6: see below); Sneak goes down. Under a ceiling, Jump or a Sneak press slams you up into it, you move with the movement keys, and another Sneak press drops you. Climbing over a top edge hops you onto the ledge. The server allows floating while it's on and resets fall distance while you're touching a wall or ceiling. |
| 3 | Consume | 160 | 0 | Nothing is eaten alive (0.8). A corpse you carry is eaten. Else a free corpse within `CONSUME_CORPSE_RANGE` 6 (the one you look at first) is grabbed by a tendril, carried, then eaten. Else a non-player creature at **current** health <= `CONSUME_MAX_HEALTH` 10 within `CONSUME_RANGE` 3 is finished off by a lash (`CONSUME_FINISH_LAG` 8 ticks), and its corpse grabbed, carried and eaten. Tamed, name-tagged, inedible and corpse-less creatures are never taken. By the creature's size (see **Meals by size**): your hunger 1-16 points, heals 2-10 (more past a full bar), feeds it 30-80. |
| 4 | Spike Burst | 240 | 8 | Everything within `SPIKE_RADIUS` 5 takes `SPIKE_DAMAGE` 8 (player attack) and is thrown `SPIKE_KNOCKBACK` 1.3 outward and 0.45 up. 38 rigid black spikes shoot out of the whole body (`WebSpikeLayer`, pose `WebPose.SPIKES`, `SPIKE_TICKS` 16); each victim flashes (`spikeHit`) and bleeds (`BladeBlood`). The roar throws the same spikes. |
| 5 | Shroud | 600 | 5 | Invisibility (no particles) until you damage anything, or `SHROUD_TICKS` 100 (5 s). |

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
     first. No time limit (0.10.10): it only gives up if the prey gets `TAKEOVER_ESCAPE` 64 away or is gone, or you
     break free. A hungry or rampage takeover that kills its own prey also goes on into feeding. Either way it keeps
     the wheel through grab, carry and meal (`holdForMeal`: the takeover's target becomes you, and your client holds
     still), letting go when the meal ends (or when the post-meal escape leap fires). Takeovers no longer end on a
     timer (`TAKEOVER_LIMIT` is only the hold the client is told, renewed by `renewHold`).
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
(`WebTendrilCombos.kind`, plus `crosshairTarget`: the living thing under the crosshair within `CRUSH_REACH` 12, seen past
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

**Narration:** `WebSymbiote.narrate(player, act)` speaks `say.<act>.1-3` with a chance from `NARRATE_MIN` 0.25 to
`NARRATE_MAX` 0.95 by bond (0.9.8; was 0.1-0.9), at most every `NARRATE_GAP_MAX` 400 to `NARRATE_GAP_MIN` 80 ticks. Every line is a
whisper, so the vignette, dark screen and growl play each time.

**Takeovers can't be interrupted.** `WebTakeoverInputMixin` zeroes movement while it drives, and clicks, power keys,
mode, ultimate and the menu key are swallowed. Each fresh press is a struggle (`WebNet.Struggle`): once you've
earned `BREAK_FREE_ABILITIES` 3 abilities, `BREAK_FREE_BASE` 80 - 6 per ability presses (44 with all six, since 0.10.10; the
count fades by `TAKEOVER_STRUGGLE_FADE` 0.25 per tick) break its hold, which
adds `BREAK_FREE_STRESS` 10 stress. A meter shows on the HUD (a tick mark at half).

**Struggle lockout, ripping out, quicker takeovers, the gift.**
- *Self takeovers are 2x quicker* (`SELF_TAKEOVER_SPEED` 2.0; every takeover is one it starts itself): the suit and big
  body engulf you in half the time (`WebSuitGrowth`, a change made while `WebSymbioteClient.seized`), the dread swells in
  twice as fast (the struggle bar drains as before; doubled it was unwinnable). Put on by hand (`DONNED`), the suit
  or big body lashes its tongue once as the head forms (`WebHulk.DON_LASH_AT`, `submitSuitTongue` on the plain suit).
- *Lockout:* push the bar past `LOCKOUT_SURGE` 0.5 twice in one takeover (it must fall back under `LOCKOUT_REARM` 0.25
  between: "it surges back over you", shake `SURGE_BACK_SHAKE` 0.8, full dread, a `surge.1-3` taunt) and you win: the
  takeover ends and the suit is disabled for `SUIT_LOCKOUT_TICKS` 1200 (`LOCKOUT` attachment, saved; counts as
  `sulking()`, so no suit, Living Armor, big body, hunts, defence roar or takeovers), with a `lockout.1-4` remark;
  asking for the suit or Living Armor answers `locked_out.1-3`; it ends with `lockout_over`.
- *Ripping out:* the server syncs the bar to everyone (`STRUGGLE`, 0-1 in steps of `STRUGGLE_SYNC_STEP` 0.02, present
  for the whole takeover; your own client uses its local tally). `WebSuitGrowth.PULL` (the share plus a back-and-forth
  tug, `TUG_WOBBLE`) takes the big body apart from its last-built pieces (head and jaws, claws, hands: `HULK_PEEL`
  0.55 at a full bar; the tongue draws in with it), shows the suit under it, and peels the suit back off each part toward
  its joint (`PEEL_START`/`PEEL_MAX`: the face first, then arms, chest, barely the legs), with `WebSuitWrapLayer`'s
  tendrils clinging to the bared parts. Each press shakes your view (`STRUGGLE_SHAKE` 0.25 + 0.6 × bar); the takeover
  vignette and dread give way as the bar fills (`HUD_CLEAR` 0.6, `STRUGGLE_CLEAR` 0.55) and flicker.
- *The gift:* any takeover ending (but the lockout) has `TAKEOVER_GIFT_CHANCE` 10% to leave you its Living Armor, big
  body and all, yours to drive for `TAKEOVER_GIFT_TICKS` 1200 whatever the bond (no idle withdraw; you can always put it
  away), with a `gift.1-3` remark; then `gift_over` and it takes it back (at a whole bond it's yours anyway).
- *Manual equip signal:* `WebSymbiote.DONNED` (synced to all, game time) is set when the suit or Living Armor is put on
  from the radial menu, never by a takeover: for the manual-equip tongue animation.

**Takeover combat:** the tendrils do the fighting: `FRENZY_TICKS` 18 strikes every `FRENZY_EVERY` 4 ticks for
`FRENZY_DAMAGE` 9, with a `FRENZY_SNATCH` 18% chance to snatch and hurl the prey. Its own punches slowed to
`TAKEOVER_ATTACK_TICKS` 20.

**Charged cross (click Smash with a chunk, `WebTendrils.smash`):** `CROSS_WINDUP` 22 ticks drawing the chunk back and
across, `CROSS_SWING` 5 whipping it through, `CROSS_FOLLOW` 8, then `CROSS_RECOVER` 18 before anything else. Damage
6 + 1.5 per piece (max 16).

**Debris pull (Sneak+R/G at a wall):** the tendril reaches in, then hesitates and struggles (0.5 s, `TUG_MIN`/`TUG_MAX` 10
since 0.9.8; spawners are wrecked), cracking the blocks, before it tears the chunk out and hauls it in. Letting go
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
- **Dodge (from 0.10.2, before ascending too):** hits from an entity (melee or projectile; nothing that bypasses
  invulnerability) are cancelled (`ALLOW_DAMAGE`) while a sense charge is ready; each charge comes back after
  `DODGE_COOLDOWN` 400 ticks. Charges grow with the power (`senseCharges`): `CHARGES_BY_UNLOCKED` {1, 1, 2, 3, 4} by
  abilities unlocked, `DODGE_CHARGES` 5 once ascended. You get a sidestep nudge square to the attack (`web_nudge`),
  an amethyst tingle, and sparkles. The red outlines stay ascended-only.
- **Counters (`WebCounter`):** for `WINDOW` 30 ticks after a dodge (`web_counter_ready`, owner-synced), right click
  (consumed client-side) counters the dodged attacker within 7, else whatever's under the crosshair within 10. A
  random move, never the same twice running. Web: Spinning Heel Kick, Yank and Uppercut, Web Slam, Blinding Jabs,
  Flip Kick, Cocoon Throw, Zip Kick, Trip Sweep, Pinned Haymaker. With the symbiote suit showing: Impale, Whiplash,
  Hurl, Constrict, Spear Flurry (tendrils, drawn with the symbiote's own `STRIKE` plus `TENDRILS_OUT`; `WebSymbiote`
  skips its own damage for these via `WebCounter.ownsStrike`), Jaw Snap, Maul, Maw Roar, Drag to the Jaws (the maw,
  drawn by `WebMouthLayer` from `web_counter` / `WebCounter.maw`). The maw only bites; nothing living is fed on.
  Damage and timings are constants at the top of `WebCounter`.
- **Sense bolts (0.10.10, `WebSenseBolts`):** no text on a dodge or counter. While the counter window is open
  (`web_counter_ready`, now synced to everyone), three faint pale-gold lightning bolts flash over the slinger's head
  every 6 ticks, fading through the window; in first person your own flash at the top of the screen.
- **Tendril-held corpse (0.10.10):** while a tendril grips a Kitchen corpse, a plain right click (no Sneak, any target)
  releases it into your arms (`WebTendrils.CarryHeld` → `carryHeld` → `KitchenCompat.carry`).
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

## 0.10.21: stronger Web (`WebStrength`)
Andy asked for a stronger Web (ideas remade from Powerborne Heroes' Spider-Man, which is 1.20.1 Palladium and ARR).
- **Spider physique** (passive): +3 attack damage, +15% speed, +25% jump, +0.4 knockback resistance, 30% softer
  landings (fall damage still applies), each scaled by abilities unlocked (a fifth at a fresh awakening).
- **Impact web**: a Web Shot charged past half pins what it hits against the wall behind it (within 3 blocks) or
  where it stands, 3-6 s; pinned things take 1.5x damage (`WebLivingEntityMixin`).
- **Taser web**: V while the Yank web holds something: 4 damage (lightning type), a stun, 1 s between shocks.
- **Web bomb**: Sneak + tap V lobs a ball that cocoons everything within 4 blocks for 4 s (6 s V cooldown).
- **Webbed fists**: X (Sneak + X with a symbiote): 10 s of sticky melee; each hit slows harder, the third
  cocoons for 3 s; 30 s cooldown.
- Numbers: web shot 2-14 damage (symbiote 3-22), pellets 3 (4), shot cooldown 30-100, zip 40, Yank 80;
  Yank and entity webs drag bigger things (horses now); Spider-Sense charges 2/2/3/4/5, 7 ascended, 15 s recharge.

## 0.10.29: parkour (`WebParkour`, `WebParkourClient`, `WebParkourPose`)
Andy asked for ParCool's moves on Web (ParCool is Forge/NeoForge only and stops at 1.21.8; LGPL, but this is our own
code). No keys of their own:
- **Vault:** sprint into a one-block obstacle with room above it: a hand planted on it, legs swung over
  (`VAULT_UP` 0.5, `VAULT_FORWARD` 0.42). Fences (1.5 high) and two-high walls don't count. Since 0.11.19 one of
  four looks at random, never the same twice running: kong, lazy vault, monkey flip, thief vault. The server picks
  it (`WebParkour.variant`, in the synced `MOVE` as `(variant * 8 + kind) * 8 + face + 1`); the poses and their
  knobs are in `WebParkourPose`.
- **Wall cling:** in the air, facing a wall, hold Sneak: you hang on it. A/D shimmies along (`CLING_SHIMMY`), let go of
  Sneak to drop. You hold for `CLING_HOLD_TICKS` by tier (2 s to 10 s ascended), then slip (`CLING_SLIP`). The server
  lets a cling float (`WebPower.allowsFloating`) and it never counts as a fall.
  Since 0.10.100 a cling only starts with webbed fists on (X; synced as `WebStrength.FISTS`) or in the symbiote's suit
  (`WebParkour.canCling`). Holding W climbs (`CLING_CLIMB`) and pulls you over the top (`MANTLE_UP`, `MANTLE_IN`). With
  fists it's a spider perch, crawling hand over hand as you move; in the suit the walking tendrils hold the wall
  (`WebTendrilWalk.climbing`) and it never slips.
- **Wall jump:** in the air against a wall (front or side), or from a cling, Jump kicks you up and away
  (`WALL_JUMP_UP` by tier, `WALL_JUMP_OUT`). From a cling with W held you climb instead (more up, `CLIMB_OUT` out), so
  cling, jump, cling again scales a wall. Each wall face once until you land; two facing walls chain like a chimney.
- **Slide:** sprinting, press Sneak: a fast low slide (`SLIDE_BOOST`, decays by `SLIDE_KEEP`, at most `SLIDE_TICKS`).
  Let go of Sneak to stand. Jump out of it leaps higher and further than a plain jump (0.11.19: `SLIDE_JUMP_UP` 0.62
  up, the slide's speed × `SLIDE_JUMP_KEEP` 1.3 + `SLIDE_JUMP_ADD` 0.12 ahead, at most `SLIDE_JUMP_MAX` 0.95). It always flips out: a front flip, corkscrew or cartwheel at random (`WebParkourClient.SLIDE_FLIPS`, Andy). Four slide
  looks, one at random (never the same twice running; 0.11.19, Andy): baseball (feet first, a hand trailing), knee
  slide (arms flung wide), hip slide (on one forearm, legs stacked ahead) and head-first dive. The hitbox stays crouch-height (no sliding under one-block
  gaps; the double-tap crawl does that).
None of them run during a web, zip, the wings, a wall run, a takeover or the symbiote's wall crawl.

## 0.10.53: lines with weight (`WebLineShape`, `WebHeft`)
Andy linked Advanced Hook Launchers and asked to adapt its line and behaviours. That mod is CC BY-ND 4.0 (no
derivatives) and has no public source, so nothing was ported: these are our own takes on its ideas.
- **Living lines (client, `WebLineShape`, `WebRopeSim`):** once stuck, every web line is a simulated verlet rope
  (a point every `POINT_SPACING` 0.8 blocks, 10-48 points, `GRAVITY` 0.05, `ITERATIONS` 14) pinned at the hand and
  the anchor: slack line hangs, swings and bounces when it's caught taut, drapes over edges and lies on the ground
  instead of passing through blocks (collision shapes, `FRICTION` 0.5). Looks only: the swing still pulls straight
  at the anchor. Andy also linked Enchanted Fishing Line (ARR, client-only); this is our own rope after its idea. A
  web snakes out through the air as it shoots (`WAVE_*`); it twangs when it sticks (`TWANG_*`); a line stretched
  past its length hums and thins (`HUM_*`, `STRETCH_THIN`). The symbiote's corded ropes follow the same shape. Everyone sees the same rope: each
  swinger's game sends its rope length when it changes by 0.3 blocks (`WebNet.Rope` into the synced `WebPower.ROPE` /
  `ROPE_OFF`), and the Yank lead is always 3 blocks.
- **Weight (`WebHeft`):** a catch's weight is its bounding-box volume against how much you can haul
  (`YANK_HEAVY_VOLUME` 4, ×1.5 in the suit, ×2 in Living Armor). Over it, you're hauled to it as before. Several
  slingers holding the same catch on their Yank lines add up, so friends can drag in what one can't. A catch over
  `RECOIL_FROM` (35%) of your strength jerks you toward it as it comes in, and held on the lead it fights back (the
  line pulls it more weakly and drags you along when it pulls away).
- **Plucks riders:** Yank on a rider pulls them off their mount; aimed at a ridden mount, the web takes the rider.
- **Rips out torches, lanterns and bells:** B aimed at one tears it off and it flies back to you (as a block break, so
  protection and Kingdoms' crime rules apply; `RIP_COOLDOWN` 1 s).
- **Burns:** a swing web or Yank lead running through fire, lava or a lit campfire, or stuck to a burning creature,
  burns through and lets go (checked every `BURN_EVERY` 5 ticks).
