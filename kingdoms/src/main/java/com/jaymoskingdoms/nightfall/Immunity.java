package com.jaymoskingdoms.nightfall;

import com.jaymoskingdoms.Vampires;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * The cure for the plague, brewed from a Cursed Tusk ({@link Nightfall}): while it lasts the plague can't take hold
 * ({@link Nightfall#infect}, {@link Atrophy}), now and then it clears hunger, wither or poison, and it mends a heart every few
 * seconds. To the undead and to vampires it's poison: they catch fire instead (an Immunity Arrow is the hunter's
 * weapon). It does not cure a vampire: that's still the priest's quest.
 */
final class Immunity extends MobEffect {
	/** Every this many ticks, this chance of clearing one of hunger, wither or poison. */
	static final int CLEANSE_EVERY = 80;
	static final float CLEANSE_CHANCE = 0.5F;
	/** A heart (this much health) every this many ticks. */
	static final int MEND_EVERY = 100;
	static final float MEND = 2.0F;
	/** How long it burns the undead (seconds). */
	static final float BURN_SECONDS = 6.0F;

	Immunity() {
		super(MobEffectCategory.BENEFICIAL, 0xF2D25C);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		if (unholy(mob)) {
			// (it burned them as it went in; it does them no good)
			return false;
		}
		if (mob.tickCount % CLEANSE_EVERY == 0 && mob.getRandom().nextFloat() < CLEANSE_CHANCE) {
			if (!mob.removeEffect(MobEffects.POISON) && !mob.removeEffect(MobEffects.WITHER)) {
				mob.removeEffect(MobEffects.HUNGER);
			}
		}
		if (mob.tickCount % MEND_EVERY == 0 && mob.getHealth() < mob.getMaxHealth()) {
			mob.heal(MEND);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return tickCount % 20 == 0;
	}

	@Override
	public void onEffectAdded(LivingEntity mob, int amplifier) {
		mob.removeEffect(Nightfall.ATROPHY);
		if (mob.level() instanceof ServerLevel level && unholy(mob)) {
			mob.igniteForSeconds(BURN_SECONDS);
			level.sendParticles(ParticleTypes.WAX_OFF, mob.getX(), mob.getY(0.6), mob.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
		}
	}

	/** The undead, Night Bleeders and their bats, and every vampire who isn't half human. */
	static boolean unholy(LivingEntity mob) {
		return mob.is(EntityTypeTags.UNDEAD) || Nightfall.vampiric(mob) || Vampires.isVampire(mob) && !Vampires.dhampir(mob);
	}
}
