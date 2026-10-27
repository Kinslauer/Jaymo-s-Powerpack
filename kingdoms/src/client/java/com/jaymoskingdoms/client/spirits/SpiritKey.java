package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.spirits.Move;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * (0.7.238) The spirit key. Andy: "a mage hand type of ability that mimics the way stands work ... use of the mage hands calls
 * upon a mana spirit". {@code L} (the only free letter): a tap calls the spirit when it is not out, and casts the selected
 * move when it is, the ticks the key was held going with the cast so a charging move (the javelin) knows how hard to hit;
 * sneak and tap picks the next move; sneak and hold {@link #DISMISS_TICKS} sends it away. What is out is read from the synced
 * {@link Spirits#SPIRIT} state, so the key never needs to ask.
 * <p>
 * The server keeps the cooldowns ({@code Moves}) and does not sync them, so the HUD greys a move by a copy kept here: set when
 * a cast is sent. A cast that did nothing (no target) did not really start its cooldown, so the grey can run a little long.
 */
public final class SpiritKey {
	/** Sneak and hold the key this many ticks to send the spirit away. */
	static final int DISMISS_TICKS = 15;
	/**
	 * Once per install, vanilla's Advancements comes off L while the spirit key is on it (Advancements stays on the pause
	 * menu): with both on L the advancements screen opens on the press and the spirit never hears the release. Powers'
	 * KeyConflicts did this, but only on installs that hadn't already run its v4 pass, and not in a Kingdoms-only game.
	 */
	private static final String KEY_MARKER = "jaymoskingdoms-spirit-key-v1";

	private static KeyMapping key;
	private static boolean was, sneakPress, sent, keysChecked;
	private static int held;
	/** Game time each move's cooldown (as the HUD believes it) ends, by move id. */
	private static final Map<String, Long> READY = new HashMap<>();

	private SpiritKey() {
	}

	public static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.spirit", InputConstants.KEY_L,
			KeyMapping.Category.register(JaymosKingdoms.id("spirits"))));
		ClientTickEvents.END_CLIENT_TICK.register(SpiritKey::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			READY.clear();
			was = false;
			held = 0;
		});
	}

	/** Ticks left on the HUD's belief of {@code move}'s cooldown (0 ready). */
	static long wait(Move move, long now) {
		Long at = READY.get(move.id());
		return at == null ? 0L : Math.max(0L, at - now);
	}

	/** How many ticks the key has been held right now (0 if it isn't). */
	static int held() {
		return was ? held : 0;
	}

	private static void tick(Minecraft minecraft) {
		if (!keysChecked && minecraft.options != null) {
			keysChecked = true;
			advancementsOffL(minecraft);
		}
		LocalPlayer player = minecraft.player;
		while (key.consumeClick()) {
			// the press is read from isDown below; drain the clicks so they don't pile up
		}
		if (player == null || minecraft.gui.screen() != null) {
			was = false;
			held = 0;
			return;
		}
		boolean down = key.isDown();
		if (down && !was) {
			held = 0;
			sent = false;
			sneakPress = player.isShiftKeyDown();
		}
		if (down) {
			held++;
			if (sneakPress && !sent && held >= DISMISS_TICKS && Spirits.out(player)) {
				send(Spirits.DISMISS, 0, true);
				sent = true;
			}
		} else if (was && !sent) {
			boolean sneak = sneakPress || player.isShiftKeyDown();
			boolean out = Spirits.out(player);
			if (sneak) {
				if (out) {
					send(Spirits.NEXT, 0, true);
				}
			} else if (!out) {
				send(Spirits.SUMMON, 0, false);
			} else {
				cast(player, held);
			}
		}
		was = down;
	}

	private static void advancementsOffL(Minecraft minecraft) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(KEY_MARKER);
		if (Files.exists(marker)) {
			return;
		}
		KeyMapping advancements = minecraft.options.keyAdvancements;
		boolean moved = advancements.saveString().equals(key.saveString());
		if (moved) {
			advancements.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		try {
			Files.writeString(marker, "Jaymo's Kingdoms " + (moved ? "unbound Advancements (it shared the spirit key)" : "found no clash on the spirit key")
				+ ". Delete this file to check again.\n");
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}

	private static void cast(LocalPlayer player, int ticks) {
		SpiritState state = Spirits.state(player);
		Move move = state == null ? null : Spirits.selected(state);
		if (move != null) {
			long now = player.level().getGameTime();
			if (wait(move, now) <= 0L) {
				READY.put(move.id(), now + move.cooldown());
			}
		}
		send(Spirits.CAST, ticks, false);
	}

	private static void send(int action, int ticks, boolean sneak) {
		if (ClientPlayNetworking.canSend(Spirits.Key.TYPE)) {
			ClientPlayNetworking.send(new Spirits.Key(action, ticks, sneak));
		}
	}
}
