package com.jaymospowers.client.power.wind;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.power.wind.WindCold;
import com.jaymospowers.power.wind.WindWeather;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung) <b>Airflow sense</b>: a Wind user feels the air that moving things stir. Round the crosshair, a faint streak
 * shows on the side each one is on (ahead is up, behind is down), brighter the faster it moves and the nearer it is,
 * and fading over a moment once it stops. It feels through walls and in the dark, and it feels the unseen. It is a
 * sense of direction only: it gives no outline, no distance and no height.
 *
 * <p>What it can't feel is anything that is still or moving softly: standing, sleeping, or sneaking
 * ({@link #MOVE_MIN} is above a sneak's pace). It reaches further with each ability unlocked, half as far with no sky
 * overhead, and further in a storm, where the gale throws the streaks about so they wander off true. In the rain or
 * the cold it loses soft movement. Under water it feels nothing.
 *
 * <p>Client side only, from what this client already knows of the things round it: nothing is sent, and it costs
 * nothing. Always on from {@link #MIN_MASTERY} unlocked abilities.
 */
final class WindAirSense {
	// ---- tuning ----
	/** Unlocked abilities needed to feel anything. */
	static final int MIN_MASTERY = 1;
	/** How far it reaches (blocks): this much, plus this much for each unlocked ability (12 at one, 24 at four). */
	static final double RANGE_BASE = 8.0, RANGE_PER_ABILITY = 4.0;
	/** The reach under a roof or underground, in a dimension with no weather, and in a storm (shares of the reach). */
	static final double SHELTERED_RANGE = 0.5, OTHERWORLD_RANGE = 0.75, STORM_RANGE = 1.25;
	/** Slower than this (blocks a tick) stirs too little to feel: a sneak is about 0.065, a walk about 0.22. */
	static final double MOVE_MIN = 0.08;
	/** In the rain or the cold, soft movement is lost: the least it feels is this many times {@link #MOVE_MIN}. */
	static final double DULLED_MIN = 1.5;
	/** This fast (blocks a tick) or faster is felt at full strength. */
	static final double MOVE_FULL = 0.3;
	/** The faintest a felt movement starts at, and what a streak loses each tick once its maker stops (so about a second). */
	static final float STRENGTH_MIN = 0.2F, FADE = 0.05F;
	/** The most streaks drawn at once (the strongest). */
	static final int MAX_MARKS = 12;
	/** The streaks: how far from the crosshair they start (pixels), the dots in each and the gap between dots. */
	static final float RING_RADIUS = 30.0F;
	static final int DOTS = 3;
	static final float DOT_GAP = 3.0F;
	/** The furthest things show this much fainter (a share) than the nearest. */
	static final float FAR_DIM = 0.6F;
	/** Something at least this wide or this tall (blocks) leaves a thicker streak. */
	static final float BIG_WIDTH = 1.2F, BIG_HEIGHT = 2.4F;
	/** In a storm each streak wanders this far off true (degrees), this fast (radians a tick). */
	static final float STORM_WOBBLE = 20.0F, STORM_WOBBLE_RATE = 0.35F;
	/** The streak's colour and its most opaque (0-255). */
	static final int COLOR = 0xDFF6FF, MAX_ALPHA = 190;

	/** entity id → how strongly its movement is felt now (0..1]. */
	private static final Map<Integer, Float> SIGNALS = new HashMap<>();

	/** A streak to draw: the side it is on (degrees clockwise from ahead), how bright, and whether its maker is big. */
	private record Mark(float bearing, float brightness, boolean big) {
	}

	private WindAirSense() {
	}

	static void clear() {
		SIGNALS.clear();
	}

	/** Whether {@code player} feels the air right now. */
	private static boolean feels(Player player) {
		return Mastery.unlocked(player) >= MIN_MASTERY && player.isAlive() && !player.isSpectator() && !player.isEyeInFluid(FluidTags.WATER);
	}

	/** How far {@code player} feels (blocks): by mastery, then by the sky over them. */
	private static double range(Player player) {
		double range = RANGE_BASE + RANGE_PER_ABILITY * Mastery.unlocked(player);
		return range * switch (WindWeather.sky(player)) {
			case SHELTERED -> SHELTERED_RANGE;
			case OTHERWORLD -> OTHERWORLD_RANGE;
			case STORM -> STORM_RANGE;
			default -> 1.0;
		};
	}

	/** Every tick, for the local Wind user: what moved near them, and the fading of what has stopped. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		ClientLevel level = minecraft.level;
		if (level == null || !feels(player)) {
			SIGNALS.clear();
			return;
		}
		SIGNALS.replaceAll((id, strength) -> strength - FADE);
		SIGNALS.values().removeIf(strength -> strength <= 0.0F);
		double range = range(player);
		WindWeather.Kind sky = WindWeather.sky(player);
		double least = sky == WindWeather.Kind.RAIN || WindCold.isCold(player) ? MOVE_MIN * DULLED_MIN : MOVE_MIN;
		Vec3 eye = player.getEyePosition();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range), LivingEntity::isAlive)) {
			if (entity == player || entity.isSpectator() || entity.distanceToSqr(player) > range * range
				|| entity.getVehicle() == player || player.getVehicle() == entity) {
				continue;
			}
			double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
			double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (moved < least) {
				continue;
			}
			float strength = (float)Mth.clamp((moved - least) / (MOVE_FULL - least), STRENGTH_MIN, 1.0);
			SIGNALS.merge(entity.getId(), strength, (was, now) -> Math.max(was, now));
		}
	}

	/** The streaks round the crosshair. */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (SIGNALS.isEmpty() || level == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float yaw = player.getViewYRot(partial);
		Vec3 eye = player.getEyePosition(partial);
		double range = range(player);
		boolean storm = WindWeather.sky(player) == WindWeather.Kind.STORM;
		float time = player.tickCount + partial;
		List<Mark> marks = new ArrayList<>();
		for (Map.Entry<Integer, Float> signal : SIGNALS.entrySet()) {
			Entity entity = level.getEntity(signal.getKey());
			if (entity == null) {
				continue;
			}
			Vec3 to = entity.getEyePosition(partial).subtract(eye);
			float far = (float)Mth.clamp(to.length() / range, 0.0, 1.0);
			// 0: straight ahead (drawn above the crosshair), clockwise round to behind (below)
			float bearing = (float)Math.toDegrees(Mth.atan2(to.z, to.x)) - 90.0F - yaw;
			if (storm) {
				// the gale throws it about: each streak wanders on its own beat
				bearing += Mth.sin(time * STORM_WOBBLE_RATE + signal.getKey() * 1.7F) * STORM_WOBBLE;
			}
			boolean big = entity.getBbWidth() >= BIG_WIDTH || entity.getBbHeight() >= BIG_HEIGHT;
			marks.add(new Mark(bearing, signal.getValue() * (1.0F - far * FAR_DIM), big));
		}
		marks.sort((a, b) -> Float.compare(b.brightness(), a.brightness()));
		float cx = graphics.guiWidth() / 2.0F, cy = graphics.guiHeight() / 2.0F;
		int count = Math.min(marks.size(), MAX_MARKS);
		for (int m = 0; m < count; m++) {
			Mark mark = marks.get(m);
			float angle = (float)Math.toRadians(mark.bearing());
			int half = mark.big() ? 1 : 0;
			for (int i = 0; i < DOTS; i++) {
				// the streak thins away from the crosshair
				int alpha = Math.round(MAX_ALPHA * mark.brightness() * (1.0F - i / (float)DOTS));
				if (alpha < 8) {
					continue;
				}
				float radius = RING_RADIUS + i * DOT_GAP;
				int x = Math.round(cx + Mth.sin(angle) * radius), y = Math.round(cy - Mth.cos(angle) * radius);
				graphics.fill(x - half, y - half, x + half + 1, y + half + 1, alpha << 24 | COLOR);
			}
		}
	}
}
