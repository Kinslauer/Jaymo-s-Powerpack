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
