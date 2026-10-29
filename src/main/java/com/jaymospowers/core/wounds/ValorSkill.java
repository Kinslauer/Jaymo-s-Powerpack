package com.jaymospowers.core.wounds;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.7) Jaymo's Kingdoms' Valor, asked by reflection: a coated blow (Armament, or a shot loosed coated) isn't turned
 * aside by magic. Chrono's intangibility, Darktide's untouchable flurry, Smoke's vapour and a Flame Avatar's flame all let
 * it land ({@code com.jaymoskingdoms.Valor.pierces}). Without Kingdoms (or an older one), nothing pierces.
 */
public final class ValorSkill {
	private static final @Nullable Method PIERCES;

	static {
		Method pierces = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				pierces = Class.forName("com.jaymoskingdoms.Valor").getMethod("pierces", DamageSource.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				// (an older Kingdoms: no Valor)
			}
		}
		PIERCES = pierces;
	}

	private ValorSkill() {
	}

	/** Whether {@code source} is a coated blow, which magic doesn't turn aside. */
	public static boolean pierces(DamageSource source) {
		if (PIERCES == null) {
			return false;
		}
		try {
			return (boolean) PIERCES.invoke(null, source);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
