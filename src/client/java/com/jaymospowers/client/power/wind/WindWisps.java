package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindModes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The cloak's drift, drawn for everyone in view ({@link WindModes#DRIFTING}): a little cloud under the drifting wind
 * and breeze wisps curling up around it. After landing the wisps thin out over {@link #FADE_TICKS}.
 */
final class WindWisps {
	/** How long the wisps linger after the drift ends (ticks). */
	static final int FADE_TICKS = 25;
	/** Wisp circle radius around the body, and how fast they turn (radians a tick). */
	private static final double RADIUS = 0.55, TURN = 0.35;

	/** Each player's ticks of fade left (FADE_TICKS while drifting). */
	private static final Map<Integer, Integer> FADING = new HashMap<>();

	private WindWisps() {
	}

	static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			FADING.clear();
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		for (Player player : minecraft.level.players()) {
			int left;
			if (player.getAttached(WindModes.DRIFTING) != null) {
				left = FADE_TICKS;
			} else {
				left = FADING.getOrDefault(player.getId(), 0) - 1;
			}
			if (left <= 0) {
				FADING.remove(player.getId());
				continue;
			}
			FADING.put(player.getId(), left);
			float strength = left / (float)FADE_TICKS;
			boolean drifting = left == FADE_TICKS;
			// the cloud underfoot (only while drifting)
			if (drifting && player.tickCount % 2 == 0) {
				minecraft.level.addParticle(ParticleTypes.CLOUD, player.getX() + (random.nextDouble() - 0.5) * 0.6, player.getY() - 0.1,
					player.getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, -0.03, 0.0);
			}
			// two wisps curling round the body, rising; fewer as they fade
			if (random.nextFloat() < strength) {
				for (int i = 0; i < 2; i++) {
					double angle = player.tickCount * TURN + i * Math.PI;
					double height = Mth.frac(player.tickCount * 0.04 + i * 0.5) * player.getBbHeight();
					minecraft.level.addParticle(ParticleTypes.WHITE_ASH, player.getX() + Math.cos(angle) * RADIUS, player.getY() + height,
						player.getZ() + Math.sin(angle) * RADIUS, -Math.sin(angle) * 0.04, 0.03, Math.cos(angle) * 0.04);
				}
			}
			if (!drifting && random.nextFloat() < strength * 0.4F) {
				minecraft.level.addParticle(ParticleTypes.POOF, player.getX(), player.getY() + 0.2, player.getZ(),
					(random.nextDouble() - 0.5) * 0.05, 0.02, (random.nextDouble() - 0.5) * 0.05);
			}
		}
	}
}
