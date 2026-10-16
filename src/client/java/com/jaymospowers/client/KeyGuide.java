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
 * R G V B, %5$s Z, %6$s C and %7$s X. Each line's {@code .name} (e.g. {@code .key.ability_1.corrupted.name}) is its
 * short name, for the screen's rows and the unlock title; a line without one shows its text cut short.
 */
final class KeyGuide {
	static final int COLOR_TEXT = 0xFFFFFFFF;
	static final int COLOR_DIM = 0xFFA8A8B8;
	static final int COLOR_STARTER = 0xFFE8D8A0;
	static final int COLOR_ULTIMATE = 0xFFFFD54A;
	static final int COLOR_SPECIAL = 0xFF9FD8FF;
	/** The X key is shared: Web's symbiote menu, Wind's Tempest Kick and Cyclone. */
	private static final String SPECIAL_KEY = "key.jaymospowers.web_symbiote";
	/** (0.11.87) The Rig's lines (shown while you wear or have parked a Rig). */
	static final int COLOR_RIG = 0xFF5FE6FF;

	/** One key's line: the key's name, the ability's short name (null if it has none), what it does, its colour. */
	record Line(Component key, @Nullable Component name, Component text, int color, boolean locked) {
		Component full() {
			return Component.literal("[").append(key).append("] ").append(text);
		}
	}

	/** A resolved line: its text and short name. */
	private record Resolved(Component text, @Nullable Component name) {
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
				shortName(base + "starter"), Component.translatable(base + "starter", args), COLOR_STARTER, false));
		}
		for (PowerKey key : PowerKey.values()) {
			Resolved line = resolve(base + key.id(), suffixes, args);
			if (line == null) {
				continue;
			}
			// your own power's locked abilities show greyed out
			boolean locked = mastering && !Mastery.isUnlocked(player, key);
			lines.add(locked ? locked(name(JaymosPowersClient.keyMapping(key)))
				: new Line(name(JaymosPowersClient.keyMapping(key)), line.name(), line.text(), COLOR_TEXT, false));
		}
		add(lines, power, player, "mode", base, suffixes, args, JaymosPowersClient.modeKey(), COLOR_TEXT, mastering);
		// the C key shows up only once it answers (ascended; some powers' corruption too)
		// (or when the power's own state has its own C line: Darktide's Drown at night)
		String own = player == null ? null : power.statusSuffix(player);
		if (player != null && (Fate.isAscended(player) || power.ultimateWhenCorrupted() && Fate.isCorrupted(player)
			|| own != null && Language.getInstance().has(base + "ultimate" + own))) {
			add(lines, power, player, "ultimate", base, suffixes, args, JaymosPowersClient.ultimateKey(), COLOR_ULTIMATE, false);
		}
		add(lines, power, player, "special", base, suffixes, args, special(), COLOR_SPECIAL, mastering);
		if (player != null) {
			rig(lines, player);
		}
		return lines;
	}

	/**
	 * (0.11.87) The Rig's keys, after the power's, while a piece is worn or the suit is parked: lang
	 * {@code rig.jaymospowers.key.<line>} (and {@code .name}), with the same key arguments plus the Rig key (%8$s).
	 * A line only shows once the Mind's level has reached it (Andy: nothing shows until you find it).
	 */
	private static void rig(List<Line> lines, Player player) {
		if (!com.jaymospowers.rig.Rig.wearsAny(player) && !com.jaymospowers.rig.RigCall.parked(player)) {
			return;
		}
		Object[] names = keyNames();
		Object[] args = java.util.Arrays.copyOf(names, 8);
		Component rigKey = name(com.jaymospowers.client.rig.RigClient.rigKey());
		args[7] = rigKey;
		int level = com.jaymospowers.rig.Rig.level(player);
		boolean powerless = com.jaymospowers.core.Powers.of(player) == null;
		if (powerless) {
			rigLine(lines, "hover", (Component)names[0], args, 1, level);
			rigLine(lines, "palm", (Component)names[1], args, 1, level);
			rigLine(lines, "launcher", (Component)names[2], args, 1, level);
			rigLine(lines, "report", (Component)names[3], args, 1, level);
			rigLine(lines, "scan_hold", Component.translatable("rig.jaymospowers.key.hold", names[3]), args, com.jaymospowers.rig.Rig.SCAN_LEVEL, level);
			rigLine(lines, "call", (Component)names[5], args, com.jaymospowers.rig.Rig.CALL_LEVEL, level);
		} else {
			rigLine(lines, "rig", rigKey, args, 1, level);
			rigLine(lines, "rig_next", Component.translatable("rig.jaymospowers.key.sneak", rigKey), args, 1, level);
		}
		rigLine(lines, "lock", Component.translatable("rig.jaymospowers.key.hold", rigKey), args, com.jaymospowers.rig.Rig.LOCK_LEVEL, level);
		rigLine(lines, "flight", Component.translatable("rig.jaymospowers.key.double", name(Minecraft.getInstance().options.keyJump)), args,
			com.jaymospowers.rig.Rig.FLIGHT_LEVEL, level);
	}

	private static void rigLine(List<Line> lines, String id, Component key, Object[] args, int needs, int level) {
		String base = "rig.jaymospowers.key." + id;
		Component name = shortName(base);
		if (level >= needs) {
			lines.add(new Line(key, name, Component.translatable(base, args), COLOR_RIG, false));
		}
	}

	private static void add(List<Line> lines, Power power, @Nullable Player player, String slot, String base, List<String> suffixes,
			Object[] args, @Nullable KeyMapping mapping, int color, boolean mastering) {
		Resolved line = resolve(base + slot, suffixes, args);
		if (line == null || mapping == null) {
			return;
		}
		boolean locked = mastering && !power.slotUnlocked(player, slot);
		lines.add(locked ? locked(name(mapping)) : new Line(name(mapping), line.name(), line.text(), color, false));
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

	private static Line locked(Component key) {
		return new Line(key, Component.translatable("screen.jaymospowers.locked_name"), Component.translatable("message.jaymospowers.locked"), COLOR_DIM, true);
	}

	/** The short name for the line {@code key}, or null if it has none. */
	private static @Nullable Component shortName(String key) {
		return Language.getInstance().has(key + ".name") ? Component.translatable(key + ".name") : null;
	}

	/** The most specific line for {@code key}, or null if there's none or it's empty (nothing in this state). */
	private static @Nullable Resolved resolve(String key, List<String> suffixes, Object[] args) {
		Language language = Language.getInstance();
		for (String suffix : suffixes) {
			if (language.has(key + suffix)) {
				return language.getOrDefault(key + suffix).isEmpty() ? null
					: new Resolved(Component.translatable(key + suffix, args), shortName(key + suffix));
			}
		}
		return null;
	}

	/** The bound keys, as arguments for key lines: R G V B, Z, C, X. */
	static Object[] keyNames() {
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
