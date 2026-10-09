package com.jaymospowers;

import com.jaymospowers.core.PowerEvents;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.chrono.ChronoPower;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.WindPower;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaymosPowers implements ModInitializer {
	public static final String MOD_ID = "jaymospowers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PowerEvents.register();
		// each power registers itself (and its items, entities, packets) from its own package
		WindPower.init();
		ArsenalPower.init();
		WebPower.init();
		ChronoPower.init();
		GravityPower.init();
		registerAttachments();
	}

	/**
	 * Attachment types register when their class first loads, which on a dedicated server can be long after the client
	 * has told the server which synced attachments it knows: any it hadn't loaded yet were never sent (no ascension,
	 * mastery or modes on the client). Loading every class that declares one at startup registers them all, both sides.
	 */
	private static void registerAttachments() {
		String[] holders = {
			"core.Affinity", "core.Fate", "core.Mastery", "core.Powers",
			"power.arsenal.ArsenalArmory", "power.arsenal.ArsenalPower", "power.arsenal.ArsenalReach", "power.arsenal.ArsenalRelics",
			"power.arsenal.ArsenalStash", "power.arsenal.HundredBlades",
			"power.chrono.ChronoEchoes", "power.chrono.ChronoMemory",
			"power.gravity.GravityPower",
			"power.web.WebAscension", "power.web.WebBridges", "power.web.WebPower", "power.web.WebSymbiote", "power.web.WebTendrils",
			"power.wind.WindModes", "power.wind.WindPower", "power.wind.WindStorm", "power.wind.WindTempest",
		};
		for (String holder : holders) {
			try {
				Class.forName("com.jaymospowers." + holder, true, JaymosPowers.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
