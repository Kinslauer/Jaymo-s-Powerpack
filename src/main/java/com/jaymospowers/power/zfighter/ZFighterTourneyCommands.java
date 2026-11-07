package com.jaymospowers.power.zfighter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.60, Andy's NOEA picks) The commands of the tournament, invasion and realistic-mode features:
 * <ul>
 * <li>{@code /zfighter bracket} (or {@code /tournament}): opens the bracket screen for the tournament that is on, or the last
 * one's final board;</li>
 * <li>{@code /zfighter invasion}: tells the state of the invasion that is on and its prize and rules;</li>
 * <li>{@code /zfighter realism}: whether realistic mode is on; gamemasters can add {@code on} or {@code off};</li>
 * <li>{@code /powerdebug zfighter royale}: a last-one-standing tournament opens near you; {@code /powerdebug zfighter invasion
 * [frost|android|ape|demon]}: an army of that theme (or any) lands round you.</li>
 * </ul>
 */
final class ZFighterTourneyCommands {
	private ZFighterTourneyCommands() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static int bracket(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ZFighterBracketNet.open(player, ZFighterTournaments.board());
		return 1;
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tournament").executes(c -> bracket(c.getSource())));
		dispatcher.register(Commands.literal("zfighter")
			.then(Commands.literal("bracket").executes(c -> bracket(c.getSource())))
			.then(Commands.literal("invasion").executes(c -> {
				for (Component line : ZFighterInvasions.describe()) {
					c.getSource().sendSuccess(() -> line, false);
				}
				return 1;
			}))
			.then(Commands.literal("realism")
				.executes(c -> {
					c.getSource().sendSuccess(() -> Component.translatable(ZFighterRealism.on() ? "message.jaymospowers.zfighter_realism_on"
						: "message.jaymospowers.zfighter_realism_off").withStyle(ChatFormatting.GOLD), false);
					return 1;
				})
				.then(Commands.literal("on").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
					ZFighterRealism.set(true);
					c.getSource().sendSuccess(() -> Component.translatable("message.jaymospowers.zfighter_realism_set_on").withStyle(ChatFormatting.GOLD), true);
					return 1;
				}))
				.then(Commands.literal("off").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
					ZFighterRealism.set(false);
					c.getSource().sendSuccess(() -> Component.translatable("message.jaymospowers.zfighter_realism_set_off").withStyle(ChatFormatting.GOLD), true);
					return 1;
				}))));
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("zfighter")
				.then(Commands.literal("royale").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					boolean opened = ZFighterTournaments.open(player.level(), player.blockPosition(), "Debug", true);
					c.getSource().sendSuccess(() -> Component.literal(opened ? "A last-one-standing tournament opens nearby." : "No tournament: one is already on, or there is no flat, clear ground for a ring nearby."), false);
					return 1;
				}))
				.then(Commands.literal("invasion")
					.executes(c -> invade(c.getSource(), null))
					.then(Commands.argument("theme", StringArgumentType.word())
						.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ZFighterInvasions.themes(), builder))
						.executes(c -> invade(c.getSource(), StringArgumentType.getString(c, "theme")))))));
	}

	private static int invade(CommandSourceStack source, String theme) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean landed = ZFighterInvasions.begin(player.level(), player.blockPosition(), "Debug", theme);
		source.sendSuccess(() -> Component.literal(landed ? "An army lands round you." : "An invasion is already on."), false);
		return 1;
	}
}
