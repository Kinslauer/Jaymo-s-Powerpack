package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.chicken.Chicken;

/**
 * Laying hens (after Better With Mods): a chicken that pecks a seed off the ground ({@link EatGroundFoodGoal}) is fed,
 * and lays an egg at the next dawn. Fed or not, its own egg timer still runs, an unfed hen's slower
 * ({@link #UNFED_RATE}). Hand-feeding seeds to breed them is untouched. Ticked from {@code ChickenMixin}.
 */
public final class LayingHens {
	/** How fast a hen's own egg timer runs (1 = vanilla), fed and unfed. */
	static final float FED_RATE = 1.0F;
	static final float UNFED_RATE = 0.5F;
	private static final long DAY_TICKS = 24000L;

	/** The day (overworld clock / a day's ticks) a hen was fed on; gone once it has laid its dawn egg. */
	public static final AttachmentType<Long> FED_DAY = AttachmentRegistry.create(JaymosKitchen.id("hen_fed_day"),
		builder -> builder.persistent(Codec.LONG));

	private LayingHens() {
	}

	/** Called at init, so the attachment type exists before any world loads (dedicated servers need it). */
	public static void register() {
	}

	/** {@code hen} just ate a seed off the ground. */
	public static void feed(Chicken hen) {
		if (hen.level() instanceof ServerLevel level && !hen.isBaby()) {
			hen.setAttached(FED_DAY, day(level));
		}
	}

	/** Every tick, before vanilla counts down the egg timer. */
	public static void tick(Chicken hen) {
		if (!(hen.level() instanceof ServerLevel level) || !hen.isAlive() || hen.isBaby() || hen.isChickenJockey()) {
			return;
		}
		Long fedOn = hen.getAttached(FED_DAY);
		if (fedOn != null && fedOn != day(level)) {
			// a new day (or the clock was turned back): the egg comes now, vanilla's own laying (sound, variant egg)
			hen.removeAttached(FED_DAY);
			hen.eggTime = Math.min(hen.eggTime, 1);
			return;
		}
		float rate = fedOn != null ? FED_RATE : UNFED_RATE;
		if (rate < 1.0F && hen.getRandom().nextFloat() >= rate) {
			hen.eggTime++;
		} else if (rate > 1.0F && hen.getRandom().nextFloat() < rate - 1.0F) {
			hen.eggTime--;
		}
	}

	private static long day(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
	}
}
