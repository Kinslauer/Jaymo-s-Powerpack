package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.573) Taken in. Andy, on being left for dead: "what about the other possibilities? kidnapped, taken in by a village or
 * nearby villagers", then "yes add this" to waking in a bed in the nearest friendly village, bandaged. Called by Jaymo's
 * Powers' death by cause ({@code Mortality.SHELTERS}, after its captors had their say): when a village, fiefdom or kingdom
 * that thinks well enough of them ({@link #FRIENDLY}) has its seat within {@link #RANGE} of where they fell, it may
 * ({@link #CHANCE}) send folk to carry them in. They wake on one of its beds (Powers sets and dresses what it can: their
 * wounds are bandaged, their breaks splinted) and, once they come round, lie there resting ({@link BedRest#lay}) until they
 * get up. Only a real village carries anyone in (Andy, 2026-11-18: "you need to be taken to an actual village"): a bell with
 * homes round it ({@link #VILLAGE_HOMES}) by the faction's seat, and a free bed indoors there. A seat with none (a lone castle,
 * or a kingdom whose town never rose) sends nobody, and the next nearest that has one is tried.
 */
public final class Shelter {
	// ---- tuning ----
	/** How far (blocks) from where they fell a seat may be and still send for them. */
	static final double RANGE = 320.0;
	/** The chance that folk who'd take them in find them. */
	static final float CHANCE = 0.45F;
	/** The least reputation a faction needs with them to bother (anyone not set against them). */
	static final int FRIENDLY = Reputation.WELCOME;
	/** How far from its seat a faction's village bell may be, and how far round the bell its homes and beds are looked for. */
	static final int VILLAGE_REACH = 64;
	/** The fewest homes (beds) round the bell for it to be a village. */
	static final int VILLAGE_HOMES = 3;
	/** How far over a bed its roof may be. */
	static final int ROOF_HEIGHT = 8;
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
		// (nobody carries one of the walking dead home to tend: HollowSighting)
		if (Heritage.is(player, Heritage.Kind.HOLLOW)) {
			return null;
		}
		BlockPos fell = player.blockPosition();
		List<Faction> near = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.is(FactionType.BARBARIAN) || f.seat() == null) {
				continue;
			}
			if (f.seat().distSqr(fell) <= RANGE * RANGE && Reputation.get(player, f.id()) >= FRIENDLY) {
				near.add(f);
			}
		}
		near.sort(Comparator.comparingDouble(f -> f.seat().distSqr(fell)));
		RandomSource random = player.getRandom();
		for (Faction home : near) {
			BlockPos at = bed(level, home.seat(), random);
			if (at != null) {
				WAITING.put(player.getUUID(), new Waiting(at, server.getTickCount()));
				JaymosKingdoms.LOGGER.info("{} was taken in by {} ({})", player.getScoreboardName(), home.name(), home.id());
				return GlobalPos.of(level.dimension(), at);
			}
		}
		return null;
	}

	/**
	 * A free bed's head indoors in the village by {@code seat}, or null when there's no village there: no bell within {@link
	 * #VILLAGE_REACH}, or fewer than {@link #VILLAGE_HOMES} homes round it. Beds are found by their points of interest, so
	 * nothing is loaded for it; one in ground not loaded is taken on trust.
	 */
	private static @Nullable BlockPos bed(ServerLevel level, BlockPos seat, RandomSource random) {
		PoiManager pois = level.getPoiManager();
		Optional<BlockPos> bell = pois.findClosest(type -> type.is(PoiTypes.MEETING), seat, VILLAGE_REACH, PoiManager.Occupancy.ANY);
		if (bell.isEmpty()) {
			return null;
		}
		List<BlockPos> beds = pois.getInRange(type -> type.is(PoiTypes.HOME), bell.get(), VILLAGE_REACH, PoiManager.Occupancy.ANY)
			.map(r -> r.getPos().immutable()).toList();
		if (beds.size() < VILLAGE_HOMES) {
			return null;
		}
		BlockPos found = null;
		double best = Double.MAX_VALUE;
		for (BlockPos head : beds) {
			if (Loaded.made(level, head)) {
				BlockState state = level.getBlockState(head);
				if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED) || !roofed(level, head)) {
					continue;
				}
			}
			// (not always the one nearest the bell: any of the near ones)
			double d = head.distSqr(bell.get()) * (0.75 + random.nextDouble() * 0.5);
			if (d < best) {
				best = d;
				found = head;
			}
		}
		return found;
	}

	/** Whether something solid stands over {@code head} within {@link #ROOF_HEIGHT} (not by sky light, which the stacked world gets wrong). */
	private static boolean roofed(ServerLevel level, BlockPos head) {
		BlockPos.MutableBlockPos p = head.mutable();
		for (int y = 1; y <= ROOF_HEIGHT; y++) {
			if (level.getBlockState(p.move(0, 1, 0)).isSolid()) {
				return true;
			}
		}
		return false;
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
