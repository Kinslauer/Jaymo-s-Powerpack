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
 * <li>{@code /powerdebug zfighter foes}: lists every foe that has beaten you and the rematches still owed;
 * {@code foes clear} forgets them;</li>
 * <li>{@code /powerdebug zfighter tp <n>}: sets your Training Points;</li>
 * <li>{@code /powerdebug zfighter mastery <form> <percent>}: sets a form's mastery (1-3).</li>
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
				.then(Commands.literal("tp")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 10000000))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.setAttached(ZFighterTraining.DATA, ZFighterTraining.of(player).withTp(IntegerArgumentType.getInteger(c, "n")));
							c.getSource().sendSuccess(() -> Component.literal("TP: " + ZFighterTraining.of(player).tp()), false);
							return 1;
						})))
				.then(Commands.literal("mastery")
					.then(Commands.argument("form", IntegerArgumentType.integer(1, ZFighterTraining.FORMS))
						.then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								int form = IntegerArgumentType.getInteger(c, "form");
								player.setAttached(ZFighterTraining.DATA, ZFighterTraining.of(player).withMastery(form,
									IntegerArgumentType.getInteger(c, "percent") * 100));
								c.getSource().sendSuccess(() -> Component.literal("Form " + form + " mastery: "
									+ ZFighterTraining.of(player).mastery(form) / 100 + "%"), false);
								return 1;
							}))))
				.then(Commands.literal("foes")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						List<String> foes = player.getAttachedOrElse(ZFighterPower.DEFEATS, List.of());
						List<String> grudges = player.getAttachedOrElse(ZFighterPower.GRUDGES, List.of());
						c.getSource().sendSuccess(() -> Component.literal("Beaten by (" + foes.size() + "): " + String.join(", ", foes)
							+ "; rematches owed: " + String.join(", ", grudges)
							+ (Powers.has(player, ZFighterPower.INSTANCE) ? "" : " (not a Z Fighter)")), false);
						return 1;
					})
					.then(Commands.literal("clear")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.removeAttached(ZFighterPower.DEFEATS);
							player.removeAttached(ZFighterPower.GRUDGES);
							c.getSource().sendSuccess(() -> Component.literal("Foes forgotten."), false);
							return 1;
						})))));
	}
}
