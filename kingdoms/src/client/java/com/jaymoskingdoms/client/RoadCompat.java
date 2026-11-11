package com.jaymoskingdoms.client;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The player's own roads through the Retaking from Jaymo's Powers ({@code com.jaymospowers.client.StoryRoads}: the Z
 * Fighter's sagas, the Chrono's road), by reflection, since Kingdoms never compiles against Powers. Without Powers, or if
 * its shape ever changes, there are simply no Powers roads.
 */
final class RoadCompat {
	static final String ROADS = "com.jaymospowers.client.StoryRoads";
	private static boolean looked;
	private static @Nullable Method keys, title, goals, hasScreen, open;

	private RoadCompat() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		try {
			Class<?> roads = Class.forName(ROADS);
			keys = roads.getMethod("keys");
			title = roads.getMethod("title", String.class);
			goals = roads.getMethod("goals", String.class);
			hasScreen = roads.getMethod("hasScreen", String.class);
			open = roads.getMethod("open", String.class, Screen.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			keys = title = goals = hasScreen = open = null;
		}
	}

	/** The player's Powers roads, by key. */
	@SuppressWarnings("unchecked")
	static List<String> keys() {
		look();
		try {
			return keys == null ? List.of() : (List<String>)keys.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	/** Road {@code key}'s name. */
	static Component title(String key) {
		look();
		try {
			return title == null ? Component.empty() : (Component)title.invoke(null, key);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Component.empty();
		}
	}

	/** Its goals: done or not, and the line. */
	@SuppressWarnings("unchecked")
	static List<Map.Entry<Boolean, Component>> goals(String key) {
		look();
		try {
			return goals == null ? List.of() : (List<Map.Entry<Boolean, Component>>)goals.invoke(null, key);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	/** Whether road {@code key} has its own screen. */
	static boolean hasScreen(String key) {
		look();
		try {
			return hasScreen != null && (Boolean)hasScreen.invoke(null, key);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Opens road {@code key}'s own screen, coming back to {@code parent}. */
	static void open(String key, Screen parent) {
		look();
		try {
			if (open != null) {
				open.invoke(null, key, parent);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// no road screen: stay where we are
		}
	}
}
