package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * (Andy, 2026-11-23: "the pop up should rise for any clash, to help the player resolve them. it should only play twice ever";
 * then: "the bind clashing tool tip should appear per bind and only when you first press it after you spawn")
 * The first time in a visit that you press a key two bindings share, a pop-up names that key and what's bound to it and
 * asks to rebind one in Controls. Each key's pop-up shows {@link #TIMES} times at most, ever: the counts are kept in a file
 * in the config folder, so someone happy with their clashes isn't asked again. Keys our own mods share on purpose
 * ({@link #ON_PURPOSE}, each used only in its own moment: a power's key and a class's, riding and not) are left out while
 * both are on their defaults.
 */
public final class KeyClashes {
	/** How many times each key's pop-up may show, ever. */
	static final int TIMES = 2;
	/** How long the pop-up stays (ms). */
	static final long SHOWN_MS = 12000L;
	private static final String FILE = "jaymoskingdoms-key-clashes.txt";
	/** Pairs of our own bindings that share a default key on purpose (Andy: "have it ignore those"). */
	private static final Set<String> ON_PURPOSE = Set.of(
		pair("key.jaymoskingdoms.imbue", "key.jaymoskingdoms.trick"),
		pair("key.jaymoskingdoms.imbue", "key.jaymospowers.rig"),
		pair("key.jaymoskingdoms.trick", "key.jaymospowers.rig"),
		pair("key.jaymoskingdoms.dragon_breath", "key.jaymospowers.ability_1"),
		pair("key.jaymoskingdoms.valor", "key.jaymospowers.shove"),
		pair("key.jaymoskingdoms.follower_orders", "key.jaymospowers.swallowed"),
		pair("key.jaymoskingdoms.ash_rot", "key.jaymospowers.laser"),
		pair("key.jaymoskingdoms.skills", "key.jaymospowers.web_arm"),
		pair("key.jaymoskingdoms.whistle", "key.jaymospowers.web_mantle"),
		pair("key.jaymoskingdoms.night_dash", "key.jaymospowers.web_goo_spit"),
		pair("key.jaymoskingdoms.night_pound", "key.jaymospowers.web_goo_puddle"),
		pair("key.jaymoskingdoms.frost_prison", "key.jaymospowers.web_goo_wall"),
		pair("key.jaymoskingdoms.werewolf_shift", "key.jaymospowers.web_arm_use"),
		pair("key.jaymospowers.limbo_leave", "key.jaymospowers.skip_cinematic"),
		// the medallion's marks have the pad to themselves while the board is up (MedallionNumpadMixin)
		pair("key.jaymoskingdoms.medallion_mark_8", "key.jaymoskingdoms.plan_layer_up"),
		pair("key.jaymoskingdoms.medallion_mark_2", "key.jaymoskingdoms.plan_layer_down"));
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(SHOWN_MS);
	/** Keys already pressed this visit (shown or not), by {@link KeyMapping#saveString()}. */
	private static final Set<String> PRESSED = new HashSet<>();

	private KeyClashes() {
	}

	static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> PRESSED.clear());
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.player == null) {
				return;
			}
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (mapping.isDown() && !mapping.isUnbound() && PRESSED.add(mapping.saveString())) {
					pressed(minecraft, mapping);
				}
			}
		});
	}

	/** The first press this visit of {@code pressed}'s key: a pop-up if that key clashes and its pop-up hasn't shown twice yet. */
	private static void pressed(Minecraft minecraft, KeyMapping pressed) {
		List<KeyMapping> onKey = new ArrayList<>();
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping.same(pressed)) {
				onKey.add(mapping);
			}
		}
		if (!clashes(onKey)) {
			return;
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		Map<String, Integer> shown = shown(file);
		String key = pressed.saveString();
		int times = shown.getOrDefault(key, 0);
		if (times >= TIMES) {
			return;
		}
		MutableComponent names = Component.empty();
		for (int i = 0; i < onKey.size(); i++) {
			if (i > 0) {
				names.append(Component.literal(i == onKey.size() - 1 ? " and " : ", "));
			}
			names.append(Component.translatable(onKey.get(i).getName()));
		}
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST,
			Component.translatable("message.jaymoskingdoms.key_clash.title", pressed.getTranslatedKeyMessage()),
			Component.translatable("message.jaymoskingdoms.key_clash.bound", names)
				.append(Component.literal(" ")).append(Component.translatable("message.jaymoskingdoms.key_clash.how")));
		shown.put(key, times + 1);
		StringBuilder text = new StringBuilder("# Jaymo's Kingdoms: how many times each key's clash pop-up has shown (it stops at "
			+ TIMES + "). Delete this file to see them again.\n");
		shown.forEach((k, n) -> text.append(k).append('=').append(n).append('\n'));
		try {
			Files.writeString(file, text);
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't write {}", file, e);
		}
	}

	/** Whether any two of the bindings on one key clash (not a pair of ours that share it on purpose). */
	private static boolean clashes(List<KeyMapping> onKey) {
		for (int i = 0; i < onKey.size(); i++) {
			for (int j = i + 1; j < onKey.size(); j++) {
				KeyMapping a = onKey.get(i);
				KeyMapping b = onKey.get(j);
				if (!(a.isDefault() && b.isDefault() && ON_PURPOSE.contains(pair(a.getName(), b.getName())))) {
					return true;
				}
			}
		}
		return false;
	}

	private static String pair(String a, String b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** Each key's count from the file; the old single-count file reads as none. */
	private static Map<String, Integer> shown(Path file) {
		Map<String, Integer> shown = new LinkedHashMap<>();
		try {
			if (Files.exists(file)) {
				for (String line : Files.readAllLines(file)) {
					int eq = line.indexOf('=');
					if (eq > 0 && !line.startsWith("#")) {
						shown.put(line.substring(0, eq).trim(), Integer.parseInt(line.substring(eq + 1).trim()));
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't read {}", file, e);
		}
		return shown;
	}
}
