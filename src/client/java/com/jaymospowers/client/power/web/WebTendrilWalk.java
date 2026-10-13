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
 * surface and plants again ahead, in diagonal pairs (one pair steps while the other holds). They arch out from the
 * back to their tips, bowed away from the surface. The rest keep idling as usual, and the shoulder pair stays free for
 * R and G. Worked out on every client from where the climber is (the server only says that they're climbing), so
 * everyone sees the same walk.
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
	private static final float STEP_TICKS = 5.0F, STEP_LIFT = 0.45F, PAIR_GAP = 1.5F;
	/** How far it looks for the surface: out in front of the spot, and in behind it. */
	private static final double SURFACE_OUT = 0.6, SURFACE_IN = 1.6;
	/** How far each bows out from the surface, as a share of its length. */
	static final float ARCH = 0.28F;
	/** A tip planting: its soft thud (volume, pitch). */
	private static final float PLANT_VOLUME = 0.25F, PLANT_PITCH = 0.75F;

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

	/** Climbing by the tendrils right now (Wall Crawl's latch is on). */
	static boolean climbing(Player player) {
		return player.getAttached(WebTendrils.CLIMB) != null;
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
		if (normal == null) {
			return null;
		}
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
			Vec3 spot = plant(player, middle.add(right.scale(HOME[i][0] * scale)).add(up.scale(HOME[i][1] * scale)).add(lead), normal);
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
			if (leg.stepStart < 0.0F && leg.foot.distanceTo(spot) > STEP_FAR * scale && !otherStepping && time - walk.pairDone[other] >= PAIR_GAP) {
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
					if (i == 0 || i == 1) {
						// a soft thud as the pair plants (once a pair)
						player.level().playLocalSound(spot.x, spot.y, spot.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, PLANT_VOLUME,
							PLANT_PITCH + player.getRandom().nextFloat() * 0.2F, false);
					}
				} else {
					float e = t * t * (3.0F - 2.0F * t);
					tip = leg.from.lerp(leg.to, e).add(normal.scale(STEP_LIFT * scale * Mth.sin(t * Mth.PI)));
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
			out[index * 6 + 3] = (float)normal.dot(left);
			out[index * 6 + 4] = (float)-normal.y;
			out[index * 6 + 5] = (float)-normal.dot(ahead);
		}
		return out;
	}

	/** Where a tip plants near {@code at}: on the surface behind it, or, with none there, just out from where it would be. */
	private static Vec3 plant(Player player, Vec3 at, Vec3 normal) {
		BlockHitResult hit = player.level().clip(new ClipContext(at.add(normal.scale(SURFACE_OUT)), at.subtract(normal.scale(SURFACE_IN)),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at.subtract(normal.scale(0.4));
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
