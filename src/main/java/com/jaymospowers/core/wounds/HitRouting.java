package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.LimbBoxes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a hit lands, and how a part's overflow spills on. Works in health points on a creature's parts
 * ({@code points[i]} of {@code plan.share(i) * maxHealth}).
 */
final class HitRouting {
	/** Share of the box's height (from the feet) above which a hit is the head, and below which (humanoid) the legs, and below which the feet. */
	static final double HEAD_FROM = 0.75, BODY_FROM = 0.4, FOOT_BELOW = 0.12;
	/** Sideways off-centre (share of the width) beyond which a hit to a humanoid's middle is an arm, or a flyer's is a wing. */
	static final double SIDE_SHARE = 0.25;
	/** A quadruped's hit this far toward its front (share of the width) and in its upper half is its head. */
	static final double FRONT_SHARE = 0.2;
	/** Anything small or spiderlike: below this share of the height is its legs, above {@link #SMALL_HEAD_FROM} its head. */
	static final double LEGS_BELOW = 0.35, SMALL_HEAD_FROM = 0.7;
	/** How far a mob's blow wanders up and down the body, as a share of the height either way. */
	static final double MOB_AIM_SPREAD = 0.25;

	private HitRouting() {
	}

	/**
	 * Takes {@code loss} from {@code points} the way {@code source} would land it. (0.14.2) Returns the part a blow, an
	 * arrow or a thrown thing landed on (as the plan has it), or null for what spreads or has no point (falls,
	 * explosions, drowning, fire, magic).
	 */
	static @Nullable BodyPart route(LivingEntity victim, BodyPlan plan, DamageSource source, float[] points, float max, float loss) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			BodyPart[] feet = plan.fallParts();
			for (BodyPart part : feet) {
				damage(points, plan, part, loss / feet.length);
			}
		} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			for (int i = 0; i < plan.size(); i++) {
				damage(points, plan, plan.part(i), loss * plan.share(i));
			}
		} else if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)) {
			damage(points, plan, BodyPart.HEAD, loss);
		} else if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
			|| source.is(DamageTypes.LIGHTNING_BOLT)) {
			damage(points, plan, BodyPart.BODY, loss);
		} else if (source.getDirectEntity() != null || source.getEntity() != null) {
			BodyPart landed = plan.resolve(landed(victim, plan, source));
			damage(points, plan, landed, loss);
			return landed;
		} else {
			damage(points, plan, BodyPart.BODY, loss);
		}
		return null;
	}

	/** Takes {@code amount} from {@code first}; what it can't give spills to {@link BodyPart#overflow}, and on until spent or nothing is left. */
	static void damage(float[] points, BodyPlan plan, BodyPart first, float amount) {
		int current = plan.index(plan.resolve(first));
		int visited = 0;
		float left = amount;
		while (left > Wounds.EPS) {
			visited |= 1 << current;
			float take = Math.min(left, points[current]);
			points[current] -= take;
			left -= take;
			if (left <= Wounds.EPS) {
				return;
			}
			int next = plan.index(plan.resolve(plan.part(current).overflow()));
			if ((visited >> next & 1) != 0) {
				next = -1;
				for (int i = 0; i < points.length; i++) {
					if ((visited >> i & 1) == 0 && points[i] > Wounds.EPS) {
						next = i;
						break;
					}
				}
				if (next < 0) {
					return;
				}
			}
			current = next;
		}
	}

	// ---- where a blow lands ----------------------------------------------------------------------------------------------

	private static BodyPart landed(LivingEntity victim, BodyPlan plan, DamageSource source) {
		Entity direct = source.getDirectEntity();
		Entity by = source.getEntity();
		AABB box = victim.getBoundingBox();
		Vec3 point;
		if (by instanceof Player player && direct == by) {
			// a player's swing: the part their crosshair crosses
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getLookAngle().scale(16.0));
			if (plan == BodyPlan.HUMANOID) {
				LimbBoxes.Box hit = null;
				double first = Double.MAX_VALUE;
				for (LimbBoxes.Box candidate : LimbBoxes.of(victim)) {
					double at = candidate.clip(eye, end);
					if (at >= 0.0 && at < first) {
						first = at;
						hit = candidate;
					}
				}
				if (hit != null) {
					return fromLimb(hit.part(), box, eye.y + (end.y - eye.y) * first);
				}
			}
			point = box.inflate(0.1).clip(eye, end).orElse(null);
			if (point == null) {
				point = box.getCenter();
			}
		} else if (direct != null && !(direct instanceof LivingEntity)) {
			// an arrow, a thrown trident, a fireball: where it is
			point = direct.position();
		} else if (by != null) {
			// a mob's blow: from its side of the body, around its own height
			double dx = by.getX() - victim.getX();
			double dz = by.getZ() - victim.getZ();
			double flat = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
			double half = box.getXsize() * 0.5;
			double jitter = (victim.getRandom().nextDouble() - 0.5) * 2.0 * MOB_AIM_SPREAD * box.getYsize();
			double y = Mth.clamp(by.getY() + by.getBbHeight() * 0.5 + jitter, box.minY, box.maxY);
			point = new Vec3(victim.getX() + dx / flat * half, y, victim.getZ() + dz / flat * half);
		} else {
			point = box.getCenter();
		}
		return partAt(victim, plan, point);
	}

	/** A humanoid's part from {@link LimbBoxes} ({@link LimbBoxes#TORSO} or a {@link Dismember} bit), a leg low down being its foot. */
	private static BodyPart fromLimb(int limb, AABB box, double y) {
		boolean low = (y - box.minY) / Math.max(0.01, box.getYsize()) < FOOT_BELOW;
		return switch (limb) {
			case Dismember.HEAD -> BodyPart.HEAD;
			case Dismember.RIGHT_ARM -> BodyPart.RIGHT_ARM;
			case Dismember.LEFT_ARM -> BodyPart.LEFT_ARM;
			case Dismember.RIGHT_LEG -> low ? BodyPart.RIGHT_FOOT : BodyPart.RIGHT_LEG;
			case Dismember.LEFT_LEG -> low ? BodyPart.LEFT_FOOT : BodyPart.LEFT_LEG;
			default -> BodyPart.BODY;
		};
	}

	/** The part of {@code victim} that {@code point} is on, by its height, how far to the side and how far forward. */
	private static BodyPart partAt(LivingEntity victim, BodyPlan plan, Vec3 point) {
		AABB box = victim.getBoundingBox();
		double share = (point.y - box.minY) / Math.max(0.01, box.getYsize());
		double width = Math.max(0.01, box.getXsize());
		Vec3 rel = point.subtract(victim.position());
		// its right, as it faces (as Dismember reads it)
		Vec3 right = Vec3.directionFromRotation(0.0F, victim.yBodyRot + 90.0F);
		double side = rel.dot(right);
		boolean onRight = side < 0.0;
		return switch (plan) {
			case HUMANOID -> {
				if (share >= HEAD_FROM) {
					yield BodyPart.HEAD;
				}
				if (share >= BODY_FROM) {
					yield Math.abs(side) > width * SIDE_SHARE ? (onRight ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM) : BodyPart.BODY;
				}
				if (share >= FOOT_BELOW) {
					yield onRight ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
				}
				yield onRight ? BodyPart.RIGHT_FOOT : BodyPart.LEFT_FOOT;
			}
			case QUADRUPED -> {
				Vec3 forward = Vec3.directionFromRotation(0.0F, victim.yBodyRot);
				double along = rel.dot(forward);
				if (along > width * FRONT_SHARE && share > 0.5) {
					yield BodyPart.HEAD;
				}
				if (share < BODY_FROM) {
					yield along >= 0.0 ? BodyPart.FRONT_LEGS : BodyPart.HIND_LEGS;
				}
				yield BodyPart.BODY;
			}
			case ARTHROPOD -> share >= SMALL_HEAD_FROM ? BodyPart.HEAD : share < LEGS_BELOW ? BodyPart.LEGS : BodyPart.BODY;
			case FLYER -> share >= SMALL_HEAD_FROM ? BodyPart.HEAD : Math.abs(side) > width * SIDE_SHARE ? BodyPart.WINGS : BodyPart.BODY;
			default -> BodyPart.BODY;
		};
	}
}
