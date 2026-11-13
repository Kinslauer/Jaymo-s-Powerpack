package com.jaymospowers.core;

import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Breaks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToDoubleFunction;
import net.minecraft.network.chat.Component;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-13): "you should be able to target different regions on enemies you target with a lock on, now scrolling
 * should pick the seperate regions you can target on the body (head, legs, limbs)". While locked on, the scroll wheel picks
 * a {@link Region} of the target's body; the camera aims there ({@link #point}), the lock-on marker names it, and the
 * player's own blows, shots and power cuts on that target land on it ({@link #aimedPart}, read by the wounds' hit routing
 * and {@link Dismember#aimedPart}), so the region's part takes the damage, breaks and comes off. On top of what that part
 * already does when hurt ({@link #onAimedHit}): a head hit bites harder (though {@link #HEAD_GLANCE} of them glance off onto
 * the torso), a leg hit slows, an arm hit weakens.
 *
 * <p>Which regions a target has follows its {@link BodyPlan}: a person has all four, an animal or a spider head, body and
 * legs, a flyer head, body and wings (its "arms"). A part cut off is gone from the list (both arms off: no arms). A body
 * with no parts of its own (bosses, armour stands, slimes and such) has only the torso.
 */
public final class LockOnRegions {
	/** An aimed head hit does this much more damage. */
	public static final float HEAD_BONUS = 1.25F;
	/** An aimed leg hit slows for this long (ticks); a hit of {@link #HEAVY} or more slows harder. */
	public static final int LEG_SLOW_TICKS = 40;
	/** An aimed arm hit weakens their blows for this long (ticks); a hit of {@link #HEAVY} or more weakens harder. */
	public static final int ARM_WEAK_TICKS = 40;
	/** Damage from which a leg or arm hit's slowness or weakness is level II. */
	public static final float HEAVY = 6.0F;
	/**
	 * Andy (2026-11-13), on head shots felling weaker mobs in one hit: "yes", lock-on head shots sometimes glance off. This
	 * share of aimed head hits lands on the torso instead (no head bonus).
	 */
	public static final float HEAD_GLANCE = 0.35F;
	/** Andy (2026-11-13): "hits toward the head are less accurate too, so you have a higher chance to miss". This share of aimed head hits misses outright. */
	public static final float HEAD_MISS = 0.2F;
	/**
	 * Andy (2026-11-13): "finesse stat should increase accuracy". At full Finesse a head shot misses and glances this much
	 * less (a share of {@link #HEAD_MISS} and {@link #HEAD_GLANCE}), in step with the stat.
	 */
	public static final float FINESSE_AIM = 0.75F;
	/**
	 * How far {@code player}'s Finesse has come, 0 to 1. Jaymo's Kingdoms holds the stat and sets this at server start
	 * (its {@code PowersCompat}); without Kingdoms nobody has any.
	 */
	public static ToDoubleFunction<Player> finesse = player -> 0.0;
	/** The last aimed hit's roll on each victim, so the one blow asked twice (routing, then its damage) glances the same. */
	private static final Map<LivingEntity, Rolled> ROLLED = new WeakHashMap<>();

	private record Rolled(DamageSource source, BodyPart part) {
	}

	/** Where on the body the lock aims, top to bottom (the scroll wheel's order). */
	public enum Region {
		HEAD, TORSO, ARMS, LEGS,
		/** The Great Ape's tail (only it has one to aim at): a blow there lands on the body, a blade while it lies crippled cuts it off. */
		TAIL;

		public static final Region[] ALL = values();

		public static Region of(int index) {
			return index >= 0 && index < ALL.length ? ALL[index] : TORSO;
		}

		/** Its name on the marker ({@code lock_on.jaymospowers.region.<name>}); a flyer's arms are its wings. */
		public Component displayName(@Nullable BodyPlan plan) {
			String name = this == ARMS && plan == BodyPlan.FLYER ? "wings" : this.name().toLowerCase(Locale.ROOT);
			return Component.translatable("lock_on.jaymospowers.region." + name);
		}
	}

	private LockOnRegions() {
	}

	public static void init() {
		// an aimed head hit may miss outright
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !missesHead(entity, source));
	}

	/** Whether this blow, aimed at {@code victim}'s head through the lock, misses ({@link #HEAD_MISS}); a whiff is heard. */
	private static boolean missesHead(LivingEntity victim, DamageSource source) {
		if (aimedRegion(victim, source) != Region.HEAD || !(source.getEntity() instanceof Player player)
			|| victim.getRandom().nextFloat() >= HEAD_MISS * steadiness(player) || !(victim.level() instanceof ServerLevel level)) {
			return false;
		}
		level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	/** What's left of the head's miss and glance chances for {@code player}: 1 with no Finesse, {@code 1 - FINESSE_AIM} at full. */
	static float steadiness(Player player) {
		double share;
		try {
			share = finesse.applyAsDouble(player);
		} catch (RuntimeException e) {
			share = 0.0;
		}
		return 1.0F - FINESSE_AIM * (float)Math.clamp(share, 0.0, 1.0);
	}

	/** A blow, shot or throw that lands on one place (not a fall, blast, fire, drowning or magic). */
	private static boolean aimable(DamageSource source) {
		return !(source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_DROWNING)
			|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.MAGIC)
			|| source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.LIGHTNING_BOLT));
	}

	/**
	 * The region of {@code victim} this blow is aimed at through its attacker's lock: the player's own swing, or something
	 * they shot or threw, while locked onto {@code victim}. Null for anything else (another's blow, a stray, a blast).
	 */
	public static @Nullable Region aimedRegion(LivingEntity victim, DamageSource source) {
		Entity by = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (!(by instanceof ServerPlayer player) || direct != by && (direct == null || direct instanceof LivingEntity) || !aimable(source)
			|| LockOn.target(player) != victim) {
			return null;
		}
		return fit(victim, LockOn.region(player));
	}

	/** Whether {@code target} is the Great Ape (a player's or a villager's), the one body with a tail to aim at. */
	public static boolean greatApe(LivingEntity target) {
		return Boolean.TRUE.equals(target.getAttached(ZFighterLineage.GREAT_APE));
	}

	/** The regions {@code target} can be hit in, top to bottom (always at least the torso). */
	public static List<Region> regions(LivingEntity target) {
		List<Region> out = new ArrayList<>(4);
		BodyPlan plan = BodyPlan.of(target);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			out.add(Region.TORSO);
			return out;
		}
		int missing = Dismember.missing(target);
		if ((missing & Dismember.HEAD) == 0) {
			out.add(Region.HEAD);
		}
		out.add(Region.TORSO);
		if (plan == BodyPlan.HUMANOID && (missing & (Dismember.LEFT_ARM | Dismember.RIGHT_ARM)) != (Dismember.LEFT_ARM | Dismember.RIGHT_ARM)
			|| plan == BodyPlan.FLYER) {
			out.add(Region.ARMS);
		}
		if (plan == BodyPlan.QUADRUPED || plan == BodyPlan.ARTHROPOD
			|| plan == BodyPlan.HUMANOID && (missing & (Dismember.LEFT_LEG | Dismember.RIGHT_LEG)) != (Dismember.LEFT_LEG | Dismember.RIGHT_LEG)) {
			out.add(Region.LEGS);
		}
		if (greatApe(target)) {
			out.add(Region.TAIL);
		}
		return out;
	}

	/** {@code wanted} if {@code target} has it, else the torso. */
	public static Region fit(LivingEntity target, Region wanted) {
		return regions(target).contains(wanted) ? wanted : Region.TORSO;
	}

	/**
	 * The part a hit in {@code region} lands on, seen from {@code from}: of a pair (arms, legs, an animal's front and hind
	 * legs) the one nearer, or the one still there.
	 */
	public static BodyPart part(LivingEntity target, BodyPlan plan, Region region, Vec3 from) {
		return switch (region) {
			case HEAD -> BodyPart.HEAD;
			case TORSO, TAIL -> BodyPart.BODY;
			case ARMS -> plan == BodyPlan.FLYER ? BodyPart.WINGS : nearer(target, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, from);
			case LEGS -> switch (plan) {
				case QUADRUPED -> facing(target, from) >= 0.0 ? BodyPart.FRONT_LEGS : BodyPart.HIND_LEGS;
				case ARTHROPOD -> BodyPart.LEGS;
				default -> nearer(target, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, from);
			};
		};
	}

	/** How far {@code from} is in front of {@code target} (negative: behind it). */
	private static double facing(LivingEntity target, Vec3 from) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, target.yBodyRot);
		return from.subtract(target.position()).dot(forward);
	}

	/** Of a person's two arms or legs, the one on the side nearer {@code from}, or whichever is still on. */
	private static BodyPart nearer(LivingEntity target, BodyPart right, BodyPart left, Vec3 from) {
		int missing = Dismember.missing(target);
		boolean rightGone = (missing & Breaks.limb(right)) != 0, leftGone = (missing & Breaks.limb(left)) != 0;
		if (rightGone != leftGone) {
			return rightGone ? left : right;
		}
		return sideOf(target, right).distanceToSqr(from) <= sideOf(target, left).distanceToSqr(from) ? right : left;
	}

	/**
	 * Where on {@code target} an arm or leg is: from its limb boxes for a person laid out by them (they say exactly), else
	 * a step to the side as the wounds' hit routing reads a side (its right being where {@code rel.dot(right) < 0}).
	 */
	private static Vec3 sideOf(LivingEntity target, BodyPart part) {
		int limb = Breaks.limb(part);
		for (LimbBoxes.Box box : LimbBoxes.of(target)) {
			if (box.part() == limb) {
				return box.center();
			}
		}
		AABB box = target.getBoundingBox();
		Vec3 right = Vec3.directionFromRotation(0.0F, target.yBodyRot + 90.0F);
		boolean onRight = part == BodyPart.RIGHT_ARM || part == BodyPart.RIGHT_LEG;
		boolean arm = part == BodyPart.RIGHT_ARM || part == BodyPart.LEFT_ARM;
		double y = box.minY + box.getYsize() * (arm ? 0.6 : 0.25);
		double out = box.getXsize() * (arm ? 0.4 : 0.2);
		return new Vec3(target.getX(), y, target.getZ()).add(right.scale(onRight ? -out : out));
	}

	/** The point the lock aims at for {@code region} of {@code target}, seen from {@code from}. */
	public static Vec3 point(LivingEntity target, Region region, Vec3 from) {
		AABB box = target.getBoundingBox();
		Vec3 centre = box.getCenter();
		BodyPlan plan = BodyPlan.of(target);
		if (plan == null || plan == BodyPlan.SIMPLE || region == Region.TORSO && plan != BodyPlan.HUMANOID) {
			return centre;
		}
		if (region == Region.TAIL) {
			// low behind its back
			Vec3 back = Vec3.directionFromRotation(0.0F, target.yBodyRot).scale(-box.getXsize() * 0.55);
			return new Vec3(target.getX(), box.minY + box.getYsize() * 0.4, target.getZ()).add(back);
		}
		BodyPart part = part(target, plan, region, from);
		int limb = plan == BodyPlan.HUMANOID ? (region == Region.TORSO ? LimbBoxes.TORSO : Breaks.limb(part)) : -1;
		if (limb >= 0) {
			for (LimbBoxes.Box limbBox : LimbBoxes.of(target)) {
				if (limbBox.part() == limb) {
					return limbBox.center();
				}
			}
		}
		double h = box.getYsize();
		Vec3 feet = new Vec3(target.getX(), box.minY, target.getZ());
		Vec3 forward = Vec3.directionFromRotation(0.0F, target.yBodyRot);
		return switch (plan) {
			case HUMANOID -> switch (region) {
				case HEAD -> feet.add(0.0, h * 0.875, 0.0);
				case TORSO -> feet.add(0.0, h * 0.6, 0.0);
				default -> sideOf(target, part);
			};
			case QUADRUPED -> switch (region) {
				case HEAD -> feet.add(0.0, h * 0.75, 0.0).add(forward.scale(box.getXsize() * 0.4));
				default -> feet.add(0.0, h * 0.2, 0.0).add(forward.scale(box.getXsize() * (part == BodyPart.FRONT_LEGS ? 0.3 : -0.3)));
			};
			case ARTHROPOD -> feet.add(0.0, h * (region == Region.HEAD ? 0.85 : 0.15), 0.0);
			case FLYER -> {
				if (region == Region.HEAD) {
					yield feet.add(0.0, h * 0.85, 0.0);
				}
				// the wing on the side nearer the eye
				Vec3 right = Vec3.directionFromRotation(0.0F, target.yBodyRot + 90.0F);
				double side = from.subtract(target.position()).dot(right) >= 0.0 ? 1.0 : -1.0;
				yield feet.add(0.0, h * 0.5, 0.0).add(right.scale(side * box.getXsize() * 0.45));
			}
			default -> centre;
		};
	}

	/**
	 * The part a blow on {@code victim} lands on because its attacker is locked onto it ({@link #aimedRegion}), a head shot
	 * glancing off onto the torso {@link #HEAD_GLANCE} of the time; null when it isn't aimed through a lock.
	 */
	public static @Nullable BodyPart aimedPart(LivingEntity victim, BodyPlan plan, DamageSource source) {
		Region aimed = aimedRegion(victim, source);
		if (aimed == null || !(source.getEntity() instanceof ServerPlayer player)) {
			return null;
		}
		Rolled rolled = ROLLED.get(victim);
		if (rolled != null && rolled.source() == source) {
			return plan.index(rolled.part()) >= 0 ? rolled.part() : null;
		}
		Region region = aimed;
		// a head shot may glance off onto the torso
		if (region == Region.HEAD && victim.getRandom().nextFloat() < HEAD_GLANCE * steadiness(player)) {
			region = Region.TORSO;
		}
		BodyPart part = part(victim, plan, region, player.getEyePosition());
		ROLLED.put(victim, new Rolled(source, part));
		return plan.index(part) >= 0 ? part : null;
	}

	/** {@link Dismember}'s bit for where {@code attacker}'s lock on {@code target} aims (head, an arm, a leg), or null (the torso, or no lock). */
	public static @Nullable Integer aimedLimb(LivingEntity attacker, LivingEntity target) {
		if (!(attacker instanceof Player player) || LockOn.target(player) != target) {
			return null;
		}
		BodyPlan plan = BodyPlan.of(target);
		if (plan != BodyPlan.HUMANOID) {
			return null;
		}
		Region region = fit(target, LockOn.region(player));
		if (region == Region.TORSO || region == Region.TAIL) {
			return null;
		}
		if (region == Region.HEAD && target.getRandom().nextFloat() < HEAD_GLANCE * steadiness(player)) {
			// glanced off the head onto the shoulder: the nearer arm (the torso itself never comes off)
			region = regions(target).contains(Region.ARMS) ? Region.ARMS : Region.LEGS;
			if (!regions(target).contains(region)) {
				return null;
			}
		}
		int limb = Breaks.limb(part(target, plan, region, player.getEyePosition()));
		return limb == 0 ? null : limb;
	}

	/** An aimed head hit's damage, as {@link #HEAD_BONUS} makes it; anything else as it was. */
	public static float scale(@Nullable BodyPart aimed, float amount) {
		return aimed == BodyPart.HEAD ? amount * HEAD_BONUS : amount;
	}

	/** After an aimed hit landed on {@code landed}: a leg slows, an arm weakens. */
	public static void onAimedHit(LivingEntity victim, DamageSource source, BodyPart landed, float loss) {
		if (!victim.isAlive() || loss <= 0.0F) {
			return;
		}
		int level = loss >= HEAVY ? 1 : 0;
		Entity by = source.getEntity();
		switch (landed) {
			case LEFT_LEG, RIGHT_LEG, FRONT_LEGS, HIND_LEGS, LEGS ->
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LEG_SLOW_TICKS, level), by);
			case LEFT_ARM, RIGHT_ARM, WINGS ->
				victim.addEffect(new MobEffectInstance(landed == BodyPart.WINGS ? MobEffects.SLOWNESS : MobEffects.WEAKNESS, ARM_WEAK_TICKS, level), by);
			default -> {
			}
		}
	}
}
