package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * (Andy, 2026-11-23: "the pop up should rise for any clash, to help the player resolve them. it should only play twice ever")
 * On joining a world, when any two key bindings share a key, a pop-up names the clashes and asks to rebind one in Controls.
 * It shows on {@link #TIMES} joins at most, ever: the count is kept in a file in the config folder, so someone happy with
 * their clashes isn't asked again. A join with no clash doesn't count. Keys our own mods share on purpose ({@link #ON_PURPOSE},
 * each used only in its own moment: a power's key and a class's, riding and not) are left out while both are on their defaults.
 */
public final class KeyClashes {
	/** How many joins the pop-up may show on, ever. */
	static final int TIMES = 2;
	/** How many clashes the pop-up names before "and N more". */
	static final int NAMED = 3;
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
	/** Whether this visit has been checked. */
	private static boolean checked;

	private KeyClashes() {
	}

	static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> checked = false);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (checked || minecraft.player == null) {
				return;
			}
			checked = true;
			check(minecraft);
		});
	}

	private static void check(Minecraft minecraft) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		int shown = shown(file);
		if (shown >= TIMES) {
			return;
		}
		List<Component> clashes = new ArrayList<>();
		KeyMapping[] all = minecraft.options.keyMappings;
		for (int i = 0; i < all.length; i++) {
			if (all[i].isUnbound()) {
				continue;
			}
			for (int j = i + 1; j < all.length; j++) {
				if (all[i].same(all[j]) && !(all[i].isDefault() && all[j].isDefault() && ON_PURPOSE.contains(pair(all[i].getName(), all[j].getName())))) {
					clashes.add(Component.translatable("message.jaymoskingdoms.key_clash.pair", all[i].getTranslatedKeyMessage(),
						Component.translatable(all[i].getName()), Component.translatable(all[j].getName())));
				}
			}
		}
		if (clashes.isEmpty()) {
			return;
		}
		net.minecraft.network.chat.MutableComponent message = Component.empty();
		for (int i = 0; i < Math.min(NAMED, clashes.size()); i++) {
			message.append(i == 0 ? Component.empty() : Component.literal(" ")).append(clashes.get(i));
		}
		if (clashes.size() > NAMED) {
			message.append(Component.literal(" ")).append(Component.translatable("message.jaymoskingdoms.key_clash.more", clashes.size() - NAMED));
		}
		message.append(Component.literal(" ")).append(Component.translatable("message.jaymoskingdoms.key_clash.how"));
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, Component.translatable("message.jaymoskingdoms.key_clash.title"), message);
		try {
			Files.writeString(file, (shown + 1) + "\n# Jaymo's Kingdoms: how many times the key clash pop-up has shown (it stops at " + TIMES
				+ "). Delete this file to see it again.\n");
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't write {}", file, e);
		}
	}

	private static String pair(String a, String b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	private static int shown(Path file) {
		try {
			return Files.exists(file) ? Integer.parseInt(Files.readAllLines(file).get(0).trim()) : 0;
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}
}
