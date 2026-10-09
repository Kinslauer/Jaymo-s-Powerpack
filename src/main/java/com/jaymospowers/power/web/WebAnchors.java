package com.jaymospowers.power.web;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Finding somewhere to stick a swing web (shared: the client picks, the server checks). The crosshair wins when it's
 * on a block that's not far below you; otherwise an "assisted" anchor is searched for above and ahead of you, the
 * way the Spider-Man games do it, so swinging over open ground with the camera level still works.
 */
public final class WebAnchors {
	/** Furthest a swing web reaches (crosshair), and how far the assisted search looks. */
	public static final double SWING_RANGE = 48.0;
	public static final double ASSIST_RANGE = 40.0;
	/** A crosshair anchor more than this far below the eyes is ignored in favour of an assisted one, if there is one. */
	private static final double DIRECT_MAX_DROP = 4.0;
	/** Assisted anchors must be at least this high above the eyes, and this far away. */
	private static final double ASSIST_MIN_HEIGHT = 3.0;
	private static final double ASSIST_MIN_DISTANCE = 6.0;
	/** Assisted search fan: angles above the horizon, and yaw offsets either side of where you look (degrees). */
	private static final float[] ASSIST_ELEVATIONS = {64.0F, 54.0F, 44.0F, 74.0F, 34.0F, 24.0F};
	private static final float[] ASSIST_YAWS = {0.0F, -12.0F, 12.0F, -25.0F, 25.0F, -40.0F, 40.0F};
	/** The assisted pick prefers this elevation and distance. */
	private static final double ASSIST_BEST_ELEVATION = 52.0;
	private static final double ASSIST_BEST_DISTANCE = 20.0;

	private WebAnchors() {
	}

	/** Where a swing web would stick, and whether the crosshair found it ({@code assisted} = false). */
	public record Anchor(Vec3 pos, boolean assisted) {
	}

	public static @Nullable Anchor find(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		// a symbiote's webs reach further; its tendrils (the suit on) don't reach far at all
		double scale = WebSymbiote.swingScale(player);
		BlockHitResult hit = clip(player, eye, eye.add(look.scale(SWING_RANGE * scale)));
		Vec3 direct = hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
		if (direct != null && direct.y >= eye.y - DIRECT_MAX_DROP) {
			return new Anchor(direct, false);
		}
		Vec3 assisted = assist(player, eye, scale);
		if (assisted != null) {
			return new Anchor(assisted, true);
		}
		return direct == null ? null : new Anchor(direct, false);
	}

	private static @Nullable Vec3 assist(Player player, Vec3 eye, double scale) {
		float yaw = player.getYRot();
		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (float elevation : ASSIST_ELEVATIONS) {
			for (float yawOffset : ASSIST_YAWS) {
				Vec3 dir = Vec3.directionFromRotation(-elevation, yaw + yawOffset);
				BlockHitResult hit = clip(player, eye, eye.add(dir.scale(ASSIST_RANGE * scale)));
				if (hit.getType() == HitResult.Type.MISS) {
					continue;
				}
				Vec3 pos = hit.getLocation();
				double distance = pos.distanceTo(eye);
				if (pos.y < eye.y + ASSIST_MIN_HEIGHT || distance < ASSIST_MIN_DISTANCE) {
					continue;
				}
				double score = -Math.abs(elevation - ASSIST_BEST_ELEVATION) / 25.0 - Math.abs(yawOffset) / 30.0
					- Math.abs(distance - ASSIST_BEST_DISTANCE) / 25.0;
				if (score > bestScore) {
					bestScore = score;
					best = pos;
				}
			}
		}
		return best;
	}

	public static BlockHitResult clip(Player player, Vec3 from, Vec3 to) {
		return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
	}

	/** Server check of a client's anchor: in range, next to something solid, and in sight (leniently: the server lags). */
	public static boolean valid(Player player, Vec3 anchor, double range) {
		Vec3 eye = player.getEyePosition();
		if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z) || eye.distanceToSqr(anchor) > range * range) {
			return false;
		}
		if (!nearSolid(player.level(), anchor)) {
			return false;
		}
		Vec3 dir = anchor.subtract(eye).normalize();
		BlockHitResult hit = clip(player, eye, anchor.subtract(dir.scale(0.1)));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(anchor) < 2.5 * 2.5;
	}

	/** True if a block with a collision shape is within half a block of {@code pos}. */
	public static boolean nearSolid(Level level, Vec3 pos) {
		BlockPos min = BlockPos.containing(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5);
		BlockPos max = BlockPos.containing(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			if (level.isLoaded(p) && !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
