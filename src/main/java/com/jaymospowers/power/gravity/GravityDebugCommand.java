package com.jaymospowers.power.gravity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug gravity kills <n>}: sets your deaths-by-weight count (ascension at {@value GravityPower#ASCEND_KILLS});</li>
 * <li>{@code /powerdebug gravity ready}: clears every Gravity cooldown;</li>
 * <li>{@code /powerdebug gravity levitate <seconds>}: gives you Levitation (to test the Collapse cure).</li>
 * </ul>
 */
final class GravityDebugCommand {
	private GravityDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("gravity")
				.then(Commands.literal("kills")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							int n = IntegerArgumentType.getInteger(c, "n");
							GravityPower.setKills(player, n);
							c.getSource().sendSuccess(() -> Component.literal("Deaths by weight: " + n + "/" + GravityPower.ASCEND_KILLS
								+ " (the next one counts if every ability is mastered)"), false);
							return 1;
						})))
				.then(Commands.literal("ready")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						player.setAttached(GravityPower.COOLDOWNS, GravityCooldowns.NONE);
						c.getSource().sendSuccess(() -> Component.literal("Gravity cooldowns cleared."), false);
						return 1;
					}))
				.then(Commands.literal("levitate")
					.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 120))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							int seconds = IntegerArgumentType.getInteger(c, "seconds");
							player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, seconds * 20, 0));
							return 1;
						})))));
	}
}
