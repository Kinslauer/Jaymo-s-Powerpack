package com.jaymospowers.core.gifted;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * (0.17.2) {@code /power gift <targets> <power> [bold|steady|wary]}, {@code /power gift <targets> clear|corrupt|ready},
 * and {@code /power gift <targets> unleash} (their ultimate on whoever runs it): for testing gifted villagers.
 */
public final class GiftedCommand {
	private static final String[] STYLES = {"bold", "steady", "wary"};

	private GiftedCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("gift")
			.then(Commands.argument("targets", EntityArgument.entities())
				.then(Commands.literal("clear").executes(c -> {
					Collection<? extends Entity> targets = EntityArgument.getEntities(c, "targets");
					targets.forEach(Gifted::take);
					c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.gift.clear", targets.size()), true);
					return targets.size();
				}))
				.then(Commands.literal("corrupt").executes(c -> {
					int n = 0;
					for (Entity e : EntityArgument.getEntities(c, "targets")) {
						n += Gifted.corrupt(e) ? 1 : 0;
					}
					int done = n;
					c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.gift.corrupt", done), true);
					return n;
				}))
				.then(Commands.literal("ready").executes(c -> {
					Collection<? extends Entity> targets = EntityArgument.getEntities(c, "targets");
					for (Entity e : targets) {
						if (e instanceof PathfinderMob mob) {
							GiftedAI.ready(mob);
						}
					}
					return targets.size();
				}))
				.then(Commands.literal("unleash").executes(c -> {
					Entity foe = c.getSource().getEntityOrException();
					int n = 0;
					for (Entity e : EntityArgument.getEntities(c, "targets")) {
						if (e instanceof PathfinderMob mob) {
							GiftedAI.ready(mob);
							n += Gifted.unleash(mob, foe) ? 1 : 0;
						}
					}
					return n;
				}))
				.then(Commands.argument("power", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Gifted.giftable(), b))
					.executes(c -> give(c.getSource(), EntityArgument.getEntities(c, "targets"), StringArgumentType.getString(c, "power"), Gifted.STEADY))
					.then(Commands.argument("style", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(STYLES, b))
						.executes(c -> {
							String style = StringArgumentType.getString(c, "style");
							int s = style.equals("bold") ? Gifted.BOLD : style.equals("wary") ? Gifted.WARY : Gifted.STEADY;
							return give(c.getSource(), EntityArgument.getEntities(c, "targets"), StringArgumentType.getString(c, "power"), s);
						}))));
	}

	private static int give(CommandSourceStack source, Collection<? extends Entity> targets, String power, int style) {
		int n = 0;
		for (Entity e : targets) {
			n += Gifted.give(e, power, style) ? 1 : 0;
		}
		int done = n;
		source.sendSuccess(() -> Component.translatable("commands.jaymospowers.gift.give", power, done), true);
		return n;
	}
}
