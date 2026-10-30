package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.7) Andy: "shift should now animate a crouch, the players gets low, hands out to the side, swaying as they move, ready
 * to draw a weapon and fire more comfortably on one knee when they stop moving". Crouched and still on the ground for
 * {@link #KNEEL_AFTER} ticks, a player is down on one knee (drawn by the client's {@code CrouchPose}, which reads the same
 * rule off what it sees). On one knee a shot is steadier (Andy: "Bows crossbows and guns get the benefit"): anything
 * loosed or fired (an arrow, a bolt, a TaCZ bullet, a musket ball: any projectile) leaves on a line {@link #STEADY} as far
 * off your aim as it would have ({@link #steady}). A deliberate spread wider than {@link #MAX_STEADIED} degrees (a
 * multishot crossbow's side bolts, a shotgun's outer pellets) is left as it is.
 */
public final class Kneel {
	/** Crouched and still this long (ticks) and you're on one knee. */
	public static final int KNEEL_AFTER = 8;
	/** Moving less than this a tick (blocks, across the ground) counts as still. */
	public static final double STILL = 0.03;
	/** A shot's spread on one knee, as a share of its usual. */
	public static final double STEADY = 0.3;
	/** Shots further off the aim than this (degrees) are spread on purpose, and left alone. */
	public static final double MAX_STEADIED = 8.0;

	/** Player → since when they've been crouched and still (game time). */
	private static final Map<UUID, Long> STILL_SINCE = new HashMap<>();
	/** Player → where they were last tick. */
	private static final Map<UUID, Vec3> LAST = new HashMap<>();

	private Kneel() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Kneel::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot && shot.tickCount == 0 && shot.getOwner() instanceof ServerPlayer player && kneeling(player)) {
				steady(shot, player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STILL_SINCE.clear();
			LAST.clear();
		});
	}

	/** Server: whether {@code player} is down on one knee just now. */
	public static boolean kneeling(Player player) {
		Long since = STILL_SINCE.get(player.getUUID());
		return since != null && player.level().getGameTime() - since >= KNEEL_AFTER;
	}

	/**
	 * Whether this player could be on one knee at all: crouched on the ground, on their own feet. (0.18.66) Andy: "it only
	 * kneels on the second to last option, vanilla sneak stays": only from the low crouch ({@link Stance}), not a plain sneak.
	 */
	public static boolean crouched(Player player) {
		return player.isShiftKeyDown() && Stance.low(player) && player.onGround() && !player.isPassenger() && !player.isSwimming() && !player.isFallFlying()
			&& !player.isSpectator() && !Carry.isCarried(player);
	}

	/** A shot just loosed from the knee: turned most of the way back onto the shooter's aim, at the same speed. */
	static void steady(Projectile shot, Player player) {
		Vec3 v = shot.getDeltaMovement();
		double speed = v.length();
		if (speed < 1.0E-4) {
			return;
		}
		Vec3 heading = v.scale(1.0 / speed);
		Vec3 aim = player.getLookAngle();
		double off = Math.toDegrees(Math.acos(Math.clamp(heading.dot(aim), -1.0, 1.0)));
		if (off > MAX_STEADIED || off < 0.01) {
			return;
		}
		Vec3 steadied = aim.add(heading.subtract(aim).scale(STEADY)).normalize().scale(speed);
		shot.setDeltaMovement(steadied);
		shot.syncVelocity = true;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Vec3 here = player.position();
			Vec3 last = LAST.put(id, here);
			boolean still = last != null && here.subtract(last).horizontalDistanceSqr() < STILL * STILL;
			if (crouched(player) && still) {
				STILL_SINCE.putIfAbsent(id, player.level().getGameTime());
			} else {
				STILL_SINCE.remove(id);
			}
		}
		if (LAST.size() > server.getPlayerList().getPlayerCount() * 2 + 8) {
			LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			STILL_SINCE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}
}
