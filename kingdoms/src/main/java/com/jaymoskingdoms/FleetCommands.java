package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.42) Operators' commands for the ships with crews of their own (debug: plain text): {@code /kingdom fleet harbour |
 * navy | merchant | pirate | wreck | chart | info | ship <rig>}.
 */
final class FleetCommands {
	private FleetCommands() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("fleet").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("harbour").executes(FleetCommands::harbour))
			.then(Commands.literal("navy").executes(FleetCommands::navy))
			.then(Commands.literal("merchant").executes(FleetCommands::merchant))
			.then(Commands.literal("pirate").executes(FleetCommands::pirate))
			.then(Commands.literal("wreck").executes(FleetCommands::wreck))
			.then(Commands.literal("chart").executes(FleetCommands::chart))
			.then(Commands.literal("info").executes(FleetCommands::info))
			.then(Commands.literal("ship").then(Commands.argument("rig", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Fleet.Rig.values()).map(Fleet.Rig::id), b))
				.executes(FleetCommands::ship)));
	}

	private static @Nullable Faction nearest(CommandContext<CommandSourceStack> c, Predicate<Faction> which) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(c.getSource().getServer())) {
			double d = Raids.horizontal(f.seat(), player.blockPosition());
			if (!f.fallen() && which.test(f) && d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	private static int fail(CommandContext<CommandSourceStack> c, String why) {
		c.getSource().sendFailure(Component.literal(why));
		return 0;
	}

	private static int ok(CommandContext<CommandSourceStack> c, String what) {
		c.getSource().sendSuccess(() -> Component.literal(what), true);
		return 1;
	}

	private static int harbour(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction f = nearest(c, x -> x.type().isRealm());
		if (f == null) {
			return fail(c, "No realm near");
		}
		ServerLevel level = Factions.level(c.getSource().getServer());
		Navies.Harbour h = Navies.harbour(c.getSource().getServer(), f.id());
		if (h == null) {
			h = Navies.build(level, f);
		}
		return h == null ? fail(c, f.name() + " has no shore with open sea within " + PirateCoves.COAST_REACH + " blocks (loaded)")
			: ok(c, f.name() + "'s harbour: pier " + h.shore().toShortString() + ", mooring " + h.mooring().toShortString());
	}

	private static int navy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		Faction f = nearest(c, x -> x.type().isRealm() && Navies.harbour(server, x.id()) != null);
		if (f == null) {
			return fail(c, "No realm with a harbour near (/kingdom fleet harbour)");
		}
		ServerLevel level = Factions.level(server);
		Ship ship = Navies.warship(level, f, Navies.harbour(server, f.id()), level.getGameTime(), true);
		return ship == null ? fail(c, "No room to put out " + f.name() + "'s warship at its mooring")
			: ok(c, f.name() + "'s warship " + (ship.crew == null ? "" : ship.crew.name) + " at " + ship.blockPosition().toShortString());
	}

	private static int merchant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		Faction f = nearest(c, x -> x.type().isRealm() && Navies.harbour(server, x.id()) != null);
		if (f == null) {
			return fail(c, "No realm with a harbour near (/kingdom fleet harbour)");
		}
		ServerLevel level = Factions.level(server);
		Faction to = Navies.destination(level, f);
		if (to == null) {
			return fail(c, "No other realm's harbour for " + f.name() + " to trade with");
		}
		Ship ship = Navies.merchant(level, f, to, "");
		return ship == null ? fail(c, "No room to put out a merchantman at " + f.name() + "'s mooring")
			: ok(c, (ship.crew == null ? "A merchantman" : ship.crew.name) + " sails from " + f.name() + " for " + to.name());
	}

	private static int pirate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		MinecraftServer server = c.getSource().getServer();
		Faction cove = nearest(c, x -> x.type() == FactionType.BARBARIAN && Camps.pirate(server, x.id()));
		if (cove == null) {
			return fail(c, "No pirate cove near (/kingdom camp found pirate on a coast)");
		}
		PirateCoves.Cove data = PirateCoves.cove(server, cove.id());
		ServerLevel level = Factions.level(server);
		if (data != null && data.ship().isPresent() && level.getEntity(data.ship().get()) instanceof Ship there) {
			return ok(c, cove.name() + "'s ship is already out at " + there.blockPosition().toShortString());
		}
		Ship ship = PirateCoves.launch(level, cove);
		return ship == null ? fail(c, "No room to put out " + cove.name() + "'s ship (its mooring unloaded or crowded)")
			: ok(c, cove.name() + " put out " + (ship.crew == null ? "her" : ship.crew.name) + " at " + ship.blockPosition().toShortString());
	}

	private static int wreck(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		if (!TreasureWrecks.layNear(level, player)) {
			return fail(c, "No deep water loaded " + TreasureWrecks.WRECK_NEAREST + "-" + TreasureWrecks.WRECK_FURTHEST + " blocks off");
		}
		BlockPos at = TreasureWrecks.lastLaid(level);
		return ok(c, "A treasure wreck lies at " + (at == null ? "?" : at.toShortString()));
	}

	private static int chart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		player.getInventory().addAndPickItem(TreasureWrecks.chart(level, player.blockPosition(), level.getRandom()));
		return ok(c, "A Pirate's Chart");
	}

	/**
	 * (0.7.108) A ship of any rig built on open water near you (or where the command runs), bow the way you face, and cast
	 * off with no crew: to look over, or board and sail yourself.
	 */
	private static int ship(CommandContext<CommandSourceStack> c) {
		String id = StringArgumentType.getString(c, "rig");
		Fleet.Rig rig = Arrays.stream(Fleet.Rig.values()).filter(r -> r.id().equals(id)).findFirst().orElse(null);
		if (rig == null) {
			return fail(c, "No rig " + id);
		}
		ServerLevel level = c.getSource().getLevel();
		BlockPos near = BlockPos.containing(c.getSource().getPosition());
		Direction bow = Direction.fromYRot(c.getSource().getRotation().y);
		Fleet.Look look = new Fleet.Look(Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.SPRUCE_LOG, Blocks.WOOL.pick(DyeColor.WHITE), null);
		for (int tries = 0; tries < 8; tries++) {
			BlockPos water = Fleet.openSea(level, near, 48, 6, level.getRandom());
			List<BlockPos> where = new ArrayList<>();
			BlockPos helm = water == null ? null : Fleet.lay(level, rig, look, water, bow, where);
			if (helm == null) {
				continue;
			}
			Ship ship = Fleet.castOff(level, helm);
			if (ship == null || ship.hull() == null) {
				return fail(c, "A " + id + " (" + where.size() + " blocks) was built at " + helm.toShortString() + " but wouldn't cast off");
			}
			var h = ship.hull();
			return ok(c, "A " + id + " (" + h.pieces.size() + " blocks, " + h.sails + " sails, " + h.cannons.length + " guns, draws "
				+ String.format(java.util.Locale.ROOT, "%.1f", h.draft) + ") at " + ship.blockPosition().toShortString());
		}
		return fail(c, "No open sea with room for a " + id + " within 48 blocks (loaded)");
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		int n = 0;
		for (Ship ship : Ships.in(level)) {
			Crew crew = ship.crew;
			if (crew == null || ship.distanceTo(player) > 400.0) {
				continue;
			}
			n++;
			String line = crew.name + " (" + crew.side.id() + ", " + crew.mode + ") at " + ship.blockPosition().toShortString() + ": "
				+ ship.crewAboard().size() + " aboard, captain " + (crew.captained(ship) ? "alive" : "gone");
			c.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		int ships = n;
		c.getSource().sendSuccess(() -> Component.literal(ships + " crewed ships within 400 blocks"), false);
		return ships;
	}
}
