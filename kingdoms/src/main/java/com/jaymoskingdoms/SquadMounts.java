package com.jaymoskingdoms;

import com.jaymoskingdoms.reaped.Reaped;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How the illagers' hit squad ({@link ZombieHordes.Kind#SQUAD}) arrives, by the land it comes over (after Immersive
 * Pillagers' hordes, GPL-3.0: the idea only). Living illagers only; the dead have their own mounts ({@link ReapedHost}),
 * and only the dead ride spiders (Andy).
 * <ul>
 * <li><b>Deserts:</b> two to a camel, a crossbow behind the driver.</li>
 * <li><b>Plains and savanna:</b> on horses.</li>
 * <li><b>Water near by:</b> two to a boat, rowed in ({@link #row}); they shoot from it and step out where it grounds.</li>
 * <li><b>Now and then, anywhere under open sky:</b> two to a hot air balloon (Jaymo's Powers', if it is loaded), let down
 * from {@link #DROP_HEIGHT} blocks up; they climb out where it lands and leave it there.</li>
 * </ul>
 * A mount whose rider dies is left for whoever wants it.
 */
final class SquadMounts {
	/** How often a squad comes by balloon, and by boat when there is water by. */
	static final float BALLOON_CHANCE = 0.2F, BOAT_CHANCE = 0.6F;
	/** A balloon starts this far up; boats are looked for this far round the squad's ground, and rowed this fast (blocks a tick). */
	static final int DROP_HEIGHT = 26, WATER_REACH = 20;
	static final double ROW_SPEED = 0.14;
	static final Identifier BALLOON = Identifier.fromNamespaceAndPath("jaymospowers", "hot_air_balloon");

	private SquadMounts() {
	}

	/** The squad {@code members}, just mustered round {@code centre}, mount up as the land has it. */
	static void mount(ServerLevel level, List<Mob> members, BlockPos centre, RandomSource random) {
		List<Mob> foot = new ArrayList<>();
		for (Mob m : members) {
			if (!(m instanceof Reaped) && !m.isPassenger() && m instanceof net.minecraft.world.entity.monster.illager.AbstractIllager) {
				foot.add(m);
			}
		}
		if (foot.isEmpty()) {
			return;
		}
		var biome = level.getBiome(centre);
		EntityType<?> balloon = BuiltInRegistries.ENTITY_TYPE.getOptional(BALLOON).orElse(null);
		if (balloon != null && level.canSeeSky(centre.above()) && random.nextFloat() < BALLOON_CHANCE) {
			pairs(level, foot, balloon, null, DROP_HEIGHT);
			return;
		}
		BlockPos water = random.nextFloat() < BOAT_CHANCE ? water(level, centre, random) : null;
		if (water != null) {
			pairs(level, foot, EntityTypes.OAK_BOAT, water, 0);
		} else if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			pairs(level, foot, EntityTypes.CAMEL, null, 0);
		} else if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(BiomeTags.IS_SAVANNA)) {
			for (Mob m : foot) {
				Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.EVENT);
				if (horse != null) {
					horse.setTamed(true);
					seat(level, horse, m.position(), m.getYRot(), List.of(m));
				}
			}
		}
	}

	/** {@code foot} two to a {@code type}, a crossbow second where there is one; at {@code at} (spread out) or where each first rider stands, {@code up} blocks higher. */
	private static void pairs(ServerLevel level, List<Mob> foot, EntityType<?> type, @Nullable BlockPos at, int up) {
		// (drivers first: the axes; the crossbows ride behind)
		foot.sort((a, b) -> Boolean.compare(a instanceof Pillager, b instanceof Pillager));
		int n = 0;
		while (!foot.isEmpty()) {
			Mob first = foot.removeFirst();
			Mob second = foot.isEmpty() ? null : foot.removeLast();
			Entity carrier = type.create(level, EntitySpawnReason.EVENT);
			if (carrier == null) {
				return;
			}
			if (carrier instanceof Camel camel) {
				camel.setTamed(true);
			}
			Vec3 where = at == null ? first.position().add(0.0, up, 0.0) : new Vec3(at.getX() + 0.5 + (n % 2) * 3.0, at.getY() + 1.0, at.getZ() + 0.5 + (n / 2) * 3.0);
			n++;
			seat(level, carrier, where, first.getYRot(), second == null ? List.of(first) : List.of(first, second));
		}
	}

	private static void seat(ServerLevel level, Entity carrier, Vec3 at, float yaw, List<Mob> riders) {
		carrier.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		if (!level.noCollision(carrier)) {
			carrier.discard();
			return;
		}
		level.addFreshEntity(carrier);
		for (Mob m : riders) {
			m.startRiding(carrier, true, false);
		}
	}

	/** Open water's top block within {@link #WATER_REACH} of {@code centre}, or null. */
	private static @Nullable BlockPos water(ServerLevel level, BlockPos centre, RandomSource random) {
		for (int tries = 0; tries < 10; tries++) {
			BlockPos column = centre.offset(random.nextInt(WATER_REACH * 2 + 1) - WATER_REACH, 0, random.nextInt(WATER_REACH * 2 + 1) - WATER_REACH);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column).below();
			if (level.getFluidState(top).is(FluidTags.WATER) && level.getFluidState(top.below()).is(FluidTags.WATER)
				&& level.getFluidState(top.east(3)).is(FluidTags.WATER) && level.getFluidState(top.south(3)).is(FluidTags.WATER)) {
				return top;
			}
		}
		return null;
	}

	/**
	 * Every tick: the squad's boats are rowed toward {@code goal} and emptied where they ground; a landed balloon is climbed
	 * out of.
	 */
	static void row(List<Mob> members, Vec3 goal) {
		for (Mob m : members) {
			Entity carrier = m.getVehicle();
			if (carrier == null || carrier.getFirstPassenger() != m) {
				continue;
			}
			if (carrier instanceof AbstractBoat boat) {
				double dx = goal.x - boat.getX(), dz = goal.z - boat.getZ();
				double far = Math.sqrt(dx * dx + dz * dz);
				if (!boat.isInWater() && boat.onGround() || boat.horizontalCollision || far < 3.0) {
					boat.ejectPassengers();
					continue;
				}
				boat.setYRot(boat.getYRot() + Mth.clamp(Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F - boat.getYRot()), -6.0F, 6.0F));
				Vec3 v = boat.getDeltaMovement();
				boat.setDeltaMovement(Mth.lerp(0.2, v.x, dx / far * ROW_SPEED), v.y, Mth.lerp(0.2, v.z, dz / far * ROW_SPEED));
			} else if (BALLOON.equals(BuiltInRegistries.ENTITY_TYPE.getKey(carrier.getType())) && carrier.onGround()) {
				carrier.ejectPassengers();
			}
		}
	}
}
