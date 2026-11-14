package com.jaymospowers.power.wind;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.elemental.ElementalCorruption;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * (Jung) Wind's part in Elemental Corruption: the sky's answer. The reckoning brings a thunderstorm and {@link #BOLTS}
 * bolts round the killer; through the wrath the storm holds, and every {@link #STRIKE_EVERY} ticks lightning finds a
 * living creature near them for {@link #STRIKE} (a heart). The storm breaks when the wrath ends. Overworld weather only:
 * elsewhere there is no sky to turn, but the bolts still fall.
 */
final class WindWrath implements ElementalCorruption.Wrath {
	// ---- tuning ----
	/** The reckoning's bolts, landing this near and this far (blocks) round the killer. */
	static final int BOLTS = 8;
	static final double BOLT_NEAR = 4.0, BOLT_FAR = 10.0;
	/** How long the storm is set for each time it is kept up (ticks), and how often it is. */
	static final int STORM_TICKS = 6000, KEEP_EVERY = 1200;
	/** Lightning on a creature this near (blocks) every this many ticks (seven seconds), for this much (a heart). */
	static final double STRIKE_RADIUS = 16.0;
	static final int STRIKE_EVERY = 140;
	static final float STRIKE = 2.0F;
	/** The storm's crackle round the holder. */
	static final int COLOR = 0x9FB8FF;

	@Override
	public void erupt(ServerPlayer player, ServerLevel level) {
		storm(level, true);
		for (int i = 0; i < BOLTS; i++) {
			double angle = Mth.TWO_PI * i / BOLTS + level.getRandom().nextDouble() * 0.5;
			double far = Mth.lerp(level.getRandom().nextDouble(), BOLT_NEAR, BOLT_FAR);
			int x = Mth.floor(player.getX() + Math.cos(angle) * far), z = Mth.floor(player.getZ() + Math.sin(angle) * far);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			bolt(level, x + 0.5, y, z + 0.5, false);
		}
	}

	@Override
	public void tick(ServerPlayer player, ServerLevel level, long age) {
		if (age % KEEP_EVERY == 0) {
			storm(level, true);
		}
		if (age == 0 || age % STRIKE_EVERY != 0) {
			return;
		}
		List<LivingEntity> near = ElementalCorruption.near(player, STRIKE_RADIUS);
		if (near.isEmpty()) {
			return;
		}
		LivingEntity struck = near.get(level.getRandom().nextInt(near.size()));
		bolt(level, struck.getX(), struck.getY(), struck.getZ(), true);
		struck.hurtServer(level, level.damageSources().lightningBolt(), STRIKE);
	}

	@Override
	public void aura(ServerPlayer player, ServerLevel level) {
		level.sendParticles(MagicFx.spark(COLOR, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.5, 0.8, 0.5, 0.02);
	}

	@Override
	public void disperse(ServerPlayer player, ServerLevel level) {
		storm(level, false);
	}

	/** Turns the overworld's sky to a thunderstorm, or clears it. */
	private static void storm(ServerLevel level, boolean on) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		WeatherData weather = level.getWeatherData();
		weather.setClearWeatherTime(on ? 0 : STORM_TICKS);
		weather.setRainTime(on ? STORM_TICKS : 0);
		weather.setThunderTime(on ? STORM_TICKS : 0);
		weather.setRaining(on);
		weather.setThundering(on);
	}

	/** A bolt at a spot: a real one (fire and all), or the look of one (the damage is dealt by hand). */
	private static void bolt(ServerLevel level, double x, double y, double z, boolean look) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(x, y, z);
			bolt.setVisualOnly(look);
			level.addFreshEntity(bolt);
		}
	}
}
