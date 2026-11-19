package com.jaymospowers.core;

import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.stamina.Dodge;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The flying dismount: a freerunner (Freerunner learnt in Kingdoms' tree) who gets off a mount going at a gallop
 * leaps clear sideways with the mount's own momentum, and rolls as they land ({@link Dodge}'s roll, which takes the
 * fall away: {@link LandingRoll}). Slower, or without the skill, getting off is the ordinary climb down
 * ({@link MountClimb}); the client plays the leap ({@code MountClimbPose}, {@link MountClimb.Kind#OFF_LEAP}) by the same
 * test, {@link #leaps}.
 * <p>The server finds the dismount the way the client does (a rider seen in the saddle a tick ago who isn't now, with
 * Sneak held), since vanilla has no event for it, and measures the mount's speed from where it was a tick before.
 * Gallop is a speed, so one test serves Kingdoms' horses and vanilla ones: its canter and gallop (and a vanilla horse at
 * full tilt) pass, a walk and trot don't.
 */
public final class MountLeap {
	// ===== the knobs ================================================================================================
	/** The mount must be going this fast (blocks a tick; a vanilla horse flat out is about 0.5, a trot about 0.25). */
	public static final double LEAP_SPEED = 0.35;
	/** How much of the mount's own velocity the rider keeps, how hard they push off sideways (blocks a tick), and up. */
	static final double LEAP_KEEP = 0.9, LEAP_SIDE = 0.35, LEAP_UP = 0.3;
	/** A rider seen to dismount this far from the seat they left was thrown or moved, not leaping. */
	static final double SEAT_REACH = 4.0;
	/** A leap with no landing after this many ticks is forgotten (water, a teleport, flight). */
	static final int AIR_TICKS = 80;
	// ================================================================================================================

	/** A rider as last seen in the saddle: the mount, the seat, where the mount stood, and how it was moving (blocks a tick). */
	private record Ride(int mountId, Vec3 seat, Vec3 mountAt, Vec3 velocity) {
	}

	/** A leap in the air: when it began, where the rider was last tick, and the way it went. */
	private record Leap(long start, Vec3 last, Vec3 way) {
	}

	private static final Map<UUID, Ride> RIDING = new HashMap<>();
	private static final Map<UUID, Leap> LEAPING = new HashMap<>();

	private MountLeap() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MountLeap::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RIDING.remove(handler.player.getUUID());
			LEAPING.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RIDING.clear();
			LEAPING.clear();
		});
	}

	/** Whether {@code rider} has the skill for the leap: Freerunner (rank read from Kingdoms on the server, as last synced on a client); never without Kingdoms. */
	public static boolean skilled(Player rider) {
		return (rider.level().isClientSide() ? Freerun.perk(rider, Freerun.Perk.FREERUNNER) : Freerun.rank(rider, Freerun.Perk.FREERUNNER)) >= 1;
	}

	/** Both sides' test: {@code rider}, off a mount that was going {@code speed} blocks a tick, with Sneak held, leaps. */
	public static boolean leaps(Player rider, double speed) {
		return speed >= LEAP_SPEED && rider.isShiftKeyDown() && !rider.isInWater() && skilled(rider);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Entity vehicle = player.getVehicle();
			if (vehicle != null && MountClimb.climbable(vehicle)) {
				Ride before = RIDING.get(id);
				Vec3 velocity = before != null && before.mountId == vehicle.getId() ? vehicle.position().subtract(before.mountAt) : Vec3.ZERO;
				RIDING.put(id, new Ride(vehicle.getId(), player.position(), vehicle.position(), velocity));
				continue;
			}
			Ride ride = RIDING.remove(id);
			if (ride != null && player.isAlive() && !player.isSpectator() && !MountClimb.climbing(player) && !player.isPassenger()
				&& player.position().distanceTo(ride.seat) < SEAT_REACH && leaps(player, Math.hypot(ride.velocity.x, ride.velocity.z))) {
				leap(player, ride);
			}
		}
		Iterator<Map.Entry<UUID, Leap>> it = LEAPING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Leap> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Leap leap = e.getValue();
			long now = player == null ? 0L : player.level().getGameTime();
			if (player == null || !player.isAlive() || player.isPassenger() || player.isInWater() || now - leap.start > AIR_TICKS) {
				it.remove();
			} else if (player.onGround() && now - leap.start >= 2L) {
				it.remove();
				roll(player, leap);
			} else {
				e.setValue(new Leap(leap.start, player.position(), leap.way));
			}
		}
	}

	/** Off the side vanilla set them down on, carrying the mount's pace, a little up. */
	private static void leap(ServerPlayer player, Ride ride) {
		Vec3 carried = new Vec3(ride.velocity.x, 0.0, ride.velocity.z);
		Vec3 side = player.position().subtract(ride.seat);
		side = new Vec3(side.x, 0.0, side.z);
		if (side.lengthSqr() < 1.0E-4) {
			Entity mount = player.level().getEntity(ride.mountId);
			float facing = (mount instanceof LivingEntity living ? living.yBodyRot : player.getYRot()) * Mth.DEG_TO_RAD;
			side = new Vec3(-Mth.cos(facing), 0.0, -Mth.sin(facing));
		}
		side = side.normalize();
		Vec3 v = carried.scale(LEAP_KEEP).add(side.scale(LEAP_SIDE)).add(0.0, LEAP_UP, 0.0);
		player.setDeltaMovement(v);
		player.needsSync = true;
		Vec3 way = carried.lengthSqr() > 1.0E-4 ? carried.normalize() : side;
		LEAPING.put(player.getUUID(), new Leap(player.level().getGameTime(), player.position(), way));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	/** Touched down: the dodge's roll, in the way they were going. It costs no feathers; on its cooldown there is no roll and the fall is the ordinary fall. */
	private static void roll(ServerPlayer player, Leap leap) {
		Vec3 moved = player.position().subtract(leap.last);
		Vec3 way = new Vec3(moved.x, 0.0, moved.z);
		way = way.lengthSqr() > 1.0E-4 ? way.normalize() : leap.way;
		if (Dodge.dodgeBy(player, way)) {
			player.setAttached(Dodge.ROLLING, new Dodge.Rolling(player.level().getGameTime(), (float)way.x, (float)way.z, false));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 0.8F);
		}
	}
}
