package com.jaymospowers.client;

import com.jaymospowers.client.power.zfighter.ZFighterStoryScreen;
import com.jaymospowers.core.PowerRoads;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.ChronoPower;
import com.jaymospowers.power.chrono.ChronoRoad;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSagas;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The player's own roads through the Retaking (Jaymo's Kingdoms' story), for its Story tab, which reaches here by
 * reflection (Kingdoms never compiles against Powers). The Z Fighter's road (the three sagas folded in: Ashes, then Sky
 * and Crown), with the saga screen to pick one up or give it up ({@link #open}); the Chrono's road
 * ({@link ChronoRoad}); and the Web Slinger's, the Xenoform's and the Arsenal's ({@link PowerRoads}). The tab lists their goals as plain lines.
 * <p>
 * The contract Kingdoms calls: {@link #keys()}, {@link #title(String)}, {@link #goals(String)}, {@link #hasScreen(String)},
 * {@link #open(String, Screen)}.
 */
public final class StoryRoads {
	static final String ZFIGHTER = "zfighter", CHRONO = "chrono";

	private StoryRoads() {
	}

	/** The local player's roads, by key. */
	public static List<String> keys() {
		List<String> keys = new ArrayList<>();
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			if (Powers.has(player, ZFighterPower.INSTANCE)) {
				keys.add(ZFIGHTER);
			}
			if (Powers.has(player, ChronoPower.INSTANCE)) {
				keys.add(CHRONO);
			}
			for (String road : List.of("web", "xeno", "arsenal")) {
				if (PowerRoads.yours(player, road)) {
					keys.add(road);
				}
			}
		}
		return keys;
	}

	/** Road {@code key}'s name. */
	public static Component title(String key) {
		return Component.translatable("road.jaymospowers." + key);
	}

	/** Whether road {@code key} has its own screen ({@link #open}). */
	public static boolean hasScreen(String key) {
		return ZFIGHTER.equals(key);
	}

	/** Road {@code key}'s goals, in order: each done or not, and its line. */
	public static List<Map.Entry<Boolean, Component>> goals(String key) {
		List<Map.Entry<Boolean, Component>> goals = new ArrayList<>();
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return goals;
		}
		if (CHRONO.equals(key)) {
			for (String step : ChronoRoad.STEPS) {
				goals.add(new AbstractMap.SimpleEntry<>(ChronoRoad.done(player, step), Component.translatable("road.jaymospowers.chrono." + step)));
			}
			return goals;
		}
		List<String> steps = PowerRoads.STEPS.get(key);
		if (steps != null) {
			for (String step : steps) {
				goals.add(new AbstractMap.SimpleEntry<>(PowerRoads.done(player, key, step), Component.translatable("road.jaymospowers." + key + "." + step)));
			}
			return goals;
		}
		if (!ZFIGHTER.equals(key) || !Powers.has(player, ZFighterPower.INSTANCE)) {
			return goals;
		}
		ZFighterSagas.Data data = ZFighterSagas.of(player);
		for (int i = 0; i < ZFighterSagas.sagaCount(); i++) {
			Component title = Component.translatable("saga.jaymospowers." + ZFighterSagas.key(i) + ".title");
			if (data.finished(i)) {
				goals.add(new AbstractMap.SimpleEntry<>(true, title));
			} else if (data.active() && data.saga() == i) {
				ZFighterSagas.Step step = ZFighterSagas.step(i, data.step());
				Component what = ZFighterSagas.objective(step, data.difficulty());
				int have = ZFighterSagas.have(player, data), need = ZFighterSagas.need(step, data.difficulty());
				Component line = have >= 0 && need > 0
					? Component.translatable("road.jaymospowers.step_count", title, data.step() + 1, ZFighterSagas.stepCount(i), what, Math.min(have, need), need)
					: Component.translatable("road.jaymospowers.step", title, data.step() + 1, ZFighterSagas.stepCount(i), what);
				goals.add(new AbstractMap.SimpleEntry<>(false, line));
			} else if (ZFighterSagas.unlocked(data, i)) {
				goals.add(new AbstractMap.SimpleEntry<>(false, Component.translatable("road.jaymospowers.ready", title)));
			} else {
				goals.add(new AbstractMap.SimpleEntry<>(false, Component.translatable("road.jaymospowers.later")));
			}
		}
		return goals;
	}

	/** Opens road {@code key}'s own screen (the sagas: start, difficulty, give up, side jobs), coming back to {@code parent}. */
	public static void open(String key, Screen parent) {
		if (ZFIGHTER.equals(key)) {
			Minecraft.getInstance().gui.setScreen(new ZFighterStoryScreen(parent));
		}
	}
}
