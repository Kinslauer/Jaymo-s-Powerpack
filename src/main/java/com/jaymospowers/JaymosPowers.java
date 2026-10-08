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
	}
}
