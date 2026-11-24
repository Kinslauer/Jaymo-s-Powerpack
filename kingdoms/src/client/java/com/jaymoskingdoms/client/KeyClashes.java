package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * their clashes isn't asked again. A join with no clash doesn't count.
 */
public final class KeyClashes {
	/** How many joins the pop-up may show on, ever. */
	static final int TIMES = 2;
	/** How many clashes the pop-up names before "and N more". */
	static final int NAMED = 3;
	/** How long the pop-up stays (ms). */
	static final long SHOWN_MS = 12000L;
	private static final String FILE = "jaymoskingdoms-key-clashes.txt";
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
				if (all[i].same(all[j])) {
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

	private static int shown(Path file) {
		try {
			return Files.exists(file) ? Integer.parseInt(Files.readAllLines(file).get(0).trim()) : 0;
		} catch (IOException | RuntimeException e) {
			return 0;
		}
	}
}
