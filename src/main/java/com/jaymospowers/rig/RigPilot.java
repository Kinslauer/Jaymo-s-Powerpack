package com.jaymospowers.rig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot: the Mind flies the suit toward a waypoint the wearer marks (where they look), charge permitting,
 * until they take control back or the suit runs dry. Any movement input takes control back.
 */
public final class RigPilot {
	private RigPilot() {
	}

	/** Charge drained per tick while autopilot is engaged. */
	static final int COST = 2;
	/** How close (blocks) counts as arrived. */
	static final double ARRIVE = 2.0;
	/** Autopilot speed along the course (blocks per tick). */
	static final double SPEED = 0.3;

	/** Engaged wearers and the course they are flying. */
	private static final Map<UUID, BlockPos> COURSE = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> COURSE.remove(handler.player.getUUID()));
	}

	/** The Rig menu's pick: set the course to where the wearer is looking, or clear it. */
	static void use(ServerPlayer player) {
		UUID uuid = player.getUUID();
		if (COURSE.containsKey(uuid)) {
			COURSE.remove(uuid);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.pilot.off"));
			return;
		}
		if (!Rig.fullSet(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(64.0)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		BlockPos target = hit.getType() == HitResult.Type.BLOCK
			? BlockPos.containing(hit.getLocation())
			: player.blockPosition();
		COURSE.put(uuid, target);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.pilot.on", target.getX(), target.getY(),
			target.getZ()));
	}

	/** Whether this wearer is currently being flown by autopilot. */
	static boolean piloting(ServerPlayer player) {
		return COURSE.containsKey(player.getUUID());
	}

	private static void tick(ServerPlayer player) {
		BlockPos target = COURSE.get(player.getUUID());
		if (target == null) {
			return;
		}
		if (!player.isAlive() || !Rig.wears(player, Rig.Piece.CORE)
			|| (!RigActives.hovering(player) && !RigActives.flying(player))) {
			COURSE.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition();
		Vec3 to = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		Vec3 dir = to.subtract(from);
		double dist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		if (dist < ARRIVE && Math.abs(to.y - from.y) < ARRIVE) {
			COURSE.remove(player.getUUID());
			RigMind.sayAlways(player, "pilot_arrived", 1.0F);
			return;
		}
		if (!Rig.spend(player, COST)) {
			COURSE.remove(player.getUUID());
			RigMind.empty(player);
			return;
		}
		dir = dir.normalize();
		player.setDeltaMovement(dir.x * SPEED, Math.min(dir.y * SPEED, 0.25), dir.z * SPEED);
		player.syncVelocity = true;
		// face the course
		float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
	}
}
