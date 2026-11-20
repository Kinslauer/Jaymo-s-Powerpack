package com.jaymoskingdoms;

import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import java.util.Map;
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
 * the {@code mixin/forestfire} mixin, so its classes load only when it's there.
 * <ul>
 * <li>Fire is vanilla fire (Andy, 2026-11-20: "the fire should go out when hit, and back to the vanilla firetick/ rule"):
 * our port no longer lets its simulation take fires over, and our wildfires ({@code Calamities}) spread as they always
 * did. Its wind still follows ours ({@code Storms}).</li>
 * <li>ff-villagers: when our people put a fire out (beaten out, a golem's fist, a thrown water bottle), any of its own
 * fire there (an incident started by its commands) goes out too. Its own villager panic is off
 * ({@code ForestFirePreset} in Powers).</li>
 * <li>ff-heat: the heat off its fire counts into Kitchen's body heat ({@link #bodyHeat}); its own heat damage is off
 * (the preset), its smoke stays.</li>
 * </ul>
 */
public final class ForestFireCompat {
	public static final String MOD = "peterwolfs_forestfire";
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** How often its wind is set to ours (ticks). */
	static final int WIND_EVERY = 20;
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

	/** (ff-villagers) Its fire within {@code radius} of {@code at} goes out (cooled, soaked), as ours was just put out there. */
	public static void douse(ServerLevel level, BlockPos at, int radius) {
		if (LOADED) {
			FireSimulation.get(level).extinguishInRadius(at, radius);
		}
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
