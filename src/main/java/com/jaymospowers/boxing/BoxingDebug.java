package com.jaymospowers.boxing;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters), all acting on the player running them. Players learn styles from teachers, never here.
 * <ul>
 * <li>{@code /powerdebug boxing style <arrow_slit|long_guard|bastion|none>}: box in that style (learned at level 1 if
 * it wasn't known), or in none;</li>
 * <li>{@code /powerdebug boxing level <0-10>}: set the level of the style you box in (0 forgets it);</li>
 * <li>{@code /powerdebug boxing status}: the style, every level, the rhythm and the punch's cost.</li>
 * </ul>
 */
final class BoxingDebug {
	private BoxingDebug() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("powerdebug")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("boxing")
					.then(Commands.literal("style")
						.then(Commands.argument("style", StringArgumentType.word())
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								String id = StringArgumentType.getString(c, "style");
								Boxing.Style style = Boxing.Style.byId(id);
								if (style == null) {
									Boxing.use(player, null);
									c.getSource().sendSuccess(() -> Component.literal("Boxing in no style (styles: arrow_slit, long_guard, bastion)"), false);
									return 0;
								}
								if (Boxing.level(player, style) <= 0) {
									Boxing.setLevel(player, style, 1);
								}
								Boxing.use(player, style);
								c.getSource().sendSuccess(() -> Component.literal("Boxing in " + style.id + ", level " + Boxing.level(player, style)), false);
								return 1;
							})))
					.then(Commands.literal("level")
						.then(Commands.argument("level", IntegerArgumentType.integer(0, Boxing.MAX_LEVEL))
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								Boxing.Style style = Boxing.style(player);
								if (style == null) {
									c.getSource().sendSuccess(() -> Component.literal("No style: /powerdebug boxing style <style> first"), false);
									return 0;
								}
								int level = IntegerArgumentType.getInteger(c, "level");
								Boxing.setLevel(player, style, level);
								c.getSource().sendSuccess(() -> Component.literal(style.id + " set to level " + level), false);
								return level;
							})))
					.then(Commands.literal("status")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							Boxing.Style style = Boxing.style(player);
							StringBuilder text = new StringBuilder("style " + (style == null ? "none" : style.id));
							for (Boxing.Style each : Boxing.Style.values()) {
								text.append(", ").append(each.id).append(' ').append(Boxing.level(player, each));
							}
							text.append(", boxing ").append(Boxing.boxing(player)).append(", gassed ").append(Boxing.gassed(player))
								.append(", a punch every ").append(Boxing.interval(player)).append(" ticks");
							if (style != null) {
								text.append(", cost ").append(Boxing.costAt(Boxing.level(player, style))).append(" half-feathers");
							}
							c.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
							return 1;
						})))));
	}
}
