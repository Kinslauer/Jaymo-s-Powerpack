package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Andy (2026-11-11): power users who don't care for theatrics use their powers "mostly to help their daily life, like
 * putting fires out or keeping a food store or home cold". A gifted Cryo villager's everyday use ({@link CryoGift#everyday})
 * leaves the air round their home cold for a while ({@link #chill}). With Jaymo's Kitchen, by reflection (an older
 * Kitchen is simply left alone): a chest or barrel in that cold keeps food as a cold cellar does ({@code Freshness.addCold}),
 * and a player indoors there in a hot land is cooled ({@code BodyHeat.addWeather}).
 */
final class CryoChill {
	/** How far the cold reaches from where it was laid (blocks), and how long it lasts (ticks). */
	static final double REACH = 8.0;
	static final int LASTS = 2400;
	/** A player indoors in the cold, where the land's base temperature is at least this, is cooled this much. */
	static final float HOT_LAND = 0.95F;
	static final double COOLS = 20.0;

	/** One patch of cold air: where, in which world, until when (game time). */
	private record Spot(ResourceKey<Level> level, BlockPos pos, long until) {
	}

	private static final List<Spot> SPOTS = new ArrayList<>();

	private CryoChill() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (SPOTS) {
				SPOTS.clear();
			}
		});
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			ToDoubleFunction<ServerPlayer> cool = CryoChill::cool;
			Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addWeather", ToDoubleFunction.class).invoke(null, cool);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen body heat not found; a Cryo's cold house won't cool anyone", e);
		}
		try {
			BiPredicate<ServerLevel, BlockPos> cold = CryoChill::cold;
			Class.forName("com.jaymoskitchen.fresh.Freshness").getMethod("addCold", BiPredicate.class).invoke(null, cold);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.debug("Jaymo's Kitchen has no cold-cellar hook (older Kitchen?); a Cryo's larder keeps food as before", e);
		}
	}

	/** A gifted Cryo has chilled the air round {@code pos}. */
	static void chill(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		synchronized (SPOTS) {
			SPOTS.removeIf(s -> s.until() < now || s.level() == level.dimension() && s.pos().closerThan(pos, REACH / 2.0));
			SPOTS.add(new Spot(level.dimension(), pos.immutable(), now + LASTS));
		}
	}

	/** Whether {@code pos} is in a Cryo's cold just now. */
	static boolean cold(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		synchronized (SPOTS) {
			for (Spot s : SPOTS) {
				if (s.until() >= now && s.level() == level.dimension() && s.pos().closerThan(pos, REACH)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Body heat: indoors in a Cryo's cold, in a hot land, a player is cooled. */
	private static double cool(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		if (level.canSeeSky(at.above()) || level.getBiome(at).value().getBaseTemperature() < HOT_LAND || !cold(level, at)) {
			return 0.0;
		}
		return -COOLS;
	}
}
