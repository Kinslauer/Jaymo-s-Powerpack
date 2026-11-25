package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.glowwood.GlowWoods;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sparkles in the glow-woods at night: soft lights drift up from the giant glowcaps' gills and lanterns and from the
 * fairy rings' mushrooms, with fireflies among them (the same fireflies a Sylvan carries about, in Powers' season aura).
 * Drawn on each client, no packets, no new art: {@link Fx} motes and the vanilla firefly. A client doesn't know which
 * land is a Sylvan haven's, so it looks for the glow-wood blocks themselves, which grow round the havens (and, rarely,
 * wild).
 */
public final class GlowSparkles {
	/** Spots sampled round the player a tick, how far across and up and down (blocks). */
	private static final int SAMPLES = 6, REACH = 18, HEIGHT = 10;
	/** The chance a found glow block sparkles, and that a sparkle is a firefly (else a mote). */
	private static final float SPARKLE_CHANCE = 0.25F, FIREFLY_SHARE = 0.4F;
	/** A mote's colour and size: the glowcap's pale cyan, and the fairy ring's pink. */
	private static final int GLOW_COLOUR = 0x7FF5E8, RING_COLOUR = 0xF7A8E0;
	private static final float MOTE_SIZE = 0.8F;

	private GlowSparkles() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(GlowSparkles::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || minecraft.isPaused() || level.isBrightOutside()) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos centre = minecraft.player.blockPosition();
		for (int i = 0; i < SAMPLES; i++) {
			BlockPos at = centre.offset(random.nextInt(REACH * 2 + 1) - REACH, random.nextInt(HEIGHT * 2 + 1) - HEIGHT, random.nextInt(REACH * 2 + 1) - REACH);
			BlockState state = level.getBlockState(at);
			boolean ring = state.is(GlowWoods.FAIRY_RING);
			if (!ring && !state.is(GlowWoods.GLOWCAP_GILLS) && !state.is(GlowWoods.GLOWCAP_LANTERN)) {
				continue;
			}
			if (random.nextFloat() >= SPARKLE_CHANCE) {
				continue;
			}
			double x = at.getX() + random.nextDouble(), y = at.getY() + 0.5 + random.nextDouble() * 0.8, z = at.getZ() + random.nextDouble();
			if (random.nextFloat() < FIREFLY_SHARE) {
				level.addParticle(ParticleTypes.FIREFLY, x, y, z, 0.0, 0.0, 0.0);
			} else {
				level.addParticle(Fx.wisp(ring ? RING_COLOUR : GLOW_COLOUR, MOTE_SIZE), x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.02, (random.nextDouble() - 0.5) * 0.02);
			}
		}
	}
}
