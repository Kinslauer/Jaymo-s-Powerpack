package com.jaymospowers.power.zfighter.backhome;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.18.56, SevTech picks: far-spice) The trip buffs of Jaymo's Kitchen's far-world dishes, as three plain effects the dishes give
 * (Kitchen finds them by id, {@code jaymospowers:far_breath} and the rest, so it needs no link to this class):
 * <ul>
 * <li><b>Far Breath</b>: on a world with no air, the eater breathes as if they carried a reserve ({@link #BREATH_TICKS}, a minute);</li>
 * <li><b>Far Ward</b>: Glacia's cold and Cinder's heat are held off ({@link #WARD_TICKS}, ten minutes);</li>
 * <li><b>Frugal Fire</b>: the next pod launch burns one fuel less, once ({@link #THRIFT_TICKS} to use it).</li>
 * </ul>
 * The checks sit in {@code Breath}, {@code Heat}, {@code BreathCompat} and {@code ZFighterSpace.go}.
 */
public final class FarFood {
	// ---- tuning (the dishes in Kitchen name these lengths themselves: keep them in step) ----
	public static final int BREATH_TICKS = 60 * 20, WARD_TICKS = 10 * 60 * 20, THRIFT_TICKS = 10 * 60 * 20;

	public static Holder<MobEffect> BREATH, WARD, THRIFT;

	private FarFood() {
	}

	/** A plain beneficial effect: it does nothing by itself, the world's rules look for it. */
	private static final class Meal extends MobEffect {
		Meal(int colour) {
			super(MobEffectCategory.BENEFICIAL, colour);
		}
	}

	static void init() {
		BREATH = register("far_breath", 0xBFE8FF);
		WARD = register("far_ward", 0xFFC27A);
		THRIFT = register("far_thrift", 0xF2E58A);
	}

	private static Holder<MobEffect> register(String id, int colour) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id(id)), new Meal(colour));
	}

	/** Whether {@code entity} breathes on a far meal. */
	public static boolean breathing(LivingEntity entity) {
		return BREATH != null && entity.hasEffect(BREATH);
	}

	/** Whether {@code entity} is warded from Glacia's cold and Cinder's heat. */
	public static boolean warded(LivingEntity entity) {
		return WARD != null && entity.hasEffect(WARD);
	}

	/** Whether the next launch is a thrifty one. */
	public static boolean thrifty(LivingEntity entity) {
		return THRIFT != null && entity.hasEffect(THRIFT);
	}

	/** The thrifty launch is spent. */
	public static void spendThrift(LivingEntity entity) {
		if (THRIFT != null) {
			entity.removeEffect(THRIFT);
		}
	}
}
