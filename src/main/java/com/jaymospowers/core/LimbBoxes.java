package com.jaymospowers.core;

import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hitboxes that follow the body: people and anything built like them ({@link Dismember#canLoseParts}) are hit on their
 * head, torso, arms and legs, not anywhere in their square box. A swing or a look that passes beside the body, between
 * the legs or over a lost arm, misses; a blade takes off the part it actually lands on.
 *
 * <p>The parts are laid out from the entity alone (its height, body and head turn, pose, and lost parts), in the same
 * proportions as the player model, so the server and every client agree without any models. Standing, crouching,
 * crawling or swimming, flying, and sitting are laid out; anything else (asleep, carried, a Xenoform form, the Great
 * Ape) keeps its ordinary box.
 *
 * <p>Used by Better Combat's swings and the crosshair (client, {@code client/mixin/LimbPickMixin} and
 * {@code client/compat/LimbSwingMixin}) and by {@link Dismember#aimedPart}.
 */
public final class LimbBoxes {
	/** Slack around every part, in blocks: swinging arms and a body mid-step aren't exactly where the layout puts them. */
	public static final double SLACK = 0.1;
	/** The model's height in pixels: a part's size below is in these, scaled to the entity. */
	private static final double MODEL_PX = 32.0;
	/** The torso (it never comes off). */
	public static final int TORSO = 0;

	private LimbBoxes() {
	}

	/** One part: a box turned with the body. {@code axes} are unit vectors (right, up, forward); {@code half} along each. */
	public record Box(int part, Vec3 center, Vec3[] axes, double[] half) {
		/** Where along {@code from}→{@code to} (0-1) the line first enters this box, or -1 if it doesn't. */
		public double clip(Vec3 from, Vec3 to) {
			Vec3 o = from.subtract(this.center);
			Vec3 d = to.subtract(from);
			double near = 0.0, far = 1.0;
			for (int i = 0; i < 3; i++) {
				double start = o.dot(this.axes[i]);
				double step = d.dot(this.axes[i]);
				double h = this.half[i];
				if (Math.abs(step) < 1.0E-9) {
					if (Math.abs(start) > h) {
						return -1.0;
					}
					continue;
				}
				double a = (-h - start) / step, b = (h - start) / step;
				near = Math.max(near, Math.min(a, b));
				far = Math.min(far, Math.max(a, b));
				if (near > far) {
					return -1.0;
				}
			}
			return near;
		}

		/** How far {@code point} is from this box (0 inside). */
		public double distance(Vec3 point) {
			Vec3 o = point.subtract(this.center);
			double sq = 0.0;
			for (int i = 0; i < 3; i++) {
				double out = Math.abs(o.dot(this.axes[i])) - this.half[i];
				if (out > 0.0) {
					sq += out * out;
				}
			}
			return Math.sqrt(sq);
		}

		/** Whether this box overlaps another turned box (separating axis test). */
		public boolean overlaps(Vec3 otherCenter, Vec3[] otherAxes, double[] otherHalf) {
			Vec3 t = otherCenter.subtract(this.center);
			List<Vec3> tests = new ArrayList<>(15);
			for (int i = 0; i < 3; i++) {
				tests.add(this.axes[i]);
				tests.add(otherAxes[i]);
				for (int j = 0; j < 3; j++) {
					Vec3 cross = this.axes[i].cross(otherAxes[j]);
					if (cross.lengthSqr() > 1.0E-9) {
						tests.add(cross.normalize());
					}
				}
			}
			for (Vec3 axis : tests) {
				double reach = 0.0;
				for (int i = 0; i < 3; i++) {
					reach += this.half[i] * Math.abs(this.axes[i].dot(axis)) + otherHalf[i] * Math.abs(otherAxes[i].dot(axis));
				}
				if (Math.abs(t.dot(axis)) > reach) {
					return false;
				}
			}
			return true;
		}
	}

	/** Whether {@code entity} is hit by its parts rather than its box right now. */
	public static boolean applies(Entity entity) {
		if (!(entity instanceof LivingEntity living) || !Dismember.canLoseParts(living) || living.isSleeping()
			|| living.getVehicle() instanceof Player) {
			return false;
		}
		return !(living instanceof Player player) || !XenoformState.transformed(player) && !ZFighterLineage.ape(player);
	}

	/** {@code entity}'s parts (lost ones left out), each grown by {@link #SLACK}. Empty if it doesn't {@link #applies}. */
	public static List<Box> of(Entity entity) {
		List<Box> boxes = new ArrayList<>(6);
		if (!applies(entity)) {
			return boxes;
		}
		LivingEntity living = (LivingEntity) entity;
		int missing = Dismember.missing(living);
		double standing = living.getDimensions(Pose.STANDING).height();
		// across and front-to-back always in standing pixels; up and down squashed when crouched
		double w = standing / MODEL_PX;
		double h = living.getBbHeight() / MODEL_PX;
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 forward = Vec3.directionFromRotation(0.0F, living.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0.0F, living.yBodyRot + 90.0F);
		Vec3 feet = living.position();
		Pose pose = living.getPose();

		if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING) {
			// lying along the body's facing: head first, legs behind, centred on the box
			Vec3 mid = feet.add(0.0, living.getBbHeight() / 2.0, 0.0);
			Vec3[] axes = {right, forward, up};
			add(boxes, missing, Dismember.HEAD, mid.add(forward.scale(12.0 * w)), axes, 4.0 * w, 4.0 * w, 4.0 * w);
			add(boxes, missing, TORSO, mid.add(forward.scale(2.0 * w)), axes, 4.0 * w, 6.0 * w, 2.0 * w);
			add(boxes, missing, Dismember.RIGHT_ARM, mid.add(forward.scale(2.0 * w)).add(right.scale(6.0 * w)), axes, 2.0 * w, 6.0 * w, 2.0 * w);
			add(boxes, missing, Dismember.LEFT_ARM, mid.add(forward.scale(2.0 * w)).add(right.scale(-6.0 * w)), axes, 2.0 * w, 6.0 * w, 2.0 * w);
			add(boxes, missing, Dismember.RIGHT_LEG, mid.add(forward.scale(-10.0 * w)).add(right.scale(2.0 * w)), axes, 2.0 * w, 6.0 * w, 2.0 * w);
			add(boxes, missing, Dismember.LEFT_LEG, mid.add(forward.scale(-10.0 * w)).add(right.scale(-2.0 * w)), axes, 2.0 * w, 6.0 * w, 2.0 * w);
			return boxes;
		}

		Vec3[] body = {right, up, forward};
		Vec3 headForward = Vec3.directionFromRotation(0.0F, living.yHeadRot);
		Vec3[] head = {Vec3.directionFromRotation(0.0F, living.yHeadRot + 90.0F), up, headForward};
		add(boxes, missing, Dismember.HEAD, feet.add(0.0, 28.0 * h, 0.0), head, 4.0 * w, 4.0 * h, 4.0 * w);
		add(boxes, missing, TORSO, feet.add(0.0, 18.0 * h, 0.0), body, 4.0 * w, 6.0 * h, 2.0 * w);

		EntityType<?> type = living.getType();
		if (type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER || type == EntityTypes.WITCH) {
			// arms folded across the chest: each half of the fold is that arm
			Vec3 fold = feet.add(0.0, 19.0 * h, 0.0).add(forward.scale(4.0 * w));
			add(boxes, missing, Dismember.RIGHT_ARM, fold.add(right.scale(2.0 * w)), body, 2.0 * w, 2.0 * h, 2.0 * w);
			add(boxes, missing, Dismember.LEFT_ARM, fold.add(right.scale(-2.0 * w)), body, 2.0 * w, 2.0 * h, 2.0 * w);
		} else if (type.builtInRegistryHolder().is(EntityTypeTags.ZOMBIES)) {
			// arms held out in front, from the shoulder
			Vec3 reach = feet.add(0.0, 22.0 * h, 0.0).add(forward.scale(6.0 * w));
			Vec3[] out = {right, up, forward};
			add(boxes, missing, Dismember.RIGHT_ARM, reach.add(right.scale(6.0 * w)), out, 2.0 * w, 2.0 * h, 6.0 * w);
			add(boxes, missing, Dismember.LEFT_ARM, reach.add(right.scale(-6.0 * w)), out, 2.0 * w, 2.0 * h, 6.0 * w);
		} else {
			add(boxes, missing, Dismember.RIGHT_ARM, feet.add(0.0, 18.0 * h, 0.0).add(right.scale(6.0 * w)), body, 2.0 * w, 6.0 * h, 2.0 * w);
			add(boxes, missing, Dismember.LEFT_ARM, feet.add(0.0, 18.0 * h, 0.0).add(right.scale(-6.0 * w)), body, 2.0 * w, 6.0 * h, 2.0 * w);
		}

		if (living.isPassenger()) {
			// seated: legs out in front from the hip
			Vec3 lap = feet.add(0.0, 12.0 * h, 0.0).add(forward.scale(6.0 * w));
			add(boxes, missing, Dismember.RIGHT_LEG, lap.add(right.scale(2.0 * w)), body, 2.0 * w, 2.0 * h, 6.0 * w);
			add(boxes, missing, Dismember.LEFT_LEG, lap.add(right.scale(-2.0 * w)), body, 2.0 * w, 2.0 * h, 6.0 * w);
		} else {
			add(boxes, missing, Dismember.RIGHT_LEG, feet.add(0.0, 6.0 * h, 0.0).add(right.scale(2.0 * w)), body, 2.0 * w, 6.0 * h, 2.0 * w);
			add(boxes, missing, Dismember.LEFT_LEG, feet.add(0.0, 6.0 * h, 0.0).add(right.scale(-2.0 * w)), body, 2.0 * w, 6.0 * h, 2.0 * w);
		}
		return boxes;
	}

	private static void add(List<Box> boxes, int missing, int part, Vec3 center, Vec3[] axes, double x, double y, double z) {
		if (part != TORSO && (missing & part) != 0) {
			return;
		}
		boxes.add(new Box(part, center, axes, new double[] {x + SLACK, y + SLACK, z + SLACK}));
	}

	/** Whether the line {@code from}→{@code to} crosses {@code entity}'s body (always true for one hit by its box). */
	public static boolean crosses(Entity entity, Vec3 from, Vec3 to) {
		if (!applies(entity)) {
			return true;
		}
		for (Box box : of(entity)) {
			if (box.clip(from, to) >= 0.0) {
				return true;
			}
		}
		return false;
	}

	/** Whether a turned box (a weapon's swing) overlaps {@code entity}'s body (always true for one hit by its box). */
	public static boolean overlaps(Entity entity, Vec3 center, Vec3[] axes, double[] half) {
		if (!applies(entity)) {
			return true;
		}
		for (Box box : of(entity)) {
			if (box.overlaps(center, axes, half)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The part a look from {@code eye} along {@code look} lands on: the first part it crosses, or (a swing that hit but
	 * a look that passed beside) the part nearest the look. A blow to the torso goes to the nearest part that can come
	 * off. Null if {@code target} doesn't {@link #applies} or has nothing left to lose.
	 */
	public static @Nullable Integer aimedPart(LivingEntity target, Vec3 eye, Vec3 look) {
		List<Box> boxes = of(target);
		if (boxes.isEmpty()) {
			return null;
		}
		Vec3 end = eye.add(look.scale(16.0));
		Box hit = null;
		double first = Double.MAX_VALUE;
		for (Box box : boxes) {
			double at = box.clip(eye, end);
			if (at >= 0.0 && at < first) {
				first = at;
				hit = box;
			}
		}
		Vec3 point;
		if (hit != null) {
			if (hit.part() != TORSO) {
				return hit.part();
			}
			point = eye.add(end.subtract(eye).scale(first));
		} else {
			// where the look passes closest to the body
			Vec3 mid = target.getBoundingBox().getCenter();
			double along = Math.max(0.0, mid.subtract(eye).dot(look));
			point = eye.add(look.scale(along));
		}
		Box nearest = null;
		double best = Double.MAX_VALUE;
		for (Box box : boxes) {
			if (box.part() == TORSO) {
				continue;
			}
			double distance = box.distance(point);
			if (distance < best) {
				best = distance;
				nearest = box;
			}
		}
		return nearest == null ? null : nearest.part();
	}
}
