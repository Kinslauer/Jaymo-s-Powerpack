package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.5.29) {@code /kingdom quests}: your quests and what's left of each, with a way to give one up from afar. For ops:
 * {@code offer} (the nearest giver has work now) and {@code finish} (your quests' counts made up), for testing.
 */
final class QuestCommands {
	private QuestCommands() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("quests").executes(QuestCommands::list)
			.then(Commands.literal("abandon").then(Commands.argument("number", IntegerArgumentType.integer(1, 16)).executes(QuestCommands::abandon)))
			.then(Commands.literal("offer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(QuestCommands::offer))
			.then(Commands.literal("finish").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(QuestCommands::finish));
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		List<Quest> log = Quests.log(player);
		if (log.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("quest.jaymoskingdoms.command.none").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		ServerLevel level = (ServerLevel)player.level();
		for (int i = 0; i < log.size(); i++) {
			Quest q = log.get(i);
			int n = i + 1;
			Component abandon = Component.translatable("quest.jaymoskingdoms.command.abandon").withStyle(style -> style.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.SuggestCommand("/kingdom quests abandon " + n)));
			Component line = Component.literal(n + ". ").append(Quests.title(q).copy().withStyle(q.royal() ? ChatFormatting.RED : ChatFormatting.GOLD))
				.append(Component.literal(" (" + q.giver().name() + ") ").withStyle(ChatFormatting.GRAY)).append(abandon);
			c.getSource().sendSuccess(() -> line, false);
			Component goal = Component.literal("   ").append(Quests.objective(level, player, q)).withStyle(ChatFormatting.GRAY);
			c.getSource().sendSuccess(() -> goal, false);
		}
		return log.size();
	}

	private static int abandon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		int n = IntegerArgumentType.getInteger(c, "number");
		List<Quest> log = Quests.log(player);
		if (n > log.size()) {
			c.getSource().sendFailure(Component.translatable("quest.jaymoskingdoms.command.no_such", n));
			return 0;
		}
		Quest q = log.get(n - 1);
		Quests.abandon(player, q.id());
		c.getSource().sendSuccess(() -> Component.translatable("quest.jaymoskingdoms.abandoned", Quests.title(q)).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int offer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel)player.level();
		Optional<LivingEntity> giver = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16.0),
				e -> (e instanceof Villager || (e instanceof Folk f && f.role() == Folk.KING)) && Quests.settlement(level, e) != null)
			.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		if (giver.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No villager or king of a settlement within 16 blocks"));
			return 0;
		}
		LivingEntity g = giver.get();
		g.setAttached(Quests.OFFER, new Quests.Offer(Quests.day(level) + 1, true, Optional.empty()));
		g.setAttached(Quests.MARK, true);
		c.getSource().sendSuccess(() -> Component.literal(g.getName().getString() + " (" + QuestBoard.job(g) + ") has work now"), true);
		return 1;
	}

	private static int finish(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		int n = Quests.finishAll(player);
		c.getSource().sendSuccess(() -> Component.literal(n + " quest counts made up"), true);
		return n;
	}
}
