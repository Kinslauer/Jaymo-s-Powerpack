package com.jaymoskingdoms;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sitting down where there's no chair (for {@link TableTalk} and {@link CampSuppers}): a companion or a band member takes
 * a free cushion close by (a tavern's, {@link Cushions}), or else sits on the ground where it stands, on a plain
 * cushion of its own that goes when it gets up ({@link #TAG}). 26.3's cushions are what players sit on too.
 */
final class GroundSeats {
	/** On a cushion put down for one sitter: taken away when they stand. */
	static final String TAG = "jaymoskingdoms_ground_seat";
	/** A free cushion this near (blocks) is taken first. */
	static final double FREE_NEAR = 3.0;

	/** Who sits on which cushion (and whether it's theirs to take away). */
	private static final Map<Mob, Cushion> SITTING = new IdentityHashMap<>();

	private GroundSeats() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GroundSeats::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SITTING.clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Cushion c && c.entityTags().contains(TAG)) {
				// one left from before a restart: whoever sat on it gets up
				c.ejectPassengers();
				c.discard();
			}
		});
	}

	/** {@code m} sits down, facing {@code face}: on a free cushion near, or on the ground where it is. True if it sat. */
	static boolean sitDown(Mob m, @Nullable Vec3 face) {
		if (!(m.level() instanceof ServerLevel level) || m.isPassenger() || m.isVehicle() || !m.onGround()) {
			return m.getVehicle() instanceof Cushion;
		}
		Cushion seat = null;
		double best = FREE_NEAR * FREE_NEAR;
		for (Cushion c : level.getEntitiesOfClass(Cushion.class, m.getBoundingBox().inflate(FREE_NEAR), c -> c.isAlive() && !c.isVehicle())) {
			double d = c.distanceToSqr(m);
			if (d < best) {
				best = d;
				seat = c;
			}
		}
		if (seat == null) {
			BlockPos feet = m.blockPosition();
			Vec3 at = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			AABB box = EntityTypes.CUSHION.getSpawnAABB(at);
			if (!Cushion.canBePlacedAt(level, box)) {
				return false;
			}
			seat = EntityTypes.CUSHION.create(level, EntitySpawnReason.TRIGGERED);
			if (seat == null) {
				return false;
			}
			seat.setColor(DyeColor.BROWN);
			seat.setPermanentlyInvulnerable(true);
			seat.addTag(TAG);
			float yaw = face == null ? m.getYRot() : (float)(Math.toDegrees(Math.atan2(face.z - at.z, face.x - at.x)) - 90.0);
			seat.snapTo(at, yaw, 0.0F);
			level.addFreshEntity(seat);
		}
		m.getNavigation().stop();
		if (!m.startRiding(seat)) {
			if (seat.entityTags().contains(TAG)) {
				seat.discard();
			}
			return false;
		}
		SITTING.put(m, seat);
		if (face != null) {
			m.getLookControl().setLookAt(face.x, face.y + 1.0, face.z);
		}
		return true;
	}

	/** {@code m} gets up (if it sat down here); a cushion of its own goes. */
	static void standUp(Mob m) {
		Cushion seat = SITTING.remove(m);
		if (seat == null) {
			return;
		}
		if (m.getVehicle() == seat) {
			m.stopRiding();
		}
		if (seat.entityTags().contains(TAG)) {
			seat.discard();
		}
	}

	static boolean sitting(Mob m) {
		Cushion seat = SITTING.get(m);
		return seat != null && m.getVehicle() == seat;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || SITTING.isEmpty()) {
			return;
		}
		SITTING.entrySet().removeIf(e -> {
			Mob m = e.getKey();
			Cushion seat = e.getValue();
			if (m.isAlive() && !m.isRemoved() && m.getVehicle() == seat) {
				return false;
			}
			// got up some other way, or gone: tidy the cushion away
			if (seat.entityTags().contains(TAG) && !seat.isVehicle()) {
				seat.discard();
			}
			return true;
		});
	}
}
