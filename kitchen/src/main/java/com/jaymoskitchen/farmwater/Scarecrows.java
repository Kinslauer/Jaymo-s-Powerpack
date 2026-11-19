package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The scarecrow's watch (Medieval Deco's prop, our own): a {@link ScarecrowBlock} with a carved pumpkin keeps the
 * crop-eating animals ({@link #CROP_EATERS}) off crops within {@link #RANGE} blocks. {@link #guarded} is the one question
 * other things ask: the rabbit's raid on carrots does ({@code RaidGardenGoalMixin}), and the vine birds and the Practice
 * Dummy join through {@link #addGuard} (their own places that keep birds and beasts off) and {@link #scare}.
 */
public final class Scarecrows {
	/** How far a scarecrow with a pumpkin guards (blocks), and ticks between its looks. */
	public static final double RANGE = 8.0;
	public static final int LOOK_EVERY = 20;
	/** How far a scared animal runs from the scarecrow (blocks). */
	static final int FLEE = 10;
	/** Animals that eat crops, and (by reflection from other mods) the birds that peck vines. */
	public static final TagKey<EntityType<?>> CROP_EATERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("crop_eaters"));

	private static final Map<ResourceKey<Level>, Set<BlockPos>> WATCHES = new HashMap<>();
	private static final List<BiPredicate<Level, BlockPos>> GUARDS = new CopyOnWriteArrayList<>();

	private Scarecrows() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHES.clear());
	}

	/** For other things that keep crop eaters off (the Practice Dummy, vine bird scares): whether {@code pos} is theirs to guard. */
	public static void addGuard(BiPredicate<Level, BlockPos> guard) {
		GUARDS.add(guard);
	}

	/** A scarecrow with a pumpkin stands at {@code pos}: it guards from now on. */
	static void keep(ServerLevel level, BlockPos pos) {
		WATCHES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
	}

	/** Whether a scarecrow (or another guard) keeps crop eaters off {@code pos} (the crop). */
	public static boolean guarded(Level level, BlockPos pos) {
		Set<BlockPos> watches = WATCHES.get(level.dimension());
		if (watches != null) {
			List<BlockPos> gone = new ArrayList<>();
			boolean near = false;
			for (BlockPos watch : watches) {
				BlockState state = level.getBlockState(watch);
				if (!state.is(FarmWater.SCARECROW) || !state.getValue(ScarecrowBlock.PUMPKIN)) {
					gone.add(watch);
				} else if (watch.distSqr(pos) <= RANGE * RANGE) {
					near = true;
				}
			}
			watches.removeAll(gone);
			if (near) {
				return true;
			}
		}
		for (BiPredicate<Level, BlockPos> guard : GUARDS) {
			if (guard.test(level, pos)) {
				return true;
			}
		}
		return false;
	}

	/** Crop eaters within {@link #RANGE} of {@code at} run from it. */
	public static void scare(ServerLevel level, Vec3 at) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(RANGE),
			m -> m.isAlive() && m.getType().builtInRegistryHolder().is(CROP_EATERS))) {
			Vec3 away = mob.position().subtract(at);
			Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			mob.setTarget(null);
			mob.getNavigation().moveTo(mob.getX() + dir.x * FLEE, mob.getY(), mob.getZ() + dir.z * FLEE, 1.3);
		}
	}
}
