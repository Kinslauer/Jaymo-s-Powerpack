package com.jaymospowers.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * No idle camera while a fishing line is out, or while you're in bed. Andy: "idle cam kicks in during fishing and throws
 * you off your bobber. maybe it could swap shots but keep the bobber within them? disable it completely when fishing if
 * not", and later "stop the idlecam while sleeping so it doesnt disturb this shot" (the night view of Jaymo's Kingdoms
 * circling the house). The idle camera is the pack's Ji AFK Cinematic. Its own fishing mode plays its usual shots, which
 * leave the bobber, and only cuts to the bobber when a fish swims up; no setting of its keeps the bobber in frame. So,
 * only when that mod is loaded: its fishing cinematic is kept switched off, and each tick a line is out or you lie in
 * bed counts as activity, so the plain idle one doesn't start either (and one already playing ends). Reached by
 * reflection through its public methods and config field, so nothing of it is needed to build, and a version that
 * renames them just leaves the idle camera as it was.
 */
public final class AfkCinematicFishing {
	private static @Nullable Method registerActivity;
	private static @Nullable Method getConfig;
	private static @Nullable Field fishingCinematic;

	private AfkCinematicFishing() {
	}

	static void init() {
		if (!FabricLoader.getInstance().isModLoaded("ji-afk-cinematic")) {
			return;
		}
		try {
			registerActivity = Class.forName("com.ji.afkcinematic.afk.AFKDetector").getMethod("registerActivity");
			getConfig = Class.forName("com.ji.afkcinematic.config.ConfigManager").getMethod("getConfig");
			fishingCinematic = Class.forName("com.ji.afkcinematic.config.ModConfig").getField("fishingCinematicEnabled");
		} catch (ReflectiveOperationException | LinkageError e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Ji AFK Cinematic changed: its idle camera isn't kept off while fishing ({})", e.toString());
			return;
		}
		ClientTickEvents.START_CLIENT_TICK.register(AfkCinematicFishing::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (registerActivity == null || getConfig == null || fishingCinematic == null) {
			return;
		}
		try {
			Object config = getConfig.invoke(null);
			if (config != null && fishingCinematic.getBoolean(config)) {
				fishingCinematic.setBoolean(config, false);
			}
			if (minecraft.player != null && (minecraft.player.fishing != null || minecraft.player.isSleeping())) {
				registerActivity.invoke(null);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Ji AFK Cinematic: couldn't keep its idle camera off while fishing or sleeping ({})", e.toString());
			registerActivity = null;
		}
	}
}
