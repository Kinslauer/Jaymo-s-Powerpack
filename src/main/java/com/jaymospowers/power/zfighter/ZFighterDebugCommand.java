package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug zfighter ready}: clears every cooldown and fills the ki;</li>
 * <li>{@code /powerdebug zfighter hardship <n>}: sets the hardship ({@value ZFighterBattle#SUPER_HARDSHIP} opens the way
 * to Super);</li>
 * <li>{@code /powerdebug zfighter foes}: lists every foe that has beaten you; {@code foes clear} forgets them.</li>
 * </ul>
 */
final class ZFighterDebugCommand {
	private ZFighterDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("zfighter")
				.then(Commands.literal("ready")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						ZFighterPower.set(player, ZFighterPower.state(player).cleared());
						ZFighterPower.live(player).ki = ZFighterPower.maxKi(player);
						c.getSource().sendSuccess(() -> Component.literal("Z Fighter cooldowns cleared, ki full."), false);
						return 1;
					}))
				.then(Commands.literal("hardship")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.setAttached(ZFighterPower.HARDSHIP, IntegerArgumentType.getInteger(c, "n"));
							c.getSource().sendSuccess(() -> Component.literal("Hardship: " + ZFighterPower.hardship(player) + "/"
								+ ZFighterBattle.SUPER_HARDSHIP), false);
							return 1;
						})))
				.then(Commands.literal("foes")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						List<String> foes = player.getAttachedOrElse(ZFighterPower.DEFEATS, List.of());
						c.getSource().sendSuccess(() -> Component.literal("Beaten by (" + foes.size() + "): " + String.join(", ", foes)
							+ (Powers.has(player, ZFighterPower.INSTANCE) ? "" : " (not a Z Fighter)")), false);
						return 1;
					})
					.then(Commands.literal("clear")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.removeAttached(ZFighterPower.DEFEATS);
							c.getSource().sendSuccess(() -> Component.literal("Foes forgotten."), false);
							return 1;
						})))));
	}
}
