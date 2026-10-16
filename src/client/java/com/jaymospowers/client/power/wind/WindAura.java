package com.jaymospowers.client.power.wind;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.wind.WindStorm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * What everyone sees around a wind user who has gone further, drawn on each client from the synced fate (no packets):
 * <ul>
 * <li><b>Tempest</b> (ascended): a swirl of wind spiralling up around the body.</li>
 * <li><b>Stormbound</b> (corrupted): dark motes and crackling static, with a faint crackle now and then. As the Eye
 * of the Storm calms them (the synced calm), the static thins and the crackle softens.</li>
 * </ul>
 */
final class WindAura {
	/** Only players this close get an aura drawn. */
	private static final double VIEW_DISTANCE = 48.0;
	/** Tempest swirl: puffs per tick, radius, how fast it turns. */
	private static final int TEMPEST_STRANDS = 2;
	private static final double TEMPEST_RADIUS = 0.75;
	private static final double TEMPEST_SPIN = 0.35;
	/** Stormbound: dark motes per tick at full static, and the chance of a spark per tick. */
	private static final float STATIC_MOTES = 1.5F;
	private static final float STATIC_SPARK_CHANCE = 0.35F;
	private static final DustParticleOptions STATIC_DUST = new DustParticleOptions(0x2B1D3A, 1.3F);
	/** Stormbound crackle: about every this many ticks, at this volume (fading with the calm). */
	private static final int CRACKLE_TICKS = 50;
	private static final float CRACKLE_VOLUME = 0.25F;

	/** The Thunderhead in flight (flying at least this much): an arc of lightning off the body every this many ticks. */
	private static final float THUNDER_FLIGHT = 0.3F;
	private static final int ARC_EVERY = 2;
	/** An arc: this many jagged steps of this length (blocks). */
	private static final int ARC_STEPS = 7;
	private static final double ARC_STEP = 0.22;

	private WindAura() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isSpectator() || player.isInvisible() || !player.isAlive() || player.distanceToSqr(minecraft.player) > VIEW_DISTANCE * VIEW_DISTANCE
				|| !Powers.has(player, WindPower.INSTANCE)) {
				continue;
			}
			Fate.State fate = Fate.state(player);
			boolean self = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
			if (fate.ascended() && !self) {
				tempest(level, player);
			}
			if (fate.corrupted()) {
				Integer calmSteps = player.getAttached(WindStorm.CALM);
				float storm = 1.0F - (calmSteps == null ? 0.0F : calmSteps / (float)WindStorm.CALM_STEPS);
				stormbound(level, player, random, storm, self);
			}
			int mode = com.jaymospowers.power.wind.WindModes.mode(player);
			if ((mode & com.jaymospowers.power.wind.WindModes.STORM) != 0) {
				enshroud(level, player, random, self);
			}
			if ((mode & com.jaymospowers.power.wind.WindModes.TEMPEST) != 0) {
				ring(level, player);
			}
			if (mode == com.jaymospowers.power.wind.WindModes.BOTH && WindPose.amount(player, 1.0F) > THUNDER_FLIGHT) {
				thunderFlight(level, player, random, self);
			}
		}
	}

	/** Stormbound mode: lightning wrapped about the body, arcs crawling over it. */
	private static void enshroud(ClientLevel level, AbstractClientPlayer player, RandomSource random, boolean self) {
		int sparks = self ? 1 : 4;
		for (int i = 0; i < sparks; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 0.45 + random.nextDouble() * 0.25;
			double y = player.getY() + random.nextDouble() * player.getBbHeight();
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
				-Math.sin(a) * 0.25, (random.nextDouble() - 0.5) * 0.2, Math.cos(a) * 0.25);
		}
		if (!self && player.tickCount % 3 == 0) {
			level.addParticle(STATIC_DUST, player.getX(), player.getY() + random.nextDouble() * player.getBbHeight(), player.getZ(), 0.0, 0.0, 0.0);
		}
		if (random.nextInt(30) == 0) {
			level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
				0.15F, 1.8F + random.nextFloat() * 0.2F, false);
		}
	}

	/**
	 * The Thunderhead in flight: lightning crackles over the body and leaps off it in jagged arcs that trail behind,
	 * bright white sparks along each arc, and a quiet snap now and then (fewer round your own first-person camera).
	 */
	private static void thunderFlight(ClientLevel level, AbstractClientPlayer player, RandomSource random, boolean self) {
		if (player.tickCount % ARC_EVERY != 0 || self && random.nextBoolean()) {
			return;
		}
		net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
		double x = player.getX() + (random.nextDouble() - 0.5) * 0.6;
		double y = player.getY() + random.nextDouble() * player.getBbHeight();
		double z = player.getZ() + (random.nextDouble() - 0.5) * 0.6;
		for (int i = 0; i < ARC_STEPS; i++) {
			// each step jags off at random, drifting back against the flight
			x += (random.nextDouble() - 0.5) * 2.0 * ARC_STEP - motion.x * 0.4;
			y += (random.nextDouble() - 0.5) * 2.0 * ARC_STEP - motion.y * 0.4;
			z += (random.nextDouble() - 0.5) * 2.0 * ARC_STEP - motion.z * 0.4;
			level.addParticle(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, x, y, z, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(12) == 0) {
			level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
				0.12F, 1.9F + random.nextFloat() * 0.1F, false);
		}
	}

	/** Tempest mode: a ring of wind circling wide around the body. */
	private static void ring(ClientLevel level, AbstractClientPlayer player) {
		double base = player.tickCount * 0.45;
		for (int i = 0; i < 3; i++) {
			double a = base + i * Math.PI * 2.0 / 3.0;
			double r = 1.7;
			level.addParticle(ParticleTypes.CLOUD, player.getX() + Math.cos(a) * r, player.getY() + 0.9 + Math.sin(base * 0.5 + i) * 0.4,
				player.getZ() + Math.sin(a) * r, -Math.sin(a) * 0.18, 0.0, Math.cos(a) * 0.18);
		}
		if (player.tickCount % 8 == 0) {
			level.addParticle(ParticleTypes.SMALL_GUST, player.getX() + Math.cos(base) * 1.7, player.getY() + 1.0, player.getZ() + Math.sin(base) * 1.7,
				0.0, 0.0, 0.0);
		}
	}

	private static void tempest(ClientLevel level, AbstractClientPlayer player) {
		double base = player.tickCount * TEMPEST_SPIN;
		for (int i = 0; i < TEMPEST_STRANDS; i++) {
			double angle = base + i * Math.PI * 2.0 / TEMPEST_STRANDS;
			double height = (player.tickCount * 0.08 + i * 0.5) % 1.0 * player.getBbHeight();
			double radius = TEMPEST_RADIUS * (1.0 - 0.3 * height / player.getBbHeight());
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			// moving along the circle (tangent) and a little up
			level.addParticle(ParticleTypes.CLOUD, x, player.getY() + height, z, -Math.sin(angle) * 0.08, 0.03, Math.cos(angle) * 0.08);
		}
		if (player.tickCount % 10 == 0) {
			level.addParticle(ParticleTypes.SMALL_GUST, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 0.0, 0.0, 0.0);
		}
	}

	private static void stormbound(ClientLevel level, AbstractClientPlayer player, RandomSource random, float storm, boolean self) {
		if (storm <= 0.0F) {
			return;
		}
		float motes = STATIC_MOTES * storm * (self ? 0.4F : 1.0F);
		int count = (int)motes + (random.nextFloat() < motes - (int)motes ? 1 : 0);
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 1.2;
			double y = player.getY() + random.nextDouble() * player.getBbHeight();
			double z = player.getZ() + (random.nextDouble() - 0.5) * 1.2;
			level.addParticle(STATIC_DUST, x, y, z, 0.0, 0.0, 0.0);
		}
		if (!self && random.nextFloat() < STATIC_SPARK_CHANCE * storm) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 1.0;
			double y = player.getY() + random.nextDouble() * player.getBbHeight();
			double z = player.getZ() + (random.nextDouble() - 0.5) * 1.0;
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4,
				(random.nextDouble() - 0.5) * 0.4);
		}
		if (random.nextInt(CRACKLE_TICKS) == 0) {
			level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
				CRACKLE_VOLUME * storm * storm, 1.6F + random.nextFloat() * 0.4F, false);
		}
	}
}
