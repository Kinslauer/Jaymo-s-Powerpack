# SevTech Ages cross-reference: the plan (2026-10-30)

Andy picked 34 of 36 ideas on the pick page (project files `sevtech-study/sevtech-picks.html`, brief in
`sevtech-ages-brief.md`). SevTech is ideas only (1.12.2 Forge, ~250 mods); we borrow its STRUCTURE: one ladder from
flint to rockets where the ground changes at each rung, each rung ends in one deed, other worlds are mid-rung tests,
and far worlds restart the loop. Our own names, our own code, vanilla-style art.

Standing rules that bind every patch: no new dimensions or portals (the six planets, the Aether and the Nether are the
only "elsewhere"); no screen text that states lore, hints only through villagers, hermits, patrolmen and books; every
new iron tool or metal part is forged (Tinkers route, `WeaponParts.Kind`), never a grid recipe; pods have no recipe;
**every new recipe or route shows in JEI** (Andy's note on Sealed Helm; this also covers the kiln and the Alchemy
infusion, which have no JEI pages yet, so PR #699's bell and canopy are invisible there: fixed first, see patch 0).

Andy's notes from the page, folded in below:
- Fuel Still: "whale oil is an immense input" (so Kingdoms whale oil is the STRONG early input, not a weak one).
- Landing Light (not picked, but noted): "you keep your gear but the new material doesn't respond to it the way it
  should, you are forced to restart to progress regardless" (far-world ore shrugs off home tools: see patch 5).
- Patrol Skip Writ: "it calls in an extremely high rank patrolman to handle the target for you".
- Storm Rage (not picked, but noted): the warning should come from a hermit or patrolman, not a Tidings bird ("who would
  have sent that messenger bird?"). Built as a small part of patch 5 only if Andy says so.
- Strip the Raider Pod: "it should animate without the parts it is missing, like a hull door, cockpit glass" (PodEntity
  already draws only filled slots; patch 3 makes raider pods in flight carry real missing slots).

## Patches (one system per track; two tracks run at a time, each finding verified by an independent agent)

### Patch 0, routes in JEI (Powers, small, first)
Kiln firings and Alchemy infusions get JEI categories (`client/jei`), so the thruster bell, the canopy and every
later kiln/infusion route show up. Belongs to PR #699 since the pod parts opened the gap.

### Patch 1, The ladder (Kingdoms WorldTiers; picks ladder-book, exit-deed, world-shifts, hidden-until, mid-rung)
- **Rung Journal**: a book item (`Rung Journal`) with one page per WorldTier, written as a hermit's or patrolman's odd
  short hints, never plain lore; lines tick themselves when the feat lands (reads Progress + great-foe counts).
- **One Deed Per Rung**: each tier step gets one named exit deed beside the great foes: Hearth->Borderlands = your first
  forged iron tool; Borderlands->Wildlands = a trophy brought back from the Aether (the mid-rung test, no new portal);
  Wildlands->Dread = a mended pod flown and landed again; Dread->Legend = a Maw wreck salvaged. Deeds are feats in the
  existing Progress table; exact list is tunable constants.
- **The Ground Changes**: on a tier step a one-time pass seeds new veins in chunks the player already loaded (tracked
  per chunk per tier, like the Dread/Legend ore floor); nobody needs fresh land for the next metal.
- **Hidden Until Reached**: smiths, boards and blueprint sellers don't offer Tinkers casts, Overgeared blueprints or
  grades above the player's tier; JEI still lists them, with a greyed "beyond your tier" note.
Files: Kingdoms `worldtier/`, Careers/Boards offer filters, a new `journal/` package. Lang parts per feature.

### Patch 2, Reading the sky (Powers + Kingdoms; picks star-bench, hermit-charts, sky-tidings, chart-reveals, planets-sky, star-gazers)
- **Star-Sight Bench**: a stone bench block placed on a Lookout platform under a clear night sky wakes (glows, hums);
  using it awake gives the first chart. Makes no pod.
- **Hermit Star Charts**: books, one per planet, sold or given by the floating-island hermits and by **Villager
  Stargazers** (an MCA gift, 1 in N villagers; they tell sky Tidings and sell charts). Each chart hints its world in
  short odd sentences.
- **Map Fills as You Read**: the pod star map shows a world only after its chart is read (per-player attachment);
  costs and missions unchanged. The first chart (from the bench) opens the Pale Moon.
- **Planets in the Sky**: client-only point of light per read world in the night sky; a brighter twinkle while a pod
  is due back.
- **Lights in the Sky**: Tidings lines about a fallen light or a patrolman seen at dusk, seeded before anyone owns a
  pod.
Files: Powers `power/zfighter/sky/` (bench, charts, map filter, sky dots), Kingdoms `Tidings` lines + stargazer gift.

### Patch 3, Mending, not crafting (Powers `pod/`; picks salvage-bench, wreck-part-sets, hammer-finish, strip-raiders, mend-tiers, world-part)
- **Salvage Bench**: a bench block (`Salvage Rack`) a damaged pod part is set on; hammer blows there turn scrap-grade
  parts into sound ones with the right metal, or break them to labelled scrap. Nothing here builds a keel.
- **Wreck Part Sets**: each Maw wreck holds a fixed set of parts (deck 1 fins, deck 2 tank+bell, deck 3 plates...);
  the loot remembers which sets a player already took.
- **Finish With a Hammer**: a complete pod is only flight-ready after one hammer strike on the hull (a ring, sparks,
  the beacon lights); mending always ends with that hit.
- **Strip the Raider Pod**: raider pods in flight and the one left behind carry real empty slots (no door, no glass);
  a hammer strips the leftover instead of flying it.
- **Mended Better / One Part Per World**: pods have mend grades (I-III). A grade adds range (fuel cost -1 on far
  worlds) or a second seat, and needs a part only one world gives: Rustfall plate, Glacia ice-glass canopy, Cinder
  bell, Pale Moon mast alloy, Verdant resin seal, Maw dust fuel cell.
Files: `power/zfighter/pod/` only, plus loot tables and the pod lang part.

### Patch 4, Fuel and breath (Powers; picks fuel-grades, fuel-still, hide-bladder, sealed-helm, world-weather)
- **Fuel Grades by Age**: fuel has grades: charcoal/coal (short, home worlds and the Moon), distilled spirit (mid),
  cinder fuel (far). The star map shows which grade a world needs. Existing coal and blaze stay valid at their grade.
- **Fuel Still**: an Alchemy still that turns spirits into pod fuel; **whale oil is the strong input** (Kingdoms
  Whaling), Kitchen resin a weak one. Shows in JEI.
- **Hide Air Skin**: a tanned-hide bladder (Kitchen Hides chain) worn on the back; holds air for a set time on worlds
  with no breath, tears when hit. Cheap and early.
- **Sealed Helm**: a forged iron helm with a Tinkers seal part (`WeaponParts.Kind`), longer breath; Tinkers route only,
  in JEI.
- **Cold and Heat Gear**: Glacia uses the Seasons cold rules, Cinder a heat rule; warm Kitchen food and quenched
  Overgeared gear help.
Files: Powers `power/zfighter/breath/` + fuel grade on `PodEntity`, Alchemy still recipe, Kitchen compat for the
bladder through `KitchenCompat`.

### Patch 5, Far worlds restart the stone age (Powers + Kingdoms MonsterGear; picks prospect-first, world-part (ore side), star-touched, skip-writ; notes landing-bare, storm-rage)
- **Prospecting First**: far-world ores look like plain stone until tapped with a pick or named by a read chart; the
  first minutes feel like the flint days.
- **Gear doesn't answer** (Andy's Landing Light note): far-world ore shrugs off home tools: a home pick only chips it;
  a tool forged with that world's own first metal (knapped from local stone, then forged at a field anvil) bites. You
  keep your gear; you still restart to progress.
- **Star-Touched Foes**: MonsterGear gives far-world mobs a star-touched look and one trait each (Moon: long leaps;
  Rustfall: heavy blows; Glacia: freezing touch; Cinder: fire; Maw: pull; Verdant: regrowth). No new mobs.
- **Patrol Skip Writ**: once per world a high-rank writ calls in a Commander-rank patrolman who lands and takes the
  captain fight for you; champion and boss stay yours.
- Optional **Storm Rage**: Rustfall storms raise foe tiers briefly; warned by a hermit or patrolman line, never a bird.
Files: Powers `power/zfighter/farworld/`, Kingdoms `MonsterGear` traits, `ZFighterPatrol` writ.

### Patch 6, Who flies (Kingdoms; picks rank-tiers, landing-yard, career-pilot, off-world-orders)
- **Ranks Follow Rungs**: Patrol ranks need WorldTier deeds as well as power (Rookie = Borderlands ... Commander = Dread).
- **Landing Yard**: a kingdom structure of pads; patrol landings there are safe, trade rises, the town becomes a named
  stop on patrol routes and in Tidings.
- **Pilot and Prospector Careers**: two Careers with ranks and dusk wages: Pilot earns from trips flown with
  passengers or parcels; Prospector earns from first finds on far worlds.
- **Off-World Orders**: Merchant Orders and Bounty board warrants ask for far-world goods and name the world.
Files: Kingdoms `careers/`, `MerchantOrders`, Bounty board notices, Patrol rank gate (Powers side one check).

### Patch 7, Back home (Powers forge/alchemy + Kitchen; picks falling-stars, star-metal, quench-glacia, starlight-essence, far-spice)
- **Falling Stars**: a rare night meteor lands in the overworld; Tidings gives a bearing; the crater holds star-metal.
- **Star-Metal for the Forge**: a Tinkers material (cast and forged only) with a new trait (`Starlit`: light blows, no
  fall damage on the swing's landing); never a grid recipe.
- **Glacia Quench / Cinder Embers**: Overgeared quench in Glacia ice gives a cold edge trait, Cinder embers a hot one.
- **Starlight Essence**: an Alchemy essence from Maw dust, used at the infusion altar; hermits hint at it and trade it.
- **Far-World Pantry**: Kitchen dishes from Verdant fruit and Glacia salt; small buffs for a trip (optional items via
  `OptionalItems`).
Files: Powers `tinkering/` material + Overgeared traits, Alchemy essence, Kitchen recipes; meteor in Kingdoms world
events.

## Order
0 (JEI, into PR #699) first. Then two tracks at a time: 3 + 4 (both pod-side, disjoint packages) since they finish the
pod Andy just built; then 1 + 2 (the ladder and the sky); then 5 + 6; then 7. Each patch is its own PR with a version
bump; each pair ships before the next starts.

## Track briefs for patches 3 and 4 (run in parallel, 2026-10-30; Andy: "all eight in the doc's order")
Both tracks start from the PR #699 branch (the pod entity and parts exist). Shared rules for both:
- Compile-check with `bash /home/user/cc21/check.sh <repo> <every changed .java file> src/main/java/com/jaymospowers/power/zfighter/pod/*.java`
  (the staged old Powers jar predates the pod, so the pod sources must always ride along). 0 errors before you stop.
- Every new recipe or route shows in JEI (`client/jei`: `TinkeringJei` already lists `WeaponParts.Kind`s, `KilnJei` the
  kiln, `AlchemyJei` the infusion altar; grid recipes show by themselves; anything else gets a `SimpleCategory` page).
- No grid recipe for an iron or metal part or tool (forge route: `WeaponParts.Kind`, kiln, casting). A plain block or
  a leather thing may be a grid recipe. Pods and keels have no recipe at all.
- No screen text stating lore; names and one-line grey tooltips only, hints through villagers/hermits/patrolmen.
- Tuning numbers in `static final` constants at the top of each class. Lang in `lang/parts/<feature>.json`. Any class
  holding an attachment goes in `JaymosPowers.registerAttachments()`. Models: 26.3 block models need `#variable` face
  textures; items need `items/<id>.json`. Textures are our own, vanilla-style, painted by a small Python script under
  `tools/` (see tools/gen_pod_parts.py).
- MODLOG.md gets one entry per patch (same voice as the rest); bump `gradle.properties`. Commit on your branch with the
  attribution lines you were given; do not push.

### Track 3, Mending (Powers 0.18.13; files: `power/zfighter/pod/**`, `client/power/zfighter/pod/**`, ZFighterRaiders,
the Maw part of ZFighterSpace, ZFighterSpaceMissions rewards, loot tables, lang `zfighter_pod.json`, a `SalvageJei` page)
- **Finish With a Hammer**: `PodEntity` gains a synched FINISHED flag. A plain hammer hit on a complete, unfinished pod
  finishes it (anvil ring, sparks, the beacon mast lights up in the model; particles) instead of detaching. Only a
  finished pod launches (`canLaunch`). Detaching or damaging any part clears FINISHED. Hammer detach now needs sneaking
  when the pod is complete and unfinished (so the finishing hit can't pop the mast); on any other pod a plain hit still
  detaches as today.
- **Salvage Rack**: block `salvage_rack` (grid recipe: 4 iron nuggets? no: metal parts are forged, but a rack is
  furniture: 2 planks + 2 sticks + 1 chain, shown in JEI by itself). A carried damaged pod part set onto the rack (the
  normal set-down onto the rack) lies on it (block entity, rendered). Hammer blows on the rack with the part's metal in
  the off hand (iron ingot for plates/mast/keel, steel ingot for fins, copper ingot for tank/bell, a glass block for the
  canopy) mend it in 3 blows (uses one ingot on the first blow); sneak + hammer breaks it to scrap instead (nuggets /
  glass panes). Taking the mended part off the rack puts it in your arms (carry). `SalvageJei` page: damaged part + metal
  -> sound part, with the blow count as a note.
- **Wreck Part Sets**: each Maw wreck deck holds a fixed set (deck 1: the three fins; deck 2: tank + bell; deck 3: four
  plates; deeper/boss: canopy + mast) in its wrecked pod and chest, instead of random parts; a per-player attachment
  remembers which sets they have taken so the same deck gives the next unfilled set on a later visit, and plain scrap
  once every set is held.
- **Strip the Raider Pod**: raider pods in flight (`ZFighterRaiders`) are real pods with random missing slots (1-3 of
  plates, canopy, mast) so they visibly fly without a door or glass; the pod the band leaves keeps those gaps and its 3
  fuel; a hammer strips it for parts (today's detach) instead of flying it. The tooltip-free hint: a patrolman line
  already exists for pods; add one WorkTalk/patrol line about raiders flying "without the glass".
- **Mended Better / One Part Per World**: `PodEntity` gains a synched GRADE 0-3. Three world parts, plain items (our
  art): `rustfall_plate` (Rustfall), `glacia_ice_glass` (Glacia), `cinder_bell_alloy` (Cinder). They come from planet
  mission rewards on that world (`ZFighterSpaceMissions`: conquer reward adds one) and that world's wreck chests. Sneak +
  right-click a FINISHED pod with the next grade's part grades it up (I needs the Rustfall plate, II the Glacia glass,
  III the Cinder alloy): grade I = fuel cost -1 on worlds costing 3 or more, grade II = a second seat (a passenger rides
  along; the trip carries both), grade III = a hard landing never knocks parts off. The model shows the grade (a brass
  band per grade on the keel posts). A pod stripped to its keel keeps its grade (the keel carries it: the keel block
  item remembers the grade in a component).

### Track 4, Fuel and breath (Powers 0.18.47; files: new `power/zfighter/breath/**`, new `power/zfighter/fuel/**`,
WeaponParts (one new Kind), alchemy crucible or still recipe, ZFighterStarMapScreen (grade shown), lang `zfighter_fuel.json`)
- **Fuel grades**: `fuel/PodFuel`: grade 1 = coal, charcoal, coal block; grade 2 = blaze powder/rod and Pod Spirit (from
  the still); grade 3 = Cinder Ember (an item found only on Cinder: lava-side loot and mission rewards; our art). Each
  world needs a grade: Pale Moon 1, Verdant 2, Rustfall 2, Glacia 2, Cinder 2, Maw 3 (constants). `PodEntity` gets ONE
  new synched int FUEL_GRADE with getter/setter and one check in the fuel-item hook (the smallest possible edit to
  PodEntity: Track 3 edits the same class); adding lower-grade fuel to a tank lowers the tank's grade; the star map
  (`ZFighterStarMapScreen`) shows each world's needed grade and greys worlds the tank can't reach; `ZFighterSpace.go`
  refuses with an action-bar line.
- **Fuel Still**: an Alchemy route (use the existing crucible if it takes item recipes, else a `still` block on the
  crucible's pattern) that makes Pod Spirit: whale oil (Kingdoms Whaling's item, through a compat/OptionalItems check,
  the STRONG input: 1 oil -> 4 spirit) or Kitchen resin / honey bottle (weak: 1 -> 1), with Fire essence. Shows in JEI
  (add a crucible page if there is none).
- **Hide Air Skin**: item `air_skin` (grid: 4 leather + 1 string + 1 glass bottle is fine, leather isn't metal; Kitchen's
  tanned hide also accepted via tag when loaded). Held in the off hand or chest slot? Worn: it is a chestplate-slot item
  with no armour, drawn as a bag on the back (simple cube model). On airless worlds (Pale Moon, the Maw; constants) a
  player with no air source takes "thin air": slowness + 1 damage every 5 s after 20 s outside the pod. An Air Skin
  gives 3 minutes of air, refilled by standing in/next to a pod for 10 s; it tears (breaks) when the wearer takes a hit
  over 6 damage.
- **Sealed Helm**: `WeaponParts.Kind` "seal" (iron, 2 ingots, `Assembly.ALONE`) + a helmet made at the Tool Station
  from the seal blank and a glass block (list it as an assembly so TinkeringJei shows it): `sealed_helm`, leather-tier
  armour, gives 8 minutes of air and never tears; worn with the Air Skin the times add.
- **Cold and Heat Gear**: Glacia applies the Seasons winter cold rule (find the existing cold code: PR #358 Seasons in
  Powers 0.15.30, likely `seasons/` or `weather/`); Cinder a mirrored heat rule (`breath/Heat`): slow, then 1 damage per
  8 s unless standing in water or holding/eating a cooling thing; a quenched Overgeared piece (the existing quench
  component) worn or held cuts heat damage in half; warm Kitchen food (the existing warm-food effect from Seasons) cuts
  cold the same way. Constants at the top.

## Track briefs for patches 1 and 2 (run after 3+4 ship)
Same shared rules as above. Kingdoms depends on Powers (`kingdoms/.../PowersCompat`); Powers reaches Kingdoms only by
reflection through `power/zfighter/ZFighterKingdoms` (add a method there for anything new Powers needs from Kingdoms).
Kingdoms' "Tidings" are the per-player news letters (kingdoms/MODLOG.md around line 4514: "gathered for 20 s and written
up as a Tidings letter"); grep Tidings in kingdoms/src to find the class that posts a line.

### Track 1, The ladder (Kingdoms 0.7.x; files: `WorldTiers`, a new `journal/` package, `MonsterGear` offer filters,
Careers/BountyBoard/blueprint sellers' offer filters, lang; Powers: one event hook for "first forged iron tool" if none exists)
- **One Deed Per Rung**: `WorldTiers.Progress` gains deed bits beside NETHER/WITHER/DRAGON: FORGED (first iron tool
  finished at the anvil or Tool Station, Powers raises it through PowersCompat or an event Powers already fires),
  AETHER_TROPHY (back in the Overworld carrying an Aether boss drop: the Giants' or the Aether II bosses' drops, read
  through a tag), POD_FLIGHT (a pod trip landed and flown home: Powers' ZFighterPodTrip tells Kingdoms), MAW_SALVAGE
  (took a part set from a Maw wreck). `FEAT_NEEDED` becomes: Borderlands FORGED, Wildlands NETHER + AETHER_TROPHY, Dread
  WITHER + POD_FLIGHT, Legend DRAGON + MAW_SALVAGE (constants; the great-foe counts stay). The tier screen's requirement
  line lists them.
- **Rung Journal**: item `rung_journal` (grid: book + feather + a tier icon? no: book + string), given to each player
  when they enter Borderlands or found on patrolmen. Opening it shows one page per tier with 3-5 lines written as a
  hermit's/patrolman's odd hints ("Someone up there wants iron shaped, not found."), each line ticked when its feat lands
  (reads Progress). No lore statements; hints only. Lang in `lang/parts/journal.json`.
- **The Ground Changes**: when a player's tier rises, a one-time pass over the chunks that player has loaded (a per-chunk
  attachment: highest tier seeded) replaces a few deep stone blocks with that tier's ore veins (Borderlands iron+copper
  pockets, Wildlands gold+lapis, Dread diamond, Legend ancient debris in the Nether); runs lazily as chunks tick, N
  chunks per tick, constants at the top. Monoliths' floor raise does the same for everyone.
- **Hidden Until Reached**: smiths, boards and blueprint sellers don't offer Tinkers casts, Overgeared blueprints or
  grades above the player's tier (a `TierGate.allowed(player, stack)` table: by item tag per tier); JEI still shows the
  routes. Nothing is removed from the world, only from offers.
- **Other World as a Test**: the AETHER_TROPHY deed above is the mid-rung test; the journal hints at it on the
  Wildlands page. No new portal.

### Track 2, Reading the sky (Powers 0.18.x + Kingdoms 0.7.x; files: new Powers `power/zfighter/sky/**`, ZFighterStarMapScreen
filter, client sky renderer; Kingdoms: Hermits trades, a stargazer gift in GiftedFolk, Tidings lines)
- **Star-Sight Bench**: block `star_bench` (grid: 2 stone slabs + 1 amethyst shard; furniture, fine), placed on a Lookout
  platform (`ZFighterLookout` knows its blocks) with open sky; on a clear night (no rain, moon up) it wakes: emissive
  model state + hum; using an awake bench gives the first chart (Pale Moon) once per player (attachment). It makes no
  pod and does nothing about pods.
- **Hermit Star Charts**: items `star_chart` with a component naming the world (6); using one marks the world read on
  the player's `sky/Charts` attachment and shows a book-style page of 3-4 odd short sentences about that world (hint
  voice, no lore statements). Sky hermits (`Hermits`, Kingdoms) sell the charts in order of the star map's cost; the
  Patrol gives the Rustfall chart on joining.
- **Map Fills as You Read**: `ZFighterStarMapScreen` shows only worlds whose charts are read (plus Home); the
  server-side `ZFighterSpace.go` refuses unread worlds. Players who already flew somewhere (existing worlds visited:
  read the FLIGHTS attachment / visited dimensions) have those charts counted as read on first load so nobody loses a
  world they already reached.
- **Planets in the Sky**: client-only: a small point of light per read world in the night sky at a fixed
  right-ascension per world (constants), brighter while the player's pod trip is pending/returning. Drawn in the sky
  render pass (find how `ZFighterSpaceArt` or the Aether draws into the sky; a `WorldRenderEvents` hook is fine).
- **Villager Stargazers** (Kingdoms): a new gift in `GiftedFolk` ("stargazer", 1 in N villagers, N a constant): they
  tell sky Tidings and sell charts the player hasn't read (MCA trade or talk-screen button, the pattern the Z Fighter
  villagers use). **Lights in the Sky**: Tidings lines (a light that fell near a village, a patrolman seen at dusk) fired
  when raiders or patrol pods land near a town the player knows, before the player owns a pod.
