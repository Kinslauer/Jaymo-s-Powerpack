package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Small helpers the power "picks" share: a per-player cooldown table, who counts as a foe, what the player is aiming at
 * and a gauge string for the action bar. The gauge uses {@code =} and {@code -} on purpose: a run of {@code |} and
 * {@code .} is what the client reads as a charge bar that locks the mouse.
 */
public final class PickKit {
	private static final Map<String, Map<UUID, long[]>> TABLES = new HashMap<>();

	private PickKit() {
	}

	/** Whether {@code slot} of the table named {@code table} is ready for the player. */
	public static boolean ready(String table, ServerPlayer player, int slot) {
		return TABLES.computeIfAbsent(table, k -> new HashMap<>()).computeIfAbsent(player.getUUID(), k -> new long[16])[slot] <= player.level().getGameTime();
	}

	/** Ticks left on the slot (0: ready). */
	public static long left(String table, ServerPlayer player, int slot) {
		return Math.max(0L, TABLES.computeIfAbsent(table, k -> new HashMap<>()).computeIfAbsent(player.getUUID(), k -> new long[16])[slot] - player.level().getGameTime());
	}

	public static void spend(String table, ServerPlayer player, int slot, int cooldown) {
		TABLES.computeIfAbsent(table, k -> new HashMap<>()).computeIfAbsent(player.getUUID(), k -> new long[16])[slot] = player.level().getGameTime() + cooldown;
	}

	public static void forget(ServerPlayer player) {
		for (Map<UUID, long[]> table : TABLES.values()) {
			table.remove(player.getUUID());
		}
	}

	/** Anything the player may harm with a power: alive, not themselves, not a friend, a pet of theirs or a stand. */
	public static boolean foe(ServerPlayer player, Entity e) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand)
			&& !(e instanceof OwnableEntity pet && pet.getOwner() == player) && !player.isAlliedTo(e) && !e.isPassengerOfSameVehicle(player);
	}

	/** A foe that is not a player: monsters, and mobs that hunt the player. */
	public static boolean enemy(ServerPlayer player, Entity e) {
		return foe(player, e) && !(e instanceof Player) && (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player);
	}

	/** The nearest creature the player's crosshair is on within {@code reach}, or null. */
	public static @Nullable LivingEntity aimed(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		LivingEntity best = null;
		double near = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0),
			c -> foe(player, c))) {
			var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(eye);
				if (d < near) {
					near = d;
					best = e;
				}
			}
		}
		return best;
	}

	public static AABB around(Vec3 at, double radius) {
		return new AABB(at, at).inflate(radius);
	}

	/** A gauge for the action bar: {@code [=====-----]}. */
	public static String gauge(float fraction, int width) {
		int full = Math.max(0, Math.min(width, Math.round(fraction * width)));
		return "[" + "=".repeat(full) + "-".repeat(width - full) + "]";
	}
}
