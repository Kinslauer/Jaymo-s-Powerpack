package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterAerialCharge;
import com.jaymospowers.power.zfighter.ZFighterBracers;
import com.jaymospowers.power.zfighter.ZFighterThunderclap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The client's halves of three Z Fighter moves:
 * <ul>
 * <li><b>Aerial Charge</b>: his flight is the client's, so while the server says he is charging ({@link ZFighterAerialCharge#CHARGE})
 * the client flies him along the charge instead ({@link #steer}), and eases off when it ends.</li>
 * <li><b>Deafness</b> (a boxed ear, {@link ZFighterThunderclap.Deaf}): every sound category but the master one drops to
 * {@link #DEAF_GAIN} of what the player set, easing back over the last {@link #EASE_TICKS}, while a high ringing tone plays
 * every {@link #RING_EVERY} ticks on the master category (which is left alone, so it is the one thing heard clearly). There is no
 * text and no bar: the world just goes quiet.</li>
 * <li><b>Bracer clash</b>: a left click while the guard is up is sent to the server ({@link ZFighterBracers.Clash}).</li>
 * </ul>
 */
public final class ZFighterGammaClient {
	/** What is left of every sound while deaf (a share of the player's own volume), and the ticks the sound takes to come back. */
	static final float DEAF_GAIN = 0.08F;
	static final int EASE_TICKS = 30;
	/** The ringing tone: how often (ticks), its pitch and volume. */
	static final int RING_EVERY = 36;
	static final float RING_PITCH = 2.0F, RING_VOLUME = 0.5F;

	private static int deafLeft;
	private static float applied = 1.0F;
	private static boolean charging, attackWas;

	private ZFighterGammaClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterThunderclap.Deaf.TYPE, (payload, context) -> deafLeft = payload.ticks());
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterGammaClient::deafTick);
	}

	/** The velocity he flies at this tick: the charge's while it lasts, a hint of it after, else {@code velocity} as it was. */
	static Vec3 steer(LocalPlayer player, Vec3 velocity) {
		ZFighterAerialCharge.Run run = player.getAttached(ZFighterAerialCharge.CHARGE);
		if (run != null) {
			charging = true;
			return ZFighterAerialCharge.direction(player, run.dive()).scale(run.speed());
		}
		if (charging) {
			charging = false;
			return velocity.scale(0.1);
		}
		return velocity;
	}

	/** Every client tick, from the power's own tick: left click with the guard up is the clash. */
	static void guardTick(Minecraft minecraft, boolean guard) {
		boolean down = guard && minecraft.options.keyAttack.isDown();
		if (down && !attackWas) {
			ClientPlayNetworking.send(new ZFighterBracers.Clash());
		}
		attackWas = down;
	}

	private static void deafTick(Minecraft minecraft) {
		if (minecraft.player == null) {
			deafLeft = 0;
		} else if (deafLeft > 0) {
			deafLeft--;
		}
		float want = deafLeft <= 0 ? 1.0F : deafLeft >= EASE_TICKS ? DEAF_GAIN : DEAF_GAIN + (1.0F - DEAF_GAIN) * (1.0F - deafLeft / (float)EASE_TICKS);
		if (Math.abs(want - applied) >= 0.04F || want == 1.0F && applied != 1.0F || want == DEAF_GAIN && applied != DEAF_GAIN) {
			applied = want;
			for (SoundSource source : SoundSource.values()) {
				if (source != SoundSource.MASTER) {
					minecraft.getSoundManager().updateCategoryVolume(source, minecraft.options.getFinalSoundSourceVolume(source) * applied);
				}
			}
		}
		if (deafLeft > EASE_TICKS && deafLeft % RING_EVERY == 0 && minecraft.player != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, RING_PITCH, RING_VOLUME));
		}
	}
}
