package com.jaymospowers.power.arsenal;

import com.jaymospowers.core.Fate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /powerdebug arsenal ...} (gamemasters): test helpers for the 0.2.0 fate pieces.
 * <ul>
 * <li>{@code corruption <targets> <amount>} - feed the meter as Enderman contact would (with the whispers)</li>
 * <li>{@code dragonkill} - as if the Ender Dragon died where you stand and everyone nearby had fought it</li>
 * <li>{@code purge <targets>} - start the cure sequence on these players</li>
 * <li>{@code blink <targets>} - an involuntary void blink</li>
 * <li>{@code ready <targets>} - clear every arsenal cooldown</li>
 * <li>{@code pin <targets>} - show what the Armory has pinned</li>
 * </ul>
 */
final class ArsenalDebugCommand {
	private ArsenalDebugCommand() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("powerdebug")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("arsenal")
					.then(Commands.literal("corruption")
						.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument("amount", IntegerArgumentType.integer(-100, 100))
								.executes(c -> {
									int amount = IntegerArgumentType.getInteger(c, "amount");
									Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
									players.forEach(p -> ArsenalVoid.addCorruption(p, amount));
									c.getSource().sendSuccess(() -> Component.literal("Arsenal corruption " + amount + " -> "
										+ players.stream().map(p -> p.getPlainTextName() + "=" + Fate.corruption(p)).toList()), true);
									return players.size();
								}))))
					.then(Commands.literal("dragonkill")
						.executes(c -> {
							Set<UUID> everyone = new HashSet<>();
							c.getSource().getLevel().players().forEach(p -> everyone.add(p.getUUID()));
							int purged = ArsenalVoid.dragonDied(c.getSource().getLevel(), c.getSource().getPosition(), everyone);
							c.getSource().sendSuccess(() -> Component.literal("Simulated dragon death: " + purged + " purge(s) started"), true);
							return purged;
						}))
					.then(Commands.literal("purge")
						.then(Commands.argument("targets", EntityArgument.players())
							.executes(c -> {
								Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
								int n = 0;
								for (ServerPlayer p : players) {
									if (ArsenalVoid.isVoid(p)) {
										ArsenalVoid.startPurge(p);
										n++;
									}
								}
								int done = n;
								c.getSource().sendSuccess(() -> Component.literal(done + " purge(s) started (only corrupted arsenal users)"), true);
								return done;
							})))
					.then(Commands.literal("blink")
						.then(Commands.argument("targets", EntityArgument.players())
							.executes(c -> {
								int n = 0;
								for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
									n += ArsenalVoid.blink(p) ? 1 : 0;
								}
								int done = n;
								c.getSource().sendSuccess(() -> Component.literal(done + " blinked"), true);
								return done;
							})))
					.then(Commands.literal("relic")
						.executes(c -> {
							ServerPlayer self = c.getSource().getPlayerOrException();
							self.getInventory().add(ArsenalRelics.make(self.level(), self.getRandom()));
							c.getSource().sendSuccess(() -> Component.literal("A relic, as found in a chest."), false);
							return 1;
						}))
					.then(Commands.literal("ready")
						.then(Commands.argument("targets", EntityArgument.players())
							.executes(c -> {
								Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
								players.forEach(p -> p.setAttached(ArsenalPower.COOLDOWNS, ArsenalPower.Cooldowns.NONE));
								c.getSource().sendSuccess(() -> Component.literal("Arsenal cooldowns cleared"), true);
								return players.size();
							})))
					.then(Commands.literal("pin")
						.then(Commands.argument("targets", EntityArgument.players())
							.executes(c -> {
								for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
									c.getSource().sendSuccess(() -> Component.literal(p.getPlainTextName() + ": pin="
										+ p.getAttached(ArsenalArmory.PIN) + " (active=" + (ArsenalArmory.pinned(p) != null) + ")"), false);
								}
								return 1;
							}))))));
	}
}
