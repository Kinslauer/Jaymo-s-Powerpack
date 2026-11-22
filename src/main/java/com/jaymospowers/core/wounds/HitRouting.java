package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.LimbBoxes;
import com.jaymospowers.core.LockOnRegions;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a hit lands, and how a part's overflow spills on. Works in health points on a creature's parts
 * ({@code points[i]} of {@code plan.share(i) * Wounds.poolMax}).
 * <p>
 * Survival picks (Andy, 2026-11-22): a shot to a head with no helmet does {@link #HEADSHOT} times the damage (a Ranger's
 * arrow goes through the helmet: Jaymo's Kingdoms tags it {@link #THROUGH_HELMET}), and each armour piece cuts what lands
 * on its own part ({@link #ARMOUR_CUT} for diamond, scaled by the piece's armour). What has no place to land (fire, magic,
 * poison, drowning, a blast) wears every part by the share it takes off the health.
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
	/** (Survival picks, lso-headshot) A shot to the head with no helmet (or a Ranger's arrow through one) does this many times the damage. */
	public static final float HEADSHOT = 2.0F;
	/** (Survival picks) The scoreboard tag on a projectile that goes through the helmet (Jaymo's Kingdoms' Ranger arrows). */
	public static final String THROUGH_HELMET = "jaymospowers_through_helmet";
	/** (Survival picks, lso-armour-parts) A diamond piece cuts the damage to its own part by this share; others by their armour against diamond's, at most {@link #ARMOUR_CUT_MAX}. */
	static final float ARMOUR_CUT = 0.1F, ARMOUR_CUT_MAX = 0.2F;
	/** (Survival picks) What lands nowhere never takes a part below this share of its pool: the health runs out first. */
	static final float SPREAD_FLOOR = 0.05F;
	/** Diamond's armour on the head, chest, legs and feet. */
	static final float DIAMOND_HEAD = 3.0F, DIAMOND_CHEST = 8.0F, DIAMOND_LEGS = 6.0F, DIAMOND_FEET = 3.0F;

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
				damage(points, plan, part, loss / feet.length * guard(victim, source, part));
			}
		} else if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL)
			|| source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
			|| source.is(DamageTypes.LIGHTNING_BOLT)) {
			spread(victim, plan, points, max, loss);
		} else if (source.getDirectEntity() != null || source.getEntity() != null) {
			// a player locked onto it lands where the lock aims (LockOnRegions), else where the blow came
			BodyPart aimed = LockOnRegions.aimedPart(victim, plan, source);
			BodyPart landed = plan.resolve(aimed != null ? aimed : landed(victim, plan, source));
			damage(points, plan, landed, loss * guard(victim, source, landed) * (headshot(victim, source, landed) ? HEADSHOT : 1.0F));
			return landed;
		} else {
			spread(victim, plan, points, max, loss);
		}
		return null;
	}

	/**
	 * (Survival picks) {@code loss} of the health that lands nowhere in particular: every part loses the share of its pool
	 * that the health loses of its most, never below {@link #SPREAD_FLOOR} of it (so fire or poison never blacks out or
	 * breaks a part: the health runs out first).
	 */
	private static void spread(LivingEntity victim, BodyPlan plan, float[] points, float max, float loss) {
		float share = loss / Math.max(0.01F, victim.getMaxHealth());
		for (int i = 0; i < points.length; i++) {
			float cap = plan.share(i) * max;
			float floor = Math.min(points[i], SPREAD_FLOOR * cap);
			points[i] = Math.max(floor, points[i] - share * cap);
		}
	}

	/**
	 * (Survival picks, lso-headshot) Whether a hit by {@code source} that {@code landed} is a headshot: a projectile on the
	 * head, with no helmet on it or one that goes through the helmet ({@link #THROUGH_HELMET}).
	 */
	static boolean headshot(LivingEntity victim, DamageSource source, @Nullable BodyPart landed) {
		Entity direct = source.getDirectEntity();
		return landed == BodyPart.HEAD && direct != null && !(direct instanceof LivingEntity)
			&& (victim.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || direct.entityTags().contains(THROUGH_HELMET));
	}

	/**
	 * (Survival picks, lso-armour-parts) What's left of a hit on {@code part} after the armour piece over it: 1 bare, less
	 * {@link #ARMOUR_CUT} for diamond. Damage that goes round armour, and a Ranger's arrow on the head, aren't cut.
	 */
	static float guard(LivingEntity victim, DamageSource source, BodyPart part) {
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return 1.0F;
		}
		EquipmentSlot slot;
		float diamond;
		switch (part) {
			case HEAD -> {
				if (source.getDirectEntity() != null && source.getDirectEntity().entityTags().contains(THROUGH_HELMET)) {
					return 1.0F;
				}
				slot = EquipmentSlot.HEAD;
				diamond = DIAMOND_HEAD;
			}
			case LEFT_LEG, RIGHT_LEG, FRONT_LEGS, HIND_LEGS, LEGS -> {
				slot = EquipmentSlot.LEGS;
				diamond = DIAMOND_LEGS;
			}
			case LEFT_FOOT, RIGHT_FOOT -> {
				slot = EquipmentSlot.FEET;
				diamond = DIAMOND_FEET;
			}
			default -> {
				slot = EquipmentSlot.CHEST;
				diamond = DIAMOND_CHEST;
			}
		}
		ItemStack worn = victim.getItemBySlot(slot);
		if (worn.isEmpty()) {
			return 1.0F;
		}
		double[] armour = {0.0};
		worn.forEachModifier(slot, (attribute, modifier) -> {
			if (attribute.is(Attributes.ARMOR) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				armour[0] += modifier.amount();
			}
		});
		return 1.0F - Math.min(ARMOUR_CUT_MAX, ARMOUR_CUT * (float) armour[0] / diamond);
	}

	/**
	 * The part a hit from a player locked onto {@code victim} lands on ({@link LockOnRegions#aimedPart}), when it's a
	 * blow, shot or throw {@link #route} would land on one part; else null.
	 */
	static @Nullable BodyPart lockAimed(LivingEntity victim, BodyPlan plan, DamageSource source) {
		BodyPart aimed = LockOnRegions.aimedPart(victim, plan, source);
		return aimed == null ? null : plan.resolve(aimed);
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
