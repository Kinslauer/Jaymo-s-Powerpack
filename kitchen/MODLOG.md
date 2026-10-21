# Jaymo's Kitchen: MODLOG

A cooking minigame for Minecraft Java 26.3 (Fabric).

## Design
- **Where:** sneak + right-click a lit Campfire, Soul Campfire or Smoker with an empty hand. Normal use of
  those blocks is unchanged.
- **The minigame (recipe sequence):**
  - Pick a dish. The card shows its steps.
  - Ingredients are taken from your inventory when you start.
  - Press the buttons in the right order: the ingredients, a few decoys, and the Stir / Flip / Bake actions.
  - A timer runs.
- **Quality:**
  - **Perfect:** no mistakes, finished within par time. Extra hunger and saturation, plus a buff.
  - **Good:** 1-2 mistakes, or slower than par. Normal stats.
  - **Burnt:** 3 mistakes, or time ran out. Weak food, and it may give Hunger.
- **Dishes:** Hearty Stew, Mushroom Risotto, Sweet Berry Pie, Grilled Salmon, Honey-Glazed Chicken.
  - Each is one item.
  - Quality is stored on the stack (a custom component), and it sets the food stats and the name.
- **Authority:** the server runs each cooking session. The client sends only button presses, so it works on
  servers too.

## Environment
- Windows 10, i5-6600K, 16 GB, GTX 960.
- JDK: Temurin 25.0.4.
- `GRADLE_USER_HOME=E:\gradle-home`, because C: has little free space.
- Versions are copied from `universal-modder/examples/minecraft-gta5-passthrough/mc`: Loom 1.18-SNAPSHOT,
  loader 0.19.5, Fabric API 0.161.0+26.3, Gradle 9.7.1.

## Log
- **Setup:**
  - Installed JDK 25 and Python 3.12 with winget. Minecraft 26.3's version JSON asks for Java 25.
  - `genSources` took about 4 minutes on the first run. I unzipped the sources jars to search them.
  - The Fabric API jars ship without sources, so I read their signatures with `javap`.
- **26.3 API facts this mod relies on:**
  - `ResourceLocation` is now `Identifier`.
  - Screens draw through `extractRenderState(GuiGraphicsExtractor, ...)`, with `graphics.text`,
    `centeredText`, `fill` and `item`.
  - A screen is opened with `minecraft.gui.setScreen(...)` and read back with `gui.screen()`.
  - `ServerPlayer.drop(stack, throwRandomly, Prediction)` takes a third argument.
  - `Items.registerItem` is private, so items register with `Registry.register(BuiltInRegistries.ITEM, key,
    new Item(props.setId(key)))`.
- **Quality:** each tier is stamped onto the stack as FOOD, CONSUMABLE, ITEM_NAME and LORE components. No
  custom component type is needed, and different qualities don't stack together.
- **Art:** 16x16 pixel grids drawn with Pillow (`tools/gen_assets.py`). No vanilla textures are copied.
- **Stations (user request):**
  - The campfire offers only grill dishes, meaning dishes whose actions are all Flips (`Dish.grillOnly()`).
  - The Furnace (newly added) and the Smoker offer the multi-step dishes.
  - Honey-Glazed Chicken lost its Simmer step so it counts as a grill dish.
  - The server also refuses a dish started at the wrong station.
- **Campfire grill minigame (user request):**
  - The campfire replaces the button sequence with an animated grill (`Grill` on the server, `GrillView` on the
    client).
  - The server tracks how long each side has been face-down; doneness = ticks / `grillTicks` (salmon 100,
    chicken 140).
  - The client predicts the cooking between updates.
  - The top side's colour shows its doneness: raw → golden → brown → black.
  - The bottom side shows as an edge strip and as smoke: light wisps from 0.7, thick dark smoke above 1.0.
  - Results: Perfect is both sides in 0.8–1.0. Undercooked is a side below 0.6. Burnt is one side above 1.0
    (Hunger). Charred is both sides above 1.0 (Hunger II and Nausea).
  - A side reaching 1.6 ends the cook automatically.
  - Burnt now always gives Hunger (it was a 50% chance), at every station.
  - 26.x key codes are SDL-style: `InputConstants.KEY_SPACE` is 44, not GLFW's 32.
  - I previewed the colour ramp with a Python mock of the draw code before playing.
- **Creative mode:** `player.hasInfiniteMaterials()` skips both the ingredient check and the ingredient
  cost.
- **Dough and pizza (user request):**
  - New items: Cooking Oil and Dough.
  - Cooking Oil is a shapeless recipe: a glass bottle plus 3 × `#c:seeds` (Fabric convention tag).
  - Dough is made on a **prep board**: sneak + empty hand on the top face of any block that has a sturdy UP
    face and no block entity.
  - Prep uses a bowl (handed back), a water bottle, oil, and 3 wheat. The wheat is my addition, for flour.
  - Prep minigame: hold-to-pour water and oil into bands, then alternate A/D to knead.
  - Pours and the knead run client-side. The client reports the levels and `Prep.score` (shared code) judges
    them. The server enforces a minimum time.
  - Pizza: `Dish.Method.OVEN`, furnace only. It reuses the grill clock with a single side and no flip
    (`judgeOven`: CHARRED above 1.3). `OvenView` draws a stone oven, a rack, a pizza that slides in, and
    smoke.
  - Dishes now declare their own `Method` and stations; this replaced the `grillOnly()` rule.
  - `Step` uses suppliers, since mod items register after the enum loads. `matches()` accepts a potion only
    if it holds `Potions.WATER`.
  - All mod items are in the Food & Drinks creative tab (`CreativeModeTabEvents.modifyOutputEvent`; this
    Fabric module was renamed in 26.x).
- **Prep surfaces narrowed (user request; a campfire was being used as a prep board):**
  - Prep surfaces now come from the block tag `jaymoskitchen:prep_surfaces`: planks, logs, all slabs,
    overworld stone, cobblestones, stone bricks, and polished stone variants.
  - Only the top face counts.
  - The sturdy-face check is gone, because a bottom slab's top face isn't "sturdy".
- **Content drop 2:**
  - **New Smoke minigame (`Dish.Method.SMOKE`, Smoker only):**
    - The heat cools on its own; holding the fan raises it.
    - Random gusts push the heat either way for 10–20 ticks.
    - Ticks with the heat in the band [0.45, 0.65] cure the meat (200 needed). Ticks above 0.8 scorch it.
    - Judging (`Smoke.judge`, shared): Perfect is ≤5 scorch ticks and ≤30 cold ticks; Burnt is ≥25 scorch;
      Charred is ≥60 (it ends early). Running out of time (900 ticks) is Undercooked.
    - As with prep, the simulation runs on the client and the server checks a minimum time.
    - `SmokerView` draws the heat gauge, the progress bars, a smokehouse with the meat hanging (it darkens as
      it cures and chars), coals that glow with the heat, and a bellows.
  - **8 new dishes:**
    - Smoke: Smoked Ham, Beef Jerky.
    - Grill: Mutton Kebab, Glazed Pork Chop (with new grill sprites).
    - Oven: Rustic Bread, Apple Tart (with new oven sprites).
    - Sequence: Fish Stew, Pumpkin Soup.
  - The cooking panel is now 240 tall, with a 23px dish-list pitch to fit 8 Furnace dishes.
- **Difficulty pass (user request: harder, randomised):**
  - **Sequence:**
    - 3 decoy buttons instead of 2.
    - Base par is now 34/step (was 40); the burn limit is 70/step + 80 (was 80/step + 100).
    - Each session scales par ×0.85–1.15 and the limit ×0.9–1.1.
  - **Grill:**
    - Doneness is now accumulated as floats, at heat / grillTicks per tick.
    - The heat is re-rolled (×0.8–1.3) on every flip. A soul campfire is ×1.3.
    - The client gets `heat` and draws the flames that much taller.
    - The golden threshold is now 0.85 (was 0.8).
  - **Oven:** the heat re-rolls (×0.7–1.4) every 30–80 ticks on the server, which pushes an update; the
    flames scale with heat².
  - **Smoke:**
    - Cooling is random per session (0.0035–0.0065).
    - Gusts are 0.008–0.017 strong, last 8–22 ticks, and come every 20–70 ticks.
    - The band is now 0.46–0.62, scorching starts at 0.78, and curing needs 220 ticks.
  - **Prep:**
    - The server slides each mix's bands (water ±0.12, oil −0.08/+0.12) and sends the shifts with STARTED.
      `Prep.score` uses the session's shifts.
    - Each pour starts at 1.0–1.4× the base rate and accelerates.
    - Kneading needs 20 presses in 110 ticks.
- **Content drop 3, "Nether Kitchen":**
  - **New station:** the Blast Furnace.
  - **New Sear minigame (`Dish.Method.SEAR`):** 5 rounds. Each round puts a target at a random spot and
    sweeps the marker faster. Pressing in the core is perfect, in the zone is good, anywhere else misses.
    Judging (`Sear.judge`, shared): 3 misses is Charred, 2 is Burnt, 0 misses with ≥4 perfects is Perfect.
    The simulation runs on the client; the server checks that the rounds add up and a minimum time passed.
    `SearView` adds a sear stripe per hit and a charred blotch per miss.
  - **New dishes:** Blazing Steak and Magma Rabbit (Sear), Crimson Skewer (Grill, kebab sprite), Nether
    Wart Stew (Sequence).
  - **Soul campfire:** grills hotter and draws blue flames.
  - `SmokeUpdate` was generalised to `SimUpdate` for both client-simulated minigames.
- **Quiver (user request; not cooking):**
  - Vanilla Java 26.3 has no quiver asset, so I drew an original Bedrock-style sprite.
  - **Storage:** a `jaymoskitchen:quiver_contents` component holds a list of `ItemStackTemplate`s, up to 32
    arrows of any type in `#minecraft:arrows`. Vanilla `BundleContents` couldn't be used: its capacity is
    hard-coded to a weight of 1, which means 64 arrows.
  - **Loading:** bundle-style clicks load and unload it; it has a fullness bar and a tooltip.
  - **Ammo:** `PlayerMixin` injects into `getProjectile` before the inventory scan and returns a stand-in copy
    of the quiver's top stack (tracked in a weak-key map). `ProjectileWeaponItemMixin` runs at the RETURN of
    `useAmmo`, works out how many arrows the bow split off the stand-in, and removes that many from the quiver.
    This covers both bows and crossbows.
  - **Worn = carried:** the off-hand quiver, else the first one in the inventory. Each server tick, the quiver's
    contents are mirrored into a Fabric attachment (`worn_quiver`, synced to all tracking clients), because
    other players' inventories aren't synced to clients.
  - **Rendering:**
    - `AvatarRendererMixin` (TAIL of `extractRenderState`) puts a `QuiverRenderData` into the render state:
      one shaft per 2 arrows, 16 at most, each with the fletching colour of the arrow it stands for.
    - `QuiverLayer` (registered via `LivingEntityRenderLayerRegistrationCallback`) submits `QuiverModel`, which
      is built on the PlayerModel skeleton like the cape: a diagonal tube, rim, end cap, a strap across the
      chest, and 16 shafts whose visibility is set in `setupAnim` from the state.
    - Each fletching is its own `QuiverModel.Fletching` instance, submitted with that arrow's tint. Submission is
      deferred, so part visibility can't change between submits.
  - **Recipe:** 5 leather + 2 string.
- **Leg shots (user request):**
  - `AbstractArrowMixin` injects at the `doPostHurtEffects` call inside `AbstractArrow.onHitEntity`, so it
    only fires after real damage. It covers arrows, tipped arrows and spectral arrows; tridents have their
    own `onHitEntity`, so they're excluded.
  - The `EntityHitResult` location is where the arrow's path clips the target's (slightly inflated)
    bounding box. Height = (hitY − feetY) / bbHeight, and ≤ 0.375 counts as legs.
  - Only player-fired arrows count, so skeletons don't hobble you.
  - A leg shot gives Slowness I for 3 s, or Slowness II for 5 s on a crit (full-draw) shot. Further leg shots
    stack +1 level up to III and keep the longer duration; stronger existing slowness is left alone.
  - Feedback: crit particles and an attack-crit sound at the impact point.
  - Legless mobs are listed in `jaymoskitchen:no_legs` (entity type tag).
- **Corpses (user request):**
  - **No poof:** `LivingEntityMixin` swaps a dying Mob (not players, not `#jaymoskitchen:no_corpse`) for a
    `CorpseEntity` at the `broadcastEntityEvent(60)` point of `tickDeath`.
  - **Loot capture:** while `dropAllDeathLoot` runs, `EntityMixin` catches every
    `spawnAtLocation(ServerLevel, ItemStack, Vec3)` into the mob (via the `CorpseCapture` duck interface).
    XP still drops normally.
  - **The corpse's look:** the mob's `saveWithoutId` NBT, with heavy keys stripped, plus its `id`, synced as
    an SNBT string. There's no compound-tag entity data serializer in 26.3.
  - **Client rendering:** the client rebuilds the mob from that look (not added to the world) and draws it
    with its own renderer, deathTime forced to 20 (keeled over) and the red overlay cleared. Sheep colours,
    armour and variants come through.
  - **Kinds** (`CorpseKind`), from tags plus `instanceof Animal`:
    - ANIMAL: pick up with an empty hand into a `CorpseItem` (Slowness I while carried; named "Cow Corpse").
      Use it on a Butcher Block to lay it on top, then chop 3× with an axe for the loot plus one bonus food.
    - ZOMBIE: right-click to search (drops loot), burn to ash (80 burning ticks); reanimates at 6000 ticks,
      gear stripped.
    - SKELETON: fire-immune; each right-click gives 1 bone (1–3 rolled; loot-table bones removed), and the
      last bone drops the rest of the loot and removes the corpse.
    - OTHER: search, then burn or let it rot (12000 ticks).
  - **Fire:** flint and steel or a fire charge ignites a corpse; fire and lava work through vanilla's entity
    fire handling. A corpse that died burning keeps burning.
  - **Butcher Block:** crafted from 2 stacked logs; cube_bottom_top with two log end-grains on top; axe-
    mineable; drops itself.
  - **Verified headless** (`runServer` + RCON, EULA accepted by the user): 4 corpses with no loose items, and
    the slime was excluded. Kinds and loot were correct (zombie: rotten flesh; skeleton: Bones 3; cow:
    leather + 3 beef). The creeper corpse burned away, the skeleton corpse ignored fire, and the zombie corpse
    with Age 5990 got back up.
- **Gotcha (crash on the first in-game corpse, a chicken):**
  - *Symptom:* "Tried to access entity ID before ID assignment", thrown from
    `ItemModelResolver.updateForLiving`.
  - *Cause:* in 26.3, entities created but never added to a level have no ID, and living-entity renderers call
    `getId()`.
  - *Fix:* give the client-side body a negative ID (`-1000 - corpseId`).
- **Pmeh and joints (user request):**
  - **Pmeh:** an extra pool in `data/minecraft/loot_table/blocks/tall_grass.json` (copied from vanilla 26.3)
    drops 1–2 Pmeh when either half is broken with shears (`minecraft:tool/can_shear`). Verified with
    `loot spawn … mine … shears` over RCON: Pmeh with shears, none without.
  - **Prep board recipes:** `Prep.Recipe` is DOUGH or JOINT (1 Pmeh + 1 Paper). Right-clicking a prep surface
    with Pmeh in hand opens rolling directly; `OpenPrep` carries a recipe hint, and `StartPrep` carries the
    recipe.
  - **`RollingScreen` (client-simulated; `Rolling.score` is shared):**
    - Grind: hold and circle the mouse around the grinder. The angle delta accumulates per frame; 5–8 turns
      within 200 ticks.
    - Sprinkle: hold over the paper. Herb falls at the cursor ± jitter into 8 stretches, at a per-roll random
      rate. Evenness = 1 − Σ|stretch − share| / 2; below 0.6 is ruined, ≥ 0.82 can be Perfect.
    - Roll: hold and drag up and down. Per-tick |dy| between 1.5 and a random 7–10 rolls it; faster adds tear
      damage, and 30 tears it. A Perfect needs ≤ 5 tears and must finish within 240 ticks.
  - **Joint:** 4 durability = 4 puffs, used with the TOOT_HORN animation; ember particles while drawing, a
    smoke puff and a soft sound on finishing. No effect yet (waiting on the user).
  - **Gotcha:** `gen_assets.py`'s palette is one shared dict, so new single-character keys silently recoloured
    older sprites. The new sprites use punctuation keys.
- **Carrying big corpses (user request):**
  - Animals whose rebuilt body is ≤ 0.5 wide and ≤ 0.8 tall (chicken, rabbit, babies) still pocket as an item,
    which no longer slows you.
  - Bigger animals are carried. Players can't be vehicles: `startRiding` refuses targets whose type
    `!canSerialize()`, which includes the player. So the corpse stores a synced `CARRIER` entity id instead
    (never saved).
  - Each tick it sets its position 0.85 in front of the carrier at +0.65 height, with no physics, and gives the
    carrier Slowness I. It drops if the carrier dies, leaves, or ends up more than 4 blocks away. One corpse
    per player.
  - `CorpseRenderer` overrides `state.x/y/z` from the carrier's lerped position and yaw, so it's smooth every
    frame.
  - While carried it isn't pickable, so the crosshair passes through; right-clicking any block sets it down
    (onto the top of a Butcher Block). This check runs first in the `UseBlockCallback`.
- **Joint effects (user request):**
  - Every puff gives Hunger (15 s) plus one random effect for 20–45 s.
  - Rolled joints get a `joint_quality` component: Perfect rolls are PERFECT (good effects only), tears ≥ 0.5
    make it TORN (bad only), and otherwise it's CLEAN (either). Joints without the component count as CLEAN.
  - Names: "Perfect Joint" / "Joint" / "Torn Joint".
- **Corpse fixes (user feedback):**
  - **Fire:** the full-body vanilla fire overlay is off for corpses (`state.displayFireAnimation = false` after
    `super.extractRenderState`). Burning corpses instead smoulder on the client: small flames and lava embers
    from inside the body, and smoke from the top.
  - **Spiders / silverfish / endermites:** their death flip is 180°, pivoting at the feet, so the model ended up
    under the block. The renderer lifts those bodies by their height. The entity itself already rested on top
    (RCON: the spider corpse sat at y = −59.0 on a block at −60).
  - **Creepers:** burning plays the primed hiss, then after 30 ticks the corpse explodes (power 3, or 6 if
    charged, using MOB griefing rules). Verified over RCON: the corpse was gone and the block under it was air.
  - **Butcher block:** an axe chops; an empty hand now takes the corpse back off the block.
  - **Carrying:** sneak + empty hand picks up any non-animal corpse (skeleton, spider, creeper, zombie…), always
    carried, one at a time. A plain click still does the kind's own action. Only animals can be pocketed.
- **Tuning (user feedback):**
  - Smoulder: now every tick, with two small flames, an occasional full flame, lava embers half the time, and
    smoke (sometimes large).
  - Creeper fuse: 120 ticks (was 30). It hisses when it catches and again 30 ticks before going off.
  - **Carry pose:** carried bodies are no longer in the fallen-over death pose (`deathTime` 0) and are laid on
    their backs across the arms, using world-space rotations about the carry point in `layAcrossArms`:
    - Tall bodies (height > 1.1 × width) face away, tip back 90° about the carrier's right axis (face up, head
      toward the carrier), then swing 90° about Y so they lie across, centred by h/2.
    - Long bodies already face sideways, so they roll 180° about the right axis (legs up), lifted by h/2.
  - 26.3's `PoseStack` uses `rotate(Quaternionfc)`, not `mulPose(Quaternionf)`.
- **Burn timing fix (user: "the creeper did not explode"):**
  - *Cause:* the fuse (120) was longer than the burn-out (80), so creeper corpses turned to ash first.
  - Burn-out is now 300 ticks (15 s) and the creeper fuse 160 ticks (8 s).
  - Once a corpse has started burning, it's kept alight (`setRemainingFireTicks(20)`) until it's consumed;
    only water or rain puts it out.
  - Verified over RCON with just 1 s of initial fire: both corpses were still burning at 4 s, the creeper
    exploded by 9 s, and the zombie burned away between 9 s and 17 s.
- **First build:** a single compile error, the `drop` signature above. `runClient` reached the title screen
  with `jaymoskitchen 0.1.0` loaded and no model or texture errors.

## Butcher table tweaks
- Ground corpses are drawn shifted back by half their height along the direction they keel over (yaw's (cos, sin)), so the body's middle sits over the corpse position (the middle of the butcher block) instead of its feet. Spiders, silverfish and endermites keep their upside-down lift.
- New tag `jaymoskitchen:big_carcasses` (cow, mooshroom): these need a 2x2 square of butcher blocks. They're set down on the shared corner nearest the click (`CorpseEntity.tableSpot`); a single block is refused with `message.jaymoskitchen.corpse.big_table`.
- Carrying a big carcass gives Slowness III (other carried corpses: Slowness I).

## Versioning
- Each release bumps `version` in `gradle.properties` (fabric.mod.json reads it). 0.1.0 = first multiplayer build; 0.1.1 = butcher table tweaks (centering, 2x2 cow table, Slowness III). Next: 0.1.2. Bump the middle number for a content drop (0.2.0).

## 0.2.0: Horse drop (saddlebags, wagon, carriage)
- **Saddlebags** (`horse/Saddlebags`, `SaddlebagItem`, `SaddlebagContainer`, `mixin/AbstractHorseMixin`). Recipe: leather, string, leather over two bundles (any colour). Equippable in the BODY slot for `#can_wear_horse_armor` (horse, plus any vanilla undead horses in that tag). The contents live on the bag stack (`minecraft:container`, 15 slots), so taking the bags off, shearing, or the horse dying keeps the goods in the bags. Burning the item drops them.
  - The mixin makes `getInventoryColumns` return 5 while bags are worn, so the vanilla horse screen shows a donkey-style 5x3 grid. `openCustomInventoryScreen` opens it on a `SaddlebagContainer`, a shared view onto the bag's component (keyed by stack identity) that is written back on every change. `hasInventoryChanged` closes the screen if the bags come off. Equipping bags while the screen is open needs a reopen to show the slots.
  - Client: `SaddlebagModel` (built on the horse skeleton) with a pouch on each flank and a strap over the back. The pouches bounce out and sway with the gait, and get bigger as they fill. Drawn by `SaddlebagLayer` on armour-wearing horse renderers.
- **Wagon** (27 slots plus a bench seat for one passenger) and **Carriage** (54 slots, canvas cover) share one entity class, `CartEntity`.
  - Placing: the item places it facing your way. Recipes: wagon = chest, stick, 2 planks and 2 logs; carriage = 3 wool, 2 chests, a plank and 2 logs.
  - Hitching: right-click it while riding a tamed adult horse (any `AbstractHorse`), or while leading one on a lead. Right-click again from the saddle to unhitch.
  - Moving: it follows on a rigid pole (2.1 or 2.7 blocks from the horse's centre), facing the horse, with step height 1. It comes unhitched if it ends up more than about 6 blocks from where it should be.
  - Speed: hitching adds a transient `jaymoskitchen:towing` speed modifier to the horse (-15% wagon, -25% carriage), removed on unhitch.
  - Interacting: right-click to sit on the wagon (sneak to open its chest). The carriage opens its chest on right-click. Three hits break it and drop the contents and the item; in creative it breaks in one hit.
  - Syncing: the hitched horse is saved as `Hitch` (UUID) and found again after a reload. While hitched, the client swallows position packets (a custom `InterpolationHandler`) and computes the cart's position itself from the horse, which keeps it smooth.
  - Client: `CartModel` (wheels spin with distance travelled) and `CartRenderer`, which draws real chest items in the back. Generated textures are `entity/cart.png` (oak, dark wood and canvas regions) and `entity/saddlebag.png`.
- Verified over RCON: the cart settles on the ground; hitching through the saved `Hitch` UUID follows at exactly 2.1 blocks and faces the horse; the towing modifier is -0.15 and is removed on unhitch; the cart climbs a 1-block step; saddlebag contents persist on the horse's body item.

### 0.2.0 fixes after first playtest
- Fixed a black screen on launch: `CartRenderer` built an `ItemStack` in a static initializer before components were bound, so the resource reload failed.
- Fixed saddlebags not drawing: the layer was registered on a tag check, but tags aren't bound while renderers are made. It is now registered by type (horse, zombie horse, skeleton horse).
- Remodelled the wagon and carriage in vanilla style. `tools/gen_carts.py` holds the box list and generates both `CartShapes.java` and the textures `entity/wagon.png` and `entity/carriage.png`. Each box gets its own UV patch, painted per material and face: planks; dark wood; canvas; a chest with an outline and band; iron; round spoked wheels with see-through gaps, built from 3 overlapping boxes. The chests are modelled boxes, not items. All parts touch: the wheels meet the sides, the bed rests on the axles, and the shafts meet the floor. The old `cart.png` was removed.
- Riderless hitched horses stand still: `getNavigation().stop()` runs every tick.
- Pulling by hand: right-clicking the front of a cart (the forward component of the click is more than 0.3 of the hitbox width) makes the player the puller. Pole length is 1.6 (wagon) or 1.9 (carriage); drag is -25% or -35%. Right-click again to let go. A carriage can only be pulled while empty and is dropped if items go in. Hitch state is now "puller" (any LivingEntity); only horses persist through a reload.
- The chests on the carts are now vanilla's own: `ChestModel` (`ModelLayers.CHEST`, or `DOUBLE_CHEST_LEFT`/`RIGHT`) with the `Sheets.CHEST_REGULAR` sprites, so they follow resource packs. The wagon has a single chest in the back with its lock facing the tailboard. The carriage has a double chest lengthwise down the middle, since a double chest is too wide to fit across it. The modelled chest boxes were removed from `gen_carts.py`.
- Horses keep their native speed while pulling: the towing slowdown now only applies to a player pulling a cart by hand.

## 0.2.1
- **Build output moved to `E:\jaymos-kitchen-build`** (`layout.buildDirectory` in build.gradle). The project now lives in OneDrive, and its syncing locked files in `build/` so rebuilds failed. Jars are in `E:\jaymos-kitchen-build\libs`.
- **Corpse heads no longer shake.** `clientBody` reset `xRot` to 0 but left `xRotO` at the saved pitch, so every frame blended the two. `xRotO` is now reset too.
- **Carried bodies hang limp:** the arms and legs swing with the carrier's stride (their walk animation), and the head lolls and sways.
- **Fish leave no corpse:** cod, salmon, tropical fish and pufferfish are in `no_corpse`, so they drop their fish as usual.
- **Hostile corpses never rot away:** if the source type's category is MONSTER, the corpse skips the 10-minute rot, so it has to be burned. Zombies still get back up; skeletons still crumble once their bones are pulled. Searching shows a "burn it: it won't rot away" message.
- **Butchering rework.** An axe now butchers an animal anywhere (3 chops).
  - On a butcher block: all its loot, plus 1-2 extra of its meat, plus `ButcheringBonus` extras. These are per animal: hides and leather (70%), bones (40%), feathers, eggs, string, and rabbit foot (20%). Other animals: bones at 40%.
  - On the ground, a "rough job": each loot stack is halved (a stack of 1 has a 50% chance to drop), with no extras.
- **Quiver item sprite:** vanilla's unused quiver sprite, supplied by the user (downscaled 160 to 16 px exactly). `gen_assets.py` lists it in `HAND_MADE_SPRITES`, so only its model JSON is written.
- **Horse Hoe**, a new cart after Jethro Tull's 1701 horse-hoe.
  - Model: small front wheel in a fork, a hooked beam, shafts, three iron tines with blades, and two splayed handles with grips and a brace. `gen_carts.py` gained part rotations (`offsetAndRotation`) for the handles.
  - `CartKind` (WAGON / CARRIAGE / HORSE_HOE) now holds each cart's slots, wheel radius, pole lengths and hand drag.
  - Hoe rack: 5 slots, hoes only. The server uses `HoeRackMenu`, a hopper menu whose slots are swapped for hoe-only ones; the client shows the plain hopper screen.
  - Tilling: while pulled and moving, it tills a rough 3x3 centred 1 block behind it. Each block has a 15%/tick chance; grass, dirt and paths become farmland, and coarse or rooted dirt becomes dirt. Every block tilled takes 1 durability from a random racked hoe, and with no hoes it does nothing. A creative puller (or creative rider of the horse) needs no hoes.
  - Recipe: stick, gap, stick / 3 planks / iron, log, iron.
  - Verified headless: 23 blocks tilled over a pull, and the wooden hoe lost exactly 23 durability.

## 0.2.2
- **Carried corpses hang limp.** The walk animation is replaced by a hanging pose. `Limp` is a render-state data key set by `CorpseRenderer` with a sway value: a slow drift plus a little from the carrier's stride. `ModelFeatureRendererMixin` applies it right after a model's `setupAnim` (so after subclass overrides like zombie arms) by part name.
  - Humanoids: `right_arm`/`left_arm` hang back (toward the ground, the body lying on its back) at about 1.35 rad, and the legs droop 0.45.
  - Quadrupeds: the `*_hind_leg`/`*_front_leg` parts flop out to the sides.
  - Other models just stir with a tiny walk cycle.
- **Butcher blocks for every wood:** oak (keeps the id `butcher_block`), spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, crimson and warped.
  - Textures: sides and bottom use the log's own vanilla textures (referenced, not copied); the end-grain top is generated in each wood's colours.
  - Recipe: 2 of that wood's logs (tag `#minecraft:<wood>_logs` / `_stems`).
  - Checks go through the `jaymoskitchen:butcher_blocks` tag via `Corpses.isButcherBlock`. `gen_assets.py`'s `write_butcher_blocks()` writes everything per wood, including tags, loot tables and recipes.
  - Fixed a startup crash from building `ItemStack`s at registration; they're made in the creative-tab event.
- **Horse Hoe renamed to Plow** (ids `plow`) and remodelled after the AstikorCarts plow: two big spoked wheels (r = 10) on an axle, long shafts, an A-frame, and five arms behind on a cross bar.
  - Arms: right-click (from the saddle, the back of the plow) lowers or raises them. The front from the saddle unhitches; on foot, sneak + right-click opens the hoe rack. The arms swing between 0.7 and -0.46 rad at 0.12 rad/tick on clients (synced `LOWERED`, saved as `Lowered`).
  - Hoes: each arm carries the hoe racked in its slot, drawn as the real item at the arm's end (the stacks are synced in 5 `ITEM_STACK` entity-data slots).
  - Tilling only happens while lowered. The chance per block per tick is `1 - 0.85^strength`, with strength summed over the racked hoes: wood 1, stone 1.5, copper 1.75, iron and gold 2, diamond 3, netherite 4, other hoes 1.5, creative 10. One wooden hoe gives 15%; five netherite give 96%.
  - Wear is spread evenly across the racked hoes.
  - Verified headless: raised, no tilling; lowered with 1 wooden hoe, 24 blocks over a pull; lowered with 2 netherite, 31 blocks over 9 blocks of travel.

## 0.3.0: Carts and mills drop
Third-party work included, with licences in the jar root:
- **NiftyCarts** (MIT; MennoMax, paul101, jmb19905): the supply cart, animal cart and plow models, their runtime wood-texture layouts, item sprites, the wheel, and the plow GUI. See `LICENSE_niftycarts`.
- **Horse Power** (LGPL-3.0; Gory_Moon): the machine block models, the flour textures, and the default recipes. See `LICENSE_horsepower`.
- `fabric.mod.json` credits both.

### Carts (NiftyCarts port)
- **Line-up:** our wagon stays. The covered carriage is removed (saved ones vanish), as are the 0.2.x single plow and horse hoe.
- **New carts, in 12 woods:** oak, spruce, birch, acacia, cherry, jungle, dark oak, pale oak, crimson, warped, mangrove and bamboo. Each kind is one entity type, with the wood a synced string (`Wood`). Items are `<wood>_supply_cart`, `<wood>_animal_cart` and `<wood>_plow`, plus a `wheel` (8 sticks around a plank).
- **Recipes:** NiftyCarts' — supply cart: plank, chest, plank / plank, chest, plank / wheel, plank, wheel. Animal cart: 6 planks / wheel, plank, wheel. Plow: 3 sticks / plank, stick, plank / wheel, plank, wheel.
- **Behaviour:** `CartKind` holds slots, wheel radius, spacing (pole = spacing + half the puller's width), hand drag, and whether it's a NiftyCarts cart. NiftyCarts' carts pitch toward the puller and rest tipped 25° forward on their shafts when unhitched.
- **Supply cart:** 54 slots. Pulled by hand only while empty. Its top 4 cargo kinds are synced (`SHOWN`) and drawn in the bed: blocks as small blocks, items as flat piles (NiftyCarts' generic supplies only; no paintings, armour or flower basket).
- **Animal cart:** small non-water, non-player mobs that bump into it climb in, up to 2 (NiftyCarts' rule). Sneak + right-click unloads them; plain right-click lets you ride if there's room.
- **Plow:** NiftyCarts' model with 3 arms. The real racked hoes are drawn on the arm ends. Right-click raises or lowers it (arms 72° to 45°, following the pitch when down). Sneak + right-click opens `PlowMenu`, a registered menu type with NiftyCarts' 3-slot screen. Tilling chance is `1 - 0.85^(strength^1.3)`, where strength is the sum of the hoe strengths: 1 wooden hoe gives 15%, 3 netherite give 98%.
- **Client:** `NiftyCartModels` (geometry ported via `NiftyMesh`), `NiftyCartModel` (pitch, wheels, plow arms, attach points), and `CartTextures`, which builds each 64x64 texture the first time it's used. It reads the vanilla block PNGs from the resource manager and applies NiftyCarts' fill layouts. `CartClient` registers everything.

### Mill machines (Horse Power)
- **Blocks:** `hand_grindstone` (Hand Grindstone), `grindstone` (Horse Mill), `chopping_block`, `chopper` (Horse Chopper, 2 tall), `press` (2 tall) and `horse_press` (2 tall). Tall machines place an invisible `machine_filler` above, which forwards clicks and breaks the machine. `machine_part` is never placed; its states hold the moving-part models.
- **Models and recipes:** Horse Power's models updated to current texture names. Recipes are Horse Power's for the hand grindstone, horse mill and horse press; ours for the hand press, chopping block (3 logs) and horse chopper (leads, stick, iron axe, planks).
- **Block entity:** `MachineEntity` has an input slot, an output slot, the press tank (water or oil, 4000 mB), and progress.
  - **By hand:** the grindstone takes 6 turns per item; the chopping block takes 3 axe chops per log; the press takes 4 pulls per load.
  - **By horse:** one lap per item or load. Right-click while leading a horse to tie it on; sneak + empty hand unties it and gives the lead back.
  - **The horse's walk:** it walks circles at radius 3. It's sent 1.3 rad ahead, falling back to 2.0 and 2.6 when the path fails, because a horse can't path to a spot 2 blocks ahead. Laps are counted from the change in its angle.
  - **Hoppers:** they feed the input from the top and sides and pull the output from the bottom (`WorldlyContainer`).
- **Recipes (`MachineRecipes`):** grinding turns wheat into flour, sugar cane into sugar, bone into 3 bone meal (a bone block into 9), and flowers, beetroot and cocoa into dyes. Chopping turns a log into planks, crafting count + 1 by hand or + 2 by horse. Pressing turns 12 wheat seeds into dirt, 8 leaves into 1000 mB of water, and 4 pumpkin, melon, beetroot or torchflower seeds or a pitcher pod (or 1 sunflower) into 250 mB of oil. Draw water off with a bucket or bottle; draw oil off with a bottle as Cooking Oil.
- **Flour:** 3 flour + a water bucket make 2 Dough.
- **Rendering (`MachineRenderer`):** the hand grindstone's stone turns a quarter turn per crank, eased in; the chopper blade and press plate lower with progress; the horse mill shows flour; the log, load or input items are drawn; and a vanilla leash (via `submitLeash`) runs from the post to the horse.
- **Verified headless:** horse mill 3 wheat to 3 flour in about 30 s; horse press 16 leaves to 2000 mB of water. (The test server needs `pause-when-empty-seconds=-1` in `run/server.properties`, or it stops ticking entities when no players are on.)

### Crafting tables keep their grid
- **Storage:** `CraftingTableBlockMixin` makes the vanilla crafting table an `EntityBlock` (`CraftingTableEntity`, 9 slots, synced to clients).
- **Menu:** `CraftingMenuMixin` lays out the stored grid when the table is opened, stores every change (`slotsChanged`), and on close stores the grid and empties the screen's copy, so nothing is handed back. `AbstractCraftingMenuAccessor` provides `craftSlots`.
- **One user at a time:** a second player gets "Someone else is using this table." The lock clears on close, or if the user is offline or no longer has a crafting menu open.
- **Breaking:** a broken table drops the grid only if nobody has it open; otherwise closing hands it back, so nothing duplicates.
- **Rendering:** `CraftingTableRenderer` draws the 3x3 items lying on the top.
- Not testable headless (needs a player).

## 0.3.1
- **Carried corpses move only with the carrier:** the time-driven drift is gone. Limb sway, head bob and head turn come only from the carrier's walk animation, and everything hangs still when they stand still.
- **Crafting table items now draw:** a client table brought to life by a data packet wasn't in its chunk section's draw list. `CraftingTableEntity.loadAdditional` now marks the section for redraw on the client.
- **Cooking oil** is vanilla's potion item model with a constant golden tint (`0xFFE0B82C`) on the liquid. It follows resource packs, and the old drawn sprite is removed.
- **Flour instead of wheat:**
  - The cooking step `WHEAT` is now `FLOUR` (dough prep, risotto, berry pie, decoys).
  - Vanilla bread, cake and cookie are overridden in `data/minecraft/recipe` to use flour.
  - Hay bale, wheat from hay, and packed mud are left alone.
- **Prep boards:** only the top of a half (not double) slab that a player placed. `BlockMixin` records player-placed slabs in a persistent per-chunk attachment (`PlacedSlabs`). The client checks only for a half slab; the server also checks the record, and prunes entries whose slab is gone. The `prep_surfaces` tag is removed.
- **Sowing (`farm/Sowing`):**
  - Right-click with `#jaymoskitchen:sowable_seeds` (wheat, beetroot, melon, pumpkin, torchflower, pitcher pod) to scatter 3 seeds 1.5–4 blocks ahead in a fan, with item particles arcing out.
  - Seeds landing on open farmland take root; the rest become seed item entities on the ground.
  - A 6-tick cooldown applies. Sneak + right-click plants as vanilla does.
  - Animals that eat wheat seeds (chickens, parrots) get `EatGroundSeedsGoal` on load (via `MobAccessor`): they walk to seed items within 8 blocks and eat them (heal 1, a 1-in-4 chance to fall in love).
  - Verified headless: a chicken ate 2 seeds off the ground.
- **Player corpses:**
  - **On death:** `PlayerMixin.dropEquipment` sets the hotbar aside (unless keepInventory; vanishing-curse items are dropped from it) along with the selected slot; the rest drops as usual. When the death animation ends, `LivingEntityMixin` spawns a `CorpseEntity` of kind `PLAYER` (owner UUID and name synced; hotbar and selected slot saved).
  - **Right-click:** the hotbar goes back to the same slots where they're free, otherwise into the inventory; leftovers stay on the body. The owner also gets their selected slot back.
  - **Never rots:** after 5 minutes it rises as a `ZombiePlayer` unless burned. Like other corpses, it can be carried (sneak + empty hand) and burned.
  - **Rendering:** `CorpseRenderer` draws player corpses as a `PlayerModel` in the owner's skin (from the player list, or the default skin for their UUID). On the ground it lies face up or down in a seeded `Ragdoll` pose: arms and legs splayed outward along the ground, lifted slightly off it, head turned. Carried, it hangs `Limp`. Ragdoll is applied by part name in `ModelFeatureRendererMixin`, like Limp.
- **Zombie players (`ZombiePlayer`, extends Zombie):**
  - Holds the dead player's selected-slot item (always dropped) and carries the rest of the unclaimed hotbar, which it drops on death. It doesn't turn into a drowned.
  - The owner is saved and synced, and is part of its corpse's look, so its corpse in turn reanimates as the same zombie player. It's in `zombie_corpses`.
  - **Rendering (`ZombiePlayerRenderer`):** the owner's skin, slim or wide, tinted `0xFF93A8AE`, with zombie arms (`ZombiePlayerModel`) and a held item. A tatters layer on an inflated model uses `entity/zombie_player_tatters.png` (generated by `gen_assets.py`: rips with grey-blue flesh showing through, and grime).
  - Verified headless: it summons, and its corpse keeps the owner. Player death itself needs a real player.

## 0.3.2
- **Hanging limbs with physics:**
  - **The simulation:** each carried corpse runs a damped pendulum on the client (`CorpseEntity.swingLimbs`). It has two axes: toward the carrier's back, and toward their left (along the body). It's driven by the corpse's own acceleration, so speeding up, stopping and the swing of turning all count, plus a small jog from each step. Constants: stiffness 0.16, damping 0.9, shove 9, clamp 1.3 rad.
  - **Humanoids:** in `Limp`, limbs hang straight down from the shoulder or hip (xRot π/2, since the body lies on its back) plus the swing, each limb with slightly different give. The head is thrown back (-1 rad) and rolls with the swing.
  - **Four-legged animals:** legs flop out past level (±1.9 rad) and swing.
  - Nothing moves when the carrier is still.
- **Humanoid mob corpses ragdoll:** if the body's renderer uses a `HumanoidModel` (zombies, piglins, skeletons...), the ground corpse lies flat, face up or down, in a `Ragdoll` sprawl instead of keeling over sideways. It's tipped about its side axis and centred on the corpse.
- **New pose each landing:** a synced `POSE` seed is rolled when the corpse is made and on every `putDown`. Player corpses use it too.
- **Rotting:** rotting corpses leave 1–2 rotten flesh. Hostile mobs now rot too, after 20 minutes instead of 10, with a 10% chance of their original loot instead. Exceptions: zombie kinds still get up, creepers never break down, and skeletons still crumble when their bones are pulled.
- **Press:** wheat seeds press to oil (4 per 250 mB) like the other seeds; no more dirt.
- **Verified headless:** a cow and a spider rotted to rotten flesh, and 8 wheat seeds pressed to 500 mB of oil.

## 0.3.3
- **Wagon in AstikorCarts style:** it's NiftyCarts' supply-cart frame (wheels, bed, boards, shafts) plus a plank bench, drawn 1.2x (`NiftyCarts.WAGON_SCALE`). It comes in every wood (`<wood>_wagon`; oak keeps the id `wagon`), with NiftyCarts' wagon item sprites.
  - **Recipe:** stick, gap, chest / 3 planks / wheel, gap, wheel.
  - **Parked:** it rests tipped 25° when unhitched, and the seat point follows the pitch about the axle.
  - **Chest:** a vanilla `ChestModel` at the back whose lid opens while anyone has the wagon open. Viewers are counted in `startOpen`/`stopOpen` (synced `CHEST_OPEN`, with the chest open and close sounds); the client eases the lid as vanilla does.
  - **Removed:** our generated wagon model (`CartModel`, `CartShapes`, `gen_carts.py`, `entity/wagon.png`).
- **Limb physics, stronger:** limbs now trail behind in proportion to the body's speed (3.2 rad per block/tick, clamped to 1, so most at a sprint). Every change of pace or turn shoves them (×14), and each footfall jolts them (0.09 × stride, twice a stride). Stiffness 0.2, damping 0.86.
- **Animals carried belly down across the arms** (no longer rolled onto their backs): legs hang straight down and swing, and the head hangs.
- **Undead corpses burn in daylight:** zombie and skeleton kinds, when not carried and where monsters burn, with enough light, no water or rain, and open sky. Skeleton corpses can now burn (also with flint and steel); burning away leaves their remaining bones.
- **Corpses fall when set down off a ledge or against a block's side:** gravity always applies, and `putDown` clears the on-ground flag.
- **Zombie players:** the owner is re-sent 5 ticks after spawning, and the client falls back to looking up the skin by name.
- **Verified headless:** zombie and skeleton corpses caught fire at noon and burned away; the skeleton left 3 bones.

## 0.3.4
- Fixed zombie players drawn as plain players when they first rise: the game sends any `AvatarRenderState` to its player renderer, so `ZombiePlayerRenderer` now uses its own `HumanoidRenderState` subclass and `ZombiePlayerModel` is a `HumanoidModel` built from the player mesh. Corpses looked right because `CorpseRenderer` calls the renderer directly.

## 0.3.5
- **Corpses always reach the ground:** the corpse's box is now narrow (0.9 x its smaller standing dimension, clamped to 0.4–0.9) instead of a square as wide as the body is long. The wide box rested on a neighbouring block's edge and hung over the air. Gravity applies every tick, so it also falls when the block under it goes.
- **Jolts:** entity events 70 (hard) and 71 (light) start a 10-tick jolt on the client. The body hops and rocks (up to 14 degrees, dying away) about a point just off the ground. Axe chops jolt hard; looting (searching, pulling a bone, taking gear, taking back a player's hotbar) jolts lightly.
- **Carry message:** carrying anything but an animal says only "Carrying it. Right-click a block to set it down."
- **Gear off corpses:** once a body has been searched (a skeleton: before its bones), right-clicking takes off its armour and held items one at a time (head, chest, legs, feet, body, main hand, off hand). Each piece is removed from the look's `equipment`, so it's gone from the drawn body too.
- **Four-legged animals are carried belly down:** carried bodies are classed by model (`right_front_leg` means four-legged, carried belly down; `right_arm` means humanoid, carried on its back), not by hitbox; a cow is taller than it is wide.
- **Verified headless:** a corpse fell when the block under it was removed.

## 0.3.6
- **Carry arms:** a player carrying a corpse holds both arms out forward and down (xRot -0.8, angled in 0.3) in third person (`CarryPose`, set in `AvatarRendererMixin`, applied to body and armour after `setupAnim`). In first person both arms show as with a two-handed map (`FirstPersonHandsMixin`). The carry point moved to forward 0.55, height 0.9 to sit on the hands.
- **No nametags:** zombie players are no longer named after the player (existing ones lose the name), and corpses and their bodies never show a name tag.
- **Zombie player face:** removed the grime round the mouth from the tatters texture; it read as a grin.
- **Skeleton corpses stay:** skeletons no longer catch fire in the sun (they already never rotted). They only go if you burn them.
- **Per-limb jolts:** the whole-body hop and rock is replaced by `Jolt`: head and limbs (on the root or the body part) each flop on their own beat, up to 0.9 rad for an axe blow and about 0.27 rad for looting, dying away over 10 ticks. The body stays put.

## 0.3.7
- **No "Zombie Player" nametag:** `ZombiePlayerRenderer` extends `LivingEntityRenderer`, whose name rule shows every entity's name (as for players). It now uses the mob rule: only a name-tagged one, when looked at.
- **Higher voice for slim skins:** a player's slim-arm skin (from their profile's skin metadata, or the default skin picked from their UUID) is saved on their corpse (`Slim`) and on their zombie. Twitch groans, the rising sound and the zombie's own sounds are pitched up x1.35, and so is the corpse of a killed slim zombie player.
- **Pam's HarvestCraft 2:** not ported. Its licence reserves all rights and forbids using its textures or redistributing it.

## 0.4.0: Kitchen drop
All art is original (`tools/gen_kitchen.py`). The idea of cookware staying in the crafting grid comes from Pam's HarvestCraft 2, but none of its code or textures are used: its licence forbids using its textures and allows only add-ons, JSON and config changes on top of the original mod, which doesn't run on 26.3.
- **Kitchen Cabinet (every wood):** 27 slots, barrel-style. The door shows open while someone is inside, and the stone counter top works as a prep board. Recipe: 3 smooth stone slabs over planks/chest/planks over 3 planks.
- **Fridge:** 2 tall, 54 slots held by the lower half, iron door sounds, opens on both halves. Only the lower half drops it, and breaking the top in creative doesn't drop it. Recipe: iron ring around a chest, redstone at the bottom.
- **Stove:** `FACING`, `LIT`, `UTENSIL` (none/pot/pan) blockstate, drawn as a multipart with the pot or pan on the burners. Right-click the front to turn it on or off. Right-click with a Cooking Pot or Frying Pan to set it down; right-click any other face to take it back. Light 13 while lit, with flame, bubble and smoke particles. Sneak + empty hand on a lit stove cooks: new stations `STOVE` (oven dishes), `STOVE_POT` (sequence dishes) and `STOVE_PAN` (grill and sear dishes). Recipe: 8 iron around a furnace.
- **Ingredients from storage:** cooking and prep take ingredients from the pockets first, then from cabinets and fridges within 4 blocks (2 up or down). `OpenKitchen` and `OpenPrep` now carry a `stocked` bitmask so the screens can enable dishes the client can't see the ingredients for.
- **Cookware (crafting tools):** Cooking Pot, Frying Pan, Saucepan, Baking Tray and Rolling Pin. Each is its own crafting remainder (set through the `ItemAccessor` mixin), so it stays in the grid. Cooking Oil now leaves its glass bottle when used in crafting.
- **Foods:** Fried Egg, Bacon (x3), Fries (x2), Vegetable Soup, Berry Jam, Hot Chocolate, Flatbread (x2, from dough), Pasta (x2, an ingredient), Mushroom Pasta, Brownie (x4).
- **Verified headless:** the stove's states; fridge loot (lower half only) and the lower half breaking with the upper; the cabinet storing items; the stove dropping itself; no data or recipe errors on load.

## 0.4.1
- **Zombie players have sharp senses:** follow range 64 (a zombie's is 35). A player sprinting within 24 blocks gets noticed even out of sight.
- **Zombie player speed:** a mob's ground speed goes with the square of its speed attribute (`Mob.setSpeed` also sets its forward input). Base 0.305 (0.093 squared, against a walking player's 0.098) is barely slower than a walking player. While its target is sprinting, it gets +19% speed (about 5.8 blocks a second, just faster than a sprinting player) and sprints; the boost goes as soon as the player stops sprinting. Existing zombie players are brought up to these values.
- **Verified headless:** a summoned zombie player had speed 0.305 and follow range 64.
- **Cooking for Blockheads:** its licence (All Rights Reserved) allows use in modpacks but not re-uploading its files publicly, so its models can't be bundled in this jar. It has a 26.3 build of its own to install alongside.

## 0.4.2: Cooking for Blockheads kitchens
- **Cooking for Blockheads (by BlayTheNinth) is now required, with Balm.** It isn't bundled: its licence allows modpacks but not re-uploading. Both are declared in `depends` and built against from Modrinth's maven (`implementation`, not included in the jar).
- **Removed our stove, fridge and kitchen cabinets** (blocks, block entity, assets and recipes). Its ovens, fridges, counters and cabinets replace them. Our cookware and foods stay.
- **Ovens (any colour) are the stove stations:** sneak + empty hand cooks oven dishes. Sneak with a Cooking Pot in hand cooks pot dishes, and with a Frying Pan grill and sear dishes; the pot or pan has to be in your inventory or the kitchen. `StartCooking` now carries the station, and the server checks it with `Station.at(state)`, since the stove stations all work at the oven.
- **Ingredients come from the whole kitchen** (`KitchenPool`): Cooking for Blockheads' own `KitchenMultiblockScanner` finds the connected kitchen from the spot, and its item providers (your inventory first, then fridges, counters, cabinets, milk jars...) supply ingredients one token at a time. This also applies to campfires, furnaces and so on that touch a kitchen.
- **Prep:** Cooking for Blockheads cutting boards and counter tops are prep boards.
- **Verified headless:** the server starts with balm 26.3.0.3 and cookingforblockheads 26.3.0.2, and its blocks place.

## 0.4.3
- **Zombie player undertones:** 60% of its sounds (groans, hurt cries, death) get a second sound mixed underneath. It's picked at random from ghast ambient, scream and hurt, enderman scream, phantom, zoglin, vex, warden, wither skeleton, husk, zombie villager, ravager roar and cave ambience. It plays at 35–65% volume and at 0.65–0.95 of the zombie's own pitch, so slim-skinned zombies' undertones are higher too. Hooked on `makeSound`, server side.

## 0.4.4: Leg hits knock creatures down
- **Leg hits** now also come from melee: a full-strength blow (attack cooldown above 0.9) whose line of sight meets the target's box in the bottom 3/8, via `AttackEntityCallback` and a clip of the eye ray against the box. Arrows work as before.
- **Crawl:** a leg hit puts the target down for 5 s with Slowness IV. The limp (I, II on a crit, stacking to III) is added first, so the effect system keeps it underneath and it takes over afterwards. The end time is a synced attachment (`leg_crawl`). The hit also plays a crit and a heavy-fall thud.
- **Drawn crawling** (`CrawlPose`, `LivingEntityRendererMixin`), easing down and back up over 5 ticks:
  - Humanoids lie face down like a crawling player (rotate -90° and translate by the unscaled height x0.555), and mobs paddle their arms (`swimAmount`).
  - Other creatures sink their hindquarters (14°) and drag their back legs out behind.

## 0.4.5
- **Hostile humanoids stay down for 30 s** (`#jaymoskitchen:downed_long`: zombies, skeletons, illagers, witches, piglins and zombie players); everything else stays down for 5 s. The crawl attachment now packs the end time (shifted up 12 bits) with the duration (low 12 bits), so the client can ease in and out over any length.
- **No shooting while downed:** a downed mob lets go of whatever it's drawing (`stopUsingItem`, from the `LivingEntityMixin` tick), and `RangedAttackMobMixin` cancels `performRangedAttack` for skeletons, witches, drowned, pillagers, piglins and illusioners.
- **Humanoid crawl animation:**
  - The arms alternately reach ahead and pull back under the chest, faster while it's actually moving and a weak struggle otherwise. The legs drag behind, twitching.
  - The head is tipped back to look along the ground, then turned (about the body's front-to-back axis, since it's lying down) and tilted toward where the mob is looking, i.e. at its target.
  - This replaces vanilla's swim-stroke arms. Zombies' raised arms and skeletons' aim are overridden while they crawl.
- **Verified headless:** skeletons, witches, pillagers, drowned, piglins and illusioners load with the mixin applied and no errors.

## 0.4.6: The oven works like the old stove
- **On/off:** right-click the knob strip (top quarter of the front) of a Cooking for Blockheads oven with an empty hand to turn it on or off, with flint-and-steel and button-click sounds. The rest of the front still opens the oven's own screen. The lit state is a persistent, synced block-entity attachment (`OvenHob.LIT`). Sneak-clicking an unlit oven says how to turn it on and falls through to the oven's own door toggle.
- **Cookware on the hob:** our Cooking Pot, Frying Pan, Saucepan, Baking Tray and Rolling Pin are added to Cooking for Blockheads' `utensils` item tag (a JSON add-on), so they go in its tool spots on top and are drawn there. Only one pot or pan at a time ("One pot or pan on the hob at a time.").
- **Stations from the oven's state** (`Station.of(level, pos)`): a lit oven with nothing on the hob is the Oven (oven dishes), with a pot it's the pot hob (stews and soups), with a pan it's the pan hob (grill and sear). Sessions check `station.at(level, pos)`, so turning it off or swapping the pot ends the cook. Holding the cookware is no longer needed.
- **Visuals (client mixins):** `OvenRendererMixin` lights the oven window while it's on, using the oven's own burning look. `OvenBlockMixin.animateTick` adds a crackle and burner flickers, bubbles and steam for a pot, smoke for a pan.

## 0.4.7
- **No burner flames:** a lit oven with an empty hob only crackles; a pot still bubbles and steams, a pan smokes.
- **Dishes finish on the hob:** when a minigame ends at an oven, the screen closes straight away (`CookingScreen.closeAtOven`, on all three result paths) instead of showing the result card. The dish keeps cooking for 4 s (80 ticks; `Kitchen.FINISHING`, ticked from `Kitchen.tick`), with server-sent bubbles and steam (or smoke for a pan) every 3 ticks and a bubbling or sizzling sound every second. Then it's handed over with the quality sound and "<dish> is ready!". If the cook has logged out, it pops out on top of the oven.

## 0.4.8
- **Text fixes:**
  - Flour's button name (`step.jaymoskitchen.flour`) was missing.
  - "Air is ready!": the dish's name was read after the stack had gone into the inventory and was empty; it's now read first.
  - The cooking window's bottom hint is only drawn when a translation exists, so ovens show none.
- **See-through cooking window:** panel alpha 0x90 (was 0xE0), and no blurred or dimmed background (`extractBackground` overridden), so the hob stays in view.
- **Oven heat** (`OvenHob.HEAT`, a persistent, server-only block-entity attachment, up to 3200):
  - The knobs only light it if it has something to heat with: fuel in its fuel slots, or, with a heating unit fitted, a redstone signal (otherwise there's a message and a dull click).
  - Lit, it burns the next fuel item whenever heat drops below max(400, half full), using the oven's own burn-time lookup (`OvenBlockEntityMixin` shadows `getBurnTime` and hooks the end of `serverTick`). Lava buckets leave their bucket. With a heating unit and a redstone signal, it gains 10 a tick instead.
  - It uses 1 a tick while lit; a dish takes 400 and a reheat 100 (creative is free).
  - With no heat and nothing to refill from, it goes out with a fizz.
- **Reheating:** right-click any lit station (campfire, furnace, smoker, blast furnace or oven) holding an undercooked dish. It goes back on and comes back at Good quality 4 s later, with smoke, or bubbles and steam for a pot on the hob (`Kitchen.reheat`, `Quality.of`, `ModItems.dishOf`).
- **Verified headless:** an oven places and ticks with the mixin applied, with no errors.

## 0.4.9
- **See-through cooking window:** the frame was one solid rectangle drawn under the whole panel, so the translucent panel just showed the frame colour through it. Only its four edges are drawn now, and the panel is at alpha 0x80.
- **Minigame dishes in Cooking for Blockheads' cooking table:**
  - New recipe type `jaymoskitchen:minigame` (`MinigameRecipe`, a crafting recipe that never matches a grid). There's one JSON per dish in `data/jaymoskitchen/recipe/minigame/`; ingredients and result come from the `Dish`.
  - It uses a real placement: unplaceable recipes get no display id and are dropped from displays.
  - A `KitchenRecipeHandler` registered through `CookingForBlockheadsAPI` lists them with their ingredients.
  - Picking one is caught at the head of `KitchenMenu.craft` (`KitchenMenuMixin`). The cooking table closes and the minigame starts at the nearest station within 6 blocks that can make that dish (`Kitchen.startAt`), or the player is told none is nearby. Ingredients come from the kitchen as usual.
- **Kitchen floors:** checked headless with a temporary scan command. A fridge standing on kitchen floor that runs to the cooking table is found (0 item providers without the floor, 1 with it), so the floor itself connects.

## 0.4.10: 3D cookware (four agents)
- **Renderer scout:** Cooking for Blockheads' `OvenRenderer` draws the 4 tool slots in the FIXED context, at 0.4 scale, tilted +45° about X, with slot offsets ±0.55/±0.5.
- **Models** (two modellers): `models/item/{cooking_pot,saucepan,frying_pan,baking_tray,rolling_pin}_3d.json`, built from `elements` with original textures in `textures/block/cookware_*.png`. Generators: `scratchpad/cookware.py` and `cookware3d.py`.
- **Integration:**
  - Item definitions `select` on `display_context`: the GUI keeps the flat sprite, everything else uses the 3D model.
  - `OvenRendererToolsMixin` (MixinExtras `@WrapOperation` on the four tool-slot `updateForTopItem` calls) gives our cookware the HEAD context on the oven, whose display entry undoes the 45° tilt and stands the model on the hob at about 6.5 px wide. Item frames (FIXED) and shelves (ON_SHELF) get their own normal poses.
  - Hand and ground poses were re-centred.
  - Trade-off: cookware worn on a head (commands only) shows the oven pose.

## 0.4.11: Hob spots, timed cooking, specific messages (four agents)
- **Specific "nowhere to cook" messages** (`CookingTableBridge.noStationKey`, by dish method), e.g. "Hearty Stew: put a Cooking Pot on a lit oven." Other long overlay messages were shortened to about 55 characters or less.
- **Up to 4 utensils on the hob, one per spot.**
  - `BaseKitchenBlockMixin` adds an aimable outline box per occupied slot, matching the renderer's slot centres and turned with facing. `OvenBlockCollisionMixin` keeps the collision shape the plain oven.
  - Right-click on a spot (`OvenHob.aimedSlot`): with an empty hand it takes the item there; holding a utensil it places one in that spot, or swaps with what's there. Changes sync at once.
  - The one-pot-or-pan rule is gone.
- **Stations from the whole hob:** a lit oven always offers its baked dishes; a pot anywhere adds pot dishes, a pan adds pan dishes (`STOVE_FULL` for both). Sessions check `Station.cooks(level, pos, dish)`, so adding or removing other cookware mid-cook doesn't cancel. The cooking screen's dish list shows 8 rows and scrolls (wheel or arrow buttons).
- **Timed cooking** (`HobQueue`, a persistent oven block-entity attachment `oven_pending`, ticked from the oven's server tick):
  - After the minigame, oven-baked and pot dishes take 1:00–5:00 and pan dishes 0:15–0:45. The time is ranked by `Dish.reward()` (pizza 5:00, hearty stew 4:05, grilled salmon 0:15). Reheating at an oven takes 0:10.
  - Particles show while it cooks.
  - When ready, it goes to the cook if they're online within 16 blocks; otherwise it pops out above the oven.
  - Pending dishes survive save and reload, and drop if the oven is broken.
- **Verified headless:** builds cleanly; collision stays plain with utensils in the slots; pending dishes save, reload, get served when due and drop on break; the client starts with no errors.

## 0.4.12 (four agents)
- **All our recipes show in the cooking table:** Cooking for Blockheads hides any recipe the kitchen can't fully make unless a No Filter book is in the table (`KitchenMenu.shouldShowRecipe`). `KitchenMenuMixin` now always shows recipes in our namespace (via `CraftingOperationAccessor`), greyed out with the missing ingredients marked. A refused start now rebuilds the cooking screen's dish list (it used to come back with no buttons).
- **Dishes on the oven rack:** `HobQueue`'s pending list syncs to clients, and `OvenRendererMixin` puts pending dishes into empty spots on Cooking for Blockheads' interior rack (FIXED context, as it does for its own items). The window lights while dishes are pending. As with its own items, the rack only shows with the door open.
- **In-use cookware is locked** (`HobLocks`): a pot per pot dish and a pan per pan dish, counting both running minigame sessions at that oven and dishes waiting in `HobQueue`.
  - Taking one off the hob, or swapping it out, is refused with "That pot's in use: <dish> is cooking."
  - The oven screen's tool slots are locked through a synced `hob_locked` mask (`OvenMenuMixin`, `SlotOvenToolMixin.mayPickup`).
  - Hoppers or pipes may still reach the tools (unchecked).
- **Corpse gear:**
  - Vanilla equipment drops are no longer captured into corpse loot (`LivingEntityMixin` restores the slot), so each piece comes off only by hand (`takeGear`), with no duplicates.
  - Handed over, a skeleton's main-hand weapon keeps 10–49% durability (always below half), and any other damageable gear keeps 15–90%. Gear a mob picked up itself is unchanged.
- **Verified headless:** builds cleanly; an oven with a pot, a pan and a pending stew ticks and breaks cleanly; a killed skeleton's loot no longer holds its bow, and its look keeps bow and helmet. Not verified: wear rolls, the lock, rack drawing, recipe listing (all need a player).

## 0.4.13: Performance fix (corpses)
- **Cause of the lag:** profiled with Java Flight Recorder on a loaded test server (~100 mobs and corpses). About 62% of server-thread time was `CorpseEntity.sourceType()`. It re-parsed the corpse's whole look (the mob's saved data as SNBT) every tick, called from `isCreeper()` and `isHostile()` in the rot check. Looks got bigger in 0.4.12 because bodies keep all their gear, which made it worse.
- **Fix:** the mob type is cached per look and cleared when `LOOK` changes (`onSyncedDataUpdated`); `getDimensions` uses the cache too. Corpses resting on something solid now skip the gravity/collision step each tick, and fall at once if the block under them goes (verified: one fell from -57 to -60 when its support was removed).
- **Result** (same load test, steady state): about 16.8 ms per tick average with 197 ms spikes before, about 1.1 ms with 7 ms spikes after.

## 0.4.14: corpses meet explosions, flames and gravity (asked for from the Jaymo's Powers pack)
- **Explosions:**
  - Any explosion (`#is_explosion`) blows a corpse apart in a puff of smoke. It drops whatever loot it still had,
    plus 1-2 rotten flesh, or a skeleton's remaining bones.
  - A creeper corpse caught in a blast goes off itself, so creeper corpses chain.
  - Carried corpses are spared.
- **Flames:** every 10 ticks, a corpse that can burn checks its box (and just under it) for fire, lava or a lit
  campfire, and catches (8 s). Vanilla campfires only burn living things, so this check is needed.
- **Weightless:**
  - A corpse with `NoGravity` set skips the fall and settle. It moves by its velocity with 0.85 drag and still
    collides. This is how Jaymo's Powers' Gravity lifts and throws corpses.
  - `NoGravity` is cleared when a corpse loads, so nothing stays floating after a restart.

## 0.4.15: bag small corpses, carry anything (asked for from the Jaymo's Powers pack)
- **Empty hand, no sneak, on any corpse small enough to pocket** (≤ 0.5 wide and ≤ 0.8 tall: chickens, rabbits,
  babies, silverfish...): it goes into a corpse bag, whatever its kind. Players' corpses never do.
- **Sneak with an empty hand, on any corpse:** carried in your arms (small animals included).
- **A bagged corpse comes back as what it was:** `restore` rebuilds the body from its look and takes `CorpseKind.of`
  of it. Before, everything came back as ANIMAL.

## 0.4.16: ghast corpses, quieter burning, lava (asked for from the Jaymo's Powers pack)
- **Huge bodies get a real hitbox:** anything at least 1.9 blocks on its smaller side standing (a ghast is 4) now lies
  at 0.85 x 0.7 of that size instead of the 0.9-wide sliver every corpse was clamped to. The user couldn't carry a
  ghast corpse because every click missed the sliver inside the drawn body.
- **Carrying big ones:** `carryPosition` takes the corpse's width; wide bodies are held further out (width / 2 - 0.45
  more) and low (0.3 instead of 0.9), so they sit in front of the carrier instead of around them.
- **Burning was noisy:** a burning corpse was relit only after its fire went out, so vanilla played the fire-out fizz
  every second for the whole 15 s burn. It's now topped up (40 ticks) whenever under 10 ticks remain. Corpses are
  also `setSilent(true)`, so no entity sounds of their own play (catching fire, going out, lava burn, splashing);
  everything they say goes through `play`, which uses the level directly.
- **Lava makes short work of them:** a corpse in lava jumps to its last 30 ticks of burning (with a lava sizzle).

## 0.4.17: burning ghasts scream
- A burning ghast corpse plays the ghast's hurt sound every 25 ticks at volume 4 (pitch 0.8-1.2), for the whole burn
  (the user asked for it "louder and repeatedly"). Corpses are otherwise still silent.

## 0.4.18: corpses can be taken by a power
- `CorpseEntity.takeBy(Player)`: public. Pockets the corpse if it fits (never a player's), otherwise carries it, as a
  click would. Jaymo's Powers' Gravity uses it: right click on a corpse held by Lift draws it in to you.

## 0.4.19: water puts corpses out
- A burning corpse that is in or under water is put out at once (with the extinguish hiss), and its burn starts over:
  it no longer keeps relighting itself once it leaves the water. Only a new flame lights it again.
- **Fish and squid lie properly:** a fish corpse lies on its side the way a stranded fish does (its renderer's own flop, without
  the death roll on top that sank it into the ground), still; a squid corpse slumps flat on its side, centred.
- `CorpseEntity.sourceType()` is public (Jaymo's Powers' symbiote checks what a corpse was before eating it).

## 0.4.20: drop corpses, tumbling
- **Right-click drops a carried corpse:** any right-click while carrying (at a block or at the air) lets go.
  - The client intercepts `Minecraft.startUseItem` (`MinecraftUseMixin`) and sends `DropCarried`, because an empty-handed click at the air normally sends nothing.
  - Right-clicking a butcher block still lays an animal on it; clicking another block drops the corpse too.
  - The "carrying it" prompt is gone.
- **Tumbling:** `CorpseEntity.drop(player)` lets go where it was held, with the carrier's momentum plus a little forward and upward push, and rerolls the pose. A synced `TUMBLING` flag stays set until it lands, then it plays a small thud and the flag clears. Airborne drag is 0.91; on the ground it's 0.5.
- **While tumbling** the client draws it limp, as if carried: its limbs flail from a time-based swing, and the whole body turns over (spinning and rolling about its middle). Once it lands it takes the mob's normal corpse pose (keeled over, or the sprawled ragdoll for humanoids and players).

## 0.4.21: throwing corpses, corpses in carts, real tumbling
- **Shift + right-click while carrying throws the corpse** (`DropCarried(toss)`, `CorpseEntity.toss`): a short lob ahead (0.42 forward, 0.28 up, plus the thrower's motion) with a sweep sound. A plain right-click still drops it.
- **Into carts:** a tumbling (dropped or thrown) body that touches an animal or supply cart lands in it as a passenger, if there's room (`CartEntity.takesCorpse`).
  - Room is counted in small-animal units (`CorpseEntity.cartRoom`): 3 for anything standing at least 1.2 blocks tall or wide (cows, sheep, people, zombies), 1 for small ones (chickens, fish, pigs, rabbits), and nothing bigger than 2.2.
  - The animal cart holds 6 (two cows or six small); the supply cart holds 9 (three cows or nine small). Live animals in an animal cart count as 3 each.
  - Sneak + right-click (empty hand) tips the bodies out of a supply cart. The animal cart's existing sneak-click lets them out too. Picking a body up (carry) lifts it out.
- **How the load lies** (`corpseSlot`, re-laid out as bodies come and go):
  - Big bodies lie lengthwise side by side on the floor, head to tail (one alone lies in the middle); a third lies across the top of them.
  - Small bodies fill the floor cells beside the big ones (2 across x 3 along), then pile on top, each at its own angle from its pose seed.
  - Everything follows the bed's tilt and the cart's turn (verified with markers; a passenger's saved Pos always shows its vehicle's x/z).
- **Tumbling is physical** (client, `CorpseEntity.tumble`): a dropped body starts turning over end over end and side to side (seeded spin). The spin builds with fall speed and is slowed slightly by air, so long falls turn it over and over. Its limbs are dragged up behind it as it speeds up, flailing. The renderer turns the body about its own crosswise and lengthwise axes by the simulated angles.

## 0.4.22: tipping carts, re-grab cooldown, corpses settle flat
- **Shift + right-click tips a cart** (the animal cart, or a supply cart with bodies in it). A synced `TIP` timer (36 ticks) rolls the cart over onto its right side, pivoting on that wheel (up to 70°, eased), holds a moment and rocks it back (`CartEntity.tipAngle`, drawn in `CartRenderer`).
  - Six ticks in, the load spills out (`spillLoad`): each body is put down clear of the cart on the side it tips to (half the cart's width + 0.6–0.9 out, 0.7 up) and flung onward tumbling (`CorpseEntity.tipOut`), so nothing lands underneath. Live animals are set down there too.
  - A supply cart's stored goods stay put.
- **Re-grab cooldown:** a corpse that's just been dropped, thrown or tipped out can't be grabbed for 20 ticks, nor while it's still tumbling, so holding the click no longer catches it straight back.
- **Corpses settle only when lying flat on blocks:**
  - A grounded body checks the block under its middle and under both ends along its length (the mob's standing height, less 0.15 at each end; `overhang`).
  - With an end over nothing, it slides off that way, still tumbling. With its middle over nothing but its hitbox caught on a block's rim, it's pushed away from that block (`awayFromRim`).
  - It stops (and stops tumbling) only when its whole length lies on blocks. Bodies laid on butcher blocks are exempt.
  - Verified: a cow corpse on a 1-block pillar slid off to the ground; one lying along a 3-block platform stayed put; a chicken on a pillar stayed put.

## 0.4.25: grinding stone down to sand
- **Grindstones grind rock** (`MachineRecipes.GRIND`, hand grindstone and horse mill alike): stone or cobblestone makes 1 gravel, and gravel makes 1 sand.

## 0.4.27: iron golem corpses are heavy
- **New tag `jaymoskitchen:heavy_corpses`** (iron golems; `CorpseEntity.isHeavy`). A heavy corpse can't be carried or pocketed by hand ("Solid iron: far too heavy to lift.") and can't be butchered: an axe gets "Solid iron: there's nothing to butcher.", and `butcher` does nothing for it. It can still be searched.
- A power can still take one through `takeBy`; Jaymo's Powers lets only Gravity's Lift do that.
- **It doesn't burn and doesn't rot** (`CorpseEntity.burns()` is false for heavy corpses, so fire, lava and flint and steel don't take it). Searched, it drops its loot and **falls apart** (`CRUMBLING`, synced; `CRUMBLE_TICKS` 50): iron-block chips crack off with clanks every 8 ticks while it shudders, slumps flatter and sinks into the ground (`CorpseRenderer`), then a last burst, the golem's death sound and an anvil thud, and it's gone. Left unsearched for 10 minutes, it falls apart the same way with its loot.

## 0.4.28: skeletons get back up, burning corpses spread fire, guards can carry corpses
- **Skeletons rise like zombies:** a skeleton corpse (skeleton, stray, bogged, wither skeleton, parched, skeleton horse)
  left 5 minutes gets back up empty-handed, rattling for its last 10 s, unless it's burned or all its bones are pulled.
  Searching one says "Burn it before it gets back up!" (`CorpseEntity.rises`).
- **Burning corpses spread fire:** once a second while alight (not while carried) a burning body tries two random empty
  spots in or around it (a block out, a block up); one beside something that catches (wood, wool, leaves: anything
  lava would light) takes fire. Follows the fire-spread game rules (`spreadFire`, `SPREAD_EVERY`).
- **For Jaymo's Kingdoms' guards:** `carryBy(Entity)` lets a mob carry a corpse (never a heavy one), `throwAt(Vec3)` throws it at a spot,
  and `isHostile()` is public.
- **Sheep give leather too:** butchered on a butcher block, a sheep now drops 1 leather 15% of the time, as a pig already did.

## 0.4.29: fish are looted in one click
- **New corpse kind `FISH`** (anything built on vanilla's fish class, plus the new tag `jaymoskitchen:fish_corpses`:
  cod, salmon, tropical fish, pufferfish and Alex's Mobs' flying fish, blobfish, catfish, cosmic cod, Devil's Hole
  pupfish and mudskipper). Clicking a fish corpse, with anything in hand, drops its loot and the body vanishes with a
  splash (`CorpseEntity.lootFish`). Before, a fish too big to pocket (a beached flying fish) was searched like any other
  corpse: its fish dropped and the body stayed behind.
- Fish corpses saved before 0.4.29 (as `OTHER`) count by their type too (`CorpseEntity.isFish`).

## 0.4.31: iron golems carry corpses in their arms
- For Jaymo's Kingdoms 0.5.48, where iron golems take corpses to the fire: a carrier bigger than a villager holds a
  carried corpse out past its own bulk and up at its arms (`carryPosition` scales with the carrier's size). Before, a
  golem's corpse sat at its knees, half inside it. Players and villagers carry exactly as before.
## 0.4.32: butcher villagers
- **Butcher villagers butcher for you** (`corpse/Butchers`; vanilla's butchers and MCA's, by profession). Right-click
  one with a passive animal's corpse in your arms or a bagged one in your hand: a screen (`ButcherScreen`) shows the
  price, and their skill (Clumsy, Fair, Able, Skilled, Master) and yield only if you know them. Monsters and heavy
  (iron) corpses are refused.
- **Skill** is born with each butcher (from their UUID), spread evenly from 1x to 3x what a proper job on a butcher
  block gets (`CorpseEntity.butcheredDrops`: the loot, extra meat and the block-only extras); a fifth of butchers in
  each tier. Every count is multiplied, the fraction a chance at one more. It has nothing to do with their trade level.
- **Price** is their own asking (1x to 3x, also from their UUID, unrelated to skill) times 1 emerald for a small body,
  2 for a big carcass: 1-3 or 2-6. A pricey butcher can be a hack. Creative pays nothing.
- **Knowing a butcher** (`jaymoskitchen:known_butchers` on the player, kept on death): you learn how good one is from
  gossip, or, if you're experienced with butchering, from the cuts they hand you (the hand-over says the x). Who's
  experienced is set by `Butchers.setExperienced(Predicate<Player>)` (Jaymo's Kingdoms' first Survival perk, by
  reflection); nobody without it.
  `Butchers.gossip(ServerPlayer, Entity)` (public; Jaymo's Kingdoms 0.5.47's townsfolk call it) returns a line: trash
  talk or praise for one of the speaker's own village's butchers (within 64 blocks), or word of a far-off master or a
  botcher to avoid (only the top two and bottom two tiers travel; up to 3000 blocks, with distance and direction), ones
  you haven't heard of first. Far-off butchers come from a per-dimension ledger (`jaymoskitchen:butcher_ledger`) of
  every butcher that has been loaded.
- **The errand:** paid, they take the body in their arms and walk out back: to the nearest butcher block within 14
  blocks they can reach, else a reachable spot 5-12 blocks past their smoker (job site) on the side away from you.
  They chop for 5 s (swings, cleaver sounds, the body jolting), the body is gone, and they walk back to you and put
  the cuts in your pockets (overflow at your feet). If you've wandered off they wait with the cuts and come over when
  you're within 20 blocks, or right-click them to collect. The job (cuts worked out at the deal) is saved on the
  villager (`jaymoskitchen:butcher_job`); if they die mid-job the cuts drop where they fall.
- Tuning: the constants at the top of `Butchers`.

## 0.4.34: Naturalist's animals
- Tags take Naturalist's mobs (optional entries): its big animals (bears, rhinos, hippos, elephants, mammoths,
  giraffes, alligators, lions, tigers, komodo dragons, zebras, deer, sharks, whales) are `big_carcasses`; its insects
  and lizard tails leave no corpse; its snakes, fish, sea creatures, snails and insects are `no_legs`. Its fish are
  vanilla fish underneath, so they loot in one click already. Kingdoms' moose is a big carcass too.
- Kingdoms' monsters of legend (0.5.56, optional entries): ogres are `big_carcasses`, the silver skeleton is a
  `skeleton_corpses` mob, and wraiths, mud golems and mimics leave no corpse.

## 0.4.35: predators eat their kills
- Mobs in the new `scavengers` tag (wolves, foxes, polar bears, ocelots, zombies and their kin, zoglins; optional
  entries for Kingdoms' feral wolves, coyotes, werewolves and ogres and Naturalist's bears, lions, tigers, hyenas,
  alligators, komodo dragons and vultures) go to a body when they have nothing to fight and tear into it: they lunge in,
  worry it with the head down shaking side to side, yank back and drag it, the body jerking hard, blood flying, wet
  bites and growls. After 4 bites plus 4 per block of its width it's torn open: its loot spills (leather and the like
  stay for whoever comes by) with a bone or two, plus rotten flesh if it carried no meat. Scavengers nearby then eat
  the torn-off meat off the ground a piece at a time (healing 3 each) until full (3 pieces, then 4 minutes).
- Corpses remember their killer (`Killer`, `KilledByPlayer`). Players' kills (and their tame pets' kills) are left
  alone unless the scavenger made the kill itself; tame animals never scavenge. Monster bodies, the risen dead, iron,
  players' bodies and anything burning, carried or carted are never eaten. Wild scavengers eat only meat torn from a
  body, never what a player drops.
- Tamed wolves eat meat dropped on the ground near them (anything they'd eat from your hand), unless sitting or
  fighting; while hurt they're never too full to.
- Tuning: the constants at the top of `Scavengers`.

## 0.4.36: catching falling bodies
- A body in the air (dropped, thrown, falling off something) can be caught: an empty hand's right-click takes it into
  your arms, and the crosshair takes it from up to 2 blocks off it (`CATCH_RADIUS`), so a near miss still catches it.
  Powers' tendril grabs and whips find it the same way. Iron golems are still too heavy.
- A falling body wedged in a block is eased out the nearest open way (as a dropped item is), and one that stops getting
  anywhere for 2 seconds (`STUCK_TICKS`) lies where it is instead of tumbling forever.

## 0.4.37: tagged bodies leave no corpse
- An entity with the `jaymoskitchen.no_corpse` scoreboard tag leaves no corpse, player or mob
  (`Corpses.NO_CORPSE_TAG`); a tagged player's hotbar drops with the rest of their things. Jaymo's Powers tags Darktide shades as they die and risen shadows as they rise.
- Bodies keep their lost parts: a mob or player tagged `jaymoskitchen.missing.<head|right_arm|left_arm|right_leg|left_leg>`
  (Jaymo's Powers dismemberment) leaves a corpse drawn without those parts (`CorpseEntity#missing`, saved as `Missing`;
  `client/corpse/Severed` hides them by model part name after the pose and shows them again once drawn).

## 0.4.38: plain larder dishes and three new minigames
- Nine dishes from basic ingredients: Borscht, Beetroot Hash, Carrot Soup, Glazed Carrots, Carrot Cake, Roast Chicken,
  Chicken Soup, Roast Mutton and Shepherd's Pie (`Dish`, sprites in `tools/gen_assets.py`, cooking table recipes in
  `recipe/minigame/`).
- Three new minigames (`cooking/Minigames` for the shared rules and judging, `client/LarderGames` for play):
  - **Chop** (hashes, glazed carrots): the veg slides under the knife, faster after each cut; press Space or click
    when a mark is under the blade. 8 cuts; a missed mark is ragged.
  - **Roast** (roast chicken and mutton): a spit over the fire with four sides; only the side facing down cooks, and
    the fire surges and dies back. Turn it a quarter at a time and take it off when every side is golden.
  - **Stir** (soups): circle the mouse round the pot. Too slow and it sticks, too fast and it slops over; when it
    boils up it wants stirring faster.
  - Like the sear, the client plays and reports (`MinigameFinish`); the server judges with the same code and checks
    enough time passed. Walking away chars a pot or spit, and leaves half-chopped veg a mess.
- Each game has its own station: Chop on a Cutting Board (a "Chop vegetables" button on the board's prep screen; new
  `Station.BOARD`, which never reheats), Roast on a spit over a Campfire only, Stir in a pot (Furnace, Smoker, or a
  Cooking Pot on a lit oven).
- The dish list is restyled like the Powers H screen: striped rows with the dish, its method and ingredient icons, and
  a hover box with how it's cooked, what Perfect gives and what you're missing. Click a row to cook it.

### 0.4.39: ragdoll physics
- Andy asked to work Physics Mod into our ragdolls. It can't be: it's closed source, All Rights Reserved, client-only
  and has no API, so it can't drive our server-side corpses. `client/corpse/Flop` is our own take instead, run on each
  client per corpse:
  - Every limb and the head sit on a damped spring around the rest pose (Ragdoll sprawl, Limp hang, or the keeled-over
    pose), so blows, rummaging, landing from a drop or throw, being shoved (explosions, carts) and people walking over
    the body fling them about. Limbs of a sprawled body slap down onto the ground and bounce. Replaces `Jolt`.
  - The whole body swings from where it was drawn into each new pose (dying, picked up, dropped, landing) instead of
    snapping: lying down it falls under gravity and bounces, held up it's a quick spring. A fresh body (synced
    `BORN`, `CorpseEntity.markJustDied`) falls from the dying mob's keeled-over pose, limbs swinging out from straight.
  - Lying still it tips to the ground under it (slopes, steps, slabs; up to 35 degrees; walls aren't climbed) and
    bounces a little when it lands hard.
- **0.4.41, the mill in JEI:** JEI has a page for each machine (Grinding, Chopping Block, Press), read off
  `MachineRecipes`: wheat to flour, flowers to dye, logs to planks (by hand and with a horse), seeds to oil and leaves
  to water, with the machines as their stations (`client/jei/MillJei`, the `jei_mod_plugin` entrypoint).
- **0.4.46, heartier meals:** Andy: "buff the heart and hunger saturation increase from better meals, with a slight
  bump up across the board". `Stomach`: every meal's health is 1.15x its nutrition plus 0.4 per point past 5
  (`HEALTH_SCALE`, `RICH_HEALTH`, `RICH_FROM`); dishes 1.35x (was 1.25), Perfect 1.25x on top (was 1.2); the food
  health cap is 40 (was 34). On eating (`feed`), saturation +15% for anything, +35% more for a dish, +25% more for a
  Perfect one, plus 0.3 per rich point; dishes fill 1 more hunger, Perfect ones 2.
- **0.4.50:** a corpse keeps its mob's size: the scale attribute stays in the saved look (Kingdoms' half-size spiders,
  grown apes), the rest of the attributes are still left out (`Corpses.look`).
- **0.4.51, Better With Mods batch 1:** `stomach/RawFood`: raw meat and fish (`raw_meats`) give Hunger II for 30 s
  30% of the time, an Undercooked dish half as often (replaces the old 60% Hunger I and raw chicken's own chance);
  you can still eat while sick. Llamas and trader llamas drop 1-2 mutton. `EatGroundSeedsGoal` became
  `farm/EatGroundFoodGoal`: every farm animal eats its own food off the ground (tame pets don't). `farm/LayingHens`:
  a hen that pecks a seed lays at the next dawn; unfed hens lay at half speed (`ChickenMixin`).
