package com.jaymoskingdoms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.101) Nobody hugs an Emberborn ({@link Heritage.Kind#EMBERBORN}, a villager's or a player's blood) without fire
 * resistance: their skin is too hot to hold. Whoever isn't safe from fire (not Emberborn themselves, not fireproof, no
 * Fire Resistance) is burned and pulls away, and the hug never happens. It goes for a player hugging a villager through
 * MCA's menu (either way round: an Emberborn player burns a villager they hug, {@code mixin/mca/McaDialogueMixin}) and
 * for villagers hugging each other or their partners ({@link Neighbours}; they mostly know better than to try,
 * {@link #TRY_ANYWAY}).
 */
public final class EmberHugs {
	/** How long the burned one burns (seconds), the scald on top of it, and how hard they pull back. */
	static final int BURN_SECONDS = 3;
	static final float BURN_DAMAGE = 2.0F;
	static final double RECOIL = 0.6;
	/** How often two villagers who would burn each other try to hug anyway (they mostly just talk instead). */
	static final float TRY_ANYWAY = 0.25F;

	private EmberHugs() {
	}

	/** {@code e} is Emberborn: too hot to hold. */
	public static boolean hot(LivingEntity e) {
		return VillagerHeritage.of(e) == Heritage.Kind.EMBERBORN;
	}

	/** {@code e} can hold an Emberborn unharmed: Emberborn themselves, fireproof, or under Fire Resistance. */
	public static boolean safe(LivingEntity e) {
		return hot(e) || e.fireImmune() || e.hasEffect(MobEffects.FIRE_RESISTANCE);
	}

	/** Which of {@code a} and {@code b} a hug between them would burn (the one who isn't safe from the other's heat), or null. */
	public static @Nullable LivingEntity burned(LivingEntity a, LivingEntity b) {
		if (hot(b) && !safe(a)) {
			return a;
		}
		if (hot(a) && !safe(b)) {
			return b;
		}
		return null;
	}

	/** {@code burned} tried to hug {@code hot}: a hiss and a flare, they catch fire, take a scald and pull away. */
	public static void burn(ServerLevel level, LivingEntity hot, LivingEntity burned) {
		hot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		burned.igniteForSeconds(BURN_SECONDS);
		burned.hurtServer(level, level.damageSources().onFire(), BURN_DAMAGE);
		burned.knockback(RECOIL, hot.getX() - burned.getX(), hot.getZ() - burned.getZ(), level.damageSources().onFire(), 0.0F);
		burned.syncVelocity = true;
		double x = (hot.getX() + burned.getX()) * 0.5, y = burned.getY() + burned.getBbHeight() * 0.6, z = (hot.getZ() + burned.getZ()) * 0.5;
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, 0.25, 0.3, 0.25, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.4F);
	}
}
