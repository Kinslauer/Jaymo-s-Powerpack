package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.seas.Offshore;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Ships met out at sea (Andy, 2026-11-19: "i just went across the ocean and only saw dolphins and abandoned ships").
 * Every ship of ours puts out from a harbour or a cove and keeps near it, so an open-ocean crossing met none. Now a player
 * out at sea ({@link Offshore#far} at least {@link #FAR}) with no other ship near now and then sights one heaving into
 * view out of sight range: a sea rover out of the nearest pirate cove ({@link FarGrounds#rover}), or a merchantman of the
 * nearest harbour bound for another realm's ({@link Navies#destination}), who can be hailed and traded with like any.
 * Either is stowed again once long out with nobody near ({@link FarGrounds#stowIdle}).
 */
final class SeaPassers {
	/** How often (ticks) each player at sea is looked at, and the chance a ship is sighted then. */
	static final int CHECK_TICKS = 600;
	static final float CHANCE = 0.15F;
	/** How far out (see {@link Offshore#far}) a player must be. */
	static final float FAR = 0.4F;
	/** None is sighted while any ship already sails this near the player. */
	static final double QUIET = 192.0;
	/** The share of sightings that are rovers (the rest merchantmen, where two realms trade by sea). */
	static final float ROVER_SHARE = 0.45F;
	/** A rover met at sea is as one this many steps out on the far grounds (her rig and her galleon's chance). */
	static final int ROVER_FAR = 2;

	private SeaPassers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SeaPassers::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null || level.getGameTime() % CHECK_TICKS != 13) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !atSea(level, player) || random.nextFloat() >= CHANCE
				|| !level.getEntitiesOfClass(Ship.class, player.getBoundingBox().inflate(QUIET), s -> s.crew != null).isEmpty()) {
				continue;
			}
			if (random.nextFloat() < ROVER_SHARE || merchant(level, player, random) == null) {
				FarGrounds.rover(level, player.position(), List.of(player), ROVER_FAR, random);
			}
		}
	}

	/** {@code player} is afloat (aboard something or in the water) out at sea. */
	private static boolean atSea(ServerLevel level, ServerPlayer player) {
		return (player.isPassenger() || player.isInWater()) && Math.abs(player.getY() - level.getSeaLevel()) < 8.0
			&& Offshore.far(level, player.getBlockX(), player.getBlockZ()) >= FAR;
	}

	/** A merchantman of the harbour nearest {@code player}, bound for another realm's, heaving into sight off them (null: none). */
	private static @Nullable Ship merchant(ServerLevel level, ServerPlayer player, RandomSource random) {
		MinecraftServer server = level.getServer();
		Faction from = null;
		double best = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen() || Navies.harbour(server, f.id()) == null) {
				continue;
			}
			double d = Raids.horizontal(f.seat(), player.blockPosition());
			if (d < best) {
				best = d;
				from = f;
			}
		}
		Faction to = from == null ? null : Navies.destination(level, from);
		Navies.Harbour home = from == null ? null : Navies.harbour(server, from.id()), there = to == null ? null : Navies.harbour(server, to.id());
		if (home == null || there == null) {
			return null;
		}
		for (int tries = 0; tries < 6; tries++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double d = Mth.nextDouble(random, FarGrounds.ROVER_MIN, FarGrounds.ROVER_MAX);
			BlockPos column = BlockPos.containing(player.getX() + Mth.cos(angle) * d, player.getY(), player.getZ() + Mth.sin(angle) * d);
			BlockPos water = Fleet.openSea(level, column, 16, 3, random);
			if (water == null || Raids.playerNear(level, water, FarGrounds.ROVER_HIDE)) {
				continue;
			}
			Crew crew = new Crew(Crew.Side.MERCHANT, from.id(), Pirates.merchantShip(random), home.mooring());
			crew.bound = there.mooring();
			crew.boundFor = to.id();
			crew.quest = "";
			Fleet.Look look = new Fleet.Look(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LOG, Fleet.block(from.color().getName() + "_wool"), null);
			float yaw = (float) (Mth.atan2(there.mooring().getZ() - water.getZ(), there.mooring().getX() - water.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			Ship ship = Corsairs.launch(level, random.nextBoolean() ? Fleet.Rig.CARGO_SHIP : Fleet.Rig.TRADER, look, water, Direction.fromYRot(yaw), crew,
				from, new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, Navies.MERCHANT_HANDS, 0));
			if (ship != null) {
				FarGrounds.outAtSea(level, ship);
				JaymosKingdoms.LOGGER.info("The merchantman {} of {}, bound for {}, heaves into sight at {}", crew.name, from.name(), to.name(),
					water.toShortString());
				return ship;
			}
		}
		return null;
	}
}
