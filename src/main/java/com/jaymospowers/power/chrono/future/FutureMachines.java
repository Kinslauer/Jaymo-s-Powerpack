package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The future's machines, roaming the ruins beside the dead: patrols of androids ({@link FutureAndroid}) and, now and
 * then, a war mech ({@link FutureMech}). They fight the dead as readily as the living. Checked with the hordes
 * ({@link FutureFerals}): each player gets a patrol about every {@link #PATROL_CHANCE} checks while few are near, and a
 * mech about every {@link #MECH_CHANCE} while none is.
 */
public final class FutureMachines {
	public static EntityType<FutureAndroid> ANDROID;
	public static EntityType<FutureMech> MECH;
	/** (0.17.135) The smaller android mech: now and then at the head of a patrol, and the Mad Tinkerer's test unit. */
	public static EntityType<FutureMech> ANDROID_MECH;

	/** One check in this many (with the hordes, every 2 s) sends a patrol of this many androids, while fewer than the cap are near. */
	static final int PATROL_CHANCE = 45, PATROL_MIN = 1, PATROL_MAX = 3, ANDROID_CAP = 4;
	/** One patrol in this many is led by an android mech. */
	static final int PATROL_MECH_CHANCE = 6;
	/** One check in this many sends a mech, while none is within {@link #MECH_SPACE}. */
	static final int MECH_CHANCE = 240;
	static final double NEAR = 64.0, MECH_SPACE = 128.0;
	/** How far out they turn up. */
	static final int SPAWN_MIN = 30, SPAWN_MAX = 50;

	private FutureMachines() {
	}

	static void init() {
		ANDROID = register("future_android", EntityType.Builder.<FutureAndroid>of(FutureAndroid::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(10).noLootTable());
		MECH = register("future_mech", EntityType.Builder.<FutureMech>of(FutureMech::new, MobCategory.MONSTER)
			.sized(2.1F, 4.0F).eyeHeight(3.5F).clientTrackingRange(12).noLootTable());
		ANDROID_MECH = register("android_mech", EntityType.Builder.<FutureMech>of(FutureMech::new, MobCategory.MONSTER)
			.sized(1.6F, 3.1F).eyeHeight(2.7F).clientTrackingRange(10).noLootTable());
		FabricDefaultAttributeRegistry.register(ANDROID, FutureAndroid.createAttributes());
		FabricDefaultAttributeRegistry.register(MECH, FutureMech.createAttributes());
		FabricDefaultAttributeRegistry.register(ANDROID_MECH, FutureMech.createAndroidAttributes());
	}

	private static <T extends Mob> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** With the hordes' check, for a player in the future. */
	static void check(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		if (random.nextInt(PATROL_CHANCE) == 0
			&& level.getEntitiesOfClass(FutureAndroid.class, player.getBoundingBox().inflate(NEAR), FutureAndroid::isAlive).size() < ANDROID_CAP) {
			BlockPos at = spot(level, player, random);
			if (at != null) {
				int count = PATROL_MIN + random.nextInt(PATROL_MAX - PATROL_MIN + 1);
				for (int i = 0; i < count; i++) {
					FutureAndroid android = ANDROID.create(level, EntitySpawnReason.EVENT);
					if (android != null && place(level, android, at.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2))) {
						android.outfit(level);
						level.addFreshEntity(android);
					}
				}
				if (random.nextInt(PATROL_MECH_CHANCE) == 0) {
					FutureMech lead = ANDROID_MECH.create(level, EntitySpawnReason.EVENT);
					if (lead != null && place(level, lead, at)) {
						level.addFreshEntity(lead);
					}
				}
			}
		}
		if (random.nextInt(MECH_CHANCE) == 0
			&& level.getEntitiesOfClass(FutureMech.class, player.getBoundingBox().inflate(MECH_SPACE), m -> m.isAlive() && m.getType() == MECH).isEmpty()) {
			BlockPos at = spot(level, player, random);
			FutureMech mech = at == null ? null : MECH.create(level, EntitySpawnReason.EVENT);
			if (mech != null && place(level, mech, at)) {
				level.addFreshEntity(mech);
			}
		}
	}

	private static @Nullable BlockPos spot(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int dist = SPAWN_MIN + random.nextInt(SPAWN_MAX - SPAWN_MIN + 1);
		int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
		int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
		if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
			return null;
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** Stand it at {@code pos} if there's room and dry ground. */
	private static boolean place(ServerLevel level, Mob mob, BlockPos pos) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
		if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty()) {
			return false;
		}
		mob.snapTo(at.getX() + 0.5, y, at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		AABB box = mob.getBoundingBox();
		return level.noCollision(mob, box);
	}
}
