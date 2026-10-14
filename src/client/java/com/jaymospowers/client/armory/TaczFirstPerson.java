package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * TaCZ guns (our flintlocks) in first person beside First-person Model (tr7zw). Both hook the start of drawing the
 * first-person hand: TaCZ to draw the gun with its own arms, sights and animations, First-person Model to cancel the
 * vanilla hands and show your body instead. First-person Model went first, so TaCZ never drew: you saw the body's
 * third-person arm with the gun down at the hip, nothing animated and aiming couldn't bring it up. First-person Model
 * keeps a list of items that get the usual first-person hands ("autoVanillaHands", for maps and Create's potato cannon);
 * TaCZ's gun item goes on it, so with a gun in hand the body hides its arms and TaCZ draws the gun.
 * <p>
 * Reached by reflection, so neither is a compile dependency: {@code FirstPersonModelCore.instance.getConfig()
 * .autoVanillaHands} and {@code getLogicHandler().reloadAutoVanillaHandsSettings()}. The config set isn't saved unless
 * the player saves First-person Model's settings screen, and then keeping the gun on it is right anyway.
 */
public final class TaczFirstPerson {
	/** Every TaCZ gun is this one item, told apart by its GunId (armory/TaczGuns). */
	private static final String GUN_ITEM = "tacz:modern_kinetic_gun";

	private TaczFirstPerson() {
	}

	public static void init() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("tacz") && loader.isModLoaded("firstperson")) {
			// after First-person Model has loaded its config, before its first-tick reload of the list (which reads it)
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> addGunToVanillaHands());
		}
	}

	@SuppressWarnings("unchecked")
	private static void addGunToVanillaHands() {
		try {
			Class<?> core = Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore");
			Object instance = core.getField("instance").get(null);
			Object config = core.getMethod("getConfig").invoke(instance);
			Set<String> items = (Set<String>)config.getClass().getField("autoVanillaHands").get(config);
			if (items.add(GUN_ITEM)) {
				Object logic = core.getMethod("getLogicHandler").invoke(instance);
				logic.getClass().getMethod("reloadAutoVanillaHandsSettings").invoke(logic);
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't put TaCZ guns on First-person Model's vanilla-hands list; guns may sit low in first person", e);
		}
	}
}
