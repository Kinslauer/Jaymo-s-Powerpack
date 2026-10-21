package com.jaymospowers.aether.monster;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The four afflictions the Aether's monsters deal (Aether II calls them Webbed, Venom, Toxin and Electric Shock; here
 * they are plain mob effects, no buildup meter): webbed holds you in place, venom is a slow poison that can kill, toxin
 * is a fast poison that stops at half a heart, electric shock saps your strength and stings.
 */
public final class AetherEffects {
	// ---- tuning ----
	/** Webbed: movement and jump cut by this much (0.85 = 85 percent). */
	public static final double WEBBED_SLOW = 0.85;
	/** Venom hurts for 1 every this many ticks (halved per level); toxin the same, but it can't kill. */
	public static final int VENOM_INTERVAL = 30, TOXIN_INTERVAL = 12, SHOCK_INTERVAL = 25;
	/** Electric shock takes this much off your attack damage. */
	public static final double SHOCK_WEAKNESS = 3.0;

	public static Holder<MobEffect> WEBBED, VENOM, TOXIN, ELECTRIC_SHOCK;

	private AetherEffects() {
	}

	static void init() {
		WEBBED = register("webbed", new Webbed());
		VENOM = register("venom", new Hurting(0x8fd04a, VENOM_INTERVAL, false));
		TOXIN = register("toxin", new Hurting(0xb04ad0, TOXIN_INTERVAL, true));
		ELECTRIC_SHOCK = register("electric_shock", new Shock());
	}

	private static Holder<MobEffect> register(String id, MobEffect effect) {
		ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id(id));
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, key, effect);
	}

	/** Cobweb-grey: nearly rooted, no jumping. */
	private static final class Webbed extends MobEffect {
		Webbed() {
			super(MobEffectCategory.HARMFUL, 0xdcdce4, ParticleTypes.ITEM_COBWEB);
			this.addAttributeModifier(Attributes.MOVEMENT_SPEED, JaymosPowers.id("effect.webbed.speed"), -WEBBED_SLOW,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			this.addAttributeModifier(Attributes.JUMP_STRENGTH, JaymosPowers.id("effect.webbed.jump"), -1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	/** Venom (can kill) and toxin (never below half a heart): magic damage on a timer. */
	private static final class Hurting extends MobEffect {
		private final int interval;
		private final boolean spare;

		Hurting(int color, int interval, boolean spare) {
			super(MobEffectCategory.HARMFUL, color);
			this.interval = interval;
			this.spare = spare;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			if (!this.spare || entity.getHealth() > 1.0F) {
				entity.hurtServer(level, entity.damageSources().magic(), 1.0F);
			}
			return true;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			int every = this.interval >> amplifier;
			return every <= 0 || duration % every == 0;
		}
	}

	/** Crackling: weaker blows and a small sting now and then. */
	private static final class Shock extends MobEffect {
		Shock() {
			super(MobEffectCategory.HARMFUL, 0xe8e060, ParticleTypes.ELECTRIC_SPARK);
			this.addAttributeModifier(Attributes.ATTACK_DAMAGE, JaymosPowers.id("effect.electric_shock.weakness"), -SHOCK_WEAKNESS,
				AttributeModifier.Operation.ADD_VALUE);
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			entity.hurtServer(level, entity.damageSources().lightningBolt(), 1.0F);
			return true;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return duration % SHOCK_INTERVAL == 0;
		}
	}
}
