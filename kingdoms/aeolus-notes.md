# Aeolus and dragon eggs: notes

Built on origin/claude/project-thread-fc6a5y at 8479681d (Eurus landed). Storm breath calls the real
`DragonStorm.breathe(level, this, mouth, aim, tick, griefing && level.isThundering())`.

## Lang keys (English)
- `item.jaymoskingdoms.notus_dragon_egg` Notus Dragon Egg; `boreas_dragon_egg` Boreas Dragon Egg; `zephyrus_dragon_egg` Zephyrus Dragon Egg; `eurus_dragon_egg` Eurus Dragon Egg
- `message.jaymoskingdoms.dragon_egg.hatched` = "%s hatches, and takes to you."
- `message.jaymoskingdoms.aeolus.phase2` = "The wind rises round Aeolus."
- `message.jaymoskingdoms.aeolus.phase3` = "Aeolus breathes all four winds at once."
- `message.jaymoskingdoms.aeolus.gale` = "The sky draws breath. Get low!"
- `message.jaymoskingdoms.aeolus.fallen` = "Aeolus falls. The winds are calm, and four eggs lie where he fell."
- (the entity name and spawn egg name for aeolus already exist in Dragons' lang)
Suggested lang part: `lang/parts/aeolus.json`.

## MODLOG suggestions
- Aeolus, the father of the winds: huge final boss dragon (1600 health, three phases: gusts, then a gale), breath turns through fire, frost, acid and storm; calms every wild wind dragon for a day when beaten and leaves the four lesser dragon eggs.
- Dragon eggs (notus, boreas, zephyrus, eurus): use on a block to hatch a tamed hatchling.

## Shared-file changes
None needed (DragonEggs registers itself and its creative-tab entries; Dragons.java already calls `DragonEggs.register()`).
Optional: `WindDragon.barColor()` is read once (when the bar is made), so the bar can't follow the current wind; Aeolus returns YELLOW.
`python3 tools/gen_aeolus.py` and `python3 tools/gen_eggs.py` (from kingdoms/) have been run and their output committed. villager.png is never written.

## Notes
- Not ridden, so the model has no tack. Hatchlings render at the base's 0.35 scale.
- "Puts out its own fire" (phase 2+): he extinguishes himself each tick and each gust snuffs fire blocks within 12 blocks.
- Leaves after 9600 ticks (8 min) with no survival player within 64 blocks and no target; never despawns otherwise.
- Texture 256x256; the UV table is printed by gen_aeolus.py.

## UNVERIFIED APIs (no example in the repo)
- `AgeableMob.setBaby(boolean)` (only `setBaby(false)` on a villager is used), `EntitySpawnReason.SPAWN_ITEM_USE`
- `ItemEntity.setUnlimitedLifetime()`, `Entity.setInvulnerable(boolean)`, `new ItemEntity(level, x, y, z, stack)`
- `SoundEvents.TURTLE_EGG_CRACK`, `ParticleTypes.EXPLOSION_EMITTER` / `EXPLOSION`, `Level.isThundering()`
- `CreativeModeTabEvents.modifyOutputEvent(...).register(output -> output.accept(item))`
- `CubeListBuilder.mirror()` chained after `texOffs` (Notus uses it the same way)
