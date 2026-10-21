package com.jaymoskingdoms.nightfall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The plague a Night Bleeder's bite carries ({@link Nightfall}): a weak poison that never kills on its own, and every
 * blow taken while it's in you may leave you weak a moment. Whatever dies with it in its blood rises as a vampire bat
 * ({@link Nightfall#rise}). Bitten again by a Night Bleeder, it worsens to level II ({@link NightBleeder}).
 */
final class Atrophy extends MobEffect {
	/** Ticks between its pricks (level I; level II twice as often), and what each takes. */
	static final int PRICK_EVERY = 60;
	static final float PRICK = 1.0F;
	/** A blow taken: the chance of Weakness (level I, level II), and how long it lasts (ticks). */
	static final float WEAKEN_CHANCE = 0.35F, WEAKEN_CHANCE_II = 0.6F;
	static final int WEAKEN_TICKS = 40;

	Atrophy() {
		super(MobEffectCategory.HARMFUL, 0x5A1020);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		if (!Nightfall.catches(mob)) {
			// (an Immunity potion in them, or a vampire's own blood: it doesn't take)
			return false;
		}
		// (0.7.135) a Plague Doctor feels only half its pricks
		if (mob.getHealth() > 1.0F && !(Nightfall.plagueDoctor(mob) && mob.getRandom().nextFloat() >= Nightfall.PLAGUE_DOCTOR_TAKES)) {
			mob.hurtServer(level, mob.damageSources().magic(), PRICK);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		int interval = PRICK_EVERY >> Math.min(amplification, 2);
		return interval <= 0 || tickCount % interval == 0;
	}

	@Override
	public void onMobHurt(ServerLevel level, LivingEntity mob, int amplifier, DamageSource source, float damage) {
		float chance = (amplifier > 0 ? WEAKEN_CHANCE_II : WEAKEN_CHANCE) * (Nightfall.plagueDoctor(mob) ? Nightfall.PLAGUE_DOCTOR_TAKES : 1.0F);
		if (damage > 0.0F && mob.getRandom().nextFloat() < chance) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKEN_TICKS, amplifier > 0 ? 1 : 0));
		}
	}
}
