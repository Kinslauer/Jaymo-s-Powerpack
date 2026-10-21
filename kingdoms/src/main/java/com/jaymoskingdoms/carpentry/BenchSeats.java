package com.jaymoskingdoms.carpentry;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sitting on a bench: the same seat as the stools' ({@code Cushions}), 26.3's cushion. A bench that has a cushion on
 * it is sat on through that; a bare one is given a {@link #TAG tagged} cushion for as long as someone sits there, gone
 * (dropping nothing) once they stand up or the bench is broken. Facing a table beside the bench if there is one.
 */
final class BenchSeats {
	/** Marks a cushion that's only there while someone sits on a bare bench. */
	static final String TAG = "jaymoskingdoms.bench_seat";
	/** How often (ticks) empty bench seats are cleared away. */
	static final int SWEEP_TICKS = 10;
	/** The bare bench's seat cushion's colour (it reads as a worn leather pad on the wood). */
	static final DyeColor SEAT_COLOR = DyeColor.BROWN;

	private static final Set<Cushion> SEATS = new HashSet<>();
	private static int clock;

	private BenchSeats() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Cushion cushion && cushion.entityTags().contains(TAG)) {
				SEATS.add(cushion);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++clock % SWEEP_TICKS != 0 || SEATS.isEmpty()) {
				return;
			}
			for (Iterator<Cushion> it = SEATS.iterator(); it.hasNext();) {
				Cushion seat = it.next();
				if (seat.isRemoved()) {
					it.remove();
				} else if (!seat.isVehicle()) {
					seat.discard();
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEATS.clear());
	}

	private static AABB seatBox(BlockPos pos) {
		return EntityTypes.CUSHION.getSpawnAABB(seatAt(pos));
	}

	private static Vec3 seatAt(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + FurnitureBlock.SEAT_TOP / 16.0, pos.getZ() + 0.5);
	}

	/** Seats {@code player} on the bench at {@code pos}; false if there's no room to sit. */
	static boolean sit(ServerLevel level, BlockPos pos, Player player) {
		AABB box = seatBox(pos);
		for (Cushion cushion : level.getEntitiesOfClass(Cushion.class, box)) {
			if (!cushion.isVehicle()) {
				return cushion.interact(player, InteractionHand.MAIN_HAND, cushion.position()).consumesAction();
			}
			return false;
		}
		if (!Cushion.canBePlacedAt(level, box)) {
			return false;
		}
		Cushion seat = EntityTypes.CUSHION.create(level, EntitySpawnReason.TRIGGERED);
		if (seat == null) {
			return false;
		}
		seat.setColor(SEAT_COLOR);
		seat.setPermanentlyInvulnerable(true);
		seat.addTag(TAG);
		seat.snapTo(seatAt(pos), facing(level, pos, player).toYRot(), 0.0F);
		level.addFreshEntity(seat);
		SEATS.add(seat);
		if (!seat.interact(player, InteractionHand.MAIN_HAND, seat.position()).consumesAction()) {
			seat.discard();
			return false;
		}
		return true;
	}

	/** The way to a table beside the bench, else the way the sitter faces. */
	private static Direction facing(ServerLevel level, BlockPos pos, Player player) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(d)).getBlock() instanceof FurnitureBlock f && !f.bench) {
				return d;
			}
		}
		return player.getDirection();
	}

	/** Takes away the bench seats on a bench that's gone (before they'd drop a cushion for want of support). */
	static void clear(ServerLevel level, BlockPos pos) {
		for (Cushion cushion : level.getEntitiesOfClass(Cushion.class, seatBox(pos).inflate(0.1))) {
			if (cushion.entityTags().contains(TAG)) {
				cushion.ejectPassengers();
				cushion.discard();
			}
		}
	}
}
