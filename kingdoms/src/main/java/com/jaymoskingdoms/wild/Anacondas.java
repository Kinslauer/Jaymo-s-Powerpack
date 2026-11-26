package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Alex's Mobs' anaconda (shipped unchanged) lives where a green anaconda lives and hunts the way one does. Andy
 * (2026-11-25): "they should spawn in the jungle no? hunting the other animals and players from the trees and rivers".
 * <ul>
 * <li><b>Where.</b> Out of the box it spawned only in swamps and a handful of Terralith biomes, and never in a vanilla
 * jungle. Here its spawns are taken out everywhere ({@link #home}) and put back in the jungles, the mangrove swamps,
 * and the rivers, where a river spawn needs a jungle within {@link #RIVER_REACH} blocks
 * ({@code mixin/alexsmobs/AnacondaSpawnMixin}, {@link #spawnRule}). In a jungle it may come up anywhere on the
 * ground, not only near the waterline as Alex's own rule asks.</li>
 * <li><b>Lying in wait.</b> A grown, hungry anaconda with nothing to hunt lies still ({@link #HOLD}) in the water or
 * up on a branch, and doesn't wander off; every third spell of {@link #WAIT_SPELL} ticks it moves on.</li>
 * <li><b>Up a tree.</b> In a jungle, one lying on the ground goes up the nearest tree when nobody is near to see
 * ({@link #perch}): onto a leafy branch {@link #PERCH_LOW} to {@link #PERCH_HIGH} blocks up.</li>
 * <li><b>The strike.</b> Prey (a player, or an animal it hunts, {@code alexsmobs:anaconda_targets}) that walks
 * under its branch or comes within reach of its water is struck at once: it drops onto it ({@link #DROP_REACH})
 * or surges out of the water at it ({@link #WATER_REACH}), and its own attack coils and squeezes. A sneaking player
 * has to come half as close. It takes no fall damage.</li>
 * <li><b>A fed snake.</b> It is a {@code sated_predators} hunter ({@link FedPredators}): after a kill it lies up for
 * two days and lets players, villagers and animals be, as a real one digests. Feeding it yourself (its own foods,
 * {@code alexsmobs:anaconda_foodstuffs}) counts as a kill, so a snake that's looked after leaves you and yours alone.</li>
 * </ul>
 * Nothing here runs without Alex's Mobs.
 */
public final class Anacondas {
	static final Identifier ANACONDA = Identifier.fromNamespaceAndPath("alexsmobs", "anaconda");
	static final Identifier PART = Identifier.fromNamespaceAndPath("alexsmobs", "anaconda_part");
	/** Alex's spawn weight for it, and how many come together. */
	static final int SPAWN_WEIGHT = 12, SPAWN_LEAST = 1, SPAWN_MOST = 1;
	/** A river spawn needs a jungle this near (blocks). */
	static final int RIVER_REACH = 32;
	/** How often a snake is looked at (ticks), and how often it may go up a tree (one look in this many). */
	static final int CHECK_TICKS = 5, PERCH_ODDS = 40;
	/** How long a spell of waiting or of moving on lasts (ticks). */
	static final long WAIT_SPELL = 2400L;
	/** Nobody within this many blocks sees it climb. */
	static final double UNSEEN = 24.0;
	/** How far round (blocks) it looks for a branch, and how high one may be over it. */
	static final int PERCH_REACH = 6, PERCH_LOW = 4, PERCH_HIGH = 14;
	/** The strike: from a branch, prey this near across and this far below; from the water, prey this near. */
	static final double DROP_REACH = 3.0, DROP_DEPTH = 16.0, WATER_REACH = 4.5;
	/** A sneaking player is noticed this much nearer. */
	static final double SNEAK_SHARE = 0.5;
	/** The surge out of the water (blocks a tick, across and up) and the lean of a drop toward its prey. */
	static final double SURGE = 0.8, SURGE_UP = 0.35, DROP_LEAN = 0.25;

	private static final Identifier HOLD = JaymosKingdoms.id("anaconda_lying_in_wait");
	private static final TagKey<EntityType<?>> TARGETS = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("alexsmobs", "anaconda_targets"));
	private static final TagKey<Item> FOODS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("alexsmobs", "anaconda_foodstuffs"));
	private static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("alexsmobs", "anaconda_spawns"));
	private static final Set<Mob> SNAKES = Collections.newSetFromMap(new WeakHashMap<>());

	private Anacondas() {
	}

	public static void register() {
		BiomeModifications.create(JaymosKingdoms.id("anaconda_homes")).add(ModificationPhase.POST_PROCESSING, BiomeSelectors.all(), Anacondas::home);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && is(mob, ANACONDA)) {
				SNAKES.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> SNAKES.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SNAKES.isEmpty() || server.getTickCount() % CHECK_TICKS != 0) {
				return;
			}
			for (Mob snake : SNAKES.toArray(Mob[]::new)) {
				if (snake.isAlive() && snake.level() instanceof ServerLevel level) {
					tick(snake, level);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(source.is(DamageTypeTags.IS_FALL) && is(entity, ANACONDA)));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player.getItemInHand(hand).is(FOODS)) {
				Mob snake = is(entity, ANACONDA) && entity instanceof Mob mob ? mob : is(entity, PART) ? nearest(server, entity) : null;
				if (snake != null && !snake.isBaby()) {
					// fed by a hand, it digests as after a kill: it leaves its keeper and their animals be
					snake.setAttached(FedPredators.FED_UNTIL, server.getGameTime() + FedPredators.FED_TICKS);
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** Takes the anaconda's spawns out of a biome, and puts them back if it's one of its homes. */
	private static void home(BiomeSelectionContext biome, BiomeModificationContext context) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ANACONDA).orElse(null);
		if (type == null) {
			return;
		}
		BiomeModificationContext.MobSpawnSettingsContext spawns = context.getMobSpawnSettings();
		List<Weighted<MobSpawnSettings.SpawnerData>> had = new ArrayList<>();
		for (Weighted<MobSpawnSettings.SpawnerData> entry : spawns.getMobs(MobCategory.CREATURE)) {
			if (entry.value().type() == type) {
				had.add(entry);
			}
		}
		if (!had.isEmpty()) {
			spawns.removeSpawns((category, data) -> data.type() == type);
		}
		Holder<Biome> holder = biome.getBiomeHolder();
		if (jungle(holder) || holder.is(BiomeTags.IS_RIVER) || holder.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
			spawns.addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(type, UniformInt.of(SPAWN_LEAST, SPAWN_MOST)), SPAWN_WEIGHT);
		}
	}

	/**
	 * (From {@code AnacondaSpawnMixin}) Its spawn rule for a natural spawn at {@code pos}: in a jungle, any ground it
	 * likes, at any height; in a river, none without a jungle near; elsewhere {@code null}, Alex's own rule.
	 */
	public static @Nullable Boolean spawnRule(LevelAccessor level, EntitySpawnReason reason, BlockPos pos) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return null;
		}
		Holder<Biome> biome = level.getBiome(pos);
		if (jungle(biome)) {
			return level.getBlockState(pos.below()).is(GROUND);
		}
		if (biome.is(BiomeTags.IS_RIVER) && !jungleNear(level, pos)) {
			return false;
		}
		return null;
	}

	private static boolean jungle(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_JUNGLE) || biome.is(ConventionalBiomeTags.IS_JUNGLE);
	}

	private static boolean jungleNear(LevelAccessor level, BlockPos pos) {
		for (int ring = RIVER_REACH / 2; ring <= RIVER_REACH; ring += RIVER_REACH / 2) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && jungle(level.getBiome(pos.offset(dx * ring, 0, dz * ring)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void tick(Mob snake, ServerLevel level) {
		boolean hunting = !snake.isBaby() && !snake.isPassenger() && !snake.isLeashed() && !FedPredators.fed(snake) && snake.getTarget() == null;
		boolean perched = perched(snake, level);
		boolean waiting = hunting && (snake.isInWater() || perched) && lyingUp(snake, level);
		hold(snake, waiting);
		if (!hunting) {
			return;
		}
		if (waiting) {
			LivingEntity prey = prey(snake, level, perched);
			if (prey != null) {
				strike(snake, prey, perched);
			}
		} else if (snake.onGround() && jungle(level.getBiome(snake.blockPosition())) && level.getRandom().nextInt(PERCH_ODDS) == 0
				&& level.getNearestPlayer(snake, UNSEEN) == null) {
			perch(snake, level);
		}
	}

	/** It lies in wait two spells in three and moves on (to new water or a new tree) in the third, each snake on its own beat. */
	private static boolean lyingUp(Mob snake, ServerLevel level) {
		return (level.getGameTime() / WAIT_SPELL + snake.getId()) % 3 != 0;
	}

	/** Whether it lies on a branch (leaves under it, up off the ground). */
	private static boolean perched(Mob snake, ServerLevel level) {
		return snake.onGround() && level.getBlockState(snake.blockPosition().below()).is(BlockTags.LEAVES);
	}

	/** Lies still (no walking or swimming off) while it waits, and moves again when it's done. */
	private static void hold(Mob snake, boolean still) {
		AttributeInstance speed = snake.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (!still) {
			speed.removeModifier(HOLD);
		} else if (speed.getModifier(HOLD) == null) {
			speed.addTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			snake.getNavigation().stop();
		}
	}

	/** The nearest prey in striking reach and in sight: under its branch, or near its water. */
	private static @Nullable LivingEntity prey(Mob snake, ServerLevel level, boolean perched) {
		double reach = perched ? DROP_REACH : WATER_REACH;
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, snake.getBoundingBox().inflate(reach, perched ? DROP_DEPTH : reach, reach),
				e -> e != snake && e.isAlive() && (e instanceof Player player ? !player.isCreative() && !player.isSpectator() : e.getType().builtInRegistryHolder().is(TARGETS)))) {
			double across = Math.hypot(e.getX() - snake.getX(), e.getZ() - snake.getZ());
			double below = snake.getY() - e.getY();
			double near = e instanceof Player && e.isShiftKeyDown() ? reach * SNEAK_SHARE : reach;
			boolean inReach = perched ? across <= near && below >= 1.0 && below <= DROP_DEPTH : e.distanceTo(snake) <= near;
			if (inReach && across < bestDistance && !FedPredators.leaves(snake, e) && snake.hasLineOfSight(e)) {
				best = e;
				bestDistance = across;
			}
		}
		return best;
	}

	/** Drops on it from the branch, or surges out of the water at it; its own attack does the rest. */
	private static void strike(Mob snake, LivingEntity prey, boolean perched) {
		hold(snake, false);
		snake.setTarget(prey);
		Vec3 to = prey.position().subtract(snake.position());
		Vec3 across = new Vec3(to.x, 0.0, to.z);
		if (perched) {
			snake.setDeltaMovement(across.scale(DROP_LEAN));
		} else {
			Vec3 flat = across.lengthSqr() > 1.0E-4 ? across.normalize() : Vec3.ZERO;
			snake.setDeltaMovement(flat.x * SURGE, SURGE_UP, flat.z * SURGE);
		}
		snake.needsSync = true;
	}

	/** Goes up the nearest tree onto the lowest leafy branch in reach, if there is one. */
	private static void perch(Mob snake, ServerLevel level) {
		BlockPos at = snake.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -PERCH_REACH; dx <= PERCH_REACH; dx++) {
			for (int dz = -PERCH_REACH; dz <= PERCH_REACH; dz++) {
				for (int dy = PERCH_LOW; dy <= PERCH_HIGH; dy++) {
					p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (level.getBlockState(p).is(BlockTags.LEAVES) && level.isEmptyBlock(p.above()) && level.isEmptyBlock(p.above(2))) {
						double d = dx * dx + dz * dz + dy * dy;
						if (d < bestDistance) {
							best = p.above().immutable();
							bestDistance = d;
						}
						break;
					}
				}
			}
		}
		if (best != null) {
			snake.getNavigation().stop();
			snake.snapTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, snake.getYRot(), snake.getXRot());
		}
	}

	private static @Nullable Mob nearest(ServerLevel level, Entity part) {
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, part.getBoundingBox().inflate(8.0), mob -> is(mob, ANACONDA))) {
			double d = mob.distanceToSqr(part);
			if (d < bestDistance) {
				best = mob;
				bestDistance = d;
			}
		}
		return best;
	}

	private static boolean is(Entity entity, Identifier id) {
		return id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}
}
