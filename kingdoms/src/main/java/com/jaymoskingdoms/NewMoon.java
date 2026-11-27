package com.jaymoskingdoms;

import com.jaymoskingdoms.nightfall.Nightfall;
import com.jaymoskingdoms.nightfall.NightBleeder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The new moon is theirs (Night's Blood, patch 6; Andy's plan: "on a new moon vampires are bolder: the Elder hunts twice in
 * the night, Night Bleeders come down, the fever climbs faster, and a vampire's leap and claws are a little stronger").
 * A new moon night is the Overworld's night while the game's moon is in its new phase ({@link MoonPhase#NEW_MOON}, the one
 * {@link BloodPage} counts as a new moon seen). What it does:
 * <ul>
 * <li>The Elder rests only {@link #ELDER_REST_SHARE} as long between hunts ({@link Vampires}).</li>
 * <li>Night Bleeders come down from the sky near players in the open ({@link #fall}), a few at most at once.</li>
 * <li>A vampire player's blood fever climbs {@link #FEVER_RISE} times as fast ({@link BloodFever}).</li>
 * <li>Their leap flies {@link #LEAP_SHARE} times as far and their claws bite {@link #CLAW_SHARE} times as hard
 * ({@link BloodLeap}, {@link ClawArts}).</li>
 * </ul>
 */
public final class NewMoon {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The Elder waits this share of his usual rest after a feed before he hunts again. */
	static final float ELDER_REST_SHARE = 0.5F;
	/** The fever's climb, times this, on a new moon night. */
	static final float FEVER_RISE = 1.5F;
	/** A vampire's leap (its reach and speed) and its claws (their harm), times these. */
	static final float LEAP_SHARE = 1.15F, CLAW_SHARE = 1.2F;
	/** Night Bleeders: each player is looked at this often (ticks); the chance one comes down; no more than this many within {@link #NEAR}. */
	static final int FALL_EVERY = 600, FALL_MOST = 2;
	static final float FALL_CHANCE = 0.2F;
	static final double NEAR = 64.0, FALL_FROM = 18.0, FALL_TO = 30.0, FALL_HEIGHT = 14.0;
	/** How long (ticks) a Night Bleeder floats down on a slow fall. */
	static final int FALL_TICKS = 400;

	private NewMoon() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NewMoon::tick);
	}

	/** Whether it is a new moon night now in the Overworld (server side). */
	public static boolean on(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		return Vampires.night(overworld.getOverworldClockTime())
			&& overworld.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.NEW_MOON;
	}

	/** Whether it is a new moon night now, for something in the world (false on the client). */
	public static boolean on(Entity e) {
		return e.level().getServer() != null && on(e.level().getServer());
	}

	/** Whether the moon is new now, night or day (the phase the willing turning needs, {@link OldBlood}). */
	public static boolean phase(MinecraftServer server) {
		return server.overworld().environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.NEW_MOON;
	}

	/** How long (ticks) the Elder rests between hunts tonight. */
	static int elderRest(MinecraftServer server) {
		return on(server) ? Math.round(Vampires.ELDER_REST * ELDER_REST_SHARE) : Vampires.ELDER_REST;
	}

	// ---- Night Bleeders come down ----------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % FALL_EVERY != 0 || !on(server)) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || !level.canSeeSky(player.blockPosition())
				|| level.getRandom().nextFloat() >= FALL_CHANCE) {
				continue;
			}
			if (level.getEntitiesOfClass(NightBleeder.class, player.getBoundingBox().inflate(NEAR), NightBleeder::isAlive).size() >= FALL_MOST) {
				continue;
			}
			fall(level, player);
		}
	}

	/** A Night Bleeder drops out of the dark sky 18 to 30 blocks from {@code player}, on a slow fall. */
	private static void fall(ServerLevel level, ServerPlayer player) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0, d = FALL_FROM + level.getRandom().nextDouble() * (FALL_TO - FALL_FROM);
		BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * d, player.getY(), player.getZ() + Math.sin(angle) * d);
		if (!Loaded.made(level, column)) {
			return;
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		NightBleeder bleeder = Nightfall.NIGHT_BLEEDER.create(level, EntitySpawnReason.EVENT);
		if (bleeder == null) {
			return;
		}
		bleeder.snapTo(ground.getX() + 0.5, ground.getY() + FALL_HEIGHT, ground.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		bleeder.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);
		bleeder.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FALL_TICKS, 0, false, false, false));
		level.addFreshEntity(bleeder);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, bleeder.getX(), bleeder.getY(), bleeder.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, bleeder.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.0F, 0.5F);
	}
}
