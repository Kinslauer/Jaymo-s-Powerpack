# Boreas notes (for the lead; delete after folding in)

Built: `BoreasDragon`, `DragonFrost`, `BoreasDragonModel`, `tools/gen_boreas.py` (hides, marks, egg, item json, loot).

Lang keys: none beyond those Dragons.java already lists.

Suggested MODLOG line (Kingdoms 0.7.137): "Boreas, the north wind: a long, lean frost dragon of the peaks (glides, dives in
silence); frost breath freezes and slows, puts out fires, turns still water to frosted ice (mobGriefing); raw salmon;
Frostborn and Cryokinesis bond twice as fast."

Optional shared-file change: add `jaymoskingdoms:boreas_dragon` to `minecraft:freeze_immune_entity_types`
(data/minecraft/tags/entity_type/) if you want it immune to powder snow and other freeze damage as well; `canFreeze()` is
already overridden to false.

UNVERIFIED APIs (no example in this repo):
- `LivingEntity.canFreeze()` as a public `@Override` (BoreasDragon)
- `ParticleTypes.SNOWFLAKE`, `SoundEvents.SNOW_BREAK`
- `Blocks.FROSTED_ICE`, `ServerLevel.scheduleTick(BlockPos, Block, int)` on a ServerLevel (used on Level in Powers)
- `FluidState.isSource()`, `BlockTags.FIRE` with `removeBlock` (tag used elsewhere; the removal on ServerLevel is common)
- `CampfireBlock.LIT` with `BlockState.setValue`
- `DamageTypes.FREEZE` with `damageSources().source(...)`
- `LivingEntity.getTicksRequiredToFreeze()`/`setTicksFrozen` are used in Powers (verified); `MobEffects.SLOWNESS` verified
- Items.SALMON, BossEvent.BossBarColor.WHITE
