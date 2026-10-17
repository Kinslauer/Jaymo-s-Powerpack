package com.jaymospowers.power.web;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters): {@code /powerdebug web symbiote bond|reject|feed}, {@code /powerdebug web symbiote dormant <ticks>}, {@code /powerdebug web symbiote level <0-100>} (the bond's strength), {@code /powerdebug web swung <blocks>}
 * and {@code /powerdebug web crater} (a crater, goo and all, right where you stand).
 * Strains: {@code /powerdebug web symbiote strain <0-2>} (black, white, red), {@code /powerdebug web symbiote blob <0-2>}
 * (a roaming one of that strain at your feet) and {@code /powerdebug web crater red}.
 */
final class WebDebugCommand {
	private WebDebugCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
			Commands.literal("powerdebug")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("web")
					.then(Commands.literal("symbiote")
						.then(Commands.literal("bond").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							// (a fresh bond: full reset of satiety, stress and the rejection count)
							WebSymbiote.bond(player, player.position());
							return 1;
						}))
						.then(Commands.literal("level")
							.then(Commands.argument("bond", IntegerArgumentType.integer(0, WebSymbiote.BOND_MAX)).executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								WebSymbiote.addBond(player, IntegerArgumentType.getInteger(c, "bond") - WebSymbiote.state(player).bond());
								c.getSource().sendSuccess(() -> Component.literal(WebSymbiote.describe(player)), false);
								return 1;
							})))
						// one of the symbiote's radial abilities, as if picked from the wheel (by its number, see WebSymbiote)
						.then(Commands.literal("use")
							.then(Commands.argument("ability", IntegerArgumentType.integer(0, WebSymbiote.MASK_TOGGLE)).executes(c -> {
								WebSymbiote.use(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "ability"));
								return 1;
							})))
						// dormant for this many ticks (0 wakes it), as after the first bond (WebSymbioteBond)
						.then(Commands.literal("dormant")
							.then(Commands.argument("ticks", IntegerArgumentType.integer(0, WebSymbioteBond.DORMANT_MAX_TICKS)).executes(c -> {
								WebSymbioteBond.setDormant(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "ticks"));
								return 1;
							})))
						// its strain: 0 black, 1 white, 2 red (WebSymbioteStrain)
						.then(Commands.literal("strain")
							.then(Commands.argument("strain", IntegerArgumentType.integer(0, WebSymbioteStrain.values().length - 1)).executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								WebSymbiote.setStrain(player, WebSymbioteStrain.of(IntegerArgumentType.getInteger(c, "strain")));
								c.getSource().sendSuccess(() -> Component.literal(WebSymbiote.describe(player)), false);
								return 1;
							})))
						// a roaming one of that strain, right here
						.then(Commands.literal("blob")
							.then(Commands.argument("strain", IntegerArgumentType.integer(0, WebSymbioteStrain.values().length - 1)).executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								WebSymbioteStrains.mark(WebSymbioteBlob.release(player.level(), player.position()),
									WebSymbioteStrain.of(IntegerArgumentType.getInteger(c, "strain")));
								return 1;
							})))
						.then(Commands.literal("reject").executes(c -> {
							WebSymbiote.reject(c.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("feed").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							WebSymbiote.feed(player);
							c.getSource().sendSuccess(() -> Component.literal(WebSymbiote.describe(player)), false);
							return 1;
						}))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							c.getSource().sendSuccess(() -> Component.literal(WebSymbiote.describe(player)), false);
							return 1;
						}))
					.then(Commands.literal("swung")
						.then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0)).executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							WebAscension.addSwung(player, DoubleArgumentType.getDouble(c, "blocks"));
							double total = WebAscension.swung(player);
							c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "swung %.1f / %.0f",
								total, WebAscension.ASCEND_DISTANCE)), false);
							return 1;
						}))
						.executes(c -> {
							double total = WebAscension.swung(c.getSource().getPlayerOrException());
							c.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "swung %.1f / %.0f",
								total, WebAscension.ASCEND_DISTANCE)), false);
							return 1;
						}))
					.then(Commands.literal("crater")
						.then(Commands.literal("red").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							boolean placed = WebPower.RED_GOO != null
								&& WebCraterFeature.carve(player.level(), player.getRandom(), player.blockPosition().below(), false, WebPower.RED_GOO);
							c.getSource().sendSuccess(() -> Component.literal(placed ? "red crater placed" : "no crater"), false);
							return placed ? 1 : 0;
						}))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							boolean placed = WebCraterFeature.carve(player.level(), player.getRandom(), player.blockPosition().below(), false);
							c.getSource().sendSuccess(() -> Component.literal(placed ? "crater placed" : "no crater"), false);
							return placed ? 1 : 0;
						})))));
	}
}
