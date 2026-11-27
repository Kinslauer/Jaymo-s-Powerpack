package com.jaymospowers.client.rig;

import com.jaymospowers.rig.RigOcto;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Octo Core's arms walking (Andy: "they should telescope further out, so they can do wall climbing and walking like
 * the tendrils"): the symbiote's walking tendrils (the web power's WebTendrilWalk) done in plate and piston. On a wall,
 * all four claws plant on it around you, a little ahead the way you're going, and step in diagonal pairs (one pair steps
 * while the other holds). Walking ({@link RigOcto#walking}), they telescope out round you to whatever there is to grab
 * (the ground, a wall, a ceiling, a tree) and carry you along between them, the upper pair ahead, the lower pair behind;
 * with a ceiling overhead the upper pair reach up and walk along it. Each claw that plants bites the block (the cracks
 * linger, as the tendrils' do) with a clank. Worked out on every client from where the wearer is, so everyone sees the
 * same walk; the arms are posed to the planted claws by {@link RigOctoClient}'s reach.
 */
final class RigOctoWalk {
	/** Where each claw plants on a wall, around your middle: blocks to your right, and up (arm order: upper right, upper left, lower right, lower left). */
	private static final double[][] HOME = {{0.9, 1.0}, {-0.9, 1.0}, {0.8, -0.9}, {-0.8, -0.9}};
	/** Diagonal pairs: one steps while the other holds. */
	private static final int[] PAIR = {0, 1, 1, 0};
	/** On a wall: a claw trails its spot this far (blocks) before it steps; it steps toward where you'll be this many ticks on. */
	private static final double STEP_FAR = 0.7, LEAD_TICKS = 5.0;
	/** A step: how long (ticks), how high the claw arcs off the surface (blocks), and how soon the other pair may go after. */
	private static final float STEP_TICKS = 5.0F, STEP_LIFT = 0.6F, PAIR_GAP = 1.5F;
	/** How far it looks for the surface: out in front of the spot, and in behind it. */
	private static final double SURFACE_OUT = 0.6, SURFACE_IN = 1.6;
	/** Walking: how far each arm telescopes out for something to grab (blocks), and how far down it looks for the ground. */
	static final double STRIDE_REACH = 4.5, STRIDE_GROUND = 4.0;
	/** Walking: which way each arm reaches (to your right, down, ahead): the upper pair out ahead, the lower pair out behind. */
	private static final double[][] STRIDE_DIRS = {{0.8, -1.0, 0.8}, {-0.8, -1.0, 0.8}, {0.8, -1.0, -0.6}, {-0.8, -1.0, -0.6}};
	/** With a ceiling overhead, the upper pair reach up to it instead. */
	private static final double[][] CEILING_DIRS = {{0.55, 1.0, 0.5}, {-0.55, 1.0, 0.5}};
	/** Walking, a claw trails its spot this far (blocks) before it steps, and steps this high. */
	private static final double STRIDE_STEP_FAR = 1.2;
	private static final float STRIDE_STEP_LIFT = 0.9F;
	/** How deep a planted claw cracks the block it grabs (0-9); its clank (volume, pitch). */
	private static final int PLANT_CRACK = 3;
	private static final float PLANT_VOLUME = 0.3F, PLANT_PITCH = 1.3F;
	/** Ways to look for something to grab, out from your middle. */
	private static final Vec3[] PROBES = probes();

	private static final class Leg {
		@Nullable Vec3 foot;
		Vec3 from = Vec3.ZERO;
		Vec3 to = Vec3.ZERO;
		float stepStart = -1.0F;
	}

	private static final class Walk {
		final Leg[] legs = {new Leg(), new Leg(), new Leg(), new Leg()};
		final float[] pairDone = {-100.0F, -100.0F};
		Vec3 lastPos = Vec3.ZERO;
		Vec3 velocity = Vec3.ZERO;
		float lastTime = -1.0F;
	}

	private static final Map<Integer, Walk> WALKS = new HashMap<>();

	private RigOctoWalk() {
	}

	/** Carried on the arms (walk on, off the ground, at least two things to grab), on any client. */
	static boolean striding(Player player) {
		return RigOcto.walking(player) && !player.onGround() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
			&& !player.isSpectator() && anchors(player, STRIDE_REACH * player.getScale()) >= 2;
	}

	/** How many ways out from your middle there's something to grab within {@code reach}. */
	static int anchors(Player player, double reach) {
		Vec3 middle = player.getBoundingBox().getCenter();
		int count = 0;
		for (Vec3 probe : PROBES) {
			BlockHitResult hit = player.level().clip(new ClipContext(middle, middle.add(probe.scale(reach)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				count++;
			}
		}
		return count;
	}

	/** How far below your feet the ground is (blocks), or -1 if there's none within {@code max}. */
	static double groundBelow(Player player, double max) {
		Vec3 feet = player.position();
		BlockHitResult hit = player.level().clip(new ClipContext(feet.add(0.0, 0.05, 0.0), feet.subtract(0.0, max, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? feet.y - hit.getLocation().y : -1.0;
	}

	private static Vec3[] probes() {
		java.util.List<Vec3> out = new java.util.ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (x != 0 || y != 0 || z != 0) {
						out.add(new Vec3(x, y, z).normalize());
					}
				}
			}
		}
		return out.toArray(new Vec3[0]);
	}

	/**
	 * This frame's planted claws for {@code player} (world positions, one per arm), or null when the arms aren't walking or
	 * climbing. The upper pair is null while the arms hold something (they're busy).
	 */
	static Vec3 @Nullable [] claws(Player player, float partial, boolean busyUpper) {
		Vec3 normal = RigOcto.clinging(player) ? surface(player) : null;
		boolean stride = false;
		if (normal == null) {
			if (!striding(player)) {
				WALKS.remove(player.getId());
				return null;
			}
			stride = true;
			normal = new Vec3(0.0, 1.0, 0.0);
		}
		boolean ceiling = stride && ceilingAbove(player, STRIDE_REACH * player.getScale());
		Walk walk = WALKS.computeIfAbsent(player.getId(), id -> new Walk());
		if (WALKS.size() > 64) {
			WALKS.keySet().removeIf(id -> id != player.getId() && player.level().getEntity(id) == null);
		}
		float time = player.level().getGameTime() + partial;
		Vec3 feet = player.getPosition(partial);
		if (walk.lastTime >= 0.0F && time > walk.lastTime) {
			Vec3 v = feet.subtract(walk.lastPos).scale(1.0F / (time - walk.lastTime));
			walk.velocity = walk.velocity.lerp(v, 0.3);
		}
		if (time != walk.lastTime) {
			walk.lastPos = feet;
			walk.lastTime = time;
		}
		float scale = player.getScale();
		double yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 left = new Vec3(ahead.z, 0.0, -ahead.x);
		// the surface's own frame: to your right along it, and "up" it
		boolean flat = Math.abs(normal.y) > 0.5;
		Vec3 up = flat ? ahead : new Vec3(0.0, 1.0, 0.0);
		Vec3 right = left.reverse();
		right = right.subtract(normal.scale(right.dot(normal)));
		right = right.lengthSqr() < 1.0E-3 ? up.cross(normal).normalize() : right.normalize();
		Vec3 middle = feet.add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 lead = walk.velocity.subtract(normal.scale(walk.velocity.dot(normal))).scale(LEAD_TICKS);
		Vec3[] out = new Vec3[4];
		for (int i = 0; i < 4; i++) {
			Leg leg = walk.legs[i];
			Vec3 away = normal;
			Vec3 spot;
			if (stride && ceiling && i < CEILING_DIRS.length) {
				double[] d = CEILING_DIRS[i];
				Vec3 way = left.scale(-d[0]).add(0.0, d[1], 0.0).add(ahead.scale(d[2])).normalize();
				spot = grab(player, middle.add(lead), way, STRIDE_REACH * scale);
				away = new Vec3(0.0, -1.0, 0.0);
			} else if (stride) {
				double[] d = STRIDE_DIRS[i];
				Vec3 way = left.scale(-d[0]).add(0.0, d[1], 0.0).add(ahead.scale(d[2])).normalize();
				spot = grab(player, middle.add(lead), way, STRIDE_REACH * scale);
			} else {
				spot = plant(player, middle.add(right.scale(HOME[i][0] * scale)).add(up.scale(HOME[i][1] * scale)).add(lead), normal);
			}
			if (leg.foot == null) {
				leg.foot = spot;
			}
			int pair = PAIR[i];
			int other = 1 - pair;
			boolean otherStepping = false;
			for (int j = 0; j < 4; j++) {
				if (PAIR[j] == other && walk.legs[j].stepStart >= 0.0F) {
					otherStepping = true;
				}
			}
			if (leg.stepStart < 0.0F && leg.foot.distanceTo(spot) > (stride ? STRIDE_STEP_FAR : STEP_FAR) * scale && !otherStepping
				&& time - walk.pairDone[other] >= PAIR_GAP) {
				// it trails: up it comes, and over to plant ahead (its partner goes with it)
				for (int j = 0; j < 4; j++) {
					Leg partner = walk.legs[j];
					if (PAIR[j] == pair && partner.stepStart < 0.0F && partner.foot != null) {
						partner.stepStart = time;
						partner.from = partner.foot;
					}
				}
			}
			Vec3 tip = leg.foot;
			if (leg.stepStart >= 0.0F) {
				leg.to = spot;
				float t = (time - leg.stepStart) / STEP_TICKS;
				if (t >= 1.0F) {
					leg.foot = spot;
					leg.stepStart = -1.0F;
					walk.pairDone[pair] = time;
					tip = spot;
					// it bites in: the block cracks, and a clank (once a pair)
					com.jaymospowers.power.web.WebCracks.crackAt(player.level(), spot, PLANT_CRACK);
					if (i == 0 || i == 1) {
						player.level().playLocalSound(spot.x, spot.y, spot.z, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, PLANT_VOLUME,
							PLANT_PITCH + player.getRandom().nextFloat() * 0.2F, false);
					}
				} else {
					float e = t * t * (3.0F - 2.0F * t);
					tip = leg.from.lerp(leg.to, e).add(away.scale((stride ? STRIDE_STEP_LIFT : STEP_LIFT) * scale * Mth.sin(t * Mth.PI)));
				}
			}
			out[i] = busyUpper && i < 2 ? null : tip;
		}
		return out;
	}

	/** Where a claw plants near {@code at}: on the surface behind it, or, with none there, just out from where it would be. */
	private static Vec3 plant(Player player, Vec3 at, Vec3 normal) {
		BlockHitResult hit = player.level().clip(new ClipContext(at.add(normal.scale(SURFACE_OUT)), at.subtract(normal.scale(SURFACE_IN)),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at.subtract(normal.scale(0.4));
	}

	/** Where a walking claw grabs, reaching {@code dir} from {@code from}: whatever it meets; failing that down, out to its side, then up. */
	private static Vec3 grab(Player player, Vec3 from, Vec3 dir, double reach) {
		Vec3 side = new Vec3(dir.x, 0.0, dir.z);
		side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize();
		Vec3[] tries = {dir, new Vec3(side.x * 0.4, -1.0, side.z * 0.4).normalize(), side, side.add(0.0, 1.0, 0.0).normalize()};
		for (Vec3 way : tries) {
			if (way.lengthSqr() < 1.0E-4) {
				continue;
			}
			BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(way.scale(reach)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				return hit.getLocation();
			}
		}
		return from.add(dir.scale(reach * 0.4));
	}

	/** A ceiling within {@code reach} over your middle. */
	private static boolean ceilingAbove(Player player, double reach) {
		Vec3 middle = player.getBoundingBox().getCenter();
		BlockHitResult hit = player.level().clip(new ClipContext(middle, middle.add(0.0, reach, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.DOWN;
	}

	/** Which way the wall you're gripping faces (out of it), or null. */
	private static @Nullable Vec3 surface(Player player) {
		AABB side = player.getBoundingBox().deflate(0.0, 0.1, 0.0);
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 best = null;
		double bestDot = -2.0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			Vec3 d = dir.getUnitVec3();
			if (!player.level().noCollision(player, side.move(d.scale(RigOcto.WALL_GAP)))) {
				double dot = d.dot(look);
				if (dot > bestDot) {
					bestDot = dot;
					best = d.reverse();
				}
			}
		}
		return best;
	}
}
