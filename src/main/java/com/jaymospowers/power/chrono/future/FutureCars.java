package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The future's four cars ({@link CarKind}, {@link FutureCar}), found only in the future's ruins. When a chunk of the ruins is
 * first decayed ({@link FutureRuins}), one in {@link #PARK_CHANCE} has a car left standing at its lowest point (the street,
 * not a roof), turned along the streets, its tank dry or nearly ({@link #SPLASH_CHANCE} of a splash left in it). Fuel is what
 * the future's crates still hold: redstone and gunpowder (see FutureCar#worth). There are no spawn eggs, as with the
 * future's other things; {@code /summon} does it, and nothing like them stands outside the future.
 */
public final class FutureCars {
	public static final Map<CarKind, EntityType<FutureCar>> TYPES = new EnumMap<>(CarKind.class);
	private static final Map<EntityType<?>, CarKind> KINDS = new HashMap<>();

	/** One decayed chunk in this many has a car. */
	static final float PARK_CHANCE = 0.03F;
	/** The chance a parked car has a little fuel left, and how much at most (ticks of full throttle). */
	static final float SPLASH_CHANCE = 0.3F;
	static final int SPLASH_MAX = 1800;
	/** A car stands where the chunk is this near its lowest (blocks), on flat ground this wide (blocks). */
	static final int STREET = 1;

	private FutureCars() {
	}

	static void init() {
		for (CarKind kind : CarKind.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(kind.id));
			EntityType<FutureCar> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<FutureCar>of(FutureCar::new, MobCategory.MISC)
				.sized(kind.width, kind.height).clientTrackingRange(10).updateInterval(2).noLootTable().build(key));
			TYPES.put(kind, type);
			KINDS.put(type, kind);
		}
	}

	static CarKind kindOf(EntityType<?> type) {
		CarKind kind = KINDS.get(type);
		return kind == null ? CarKind.RUNABOUT : kind;
	}

	/** A freshly decayed chunk may have a car left on its street. */
	static void park(ServerLevel level, LevelChunk chunk, RandomSource random) {
		if (random.nextFloat() >= PARK_CHANCE) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		int lowest = Integer.MAX_VALUE;
		for (int x = 2; x < 16; x += 4) {
			for (int z = 2; z < 16; z += 4) {
				lowest = Math.min(lowest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getMinBlockX() + x, pos.getMinBlockZ() + z));
			}
		}
		for (int tries = 0; tries < 6; tries++) {
			int x = pos.getMinBlockX() + 2 + random.nextInt(12), z = pos.getMinBlockZ() + 2 + random.nextInt(12);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (y > lowest + STREET || !level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(),
				net.minecraft.core.Direction.UP)) {
				continue;
			}
			CarKind kind = CarKind.values()[random.nextInt(CarKind.values().length)];
			FutureCar car = TYPES.get(kind).create(level, EntitySpawnReason.EVENT);
			if (car == null) {
				return;
			}
			car.snapTo(x + 0.5, y, z + 0.5, random.nextInt(4) * 90.0F + random.nextFloat() * 8.0F - 4.0F, 0.0F);
			if (!level.noCollision(car)) {
				continue;
			}
			if (random.nextFloat() < SPLASH_CHANCE) {
				car.setFuel(1 + random.nextInt(SPLASH_MAX));
			}
			level.addFreshEntity(car);
			return;
		}
	}
}
