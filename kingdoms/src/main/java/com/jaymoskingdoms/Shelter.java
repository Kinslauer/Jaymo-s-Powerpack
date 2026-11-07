package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.573) Taken in. Andy, on being left for dead: "what about the other possibilities? kidnapped, taken in by a village or
 * nearby villagers", then "yes add this" to waking in a bed in the nearest friendly village, bandaged. Called by Jaymo's
 * Powers' death by cause ({@code Mortality.SHELTERS}, after its captors had their say): when a village, fiefdom or kingdom
 * that thinks well enough of them ({@link #FRIENDLY}) has its seat within {@link #RANGE} of where they fell, it may
 * ({@link #CHANCE}) send folk to carry them in. They wake on one of its beds (Powers sets and dresses what it can: their
 * wounds are bandaged, their breaks splinted) and, once they come round, lie there resting ({@link BedRest#lay}) until they
 * get up. No bed found: they wake on open ground by its seat.
 */
public final class Shelter {
	// ---- tuning ----
	/** How far (blocks) from where they fell a seat may be and still send for them. */
	static final double RANGE = 320.0;
	/** The chance that folk who'd take them in find them. */
	static final float CHANCE = 0.45F;
	/** The least reputation a faction needs with them to bother (anyone not set against them). */
	static final int FRIENDLY = Reputation.WELCOME;
	/** How far round a seat (blocks across, and up or down) a bed is looked for. */
	static final int BED_REACH = 40, BED_HEIGHT = 10;
	/** The ground near the seat to wake on without a bed. */
	static final int GROUND_MIN = 3, GROUND_MAX = 12;
	/** How long (ticks) to wait for them to come round before laying them in the bed is given up. */
	static final int LAY_WAIT = 20 * 60;

	/** Who was carried to a bed: the bed's head, and the tick it was found. */
	private record Waiting(BlockPos bed, long since) {
	}

	private static final Map<UUID, Waiting> WAITING = new HashMap<>();

	private Shelter() {
	}

	static void register() {
		PowersCompat.tellShelters(Shelter::take);
		ServerTickEvents.END_SERVER_TICK.register(Shelter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
	}

	/** Where {@code player}, downed by {@code by} (or nothing known), is carried in to, or null: nobody came. */
	static @Nullable GlobalPos take(ServerPlayer player, @Nullable Entity by) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		if (player.level() != level || player.getRandom().nextFloat() >= CHANCE) {
			return null;
		}
		Faction home = null;
		double best = RANGE * RANGE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.is(FactionType.BARBARIAN) || f.seat() == null) {
				continue;
			}
			double d = f.seat().distSqr(player.blockPosition());
			if (d <= best && Reputation.get(player, f.id()) >= FRIENDLY) {
				home = f;
				best = d;
			}
		}
		if (home == null) {
			return null;
		}
		RandomSource random = player.getRandom();
		BlockPos at = bed(level, home.seat(), random);
		if (at != null) {
			WAITING.put(player.getUUID(), new Waiting(at, server.getTickCount()));
		} else {
			at = LifeStory.ground(level, home.seat(), GROUND_MIN, GROUND_MAX, random);
		}
		if (at == null) {
			return null;
		}
		JaymosKingdoms.LOGGER.info("{} was taken in by {} ({})", player.getScoreboardName(), home.name(), home.id());
		return GlobalPos.of(level.dimension(), at);
	}

	/** A free bed's head about {@code seat}, in ground already loaded (the nearest of a few looked at), or null. */
	private static @Nullable BlockPos bed(ServerLevel level, BlockPos seat, RandomSource random) {
		BlockPos found = null;
		double best = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -BED_REACH; x <= BED_REACH; x++) {
			for (int z = -BED_REACH; z <= BED_REACH; z++) {
				int bx = seat.getX() + x, bz = seat.getZ() + z;
				if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
					continue;
				}
				for (int y = -BED_HEIGHT; y <= BED_HEIGHT; y++) {
					p.set(bx, seat.getY() + y, bz);
					BlockState state = level.getBlockState(p);
					if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
						&& !state.getValue(BedBlock.OCCUPIED)) {
						// (not always the one by the seat: any of the near ones)
						double d = p.distSqr(seat) * (0.75 + random.nextDouble() * 0.5);
						if (d < best) {
							best = d;
							found = p.immutable();
						}
					}
				}
			}
		}
		return found;
	}

	/** Those carried to a bed, once they've come round where they were put: laid in it, resting. */
	private static void tick(MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Waiting> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Waiting w = e.getValue();
			if (player == null || !player.isAlive() || now - w.since() > LAY_WAIT) {
				it.remove();
				continue;
			}
			// (still out cold, or not yet carried over)
			if (Downed.isDowned(player) || player.blockPosition().distSqr(w.bed()) > 9.0) {
				continue;
			}
			it.remove();
			BlockState state = player.level().getBlockState(w.bed());
			if (state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED)) {
				BedRest.lay(player, w.bed());
			}
		}
	}
}
