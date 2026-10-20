package com.jaymospowers.aether;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Quicksoil's friction is above 1 (nothing slows you on it), so on its own you would speed up without end. Once your
 * sideways speed passes {@link #CAP_SPEED} the friction counts as {@link AetherBlocks#QUICKSOIL_FRICTION} capped to
 * 0.99 instead. The client runs it on the player it controls (their movement is their own), see
 * {@code client/aether/AetherClient}.
 */
public final class QuicksoilSpeed {
	// ---- tuning ----
	/** Sideways speed (blocks a tick) above which the cap applies. */
	public static final double CAP_SPEED = 1.0;
	/** The friction the cap allows (the block's own is {@link AetherBlocks#QUICKSOIL_FRICTION}). */
	public static final double CAPPED_FRICTION = 0.99;

	/** Items and mobs within this many blocks of a player get the cap on the server (players cap themselves on the client). */
	public static final double SERVER_RANGE = 48.0;

	private QuicksoilSpeed() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				for (ServerPlayer player : level.players()) {
					for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(SERVER_RANGE))) {
						if (!(entity instanceof Player)) {
							cap(entity);
						}
					}
				}
			}
		});
	}

	/** Slows {@code entity} to the capped friction if it's standing on quicksoil and fast. */
	public static void cap(Entity entity) {
		if (!entity.onGround()) {
			return;
		}
		Vec3 v = entity.getDeltaMovement();
		if (v.horizontalDistance() <= CAP_SPEED) {
			return;
		}
		BlockPos below = BlockPos.containing(entity.getX(), entity.getBoundingBox().minY - 0.5, entity.getZ());
		if (entity.level().getBlockState(below).is(AetherBlocks.QUICKSOIL)) {
			double scale = CAPPED_FRICTION / AetherBlocks.QUICKSOIL_FRICTION;
			entity.setDeltaMovement(v.x * scale, v.y, v.z * scale);
		}
	}
}
