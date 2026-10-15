package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.59) {@code /kingdom skills}, for ops: the skill tree's pillars (Survival, Stealth, Strength) by hand.
 * <ul>
 * <li>{@code set <players> <pillar> <level>}: that pillar at that level (its experience set to the level's start). Set
 * lower than the perks already learned in it allow, that pillar's perks are refunded.</li>
 * <li>{@code add <players> <pillar> <xp>}: experience, as if earned (levels reached are announced).</li>
 * <li>{@code get <player>}: each pillar's level, experience and points left.</li>
 * <li>{@code respec <players>}: every perk refunded, experience kept (the court wizard's unmaking, free).</li>
 * <li>{@code reset <players>}: back to nothing, experience and perks.</li>
 * </ul>
 */
final class SkillCommands {
	private SkillCommands() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		var set = Commands.literal("set");
		var add = Commands.literal("add");
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			set.then(Commands.argument("players", EntityArgument.players()).then(Commands.literal(pillar.key())
				.then(Commands.argument("level", IntegerArgumentType.integer(0, pillar.maxLevel))
					.executes(c -> set(c, pillar, IntegerArgumentType.getInteger(c, "level"))))));
			add.then(Commands.argument("players", EntityArgument.players()).then(Commands.literal(pillar.key())
				.then(Commands.argument("xp", IntegerArgumentType.integer(1))
					.executes(c -> add(c, pillar, IntegerArgumentType.getInteger(c, "xp"))))));
		}
		return Commands.literal("skills").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(set)
			.then(add)
			.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(SkillCommands::get)))
			.then(Commands.literal("respec").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
				players.forEach(Skills::respec);
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.skills.respec", players.size()), true);
				return players.size();
			})))
			.then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
				for (ServerPlayer p : players) {
					p.setAttached(Skills.PROGRESS, Skills.Progress.EMPTY);
					Skills.refresh(p);
				}
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.skills.reset", players.size()), true);
				return players.size();
			})));
	}

	private static int set(CommandContext<CommandSourceStack> c, Skills.Pillar pillar, int level) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
		for (ServerPlayer p : players) {
			Skills.Progress before = Skills.of(p);
			Map<String, Integer> xp = new HashMap<>(before.xp());
			xp.put(pillar.key(), Skills.total(level));
			Map<String, Integer> ranks = new HashMap<>(before.ranks());
			Skills.Progress after = new Skills.Progress(Map.copyOf(xp), Map.copyOf(ranks));
			if (after.points(pillar) < 0) {
				// fewer levels than its perks cost: that pillar's perks come back to spend again
				for (Skills.Perk perk : Skills.Perk.values()) {
					if (perk.pillar == pillar) {
						ranks.remove(perk.key());
					}
				}
				after = new Skills.Progress(Map.copyOf(xp), Map.copyOf(ranks));
			}
			p.setAttached(Skills.PROGRESS, after);
			Skills.refresh(p);
		}
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.skills.set", name(pillar), level, players.size()), true);
		return players.size();
	}

	private static int add(CommandContext<CommandSourceStack> c, Skills.Pillar pillar, int xp) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
		players.forEach(p -> Skills.grant(p, pillar, xp));
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.skills.add", xp, name(pillar), players.size()), true);
		return players.size();
	}

	private static int get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(c, "player");
		Skills.Progress progress = Skills.of(p);
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.skills.get", p.getDisplayName()), false);
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.skills.get.pillar", name(pillar),
				progress.level(pillar), pillar.maxLevel, progress.xp(pillar), progress.points(pillar)), false);
		}
		return 1;
	}

	private static Component name(Skills.Pillar pillar) {
		return Component.translatable("skill.jaymoskingdoms." + pillar.key());
	}
}
