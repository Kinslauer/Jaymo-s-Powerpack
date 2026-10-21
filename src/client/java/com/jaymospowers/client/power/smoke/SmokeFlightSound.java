package com.jaymospowers.client.power.smoke;

import com.jaymospowers.client.sound.Accents;
import com.jaymospowers.client.sound.PowerLoop;
import com.jaymospowers.core.Soundscape.Layer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What Vapour Form's flight sounds like to the flier, layered so it never just loops (bystanders hear the trail from
 * the server, {@code SmokeVapour}):
 * <ul>
 * <li>a rush of air that follows speed, lower and smokier than Wind's, swelling in a dive;</li>
 * <li>a smouldering bed under it, loudest hovering, thinning out at speed;</li>
 * <li>hisses and crackles at uneven intervals, a whoosh when a fast cruise banks hard, a soft rush on braking;</li>
 * <li>the world answering: rain sizzles on the embers, low over water the smoke steams, high up gusts catch it, and
 * wholly smoke there's a breathy whisper.</li>
 * </ul>
 */
final class SmokeFlightSound {
	/** The rush: volume hovering (by drift speed) and cruising (by speed share), and its pitch over the speed share. */
	static final float RUSH_HOVER = 0.05F, RUSH_HOVER_SPAN = 0.15F, RUSH_CRUISE = 0.12F, RUSH_CRUISE_SPAN = 0.7F;
	static final float RUSH_PITCH = 0.55F, RUSH_PITCH_SPAN = 0.45F, RUSH_DIVE_PITCH = 0.25F;
	/** The smoulder bed's volume hovering and at top speed, and its pitch. */
	static final float SMOULDER_HOVER = 0.22F, SMOULDER_FAST = 0.06F, SMOULDER_PITCH = 1.15F;
	/** A bank this many degrees a tick at this share of top speed or more whooshes. */
	static final float BANK_DEGREES = 5.0F, BANK_SPEED = 0.5F;
	/** Steam over water within this many blocks; gusts this high over the ground (or above this height). */
	static final int STEAM_HEIGHT = 4, GUST_HEIGHT = 24, GUST_ALTITUDE = 150;

	static final Layer HISS = Layer.of(0.08F, 0.18F, 0.4F, 0.75F, SoundEvents.FIRE_EXTINGUISH);
	static final Layer CRACKLE = Layer.of(0.25F, 0.5F, 0.7F, 1.1F, SoundEvents.CAMPFIRE_CRACKLE).chance(0.6F);
	static final Layer SIZZLE = Layer.of(0.15F, 0.3F, 1.1F, 1.6F, SoundEvents.FIRE_EXTINGUISH, SoundEvents.LAVA_EXTINGUISH);
	static final Layer STEAM = Layer.of(0.2F, 0.35F, 0.5F, 0.8F, SoundEvents.FIRE_EXTINGUISH, SoundEvents.LAVA_EXTINGUISH);
	static final Layer GUST = Layer.of(0.25F, 0.45F, 0.5F, 0.8F, SoundEvents.BREEZE_IDLE_AIR, SoundEvents.BREEZE_IDLE_GROUND);
	static final Layer BANK = Layer.of(0.3F, 0.45F, 0.6F, 0.85F, SoundEvents.BREEZE_WHIRL, SoundEvents.BREEZE_SLIDE);
	static final Layer BRAKE = Layer.of(0.2F, 0.3F, 0.5F, 0.7F, SoundEvents.BREEZE_SLIDE, SoundEvents.FIRE_EXTINGUISH);
	static final Layer WHISPER = Layer.of(0.15F, 0.3F, 0.5F, 0.8F, SoundEvents.SOUL_ESCAPE.value()).chance(0.7F);

	private static float lastYaw;
	private static boolean braking;

	private SmokeFlightSound() {
	}

	/** Every client tick, after the flight has moved. */
	static void tick(LocalPlayer player) {
		boolean flying = SmokeFlightClient.flying();
		PowerLoop.keep("smoke.rush", SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, player, SmokeFlightClient::flying, new PowerLoop.Mix() {
			@Override
			public float volume() {
				return SmokeFlightClient.cruising()
					? RUSH_CRUISE + RUSH_CRUISE_SPAN * (float)SmokeFlightClient.speedFraction(player)
					: RUSH_HOVER + RUSH_HOVER_SPAN * (float)Math.min(1.0, SmokeFlightClient.velocity().length() / 0.5);
			}

			@Override
			public float pitch() {
				float over = (float)Math.max(0.0, SmokeFlightClient.speedOverTop(player) - 1.0);
				return RUSH_PITCH + RUSH_PITCH_SPAN * (float)SmokeFlightClient.speedFraction(player) + RUSH_DIVE_PITCH * over;
			}
		});
		PowerLoop.keep("smoke.smoulder", SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value(), SoundSource.PLAYERS, player, SmokeFlightClient::flying,
			new PowerLoop.Mix() {
				@Override
				public float volume() {
					return Mth.lerp((float)SmokeFlightClient.speedFraction(player), SMOULDER_HOVER, SMOULDER_FAST);
				}

				@Override
				public float pitch() {
					return SMOULDER_PITCH;
				}
			});
		if (!flying) {
			braking = false;
			lastYaw = player.getYRot();
			return;
		}
		Level level = player.level();
		Vec3 at = player.position();
		BlockPos pos = player.blockPosition();
		float fraction = (float)SmokeFlightClient.speedFraction(player);
		Accents.every(player, "smoke.hiss", 14, 34, HISS, CRACKLE);
		if (level.isRainingAt(pos.above())) {
			Accents.every(player, "smoke.sizzle", 4, 12, SIZZLE.louder(0.6F + fraction));
		}
		if (overWater(level, pos)) {
			Accents.every(player, "smoke.steam", 8, 18, STEAM.louder(0.7F + 0.6F * fraction));
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
		if (at.y - ground > GUST_HEIGHT || at.y > GUST_ALTITUDE) {
			Accents.every(player, "smoke.gust", 40, 100, GUST.louder(0.6F + fraction));
		}
		if (SmokeFlightClient.wholly()) {
			Accents.every(player, "smoke.whisper", 30, 70, WHISPER);
		}
		float bank = Math.abs(Mth.wrapDegrees(player.getYRot() - lastYaw));
		lastYaw = player.getYRot();
		if (SmokeFlightClient.cruising() && fraction >= BANK_SPEED && bank >= BANK_DEGREES) {
			Accents.every(player, "smoke.bank", 10, 18, BANK.louder(0.5F + fraction));
		}
		boolean brake = SmokeFlightClient.cruising() && player.input.keyPresses.backward();
		if (brake && !braking) {
			Accents.play(player, BRAKE.louder(0.5F + fraction));
		}
		braking = brake;
	}

	private static boolean overWater(Level level, BlockPos pos) {
		for (int i = 1; i <= STEAM_HEIGHT; i++) {
			if (level.getFluidState(pos.below(i)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}
