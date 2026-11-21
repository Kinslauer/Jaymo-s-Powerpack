---
paths:
  - "kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/**"
  - "kingdoms/src/client/java/com/jaymoskingdoms/client/inhabitants/**"
  - "**/geckolib/**"
  - "kingdoms/art/**"
  - "tools/bbmodel_*.py"
---
# Adding a GeckoLib mob to Kingdoms

(Moved here from `docs/AI-HANDBOOK.md` section 3.)

Kingdoms draws new animated mobs with GeckoLib 5.5.7 (`maven.modrinth:geckolib:kSxHvs99`, the same file the pack ships;
package `com.geckolib`, not GeckoLib 4's `software.bernie`). The example to copy is the **barrow beetle**:
`kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/BarrowBeetle.java`. The Bogre, Impaler and Warped Clam follow it.

1. **Model, texture, animations.** Keep an editable Blockbench project in `kingdoms/art/<mob>.bbmodel` (a ported model:
   its licence and credit beside it, `kingdoms/art/<mob>-LICENSE.txt` and `-CREDITS.md`). Model it facing north (-z),
   feet at y 0; bone names must be unique. Then write the game's files with
   `py -3 -I tools/bbmodel_geckolib.py kingdoms/art/<mob>.bbmodel kingdoms/src/main/resources/assets/jaymoskingdoms <mob>`.
   It writes exactly where GeckoLib 5 looks, and nowhere else works (5.5.7 only scans these two folders):
   - `assets/jaymoskingdoms/geckolib/models/entity/<mob>.geo.json`
   - `assets/jaymoskingdoms/geckolib/animations/entity/<mob>.animation.json`
   - `assets/jaymoskingdoms/textures/entity/<mob>.png` (several Blockbench textures are stacked into one sheet)

   Old GeckoLib 4 paths (`geo/<mob>.geo.json`, `animations/<mob>.animation.json`) are not loaded: move such files.
   Check the look before Java: bake to scratch with `tools/bbmodel_bake.py` and render poses with
   `tools/bbmodel_preview.py <scratch> <mob> out.png <animation> <seconds>`.
2. **The entity** (`inhabitants/<Mob>.java`): extend the vanilla base you need (`PathfinderMob`, `Monster`, ...) and
   `implements GeoEntity`. Keep `private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);`
   and return it from `getAnimatableInstanceCache()`. In `registerControllers` add:
   - a looping controller that picks the animation every frame:
     `new AnimationController<Mob>("move", 4, test -> test.setAndContinue(test.isMoving() ? WALK : IDLE))`; the client
     entity is `test.animatable()`, so read its synced data there (a dormant statue, a block just taken);
   - a controller for one-off moves the server starts:
     `new AnimationController<Mob>("action", test -> PlayState.STOP).triggerableAnim("attack", ATTACK)`, played with
     `this.triggerAnim("action", "attack")` on the server (GeckoLib sends it to every client watching).
   Animation names are the keys in the `.animation.json`: `RawAnimation.begin().thenLoop("walk")`, `.thenPlay("attack")`.
   Attributes, goals, sounds and tuning constants as for any Kingdoms mob.
3. **Registration** (`inhabitants/Inhabitants.java`): `Beasts.type("<mob>", EntityType.Builder.of(...).sized(w, h), attrs)`
   registers the type, its attributes and its spawn egg; then `SpawnPlacements.register(...)` and, if it spawns in the
   world, `BiomeModifications.addSpawn` on a biome tag of its own (`data/jaymoskingdoms/tags/worldgen/biome/has_<mob>.json`).
4. **The renderer** (`client/inhabitants/InhabitantsClient.register()`): one line,
   `EntityRenderers.register(Inhabitants.MOB, ctx -> new InhabitantRenderer<Mob>(ctx, "<mob>", scale, shadow, "head"))`.
   To show or hide bones by state (the draugr's axe or sword, the Skeleton Lord's crystals), subclass it as
   `DraugrRenderer` does: put the state in with `addRenderData` (a `DataTicket`) and read it in `adjustModelBonesForRender`
   (`snapshots.ifPresent("bone", b -> b.skipRender(...))`).
5. **The rest:** lang in `lang/parts/inhabitants.json` (entity name and `item.jaymoskingdoms.<mob>_spawn_egg`), the egg's
   `items/`, `models/item/` and `textures/item/` files (`kingdoms/tools/gen_tomb_undead.py`'s `eggs()` makes them), a
   change note, then build and boot `runServer` once (a new entity type).

Credits: never copy Draugr Invasion (All Rights Reserved) or Supplementaries in any form. Inhabitants (JeremySeq / Team
Synapse), Stormborn Tomb (Tada_no_ahiru), DungeonZ and FrogiLib are MIT: reuse with their licence text kept beside what
uses them and credit in the change note.
