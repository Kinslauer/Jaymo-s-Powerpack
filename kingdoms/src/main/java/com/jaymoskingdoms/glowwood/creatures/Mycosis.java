package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.GlowHavenLife;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The spore-rot's Kingdoms side. The rot itself (an effect that wears the body down, and its cures) belongs to Jaymo's
 * Powers' medicine ({@code jaymospowers:mycosis}, with {@code jaymospowers:spore_ward} that turns it away), found here by
 * id so Kingdoms needs no Powers to build or run: without Powers nothing here infects. What dies carrying it rises as a
 * shambler, never within {@link #SAFE_FROM_HAVENS} blocks of a Sylvan haven.
 */
public final class Mycosis {
	private static final Identifier MYCOSIS = Identifier.fromNamespaceAndPath("jaymospowers", "mycosis");
	private static final Identifier WARD = Identifier.fromNamespaceAndPath("jaymospowers", "spore_ward");
	/** The dead do not rise this near a Sylvan haven (blocks). */
	public static final double SAFE_FROM_HAVENS = 64.0;

	private Mycosis() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && carries(entity) && entity.is(GlowCreatures.MYCOSIS_RISERS)
				&& !GlowHavenLife.sylvanNear(level, entity.blockPosition(), SAFE_FROM_HAVENS)) {
				ShamblerEntity.riseFrom(level, entity);
			}
		});
	}

	public static @Nullable Holder<MobEffect> holder() {
		return BuiltInRegistries.MOB_EFFECT.get(MYCOSIS).orElse(null);
	}

	private static @Nullable Holder<MobEffect> ward() {
		return BuiltInRegistries.MOB_EFFECT.get(WARD).orElse(null);
	}

	/** Whether {@code entity} carries the rot now. */
	public static boolean carries(LivingEntity entity) {
		Holder<MobEffect> rot = holder();
		return rot != null && entity.hasEffect(rot);
	}

	/** Fungal things, and anyone under a spore ward (Circle of Spores druids keep one), cannot carry it. */
	public static boolean isImmune(LivingEntity entity) {
		Holder<MobEffect> ward = ward();
		return entity.is(GlowCreatures.FUNGAL) || ward != null && entity.hasEffect(ward);
	}

	/** The rot as an effect to hand to a cloud; null without Powers. */
	public static @Nullable MobEffectInstance instance(int ticks, int amplifier) {
		Holder<MobEffect> rot = holder();
		return rot == null ? null : new MobEffectInstance(rot, ticks, amplifier);
	}

	/** {@code target} infected for {@code ticks}, unless it is immune (or there is no Powers). */
	public static void infect(LivingEntity target, int ticks, int amplifier, @Nullable Entity by) {
		MobEffectInstance rot = instance(ticks, amplifier);
		if (rot != null && !isImmune(target)) {
			target.addEffect(rot, by);
		}
	}

	/** A spore ward on {@code entity} for {@code ticks} (nothing without Powers). */
	public static void ward(LivingEntity entity, int ticks) {
		Holder<MobEffect> ward = ward();
		if (ward != null) {
			entity.addEffect(new MobEffectInstance(ward, ticks, 0, true, false, true));
		}
	}
}
