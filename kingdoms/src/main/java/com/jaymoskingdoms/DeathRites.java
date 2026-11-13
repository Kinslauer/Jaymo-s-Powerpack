package com.jaymoskingdoms;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The death and land rites (Andy's picks E3 to E5) as spells, for {@link NewSpells} to send a cast to: calling back (the fallen,
 * within a minute), keeping (a body fresh for days) and rebirth (a new body, at a grove). Also where the systems behind them
 * start: the graves, the groves and their old druids, the clerics' calling and tending.
 */
public final class DeathRites {
	/** The spells here. */
	public static final Set<String> SPELLS = Set.of("calling_back", "keeping", "rebirth");

	private DeathRites() {
	}

	static void register() {
		BodyKeeping.register();
		Graves.register();
		Groves.register();
		Rebirths.register();
		CallingBack.register();
	}

	static boolean ours(String spell) {
		return SPELLS.contains(spell);
	}

	static boolean cast(String spell, ServerLevel level, Player player, float power) {
		return switch (spell) {
			case "calling_back" -> CallingBack.cast(level, player);
			case "keeping" -> BodyKeeping.cast(level, player);
			case "rebirth" -> Rebirths.cast(level, player);
			default -> false;
		};
	}
}
