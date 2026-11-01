package com.jaymospowers.client.wounds;

import com.jaymospowers.core.Stance;
import com.jaymospowers.core.wounds.Resting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.19.24) The look of rest and hot springs, all particles: warm embers drifting up round someone resting by a campfire,
 * and steam rising off the water of a hot spring (water with magma two or three blocks under it) near the player.
 */
public final class RestClient {
	// ---- tuning ----
	/** (0.19.24) Ticks between the embers round a rester. */
	public static final int EMBER_EVERY = 6;
	/** (0.19.24) Random spots sampled each tick for steam, and how far round the player (blocks) they range. */
	public static final int STEAM_SAMPLES = 40, STEAM_RANGE = 9;

	private RestClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RestClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return;
		}
		RandomSource random = player.getRandom();
		boolean low = player.isShiftKeyDown() || player.hasAttached(Stance.LOW_CROUCH);
		if (low && player.tickCount % EMBER_EVERY == 0 && Resting.nearFire(level, player.blockPosition())) {
			level.addParticle(ParticleTypes.FLAME, player.getX() + random.nextGaussian() * 0.3, player.getY() + 0.4 + random.nextDouble() * 0.8,
				player.getZ() + random.nextGaussian() * 0.3, 0.0, 0.03, 0.0);
		}
		BlockPos centre = player.blockPosition();
		for (int i = 0; i < STEAM_SAMPLES; i++) {
			BlockPos at = centre.offset(random.nextInt(STEAM_RANGE * 2 + 1) - STEAM_RANGE, random.nextInt(7) - 3, random.nextInt(STEAM_RANGE * 2 + 1) - STEAM_RANGE);
			if (level.getBlockState(at).is(Blocks.WATER) && level.getBlockState(at.above()).isAir() && Resting.magmaBelow(level, at)) {
				level.addParticle(ParticleTypes.CLOUD, at.getX() + random.nextDouble(), at.getY() + 1.0, at.getZ() + random.nextDouble(), 0.0, 0.04, 0.0);
			}
		}
	}
}
