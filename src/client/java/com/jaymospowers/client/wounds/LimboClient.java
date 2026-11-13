package com.jaymospowers.client.wounds;

import com.jaymospowers.core.wounds.Limbo;
import com.jaymospowers.core.wounds.LimboGhost;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Out of your body ({@link Limbo}): while the server says so ({@link Limbo#OUT}), the view leaves the body for a ghost of
 * our own that only this client has (never added to the world, so nothing else moves it), and the moving keys drift it
 * the way you look: forward and back along your gaze, so looking up rises, and left and right across it. It can't pass
 * through blocks and can't leave the leash. Where it is goes to the server every tick ({@link Limbo.Drift}), which puts
 * the ghost everyone else sees there. The body doesn't move meanwhile (a player who isn't their own camera sends no
 * movement), and sneak still reaches the server, so holding it comes to as before.
 */
public final class LimboClient {
	/** How fast a ghost drifts (blocks a tick), and how quickly it takes up or loses that speed (share a tick). */
	static final double SPEED = 0.2, EASE = 0.25;

	private static @Nullable LimboGhost ghost;
	private static Vec3 speed = Vec3.ZERO;

	private LimboClient() {
	}

	public static void init() {
		EntityRendererRegistry.register(Limbo.GHOST, LimboGhostRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(LimboClient::tick);
	}

	/** Whether this client's player is out of their body, looking out of their ghost. */
	public static boolean drifting() {
		return ghost != null;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || !player.hasAttached(Limbo.OUT)) {
			if (ghost != null) {
				if (player != null && minecraft.getCameraEntity() == ghost) {
					minecraft.setCameraEntity(player);
				}
				ghost = null;
			}
			return;
		}
		if (ghost == null || ghost.level() != minecraft.level) {
			ghost = new LimboGhost(Limbo.GHOST, minecraft.level);
			ghost.looksWith = player;
			ghost.snapTo(player.getX(), player.getY() + Limbo.RISE, player.getZ(), player.getYRot(), player.getXRot());
			speed = Vec3.ZERO;
		}
		if (minecraft.getCameraEntity() != ghost) {
			minecraft.setCameraEntity(ghost);
		}
		Options options = minecraft.options;
		double forward = (options.keyUp.isDown() ? 1.0 : 0.0) - (options.keyDown.isDown() ? 1.0 : 0.0);
		double left = (options.keyLeft.isDown() ? 1.0 : 0.0) - (options.keyRight.isDown() ? 1.0 : 0.0);
		Vec3 look = player.getLookAngle();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 wish = look.scale(forward).add(Math.cos(yaw) * left, 0.0, Math.sin(yaw) * left);
		if (wish.lengthSqr() > 1.0E-4) {
			wish = wish.normalize().scale(SPEED);
		}
		speed = speed.lerp(wish, EASE);
		if (speed.lengthSqr() < 1.0E-6) {
			speed = Vec3.ZERO;
		}
		ghost.setOldPosAndRot();
		Vec3 moved = slide(minecraft.level, ghost, speed);
		ghost.setPos(Limbo.leashed(player, ghost.position().add(moved)));
		ghost.setYRot(player.getYRot());
		ghost.setXRot(player.getXRot());
		ClientPlayNetworking.send(new Limbo.Drift(ghost.getX(), ghost.getY(), ghost.getZ(), player.getYRot(), player.getXRot()));
	}

	/** {@code move} with whatever part of it would carry the ghost into a block taken out, one axis at a time. */
	private static Vec3 slide(Level level, LimboGhost ghost, Vec3 move) {
		AABB box = ghost.getBoundingBox();
		// (begun inside something, a body in a crawl space: let it out)
		if (!level.noCollision(ghost, box)) {
			return move;
		}
		double x = level.noCollision(ghost, box.move(move.x, 0.0, 0.0)) ? move.x : 0.0;
		box = box.move(x, 0.0, 0.0);
		double y = level.noCollision(ghost, box.move(0.0, move.y, 0.0)) ? move.y : 0.0;
		box = box.move(0.0, y, 0.0);
		double z = level.noCollision(ghost, box.move(0.0, 0.0, move.z)) ? move.z : 0.0;
		if (x != move.x || y != move.y || z != move.z) {
			speed = new Vec3(x, y, z);
		}
		return new Vec3(x, y, z);
	}
}
