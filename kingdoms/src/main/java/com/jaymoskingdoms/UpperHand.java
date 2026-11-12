package com.jaymoskingdoms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayList;
import java.util.List;

/**
 * The upper hand and bad footing (fifth edition's advantage and disadvantage, Andy's pick A1). No dice on screen: when
 * things favour whoever strikes, the blow lands harder and is likelier to be a telling one; when they don't, it lands
 * softer and may only glance. It works the same for players and everyone else.
 * <ul>
 * <li><b>The upper hand</b>: the one struck lies prone, crawling, tripped or downed, or is blinded, or the striker stands
 * well above them.</li>
 * <li><b>Bad footing</b>: the striker is waist-deep in water, on ice, poisoned or blinded, or (a player, with no way to
 * see in the dark) can't see the one they strike for darkness.</li>
 * </ul>
 * Both at once cancel out, and neither stacks: one is as good as three, as in the books. Other systems add their own
 * reasons ({@link #add}): frightened, charmed, netted, pack tactics and the rest.
 */
public final class UpperHand {
	/** With the upper hand, every blow this much harder, and this often a telling one this much harder again. */
	static final float EDGE = 1.15F, TELLING_CHANCE = 0.25F, TELLING = 1.5F;
	/** On bad footing, every blow this much softer, and this often it only glances (this share of it lands). */
	static final float FOOTING = 0.85F, GLANCE_CHANCE = 0.25F, GLANCE = 0.5F;
	/** Standing this much higher (blocks) than the one struck, within this reach (blocks), is the high ground. */
	static final double HIGH_GROUND = 1.5, HIGH_REACH = 8.0;
	/** Light this low or under (0-15) is darkness to a player who can't see in the dark. */
	static final int DARK = 3;
	/** Water this deep (blocks) at the striker's feet is wading. */
	static final double WADING = 0.6;

	/** Another reason for the upper hand (above 0) or bad footing (below 0) for {@code attacker} striking {@code target}. */
	@FunctionalInterface
	public interface Reason {
		int edge(LivingEntity attacker, LivingEntity target);
	}

	/** One who can see in the dark (a blood's darkvision, a potion), for the darkness test. */
	@FunctionalInterface
	public interface Sight {
		boolean seesInDark(LivingEntity who);
	}

	private static final List<Reason> REASONS = new ArrayList<>();
	private static final List<Sight> SIGHTS = new ArrayList<>();

	private UpperHand() {
	}

	/** Adds a reason for the upper hand or bad footing (frightened, netted, pack tactics...). */
	public static void add(Reason reason) {
		REASONS.add(reason);
	}

	/** Adds a way of seeing in the dark (darkvision). */
	public static void addSight(Sight sight) {
		SIGHTS.add(sight);
	}

	/** Whether {@code who} sees in the dark: night vision, or any of the added ways. */
	public static boolean seesInDark(LivingEntity who) {
		if (who.hasEffect(MobEffects.NIGHT_VISION)) {
			return true;
		}
		for (Sight s : SIGHTS) {
			if (s.seesInDark(who)) {
				return true;
			}
		}
		return false;
	}

	/** {@code attacker} striking {@code target}: 1 the upper hand, -1 bad footing, 0 neither (or both). */
	public static int net(LivingEntity attacker, LivingEntity target) {
		int up = 0, down = 0;
		if (lowered(target) || target.hasEffect(MobEffects.BLINDNESS)) {
			up++;
		}
		if (attacker.getY() - target.getY() >= HIGH_GROUND && attacker.distanceToSqr(target) <= HIGH_REACH * HIGH_REACH) {
			up++;
		}
		if (wading(attacker) || onIce(attacker) || attacker.hasEffect(MobEffects.POISON) || attacker.hasEffect(MobEffects.BLINDNESS)
			|| attacker instanceof Player && !seesInDark(attacker) && dark(target)) {
			down++;
		}
		for (Reason r : REASONS) {
			int e = r.edge(attacker, target);
			if (e > 0) {
				up++;
			} else if (e < 0) {
				down++;
			}
		}
		return up > 0 && down == 0 ? 1 : down > 0 && up == 0 ? -1 : 0;
	}

	/** Whether {@code attacker} has the upper hand on {@code target}. */
	public static boolean upper(LivingEntity attacker, LivingEntity target) {
		return net(attacker, target) > 0;
	}

	/** Lying prone, crawling, tripped, downed or asleep. */
	public static boolean lowered(LivingEntity body) {
		return body.isSleeping() || body.getPose() == Pose.SWIMMING && !body.isInWater() || PowersCompat.helpless(body)
			|| PowersCompat.tripped(body) || body instanceof Player p && PowersCompat.lowStance(p);
	}

	private static boolean wading(LivingEntity who) {
		return who.isInWater() && !who.isUnderWater() && who.getFluidHeight(FluidTags.WATER) >= WADING && !(who instanceof WaterAnimal)
			&& !(who instanceof Drowned);
	}

	private static boolean onIce(LivingEntity who) {
		return who.onGround() && who.getBlockStateOn().is(BlockTags.ICE);
	}

	private static boolean dark(LivingEntity target) {
		return target.level().getMaxLocalRawBrightness(target.blockPosition()) <= DARK && !target.isCurrentlyGlowing() && !target.isOnFire();
	}

	/** {@code target} taking {@code amount}: harder with the striker's upper hand, softer on bad footing. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target || source.is(DamageTypeTags.IS_EXPLOSION)
			|| !(source.getDirectEntity() == attacker || source.getDirectEntity() instanceof Projectile) || Senses.cooling(target, source)
			|| !(target.level() instanceof ServerLevel level)) {
			return amount;
		}
		int net = net(attacker, target);
		if (net == 0) {
			return amount;
		}
		var random = level.getRandom();
		if (net > 0) {
			amount *= EDGE;
			if (random.nextFloat() < TELLING_CHANCE) {
				amount *= TELLING;
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 1.1F);
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.3, 0.25, 0.2);
			}
			return amount;
		}
		amount *= FOOTING;
		if (random.nextFloat() < GLANCE_CHANCE) {
			amount *= GLANCE;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 0.8F);
		}
		return amount;
	}
}
