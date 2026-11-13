package com.jaymoskingdoms.inhabitants;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /youth}: how Hermes (Andy's bot, YOUTH) moves its wood-wisp ({@link YouthWisp}) and speaks through it. Hermes
 * sends these over the server console (RCON), from a script that only builds these commands; operators can use them too.
 * Gamemaster level, so ordinary players can't.
 *
 * <ul>
 * <li>{@code /youth summon <player>}: the player's wisp comes in beside them (or moves there).</li>
 * <li>{@code /youth say <player> <text>}: the wisp gestures and the text goes to that player as Youth's words.</li>
 * <li>{@code /youth sayall <text>}: Youth's words to everyone online.</li>
 * <li>{@code /youth dismiss <player>}: the player's wisp goes home.</li>
 * <li>{@code /youth status}: one line of JSON, the players online, where they are, and who has a wisp.</li>
 * </ul>
 */
public final class YouthCommand {
	/** The longest line it will say (characters); a longer one is cut. */
	static final int MAX_LINE = 256;

	private YouthCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("youth")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("summon").then(Commands.argument("player", EntityArgument.player()).executes(YouthCommand::summon)))
			.then(Commands.literal("say").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("text", StringArgumentType.greedyString()).executes(YouthCommand::say))))
			.then(Commands.literal("sayall").then(Commands.argument("text", StringArgumentType.greedyString()).executes(YouthCommand::sayAll)))
			.then(Commands.literal("dismiss").then(Commands.argument("player", EntityArgument.player()).executes(YouthCommand::dismiss)))
			.then(Commands.literal("status").executes(YouthCommand::status))));
	}

	private static int summon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		if (YouthWisp.summon(player) == null) {
			c.getSource().sendFailure(Component.literal("Youth couldn't come"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Youth came to " + player.getName().getString()), false);
		return 1;
	}

	private static int say(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		YouthWisp.say(player, clean(StringArgumentType.getString(c, "text")));
		c.getSource().sendSuccess(() -> Component.literal("Youth spoke to " + player.getName().getString()), false);
		return 1;
	}

	private static int sayAll(CommandContext<CommandSourceStack> c) {
		Component line = YouthWisp.line(clean(StringArgumentType.getString(c, "text")));
		for (ServerPlayer player : c.getSource().getServer().getPlayerList().getPlayers()) {
			player.sendSystemMessage(line);
			if (YouthWisp.has(player)) {
				YouthWisp.gesture(player);
			}
		}
		c.getSource().sendSuccess(() -> Component.literal("Youth spoke to everyone"), false);
		return 1;
	}

	private static int dismiss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		boolean had = YouthWisp.dismiss(player);
		c.getSource().sendSuccess(() -> Component.literal(had ? "Youth went home" : "No wisp to send home"), false);
		return had ? 1 : 0;
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		String json = YouthWisp.status(c.getSource().getServer().getPlayerList().getPlayers());
		c.getSource().sendSuccess(() -> Component.literal(json), false);
		return 1;
	}

	/** Formatting codes and control characters out, and no longer than {@link #MAX_LINE}. */
	static String clean(String text) {
		String out = text.replace('§', ' ').replaceAll("\\p{Cntrl}", " ").strip();
		return out.length() > MAX_LINE ? out.substring(0, MAX_LINE) : out;
	}
}
