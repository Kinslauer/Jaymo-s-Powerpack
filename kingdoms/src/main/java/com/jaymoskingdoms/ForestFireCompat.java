package com.jaymoskingdoms;

import com.peterwolf.forestfire.api.ForestFireApi;
import com.peterwolf.forestfire.fire.incident.FireIncident;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Peterwolf's Forest Fire &amp; Firefighting (MIT, by Peterwolf; in the pack with its Minecraft lock lifted), tied into
 * ours (Andy's forest-fire picks, 2026-11-15). Every call is safe without it: nothing happens. Touched only here and in
 * the {@code mixin/forestfire} mixins, so its classes load only when it's there.
 * <ul>
 * <li>ff-sim: a wildfire ({@code Calamities}) becomes one of its fire incidents, so its fire runs it: embers jumping
 * ahead, crown fires, faster uphill, trees burning through. Its wind follows ours ({@code Storms}), so its fire, our
 * smoke and the smog all lean the same way.</li>
 * <li>ff-villagers: when our people put a fire out (beaten out, a golem's fist, a thrown water bottle), its fire there
 * goes out too, or it would light again from the hot ground under it. Its own villager panic is off
 * ({@code ForestFirePreset} in Powers).</li>
 * <li>ff-pits: a fire kept burning on purpose (on netherrack or whatever else burns forever here: fire pits, village
 * hearths, the Nether's own fires) never becomes one of its incidents, so it isn't put out or spread
 * ({@code mixin/forestfire/ForestFireKeptMixin}).</li>
 * <li>ff-heat: the heat off its fire counts into Kitchen's body heat ({@link #bodyHeat}); its own heat damage is off
 * (the preset), its smoke stays.</li>
 * </ul>
 */
public final class ForestFireCompat {
	public static final String MOD = "peterwolfs_forestfire";
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** How often its wind is set to ours (ticks). */
	static final int WIND_EVERY = 20;
	/** A wildfire's first blaze: blocks round where it caught, by its strength ({@code Magnitude}): MINOR 2 ... CATASTROPHIC 6. */
	static final int SIZE_BASE = 1;
	/** Its burning blocks this near a player warm them (blocks), and how much: degrees for a unit of its own heat score
	 * (a block fully alight beside you is about half a unit), at most {@link #HEAT_MOST}. */
	static final double HEAT_RANGE = 12.0;
	static final double HEAT_DEGREES = 20.0, HEAT_MOST = 50.0;

	private ForestFireCompat() {
	}

	static void register() {
		if (!LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(ForestFireCompat::tick);
	}

	/** Its wind blows the way ours does, as hard (its yaw: toward (-sin, cos); ours: toward (cos, sin)). */
	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getGameTime() % WIND_EVERY != 0) {
			return;
		}
		float yaw = (float) Math.toDegrees(com.jaymoskingdoms.disasters.Storms.windAngle()) - 90.0F;
		IncidentManager.get(level).wind().setWind(Mth.clamp(com.jaymoskingdoms.disasters.Storms.windStrength(), 0.0F, 1.0F), yaw);
	}

	/** (ff-sim) A wildfire of strength {@code m} catches at {@code at}: its incident's id, or 0 (not loaded, or it has as
	 * many fires as it takes). */
	public static int wildfire(ServerLevel level, BlockPos at, int m) {
		if (!LOADED) {
			return 0;
		}
		try {
			FireIncident incident = ForestFireApi.createIncident(level, at, SIZE_BASE + Math.max(1, m));
			return incident.id;
		} catch (IllegalStateException full) {
			return 0;
		}
	}

	/**
	 * (ff-sim) Where incident {@code id} burns now, at most {@code most} spots (the fire blocks over its burning cells),
	 * into {@code front}. False when the incident is over (or gone).
	 */
	public static boolean front(ServerLevel level, int id, Deque<BlockPos> front, int most) {
		Optional<FireIncident> incident = ForestFireApi.getIncident(level, id);
		if (incident.isEmpty() || !incident.get().status.isOpen()) {
			return false;
		}
		front.clear();
		for (Map.Entry<Long, FireCell> e : FireSimulation.get(level).cells().entrySet()) {
			FireCell cell = e.getValue();
			if (cell.incidentId == id && cell.stage().isBurning()) {
				front.addLast(BlockPos.of(e.getKey()).above());
				if (front.size() >= most) {
					break;
				}
			}
		}
		return true;
	}

	/** (ff-villagers) Its fire within {@code radius} of {@code at} goes out (cooled, soaked), as ours was just put out there. */
	public static void douse(ServerLevel level, BlockPos at, int radius) {
		if (LOADED) {
			FireSimulation.get(level).extinguishInRadius(at, radius);
		}
	}

	/** (ff-pits) The fire at {@code fire} is kept burning on purpose: what's under it burns forever here. */
	public static boolean kept(ServerLevel level, BlockPos fire) {
		return level.getBlockState(fire.below()).is(level.dimensionType().infiniburn());
	}

	/**
	 * (ff-heat) The heat off its fire round {@code player}, for Kitchen's body heat (degrees): every block burning within
	 * {@link #HEAT_RANGE}, hotter and fiercer ones more, less with distance (its own heat score, the one it hurt players
	 * by), so a fire front scorches from further off than one flame does.
	 */
	public static double bodyHeat(ServerPlayer player) {
		if (!LOADED || !(player.level() instanceof ServerLevel level)) {
			return 0.0;
		}
		Map<Long, FireCell> cells = FireSimulation.get(level).cells();
		if (cells.isEmpty()) {
			return 0.0;
		}
		BlockPos at = player.blockPosition();
		double score = 0.0;
		for (Map.Entry<Long, FireCell> e : cells.entrySet()) {
			FireCell cell = e.getValue();
			if (!cell.stage().isBurning()) {
				continue;
			}
			double d2 = BlockPos.of(e.getKey()).distSqr(at);
			if (d2 <= HEAT_RANGE * HEAT_RANGE) {
				score += cell.heat01() * cell.material().flameIntensity / (1.0 + Math.sqrt(d2));
			}
		}
		return Math.min(HEAT_MOST, score * HEAT_DEGREES);
	}
}
