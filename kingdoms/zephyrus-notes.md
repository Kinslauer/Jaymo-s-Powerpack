# Zephyrus notes (delete after folding in)

## Built
- `dragons/ZephyrusDragon` (190 hp, armour 8, toughness 2, speed 0.22, bite 10; GREEN bar; favourite tropical fish; Verdant and Tideborn
  `BOND_FACTOR`; silent swoop; `breathRange` 16; seat 1.2 up). Water: `canBreatheUnderwater`, `isPushedByFluid` false, `airborne()` false
  in water (so the client draws `swim`, also when ridden), `travel` replaced while in water (driven: keeps its way with the air's drag so
  `DragonsClient.fly` works unchanged, a sink under 0.03 is cancelled so a ridden one doesn't sink, Shift still dives; on its own: paddles
  along its path at 0.35 of its speed, floats with its back out). `canBeAffected` refuses Poison (its own puddles).
- Perch: `perchable` = water over water with 3 air above; `findPerch` = the nearest such surface within 24 of home; `perchedTick` eases it
  to 0.95 blocks under the surface (head and back show) with a little bob and bubbles. Fight AI takes over unchanged (swoop, hover, ground).
- `dragons/DragonAcid` (instance per dragon): a gob every 6 ticks, 1 block/tick, gravity 0.012, 17 ticks (about 16 blocks). A gob hitting a
  non-spared creature or a block bursts: 6 damage (indirect magic) and Poison 6s to creatures within 2.2, then an AreaEffectCloud (radius 3,
  7s, green, Poison). Never changes blocks. `DragonAcid.RANGE` = 16.
- `ZephyrusDragonModel` (texture 256x256, UVs from `tools/gen_zephyrus.py`), `tools/gen_zephyrus.py` (hides, marks, egg, item json, loot).
  The script imports `gen_dragons.py` from its own folder.
- Loot: leather, bone, slime balls, spider eyes, fermented spider eyes (0-2), lily pads.

## Lang keys
None beyond those already listed (names, encounter message).

## Suggested MODLOG lines
- Zephyrus, the west wind dragon (Kingdoms 0.7.137): swamps and mangroves; perches half sunk in the water, bursts out to swoop; swims as fast
  as it flies (ridden too, no sinking); spits acid gobs that poison and leave a toxic puddle; tropical fish, Verdant and Tideborn.

## UNVERIFIED APIs (no example in the repo)
- `LivingEntity.travel(Vec3)` as a public overridable (and that the ridden path ends in `this.travel(...)`), `Entity.isPushedByFluid()`,
  `Entity.moveRelative(float, Vec3)`, `Entity.getFluidHeight(TagKey<Fluid>)`, `Entity.move(MoverType, Vec3)` and `MoverType.SELF`,
  `LivingEntity.getSpeed()`.
- `AreaEffectCloud.setPotionDurationScale`, `setRadiusPerTick`, `setCustomParticle`, `setOwner` and `addEffect` are as in Wyrm (Powers);
  `ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb)` is modelled on `create(ParticleTypes.FLASH, argb)` (ENTITY_EFFECT being a
  `ParticleType<ColorParticleOption>` is assumed).
- `AABB.clip(Vec3, Vec3)` returning `Optional<Vec3>` (as in GravityPower), `Direction.getStepX/Y/Z`, `SoundEvents.GENERIC_SPLASH`,
  `ParticleTypes.BUBBLE_COLUMN_UP`, `ParticleTypes.ITEM_SLIME` all have repo examples.
- Model: only calls Notus's model already makes (`ModelPart.visible`, `PartPose.offsetAndRotation`, `mirror()`).
- Not seen in game: the folded-wing pitch (-1.0) and the swim pose are by arithmetic, and the rider's seat (1.2) from the model's back top.
