package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What each key does for a power right now, for the powers screen (H). Lang
 * {@code power.jaymospowers.<p>.key.<slot>} is the plain line; a state's own line, when the power has one, wins:
 * the power's own state first ({@link Power#statusSuffix}, e.g. {@code .symbiote_ascended}, then {@code .symbiote}),
 * then {@code .ascended_corrupted}, {@code .corrupted}, {@code .ascended}. An empty line means the key does nothing in
 * that state. Slots: {@code ability_1}-{@code ability_4} (R G V B), {@code mode} (Z), {@code ultimate} (C, only once
 * it answers), {@code special} (X). Every line gets the bound keys as arguments, so it can name them: %1$s-%4$s are
 * R G V B, %5$s Z, %6$s C and %7$s X.
 */
final class KeyGuide {
	static final int COLOR_TEXT = 0xFFFFFFFF;
	static final int COLOR_DIM = 0xFFA8A8B8;
	static final int COLOR_STARTER = 0xFFE8D8A0;
	static final int COLOR_ULTIMATE = 0xFFFFD54A;
	static final int COLOR_SPECIAL = 0xFF9FD8FF;
	/** The X key is shared: Web's symbiote menu, Wind's Tempest Kick and Cyclone. */
	private static final String SPECIAL_KEY = "key.jaymospowers.web_symbiote";

	/** One key's line: the key's name, what it does, and its colour. */
	record Line(Component key, Component text, int color) {
		Component full() {
			return Component.literal("[").append(key).append("] ").append(text);
		}
	}

	private KeyGuide() {
	}

	/**
	 * The lines for {@code power}. With a {@code player} (your own power) they follow its state and mastery;
	 * without one (another power's card in creative) they're the plain lines.
	 */
	static List<Line> lines(Power power, @Nullable Player player) {
		String base = "power." + power.id().getNamespace() + "." + power.id().getPath() + ".key.";
		List<String> suffixes = suffixes(power, player);
		Object[] args = keyNames();
		List<Line> lines = new ArrayList<>();
		boolean mastering = player != null && !player.isCreative();

		if (mastering && Mastery.starterOnly(player) && Language.getInstance().has(base + "starter")) {
			// a fresh awakening: only the starter, on its key (or always on, if it's passive)
			PowerKey key = power.starterKey();
			lines.add(new Line(key == null ? Component.translatable("screen.jaymospowers.passive") : name(JaymosPowersClient.keyMapping(key)),
				Component.translatable(base + "starter", args), COLOR_STARTER));
		}
		for (PowerKey key : PowerKey.values()) {
			Component text = resolve(base + key.id(), suffixes, args);
			if (text == null) {
				continue;
			}
			// your own power's locked abilities show greyed out
			boolean locked = mastering && !Mastery.isUnlocked(player, key);
			lines.add(new Line(name(JaymosPowersClient.keyMapping(key)),
				locked ? Component.translatable("screen.jaymospowers.locked") : text, locked ? COLOR_DIM : COLOR_TEXT));
		}
		add(lines, power, player, "mode", base, suffixes, args, JaymosPowersClient.modeKey(), COLOR_TEXT, mastering);
		// the C key shows up only once it answers (ascended; some powers' corruption too)
		if (player != null && (Fate.isAscended(player) || power.ultimateWhenCorrupted() && Fate.isCorrupted(player))) {
			add(lines, power, player, "ultimate", base, suffixes, args, JaymosPowersClient.ultimateKey(), COLOR_ULTIMATE, false);
		}
		add(lines, power, player, "special", base, suffixes, args, special(), COLOR_SPECIAL, mastering);
		return lines;
	}

	private static void add(List<Line> lines, Power power, @Nullable Player player, String slot, String base, List<String> suffixes,
			Object[] args, @Nullable KeyMapping mapping, int color, boolean mastering) {
		Component text = resolve(base + slot, suffixes, args);
		if (text == null || mapping == null) {
			return;
		}
		boolean locked = mastering && !power.slotUnlocked(player, slot);
		lines.add(new Line(name(mapping), locked ? Component.translatable("screen.jaymospowers.locked") : text, locked ? COLOR_DIM : color));
	}

	/** The lang suffixes to try for {@code player}'s state, most specific first, ending with the plain line. */
	private static List<String> suffixes(Power power, @Nullable Player player) {
		List<String> suffixes = new ArrayList<>();
		if (player != null) {
			String own = power.statusSuffix(player);
			if (own != null) {
				suffixes.add(own);
				// ".symbiote_ascended" falls back to ".symbiote"
				int cut = own.lastIndexOf('_');
				if (cut > 0) {
					suffixes.add(own.substring(0, cut));
				}
			}
			Fate.State fate = Fate.state(player);
			if (fate.ascended() && fate.corrupted()) {
				suffixes.add(".ascended_corrupted");
			}
			if (fate.corrupted()) {
				suffixes.add(".corrupted");
			}
			if (fate.ascended()) {
				suffixes.add(".ascended");
			}
		}
		suffixes.add("");
		return suffixes;
	}

	/** The most specific line for {@code key}, or null if there's none or it's empty (nothing in this state). */
	private static @Nullable Component resolve(String key, List<String> suffixes, Object[] args) {
		Language language = Language.getInstance();
		for (String suffix : suffixes) {
			if (language.has(key + suffix)) {
				return language.getOrDefault(key + suffix).isEmpty() ? null : Component.translatable(key + suffix, args);
			}
		}
		return null;
	}

	private static Object[] keyNames() {
		Object[] names = new Object[7];
		for (PowerKey key : PowerKey.values()) {
			names[key.ordinal()] = name(JaymosPowersClient.keyMapping(key));
		}
		names[4] = name(JaymosPowersClient.modeKey());
		names[5] = name(JaymosPowersClient.ultimateKey());
		KeyMapping special = special();
		names[6] = special == null ? Component.literal("X") : name(special);
		return names;
	}

	private static Component name(KeyMapping mapping) {
		return mapping.getTranslatedKeyMessage();
	}

	private static @Nullable KeyMapping special() {
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (SPECIAL_KEY.equals(mapping.getName())) {
				return mapping;
			}
		}
		return null;
	}
}
