package com.jaymospowers.client.aether;

import com.jaymospowers.client.sound.PowerLoop;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Andy: "Does the game tell you that you're in the cloud sea?" ... "A change of wind works". No words: crossing into the
 * Cloud Sea (up out of the Overworld's sky, or down out of the Aether) brings a gust, and while you're in it a steady high
 * wind blows, which dies away as you leave.
 */
public final class CloudSeaWind {
	// ---- tuning ----
	/** The gust as you cross in. */
	static final float GUST_VOLUME = 0.6F, GUST_PITCH = 0.6F;
	/** The steady wind inside. */
	static final float WIND_VOLUME = 0.22F, WIND_PITCH = 0.55F;

	private static boolean inside;

	private CloudSeaWind() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CloudSeaWind::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			inside = false;
			return;
		}
		boolean now = AetherVeil.inCloudSea(player.getY());
		if (now && !inside) {
			player.level().playLocalSound(player, SoundEvents.BREEZE_WHIRL, SoundSource.AMBIENT, GUST_VOLUME, GUST_PITCH);
		}
		inside = now;
		PowerLoop.keep("cloud_sea.wind", SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, player, () -> inside, new PowerLoop.Mix() {
			@Override
			public float volume() {
				return inside ? WIND_VOLUME : 0.0F;
			}

			@Override
			public float pitch() {
				return WIND_PITCH;
			}
		});
	}
}
