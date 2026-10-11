package com.jaymospowers.power.resonance;

import com.jaymospowers.core.Fate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug resonance ready}: clears every Resonance cooldown;</li>
 * <li>{@code /powerdebug resonance shrieks <n>}: as if you'd set off {@code n} sculk shriekers (the corruption meter
 * jumps to n × {@value ResonanceFate#SHRIEKER_CORRUPTION}; 5 corrupts). Works in creative too.</li>
 * </ul>
 */
final class ResonanceDebugCommand {
	private ResonanceDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("resonance")
				.then(Commands.literal("ready")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						ResonancePower.set(player, ResonancePower.state(player).cleared());
						c.getSource().sendSuccess(() -> Component.literal("Resonance cooldowns cleared."), false);
						return 1;
					}))
				.then(Commands.literal("shrieks")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 5))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							int meter = Math.min(Fate.MAX_CORRUPTION, IntegerArgumentType.getInteger(c, "n") * ResonanceFate.SHRIEKER_CORRUPTION);
							Fate.State state = Fate.state(player);
							if (meter >= Fate.MAX_CORRUPTION) {
								if (!Fate.corrupt(player)) {
									c.getSource().sendFailure(Component.literal("Can't corrupt (no power, already corrupted, or purified)."));
									return 0;
								}
							} else {
								Fate.set(player, new Fate.State(state.ascended(), false, state.purified(), meter));
							}
							c.getSource().sendSuccess(() -> Component.literal("Corruption: " + Fate.corruption(player) + "/" + Fate.MAX_CORRUPTION), false);
							return 1;
						})))));
	}
}
