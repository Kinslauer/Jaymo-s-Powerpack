package com.jaymoskingdoms.client;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The player's own road through the Retaking, from Jaymo's Powers ({@code com.jaymospowers.client.StoryRoads}: the Z
 * Fighter's sagas for now), by reflection, since Kingdoms never compiles against Powers. Without Powers, or if its shape
 * ever changes, there is simply no road.
 */
final class RoadCompat {
	static final String ROADS = "com.jaymospowers.client.StoryRoads";
	private static boolean looked;
	private static @Nullable Method title, goals, open;

	private RoadCompat() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		try {
			Class<?> roads = Class.forName(ROADS);
			title = roads.getMethod("title");
			goals = roads.getMethod("goals");
			open = roads.getMethod("open", Screen.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			title = goals = open = null;
		}
	}

	/** The road's name, or null if the player has none. */
	static @Nullable Component title() {
		look();
		try {
			return title == null ? null : (Component)title.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Its goals: done or not, and the line. */
	@SuppressWarnings("unchecked")
	static List<Map.Entry<Boolean, Component>> goals() {
		look();
		try {
			return goals == null ? List.of() : (List<Map.Entry<Boolean, Component>>)goals.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	/** Opens the road's own screen, coming back to {@code parent}. */
	static void open(Screen parent) {
		look();
		try {
			if (open != null) {
				open.invoke(null, parent);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// no road screen: stay where we are
		}
	}
}
