package com.jaymoskingdoms.wither;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * The Wither's own voice for its moves (tools/gen_wither_sounds.py: synthesised, nothing sampled or borrowed). Its
 * ambient, hurt, death, spawn and skull-throw sounds are the vanilla events, given our sounds in
 * assets/minecraft/sounds.json, so every Wither speaks with them.
 */
public final class WitherSounds {
	/** The beam's tell: a rising hum that builds for the whole tell. */
	public static final SoundEvent BEAM_CHARGE = register("wither.beam_charge");
	/** The beam going off: a hard crack and a tearing hiss. */
	public static final SoundEvent BEAM_FIRE = register("wither.beam_fire");
	/** The charge's tell: a three-throated roar. */
	public static final SoundEvent CHARGE_ROAR = register("wither.charge_roar");
	/** The rush itself: a rushing of air. */
	public static final SoundEvent CHARGE_RUSH = register("wither.charge_rush");
	/** Stunned by a shield: a dazed, falling groan. */
	public static final SoundEvent STUN = register("wither.stun");

	private WitherSounds() {
	}

	static void register() {
		// (the fields above register as the class loads)
	}

	private static SoundEvent register(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, JaymosKingdoms.id(name), SoundEvent.createVariableRangeEvent(JaymosKingdoms.id(name)));
	}
}
