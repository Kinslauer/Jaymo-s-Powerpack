package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/**
 * The parasite from dirty water: instead of a straight potion hit, it ticks damage at random - slow,
 * survivable, a reason to boil water rather than a death sentence. It lasts two in-game days and deals a point of
 * damage on random ticks (roughly once every 15 seconds). A mug of hot chocolate clears it ({@link Thirst#consumed}).
 */
public final class ParasiteEffect extends MobEffect {
	/** How long the parasite lasts (ticks): two in-game days, so curing it is worth the walk back to a fire. */
	static final int TICKS = 24_000 * 2;

	/** Damage ticks roll once in this many ticks on average: about once every 15 seconds. */
	static final int ROLL_EVERY = 20 * 15;

	/** Damage per roll, plus one per amplifier. */
	static final float DAMAGE = 1.0F;

	/** The damage type the parasite's damage is recorded as: data/jaymoskitchen/damage_type/parasite.json. */
	static final ResourceKey<DamageType> PARASITE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("parasite"));

	public static Holder<MobEffect> PARASITE;

	private static final Random RANDOM = new Random();

	private ParasiteEffect() {
		super(MobEffectCategory.HARMFUL, 0x556B2F);
	}

	static void register() {
		PARASITE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
				ResourceKey.create(Registries.MOB_EFFECT, JaymosKitchen.id("parasite")),
				new ParasiteEffect());
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return RANDOM.nextInt(ROLL_EVERY) == 0;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		// slow and survivable: random ticks of damage, but never the killing blow
		float damage = DAMAGE + amplifier;
		if (entity.getHealth() > damage) {
			DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PARASITE_DAMAGE));
			entity.hurtServer(level, source, damage);
		}
		return true;
	}
}
