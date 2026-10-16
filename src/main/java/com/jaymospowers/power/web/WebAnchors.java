package com.jaymospowers.power.web;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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

	/**
	 * A swing web (R/G) aimed near a creature, a player or a corpse sticks to it instead of a block: anything whose
	 * middle is within {@code ENTITY_MAGNET} blocks of the crosshair's line, out to {@code ENTITY_RANGE}, in sight.
	 * The crosshair lights up while one is in reach.
	 */
	public static final double ENTITY_RANGE = 32.0;
	public static final double ENTITY_MAGNET = 2.0;
	/** A dropped item only catches a web aimed right at it (this close to the crosshair's line), so loot on the floor doesn't steal swings. */
	public static final double ITEM_MAGNET = 0.6;

	private WebAnchors() {
	}

	/** Something a swing web can stick to: a living creature or player (not you, your mount or a spectator), a dropped item, or a corpse. */
	public static boolean webbable(Player player, @Nullable Entity entity) {
		if (entity == null || entity == player || !entity.isAlive() || entity.isSpectator() || entity == player.getVehicle()
			|| player.hasPassenger(entity) || entity.isPassengerOfSameVehicle(player)) {
			return false;
		}
		return entity instanceof LivingEntity || entity instanceof ItemEntity || com.jaymospowers.power.gravity.GravityPower.isCorpse(entity);
	}

	/**
	 * Small enough to reel in with both webs on it and Sneak held: a dropped item, or a creature or corpse no bigger
	 * than {@link WebPower#REEL_MAX_SIZE} (a pig, a chicken, a child; not a grown villager or another player). Both sides.
	 */
	public static boolean reelable(@Nullable Entity entity) {
		if (entity == null || entity instanceof Player) {
			return false;
		}
		if (entity instanceof ItemEntity) {
			return true;
		}
		return !KitchenCompat.isHeavy(entity) && entity.getBbWidth() * entity.getBbHeight() <= WebPower.REEL_MAX_SIZE;
	}

	/**
	 * The creature, player or corpse a swing web would stick to: the one whose middle is nearest the crosshair's line
	 * (within {@link #ENTITY_MAGNET}), not behind a wall, out to {@link #ENTITY_RANGE}. Null if none.
	 */
	public static @Nullable Entity entityTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double range = ENTITY_RANGE * WebSymbiote.swingScale(player);
		BlockHitResult wall = clip(player, eye, eye.add(look.scale(range)));
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(eye) + ENTITY_MAGNET;
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(ENTITY_MAGNET + 1.0);
		for (Entity e : player.level().getEntities(player, search, e -> webbable(player, e))) {
			Vec3 middle = e.getBoundingBox().getCenter();
			double along = middle.subtract(eye).dot(look);
			if (along < 1.0 || along > reach) {
				continue;
			}
			// how far its middle is off the line, less its own size: a big creature is easier to hit
			double off = middle.distanceTo(eye.add(look.scale(along))) - Math.min(e.getBbWidth(), e.getBbHeight()) * 0.5;
			if (off > (e instanceof ItemEntity ? ITEM_MAGNET : ENTITY_MAGNET)) {
				continue;
			}
			// nearest the line first, then nearest you
			double score = Math.max(0.0, off) + along * 0.02;
			if (score < bestScore && clip(player, eye, middle).getType() == HitResult.Type.MISS) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** Where a swing web would stick, and whether the crosshair found it ({@code assisted} = false). */
	public record Anchor(Vec3 pos, boolean assisted) {
	}

	public static @Nullable Anchor find(Player player) {
		return find(player, WebNet.MAIN);
	}

	/** {@link #find}, for a web from {@code hand} (a loaded web shooter on that wrist reaches further). */
	public static @Nullable Anchor find(Player player, int hand) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		// a symbiote's webs reach further; its tendrils (the suit on) don't reach far at all
		double scale = WebSymbiote.swingScale(player) * WebShooters.rangeScale(player, hand);
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
