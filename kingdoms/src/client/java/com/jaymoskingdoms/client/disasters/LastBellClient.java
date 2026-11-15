package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.LastBell;
import com.jaymoskingdoms.disasters.RedSky;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The client's side of the Last Bell and the great disasters' sky ({@link LastBell}, {@link RedSky}): opens the bell's
 * map, plays its visions (the sky flashing red, the ground shaking under its holder, a toll from nowhere; worse each
 * ringing, no words), eases the end's red and a star's dust toward what the server says, and darkens the sky in the
 * signs before a great disaster.
 */
public final class LastBellClient {
	/** How hard a vision shakes the view (degrees, read by {@link CalamitiesClient}). */
	public static float shake;
	/** A vision: how long it runs (ticks: base, plus per level), how strong at its first and per level after. */
	static final int VISION_TICKS = 50, VISION_TICKS_PER_LEVEL = 15;
	static final float VISION_STRENGTH = 0.35F, VISION_PER_LEVEL = 0.13F, VISION_SHAKE = 2.4F;
	/** How fast the sky's red and dust ease toward the server's (per tick). */
	static final float SKY_EASE = 0.005F;
	private static int visionLeft, visionLength;
	private static float visionStrength;

	private LastBellClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LastBell.OpenMap.TYPE, (payload, context) -> context.client().gui.setScreen(new BellMapScreen(payload.range())));
		ClientPlayNetworking.registerGlobalReceiver(LastBell.Vision.TYPE, (payload, context) -> vision(context.client(), payload.level()));
		ClientPlayNetworking.registerGlobalReceiver(RedSky.Sync.TYPE, (payload, context) -> {
			RedSky.clientRedTarget = payload.red();
			RedSky.clientDustTarget = payload.dust();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			RedSky.clientRed = RedSky.clientDust = RedSky.clientDark = RedSky.clientFlash = 0.0F;
			RedSky.clientRedTarget = RedSky.clientDustTarget = 0.0F;
			shake = 0.0F;
			visionLeft = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(LastBellClient::tick);
	}

	private static void vision(Minecraft minecraft, int level) {
		visionLength = VISION_TICKS + VISION_TICKS_PER_LEVEL * level;
		visionLeft = visionLength;
		visionStrength = Math.min(1.0F, VISION_STRENGTH + VISION_PER_LEVEL * (level - 1));
		if (minecraft.player != null && minecraft.level != null) {
			// the toll of a bell that isn't there, lower the worse it is
			float pitch = Math.max(0.5F, 0.75F - 0.04F * level);
			minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY() + 6.0, minecraft.player.getZ(), SoundEvents.BELL_BLOCK,
				SoundSource.AMBIENT, 1.0F + 0.2F * level, pitch, false);
			minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY() - 4.0, minecraft.player.getZ(), SoundEvents.BELL_RESONATE,
				SoundSource.AMBIENT, 0.8F + 0.1F * level, pitch * 0.8F, false);
		}
	}

	private static float ease(float now, float target) {
		return now < target ? Math.min(target, now + SKY_EASE) : Math.max(target, now - SKY_EASE);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		boolean overworld = level.dimension() == Level.OVERWORLD;
		RedSky.clientRed = ease(RedSky.clientRed, overworld ? RedSky.clientRedTarget : 0.0F);
		RedSky.clientDust = ease(RedSky.clientDust, overworld ? RedSky.clientDustTarget : 0.0F);
		RedSky.clientDark = overworld ? CalamitiesClient.sign : 0.0F;
		if (visionLeft <= 0) {
			RedSky.clientFlash = Math.max(0.0F, RedSky.clientFlash - 0.1F);
			shake = Math.max(0.0F, shake - 0.1F);
			return;
		}
		visionLeft--;
		RandomSource random = level.getRandom();
		float through = 1.0F - visionLeft / (float)visionLength;
		// it comes on hard and fades; the sky flickers red in fits, the ground under them heaves
		float fade = Mth.clamp(through < 0.15F ? through / 0.15F : (1.0F - through) / 0.85F, 0.0F, 1.0F);
		float fit = random.nextFloat() < 0.25F ? 1.0F : 0.55F + 0.2F * Mth.sin(visionLeft * 0.7F);
		RedSky.clientFlash = visionStrength * fade * fit;
		shake = visionStrength * fade * VISION_SHAKE;
		if (random.nextInt(18) == 0 && minecraft.player != null) {
			level.playLocalSound(minecraft.player.getX() + random.nextGaussian() * 3.0, minecraft.player.getY() - 3.0,
				minecraft.player.getZ() + random.nextGaussian() * 3.0, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT,
				0.2F * visionStrength * fade, 0.2F, false);
		}
	}
}
