package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.ClimateCharms;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

/**
 * Jaymo's Kitchen's body heat (optional), reached only by reflection: the belt charms ({@link ClimateCharms}) are counted as
 * warm and cool clothes through {@code BodyHeat.addWorn}. Registered once when a server starts (before anyone's heat is
 * worked out); without Kitchen, or with one too old for it, nothing happens.
 */
public final class KitchenBodyHeat {
	private static boolean done;

	private KitchenBodyHeat() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (done) {
				return;
			}
			done = true;
			try {
				java.lang.reflect.Method addWorn = Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addWorn", boolean.class, ToDoubleFunction.class);
				addWorn.invoke(null, true, (ToDoubleFunction<Player>)ClimateCharms::warm);
				addWorn.invoke(null, false, (ToDoubleFunction<Player>)ClimateCharms::cool);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Jaymo's Kitchen is too old for the belt charms to count in body heat: {}", e.toString());
			}
		});
	}
}
