package com.jaymospowers.power.web;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The five blows of Webbed Fists ({@link WebStrength#fistsOn}): a jab, a kick, an uppercut, a twist and a crush, in that order
 * on one foe, each coming within {@link WebStrength#FISTS_SLOW_TICKS} of the last. Each hit is the plain punch plus a rider
 * here (more damage and a shove that grow with the blow), each has its own whole-body pose for everyone to see, a foe already
 * wrapped in web is staggered harder, and the crush throws it. Called from the fists' hit in {@link WebStrength}.
 */
public final class WebFistCombo {
	/** Five blows in all. */
	public static final int BLOWS = 5;
	/** The poses of the five, in order. */
	private static final int[] POSES = {WebPose.FIST_JAB, WebPose.FIST_KICK, WebPose.FIST_UPPER, WebPose.FIST_TWIST, WebPose.FIST_CRUSH};
	/** The damage each blow adds to the punch itself, and how far (blocks/tick) it shoves the foe back and up. */
	static final float[] EXTRA_DAMAGE = {0.0F, 1.5F, 2.0F, 2.5F, 4.0F};
	static final double[] SHOVE = {0.0, 0.35, 0.15, 0.45, 0.0};
	static final double[] LIFT = {0.0, 0.0, 0.5, 0.0, 0.0};
	/** A foe wrapped in web takes this much more of each blow's extra damage and shove, and is slowed and weakened for this long (ticks). */
	static final float WEBBED_MORE = 1.5F;
	static final int STAGGER_TICKS = 30;
	/** The crush throws the foe this fast along your look (blocks/tick) and up. */
	static final double THROW = 1.5, THROW_LIFT = 0.45;

	private WebFistCombo() {
	}

	/** Blow {@code n} (1 to {@link #BLOWS}) of the combo has just landed on {@code target}. */
	static void blow(ServerLevel level, ServerPlayer player, LivingEntity target, int n) {
		int i = Math.min(Math.max(n, 1), BLOWS) - 1;
		boolean webbed = Webbed.layers(target) > 0;
		float more = webbed ? WEBBED_MORE : 1.0F;
		WebPower.pose(player, POSES[i], i % 2 == 0 ? 1 : -1);
		Vec3 look = player.getLookAngle();
		Vec3 flat = look.multiply(1.0, 0.0, 1.0);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		if (EXTRA_DAMAGE[i] > 0.0F && target.isAlive()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, player.damageSources().mobAttack(player), EXTRA_DAMAGE[i] * more);
		}
		if (!target.isAlive()) {
			return;
		}
		Vec3 at = target.getBoundingBox().getCenter();
		if (i == BLOWS - 1) {
			// the crush: out of any web it was bound in, and thrown
			WebStrength.unbind(target);
			WebHaul.push(target, flat.scale(THROW * (webbed ? 1.15 : 1.0)).add(0.0, THROW_LIFT, 0.0));
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 22, 0.4, 0.4, 0.4, 0.15);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3F, 0.7F);
			com.jaymospowers.core.Shakes.send(player, 0.6F, at);
		} else if (SHOVE[i] > 0.0 || LIFT[i] > 0.0) {
			Vec3 side = i == 3 ? new Vec3(-flat.z, 0.0, flat.x).scale(player.getRandom().nextBoolean() ? 1.0 : -1.0) : flat;
			WebHaul.push(target, target.getDeltaMovement().add(side.scale(SHOVE[i] * more)).add(0.0, LIFT[i] * more, 0.0));
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6 + 2 * i, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.8F + 0.1F * i);
		}
		if (webbed) {
			// staggered harder: slowed and weakened, its mind off the fight
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 2));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 1));
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
	}
}
