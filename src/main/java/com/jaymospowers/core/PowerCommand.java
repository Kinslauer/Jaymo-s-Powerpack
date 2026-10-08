package com.jaymospowers.core;

import com.jaymospowers.api.Power;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /power set <targets> <power>}, {@code /power clear <targets>}, {@code /power awaken <targets>},
 * {@code /power level <targets> <1-4>} (how many abilities are unlocked), {@code /power get <target>}. Gamemasters only.
 */
public final class PowerCommand {
	private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(
		Component.translatable("commands.jaymospowers.unknown"));

	private PowerCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("power")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("set")
				.then(Commands.argument("targets", EntityArgument.players())
					.then(Commands.argument("power", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Powers.all().stream().map(p -> p.id().getPath()), b))
						.executes(c -> {
							Power power = Powers.byName(StringArgumentType.getString(c, "power"));
							if (power == null) {
								throw UNKNOWN.create();
							}
							Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
							players.forEach(p -> Powers.set(p, power));
							c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.set", power.name(), players.size()), true);
							return players.size();
						}))))
			.then(Commands.literal("clear")
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(c -> {
						Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
						players.forEach(p -> Powers.set(p, null));
						c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.clear", players.size()), true);
						return players.size();
					})))
			.then(Commands.literal("awaken")
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(c -> {
						Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
						players.forEach(Powers::awaken);
						return players.size();
					})))
			.then(Commands.literal("level")
				.then(Commands.argument("targets", EntityArgument.players())
					.then(Commands.argument("abilities", IntegerArgumentType.integer(1, 4))
						.executes(c -> {
							int abilities = IntegerArgumentType.getInteger(c, "abilities");
							Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
							players.forEach(p -> Mastery.setLevel(p, abilities));
							c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.level", abilities, players.size()), true);
							return players.size();
						}))))
			.then(Commands.literal("get")
				.then(Commands.argument("target", EntityArgument.player())
					.executes(c -> {
						ServerPlayer player = EntityArgument.getPlayer(c, "target");
						Power power = Powers.of(player);
						Integer left = player.getAttached(Powers.AWAKEN_IN);
						c.getSource().sendSuccess(() -> power != null
							? Component.translatable("commands.jaymospowers.get", player.getDisplayName(), power.name(), Mastery.unlocked(player), Mastery.points(player))
							: Component.translatable("commands.jaymospowers.get_none", player.getDisplayName(), left == null ? "?" : String.valueOf(left / 20)), false);
						return power != null ? 1 : 0;
					}))));
	}
}
