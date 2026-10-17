package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebTendrils;
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
 * Wall Crawl's walking tendrils, the way Doctor Octopus walks on his arms: while you climb (a wall or a ceiling), four
 * of the tendrils come alive on their own and walk you along it. Each plants its tip on the surface around you, a
 * little ahead the way you're going; when you've moved on far enough that it trails, it lifts, arcs out from the
 * surface and plants again ahead, in diagonal pairs (one pair steps while the other holds). Each is a rigid limb: two
 * straight sections from the back to its tip, jointed at a knee that juts out from the surface, so the body is
 * carried on stiff legs rather than hung on soft cords. The rest keep idling as usual, and the shoulder pair stays free for
 * R and G. Worked out on every client from where the climber is (the server only says that they're climbing), so
 * everyone sees the same walk.
 * <p>
 * Off the wall, Wall Crawl carries you on them the same way, Doctor Octopus style ({@link #striding}): hoisted up off
 * the ground on the four, each reaching out ({@code STRIDE_REACH}) to grab whatever is near (the ground, a wall, a
 * ceiling, a tree), the body carried along between them. Each tip that plants cracks the block it grabs, and the crack
 * lingers ({@code WebCracks}). The other tendrils stay free: while the four walk, four more grow to stand in for them, so
 * every other ability still has its tendrils.
 */
final class WebTendrilWalk {
	/** The walking tendrils (see {@link WebTendrilLayer}'s roots): the middle pair, then the low pair. */
	static final int[] WALKERS = {4, 5, 6, 7};
	/** Where each one plants, around your middle along the surface: blocks to your right, and up (on a ceiling, ahead). */
	private static final double[][] HOME = {{0.85, 0.95}, {-0.85, 0.95}, {0.75, -0.85}, {-0.75, -0.85}};
	/** Diagonal pairs (indices into {@link #WALKERS}): one steps while the other holds. */
	private static final int[] PAIR = {0, 1, 1, 0};
	/** Its tip trails its spot this far (blocks) before it steps; it steps toward where you'll be this many ticks on. */
	private static final double STEP_FAR = 0.6, LEAD_TICKS = 5.0;
	/** A step: how long (ticks), how high the tip arcs off the surface (blocks), and how soon the other pair may go after. */
	private static final float STEP_TICKS = 5.0F, STEP_LIFT = 0.6F, PAIR_GAP = 1.5F;
	/** How far it looks for the surface: out in front of the spot, and in behind it. */
	private static final double SURFACE_OUT = 0.6, SURFACE_IN = 1.6;
	/** A walking limb's length (model pixels, fully grown): with its tip planted closer than this, the knee bends out. */
	static final float LIMB = 32.0F;
	/** Segments from the root to the knee (the rest run from the knee to the tip), and how straight a limb may get before it stretches. */
	static final int KNEE = 3;
	static final float STRAIGHTEST = 0.96F;
	/** A tip planting: its soft thud (volume, pitch). */
	private static final float PLANT_VOLUME = 0.25F, PLANT_PITCH = 0.75F;
	/** How deep a planted tip cracks the block it grabs (0-9). */
	static final int PLANT_CRACK = 3;
	/**
	 * Carried on the tendrils (off a wall): how far each one reaches for something to grab (blocks), how far down it looks
	 * for the ground under you, and, per leg, which way it reaches (to your right, down, ahead: out round you and down).
	 */
	static final double STRIDE_REACH = 4.5, STRIDE_GROUND = 4.0;
	private static final double[][] STRIDE_DIRS = {{0.8, -1.0, 0.7}, {-0.8, -1.0, 0.7}, {0.8, -1.0, -0.6}, {-0.8, -1.0, -0.6}};
	/**
	 * With a ceiling overhead (within {@code STRIDE_REACH}), the front pair reach up and grab it instead, the way Tendril
	 * Grab's tethers bite a wall, and walk along it overhead while the back pair walk the ground: you still only walk.
	 */
	private static final double[][] CEILING_DIRS = {{0.55, 1.0, 0.5}, {-0.55, 1.0, 0.5}};
	/** Striding, a tip trails its spot this far (blocks) before it steps, and steps this high. */
	private static final double STRIDE_STEP_FAR = 1.1;
	private static final float STRIDE_STEP_LIFT = 0.9F;
	/** Ways to look for something to grab, out from your middle (anchors are counted along these). */
	private static final Vec3[] PROBES = probes();

	private static final class Leg {
		@Nullable Vec3 foot;
		Vec3 from = Vec3.ZERO;
		Vec3 to = Vec3.ZERO;
		float stepStart = -1.0F;
	}

	private static final class Walk {
		final Leg[] legs = new Leg[WALKERS.length];
		final float[] pairDone = {-100.0F, -100.0F};
		Vec3 lastPos = Vec3.ZERO;
		Vec3 velocity = Vec3.ZERO;
		float lastTime = -1.0F;

		Walk() {
			for (int i = 0; i < this.legs.length; i++) {
				this.legs[i] = new Leg();
			}
		}
	}

	private static final Map<Integer, Walk> WALKS = new HashMap<>();

	private WebTendrilWalk() {
	}

	/** Climbing by the tendrils right now: Wall Crawl's latch is on, or a wall cling in the suit (the tendrils hold the wall). */
	static boolean climbing(Player player) {
		if (player.getAttached(WebTendrils.CLIMB) != null) {
			return true;
		}
		return clinging(player) || striding(player);
	}

	/** A wall cling in the suit (the tendrils hold the wall). */
	private static boolean clinging(Player player) {
		Integer move = player.getAttached(com.jaymospowers.power.web.WebParkour.MOVE);
		return move != null && com.jaymospowers.power.web.WebParkour.kind(move) == com.jaymospowers.power.web.WebParkour.CLING
			&& com.jaymospowers.power.web.WebSymbiote.suitShown(player);
	}

	/**
	 * Carried on the tendrils (Wall Crawl on, off the ground, something near to grab): worked out the same on every
	 * client from what it can see, so everyone sees the same walk.
	 */
	static boolean striding(Player player) {
		return com.jaymospowers.power.web.WebSymbiote.isBonded(player)
			&& com.jaymospowers.power.web.WebSymbiote.crawlTendrils(player)
			&& !player.onGround() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying && !player.isInWater()
			&& !player.isSpectator() && anchors(player, STRIDE_REACH * player.getScale()) >= 2;
	}

	/**
	 * (0.13.23) Hung from the tendrils rather than walking a wall: off the ground and carried between them, or on a
	 * ceiling, with nothing under the feet. The legs dangle then (WebHangPose) instead of walking the air. A wall
	 * climb (the latch on a wall, a wall cling) isn't hanging.
	 */
	static boolean hanging(Player player) {
		if (player.onGround() || player.isPassenger() || player.isInWater() || player.isFallFlying() || !climbing(player)) {
			return false;
		}
		Vec3 normal = surface(player);
		if (normal != null && normal.y < -0.5) {
			// on a ceiling
			return true;
		}
		// no wall against you (hung off a latch, or carried in the open), or carried along beside one
		return normal == null || player.getAttached(WebTendrils.CLIMB) == null && !clinging(player) && striding(player);
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
	 * This frame's walk for {@code player}, or null when they aren't climbing: per tendril (6 floats), the chord from
	 * its root to its tip and the way it bows, both in the body's model space (+X the wearer's left, +Y down, +Z back;
	 * the chord in blocks). Zero for the ones that aren't walking.
	 */
	static float @Nullable [] legs(Player player, float partial, float[][] roots) {
		if (!climbing(player)) {
			WALKS.remove(player.getId());
			return null;
		}
		Vec3 normal = surface(player);
		boolean stride = false;
		if (normal == null || player.getAttached(WebTendrils.CLIMB) == null && !clinging(player) && striding(player)) {
			if (!striding(player)) {
				return null;
			}
			// carried between them: the knees jut up and out
			stride = true;
			normal = new Vec3(0.0, 1.0, 0.0);
		}
		boolean ceiling = stride && ceilingAbove(player, STRIDE_REACH * player.getScale());
		Walk walk = WALKS.computeIfAbsent(player.getId(), id -> new Walk());
		if (WALKS.size() > 64) {
			WALKS.keySet().removeIf(id -> id != player.getId() && (player.level().getEntity(id) == null));
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
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		double yaw = bodyYaw * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 left = new Vec3(ahead.z, 0.0, -ahead.x);
		// the surface's own frame: to your right along it, and "up" it (on a ceiling, the way you face)
		boolean flat = Math.abs(normal.y) > 0.5;
		Vec3 up = flat ? ahead : new Vec3(0.0, 1.0, 0.0);
		Vec3 right = left.reverse();
		right = right.subtract(normal.scale(right.dot(normal)));
		right = right.lengthSqr() < 1.0E-3 ? up.cross(normal).normalize() : right.normalize();
		Vec3 middle = feet.add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 lead = walk.velocity.subtract(normal.scale(walk.velocity.dot(normal))).scale(LEAD_TICKS);
		float[] out = new float[roots.length * 6];
		for (int i = 0; i < WALKERS.length; i++) {
			Leg leg = walk.legs[i];
			Vec3 legOut = Vec3.ZERO;
			Vec3 away = normal;
			Vec3 spot;
			if (stride && ceiling && i < CEILING_DIRS.length) {
				// up to the ceiling: it bites in overhead and walks along it
				double[] d = CEILING_DIRS[i];
				legOut = left.scale(-d[0]).add(0.0, d[1], 0.0).add(ahead.scale(d[2])).normalize();
				spot = grab(player, middle.add(lead), legOut, STRIDE_REACH * scale);
				away = new Vec3(0.0, -1.0, 0.0);
			} else if (stride) {
				// reaching out round you and down for whatever there is to grab
				double[] d = STRIDE_DIRS[i];
				legOut = left.scale(-d[0]).add(0.0, d[1], 0.0).add(ahead.scale(d[2])).normalize();
				spot = grab(player, middle.add(lead), legOut, STRIDE_REACH * scale);
			} else {
				spot = plant(player, middle.add(right.scale(HOME[i][0] * scale)).add(up.scale(HOME[i][1] * scale)).add(lead), normal);
			}
			if (leg.foot == null) {
				leg.foot = spot;
			}
			int pair = PAIR[i];
			int other = 1 - pair;
			boolean otherStepping = false;
			for (int j = 0; j < WALKERS.length; j++) {
				if (PAIR[j] == other && walk.legs[j].stepStart >= 0.0F) {
					otherStepping = true;
				}
			}
			if (leg.stepStart < 0.0F && leg.foot.distanceTo(spot) > (stride ? STRIDE_STEP_FAR : STEP_FAR) * scale && !otherStepping && time - walk.pairDone[other] >= PAIR_GAP) {
				// it trails: up it comes, and over to plant ahead (its partner goes with it)
				for (int j = 0; j < WALKERS.length; j++) {
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
					// it bites in: the block cracks, and stays cracked a while
					com.jaymospowers.power.web.WebCracks.crackAt(player.level(), spot, PLANT_CRACK);
					if (i == 0 || i == 1) {
						// a soft thud as the pair plants (once a pair)
						player.level().playLocalSound(spot.x, spot.y, spot.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, PLANT_VOLUME,
							PLANT_PITCH + player.getRandom().nextFloat() * 0.2F, false);
					}
				} else {
					float e = t * t * (3.0F - 2.0F * t);
					tip = leg.from.lerp(leg.to, e).add(away.scale((stride ? STRIDE_STEP_LIFT : STEP_LIFT) * scale * Mth.sin(t * Mth.PI)));
				}
			}
			// from the tendril's root on the back, in the body's model space
			int index = WALKERS[i];
			float[] r = roots[index];
			Vec3 root = feet.add(left.scale(r[0] / 16.0F * scale)).add(0.0, (24.0F - r[1]) / 16.0F * scale, 0.0)
				.subtract(ahead.scale(r[2] / 16.0F * scale));
			Vec3 chord = tip.subtract(root);
			out[index * 6] = (float)chord.dot(left);
			out[index * 6 + 1] = (float)-chord.y;
			out[index * 6 + 2] = (float)-chord.dot(ahead);
			// the way it bows: off the surface (striding, up and out to its own side)
			Vec3 bow = stride ? away.add(legOut.multiply(1.0, 0.0, 1.0)).normalize() : normal;
			out[index * 6 + 3] = (float)bow.dot(left);
			out[index * 6 + 4] = (float)-bow.y;
			out[index * 6 + 5] = (float)-bow.dot(ahead);
		}
		return out;
	}

	/** Where a tip plants near {@code at}: on the surface behind it, or, with none there, just out from where it would be. */
	private static Vec3 plant(Player player, Vec3 at, Vec3 normal) {
		BlockHitResult hit = player.level().clip(new ClipContext(at.add(normal.scale(SURFACE_OUT)), at.subtract(normal.scale(SURFACE_IN)),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at.subtract(normal.scale(0.4));
	}

	/**
	 * Where a striding tip grabs, reaching {@code dir} from {@code from}: whatever it meets; failing that straight down,
	 * then straight out to that side, then up; with nothing at all, it hangs out that way.
	 */
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

		/** Which way the surface they're climbing faces (out of it): a ceiling above, or the wall their box presses on. */
	private static @Nullable Vec3 surface(Player player) {
		AABB box = player.getBoundingBox();
		if (!player.onGround() && !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05))) {
			return new Vec3(0.0, -1.0, 0.0);
		}
		AABB side = box.deflate(0.0, 0.1, 0.0);
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 best = null;
		double bestDot = -2.0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			Vec3 d = dir.getUnitVec3();
			if (!player.level().noCollision(player, side.move(d.scale(0.2)))) {
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
