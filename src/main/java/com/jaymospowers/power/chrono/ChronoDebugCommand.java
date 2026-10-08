package com.jaymospowers.power.chrono;

import com.jaymospowers.core.Powers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug chrono echo}: one of your echoes turns up now (you need Chrono);</li>
 * <li>{@code /powerdebug chrono stasistime <seconds>}: sets your time spent in stopped/rewound time;</li>
 * <li>{@code /powerdebug chrono timers}: shows your pending echo and return timers and that time.</li>
 * </ul>
 */
final class ChronoDebugCommand {
	private ChronoDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("chrono")
				.then(Commands.literal("echo")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						if (!Powers.has(player, ChronoPower.INSTANCE)) {
							c.getSource().sendFailure(Component.literal("You don't have Chrono."));
							return 0;
						}
						ChronoEcho echo = ChronoEchoes.spawn(player, ChronoMemory.of(player));
						if (echo == null) {
							c.getSource().sendFailure(Component.literal("No room for an echo 16-28 blocks from you."));
							return 0;
						}
						c.getSource().sendSuccess(() -> Component.literal("An echo stirs at " + echo.blockPosition().toShortString() + "."), false);
						return 1;
					}))
				.then(Commands.literal("stasistime")
					.then(Commands.argument("seconds", IntegerArgumentType.integer(0, 1_000_000))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							int seconds = IntegerArgumentType.getInteger(c, "seconds");
							ChronoMemory.of(player).stoppedTime = seconds * 20;
							c.getSource().sendSuccess(() -> Component.literal("Time outside time: " + seconds + " s (needs "
								+ ChronoPower.ASCEND_STOPPED_TICKS / 20 + " s)."), false);
							return seconds;
						})))
				.then(Commands.literal("timers")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						ChronoMemory memory = ChronoMemory.of(player);
						c.getSource().sendSuccess(() -> Component.literal("Echoes due (ticks): " + memory.echoesDue + ", returns due: "
							+ memory.returnsDue + ", time outside time: " + memory.stoppedTime / 20 + " s, sighted: " + memory.sighted), false);
						return 1;
					}))));
	}
}
