package com.jaymoskitchen.climate;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Powers' Wet and Soaked effects ({@code core/wet/Wet}), read by their ids so Kitchen never needs Powers: with
 * Powers loaded they say how wet a body is; without it {@link #known} is false and body heat keeps its own reckoning.
 */
public final class Wetness {
	static final Identifier WET = Identifier.fromNamespaceAndPath("jaymospowers", "wet"),
		SOAKED = Identifier.fromNamespaceAndPath("jaymospowers", "soaked");
	private static @Nullable Holder<MobEffect> wet, soaked;
	private static boolean looked;

	private Wetness() {
	}

	private static void look() {
		if (!looked) {
			looked = true;
			wet = BuiltInRegistries.MOB_EFFECT.get(WET).orElse(null);
			soaked = BuiltInRegistries.MOB_EFFECT.get(SOAKED).orElse(null);
		}
	}

	/** Whether Powers' wetness is there to ask. */
	public static boolean known() {
		look();
		return wet != null && soaked != null;
	}

	public static boolean wet(LivingEntity body) {
		look();
		return wet != null && body.hasEffect(wet) || soaked(body);
	}

	public static boolean soaked(LivingEntity body) {
		look();
		return soaked != null && body.hasEffect(soaked);
	}
}
