package com.jaymoskingdoms.forms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.371) {@code /forms}, for ops (permission 2): {@code teach <players> <form>}, {@code forget <players> [form]} (every
 * form when none is named) and {@code list <players>} (what each knows, and at what rank).
 */
public final class FormsCommands {
	private FormsCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var form = Commands.argument("form", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Forms.ids(), b));
		var players = Commands.argument("players", EntityArgument.players());
		dispatcher.register(Commands.literal("forms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("teach").then(players.then(form.executes(FormsCommands::teach))))
			.then(Commands.literal("forget").then(Commands.argument("players", EntityArgument.players())
				.executes(c -> forget(c, null))
				.then(Commands.argument("form", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Forms.ids(), b))
					.executes(c -> forget(c, StringArgumentType.getString(c, "form"))))))
			.then(Commands.literal("list").then(Commands.argument("players", EntityArgument.players()).executes(FormsCommands::list))));
	}

	private static int teach(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String id = StringArgumentType.getString(c, "form");
		if (Forms.get(id) == null) {
			c.getSource().sendFailure(Component.translatable("command.jaymoskingdoms.forms.unknown", id));
			return 0;
		}
		int taught = 0;
		for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
			if (Forms.teach(p, id)) {
				taught++;
			}
		}
		int count = taught;
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.forms.teach", Forms.get(id).name(), count), true);
		return taught;
	}

	private static int forget(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
		if (id != null && Forms.get(id) == null) {
			c.getSource().sendFailure(Component.translatable("command.jaymoskingdoms.forms.unknown", id));
			return 0;
		}
		int changed = 0;
		for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
			boolean any = false;
			for (String known : id == null ? Forms.known(p) : java.util.List.of(id)) {
				any |= Forms.forget(p, known);
			}
			changed += any ? 1 : 0;
		}
		int count = changed;
		c.getSource().sendSuccess(() -> id == null ? Component.translatable("command.jaymoskingdoms.forms.forget_all", count)
			: Component.translatable("command.jaymoskingdoms.forms.forget", Forms.get(id).name(), count), true);
		return changed;
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Collection<ServerPlayer> found = EntityArgument.getPlayers(c, "players");
		for (ServerPlayer p : found) {
			Component names = Component.empty();
			boolean first = true;
			for (String id : Forms.known(p)) {
				names = Component.empty().append(names).append(Component.literal(first ? "" : ", "))
					.append(Component.translatable("command.jaymoskingdoms.forms.list.entry", Forms.get(id).name(), Forms.rank(p, id)));
				first = false;
			}
			Component line = first ? Component.translatable("command.jaymoskingdoms.forms.list.none", p.getDisplayName())
				: Component.translatable("command.jaymoskingdoms.forms.list", p.getDisplayName(), names);
			c.getSource().sendSuccess(() -> line, false);
		}
		return found.size();
	}
}
