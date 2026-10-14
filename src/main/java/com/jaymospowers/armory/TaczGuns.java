package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.10.41) Our flintlocks in TaCZ: the musket, blunderbuss and flintlock pistol are a gunpack of our own
 * ({@code assets/jaymospowers/custom/jaymos_flintlocks}, made by tools/gen_tacz_guns.py), handed to TaCZ through its
 * API so it copies it into {@code .minecraft/tacz} beside its default pack. Through reflection, so Jaymo's Powers
 * still runs without TaCZ. TaCZ's own modern guns, ammo and attachments are taken off its benches
 * ({@code tinkering/Uncraftable}). As TaCZ items the guns are {@code tacz:modern_kinetic_gun} with
 * {@code GunId: "jaymospowers:musket"} in their custom data (Kingdoms hands them out that way).
 */
public final class TaczGuns {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");
	static final String PACK = "/assets/" + JaymosPowers.MOD_ID + "/custom/jaymos_flintlocks";

	private TaczGuns() {
	}

	public static void init() {
		if (!LOADED) {
			return;
		}
		try {
			Class.forName("com.tacz.guns.api.resource.ResourceManager")
				.getMethod("registerExportResource", Class.class, String.class)
				.invoke(null, JaymosPowers.class, PACK);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't hand our flintlocks to TaCZ", e);
		}
	}
}
