package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The words a caster has settled on for the spells that take a choice: which word a Command carries, what a Suggestion
 * whispers, which weather the great rite asks for, and which spirit animal a familiar is bound in. They are picked on the
 * Magic screen (each button turns to the next option) and kept with the player.
 */
public final class Choices {
	/** The choices in the order the Magic screen shows them: the key, the spell that makes it matter, and the options. */
	public record Choice(String key, String spell, List<String> options) {
	}

	public static final List<Choice> ALL = List.of(
		new Choice("command", "command", List.of("flee", "drop", "grovel")),
		new Choice("suggestion", "suggestion", List.of("come", "wait", "leave", "sleep", "follow")),
		new Choice("weather", "control_weather", List.of("clear", "rain", "storm")),
		new Choice("familiar", "find_familiar", List.of("owl", "cat", "rat", "raven", "frog")));

	private static final AttachmentType<Map<String, String>> PICKED = AttachmentRegistry.create(JaymosKingdoms.id("spell_choices"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath());

	private Choices() {
	}

	static void register() {
		// the attachment is created above, so it is known on both sides at start-up
	}

	private static Choice of(String key) {
		for (Choice c : ALL) {
			if (c.key().equals(key)) {
				return c;
			}
		}
		throw new IllegalArgumentException(key);
	}

	/** What {@code player} has picked for {@code key}: the first option until they pick another. */
	public static String get(Player player, String key) {
		Choice c = of(key);
		String picked = player.getAttachedOrElse(PICKED, Map.of()).get(key);
		return picked != null && c.options().contains(picked) ? picked : c.options().getFirst();
	}

	/** Turns {@code key} to its next option. */
	static void cycle(ServerPlayer player, String key) {
		Choice c = of(key);
		int at = c.options().indexOf(get(player, key));
		Map<String, String> all = new HashMap<>(player.getAttachedOrElse(PICKED, Map.of()));
		all.put(key, c.options().get((at + 1) % c.options().size()));
		player.setAttached(PICKED, Map.copyOf(all));
	}

	/** The choices {@code player} can use, as "key=option" in the order shown. */
	static List<String> open(ServerPlayer player) {
		List<String> out = new java.util.ArrayList<>();
		for (Choice c : ALL) {
			if (NewSpells.knows(player, c.spell())) {
				out.add(c.key() + "=" + get(player, c.key()));
			}
		}
		out.addAll(Familiars.offers(player));
		return out;
	}
}
