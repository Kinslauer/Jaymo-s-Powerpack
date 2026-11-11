package com.jaymospowers.client;

import com.jaymospowers.client.power.zfighter.ZFighterStoryScreen;
import com.jaymospowers.core.Powers;
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
import org.jspecify.annotations.Nullable;

/**
 * The player's own road through the Retaking (Jaymo's Kingdoms' story), for its Story tab, which reaches here by
 * reflection (Kingdoms never compiles against Powers). One road for now: the Z Fighter's, the three sagas folded in
 * (Ashes, then Sky and Crown). The tab lists its goals as plain lines and opens the saga screen ({@link #open}) to pick
 * one up or give it up.
 * <p>
 * The contract Kingdoms calls: {@link #title()}, {@link #goals()}, {@link #open(Screen)}.
 */
public final class StoryRoads {
	private StoryRoads() {
	}

	/** The local player's road's name, or null if they have none. */
	public static @Nullable Component title() {
		Player player = Minecraft.getInstance().player;
		if (player != null && Powers.has(player, ZFighterPower.INSTANCE)) {
			return Component.translatable("road.jaymospowers.zfighter");
		}
		return null;
	}

	/** The road's goals, in order: each done or not, and its line. */
	public static List<Map.Entry<Boolean, Component>> goals() {
		List<Map.Entry<Boolean, Component>> goals = new ArrayList<>();
		Player player = Minecraft.getInstance().player;
		if (player == null || !Powers.has(player, ZFighterPower.INSTANCE)) {
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

	/** Opens the saga screen (start, difficulty, give up, side jobs), coming back to {@code parent}. */
	public static void open(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new ZFighterStoryScreen(parent));
	}
}
