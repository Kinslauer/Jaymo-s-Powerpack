package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.particles.ParticleOptions;

/** The spore motes of the glow-wood creatures, as Fx's typed motes (never recoloured dust). */
public final class GlowFx {
	/** A pale fungal spore, and the cold light of a glowcap's. */
	public static final int SPORE = 0xC9D9A0, GLOW = 0x6BF2E3;

	private GlowFx() {
	}

	public static ParticleOptions spore() {
		return Fx.mote(SPORE, 0.7F);
	}

	public static ParticleOptions glowSpore() {
		return Fx.mote(GLOW, 0.8F);
	}
}
