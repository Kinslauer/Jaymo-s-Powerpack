package com.jaymospowers.power.darktide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug darktide flow <n>}: sets your Flow;</li>
 * <li>{@code /powerdebug darktide parries <n>}: sets your katana parries toward ascension (ascends at the threshold if it can);</li>
 * <li>{@code /powerdebug darktide ready}: clears every cooldown;</li>
 * <li>{@code /powerdebug darktide cure | corrupt}: cures the corruption until nightfall, or ends the cure.</li>
 * </ul>
 */
final class DarktideDebugCommand {
	private DarktideDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("darktide")
				.then(Commands.literal("flow")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, DarktideFlow.MAX))
						.executes(c -> {
							ServerPlayer player = darktide(c);
							if (player == null) {
								return 0;
							}
							int n = IntegerArgumentType.getInteger(c, "n");
							DarktidePower.live(player).flow = n;
							c.getSource().sendSuccess(() -> Component.literal("Flow: " + n), false);
							return n;
						})))
				.then(Commands.literal("parries")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
						.executes(c -> {
							ServerPlayer player = darktide(c);
							if (player == null) {
								return 0;
							}
							int n = IntegerArgumentType.getInteger(c, "n");
							player.setAttached(DarktideState.SAVE, DarktideState.save(player).withParries(Math.max(0, n - 1)));
							// the last one counts the usual way, so the whispers and the ascension follow their rules
							if (n > 0) {
								DarktidePower.countParry(player);
							}
							c.getSource().sendSuccess(() -> Component.literal("Katana parries: " + DarktideState.save(player).parries() + " / "
								+ DarktidePower.ASCEND_PARRIES + " (counted only with all four abilities unlocked)"), false);
							return n;
						})))
				.then(Commands.literal("ready")
					.executes(c -> {
						ServerPlayer player = darktide(c);
						if (player == null) {
							return 0;
						}
						DarktidePower.clearCooldowns(player);
						DarktidePower.live(player).armsSwapAt = 0L;
						c.getSource().sendSuccess(() -> Component.literal("Every Darktide cooldown is ready."), false);
						return 1;
					}))
				.then(Commands.literal("cure")
					.executes(c -> {
						ServerPlayer player = darktide(c);
						if (player == null) {
							return 0;
						}
						player.setAttached(DarktideState.SAVE, DarktideState.save(player).withCuredUntil(
							DarktidePower.nextNightfall(player.level().getOverworldClockTime())));
						c.getSource().sendSuccess(() -> Component.literal("Darktide cured until nightfall."), false);
						return 1;
					}))
				.then(Commands.literal("corrupt")
					.executes(c -> {
						ServerPlayer player = darktide(c);
						if (player == null) {
							return 0;
						}
						player.setAttached(DarktideState.SAVE, DarktideState.save(player).withCuredUntil(0L));
						c.getSource().sendSuccess(() -> Component.literal("Darktide corrupted again."), false);
						return 1;
					}))));
	}

	private static ServerPlayer darktide(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!DarktidePower.has(player)) {
			c.getSource().sendFailure(Component.literal("You don't have Darktide."));
			return null;
		}
		return player;
	}
}
