package com.jaymospowers.client.power.web;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.fx.MagicFxClient;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.jaymospowers.power.web.WebAnchors;
import com.jaymospowers.power.web.WebCooldowns;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebShooters;
import com.jaymospowers.power.web.WebSymbiote;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Zip landing marker. Andy's pick from Legends: Superheroes, "while a Zip is ready, a small ring shows on the block
 * where you would land, fading in as you aim and turning red past range". While Zip (Z) is unlocked and off cooldown
 * the ring sits on the face of the block the crosshair is on, the very ray Zip fires along ({@link WebPower#ZIP_RANGE});
 * it fades in over {@link #FADE_TICKS} ticks of holding the aim there, and is red where the block is further than Zip
 * reaches (so Z would only fire the web out into the air). It is the MagicFx ring ({@link MagicFxClient#drawRing}) held
 * still, so it is the same look as the rest of the pack's rings. Turned off from the HUD arrange screen's button
 * ({@link #enabled}).
 */
public final class WebZipMarker {
	/** Ticks of aiming at a block for the ring to be fully in; the ring's colour in reach and out of it. */
	static final int FADE_TICKS = 8;
	static final int COLOR_IN_REACH = 0xCFE8FF, COLOR_PAST_RANGE = 0xFF2A2A;
	/** How much further than Zip's reach the aim is followed, so a block just past it still gets a (red) ring. */
	static final double PAST_RANGE = 1.5;
	/** The ring's radius (blocks) near, and how much it grows per block of distance (so it can still be seen far off), at most. */
	static final float RADIUS = 0.45F, RADIUS_PER_BLOCK = 0.012F, RADIUS_MAX = 1.3F;
	/** The strongest the ring is drawn (0-1). */
	static final float ALPHA = 0.8F;
	/** The setting that turns the marker off, in {@code config/jaymospowers-client.properties}. */
	static final String OFF = "web.zip_marker.off";

	private static @Nullable Vec3 at;
	private static Vec3 normal = new Vec3(0.0, 1.0, 0.0);
	private static boolean past;
	private static int aimTicks;
	private static float distance;

	private WebZipMarker() {
	}

	static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WebZipMarker::render);
	}

	/** Whether the marker is on (it is, unless the HUD arrange screen's button turned it off). Kept on this computer. */
	public static boolean enabled() {
		return !"true".equals(ZFighterClientConfig.get(OFF, "false"));
	}

	public static void setEnabled(boolean on) {
		ZFighterClientConfig.set(OFF, on ? "false" : "true");
		if (!on) {
			clear();
		}
	}

	static void clear() {
		at = null;
		aimTicks = 0;
	}

	/** Every tick of the web slinger: where Zip would land, if it is ready. */
	static void tick(LocalPlayer player, WebSwing swing) {
		WebCooldowns cooldowns = player.getAttached(WebPower.COOLDOWNS);
		boolean ready = enabled() && !player.isSpectator() && player.isAlive() && JaymosPowersClient.isUnlocked(PowerKey.ABILITY_2)
			&& (cooldowns == null || cooldowns.zip() <= player.level().getGameTime()) && swing.pull == null && !WebSymbioteClient.driving()
			&& !WebShooters.blocked(player, WebNet.MAIN);
		if (!ready) {
			clear();
			return;
		}
		double range = WebPower.ZIP_RANGE * WebSymbiote.reachScale(player) * WebShooters.rangeScale(player, WebNet.MAIN);
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(range * PAST_RANGE)));
		if (hit.getType() == HitResult.Type.MISS) {
			at = null;
			aimTicks = 0;
			return;
		}
		Vec3 where = hit.getLocation();
		if (at == null || at.distanceToSqr(where) > 0.25) {
			// a new place: it fades in again
			aimTicks = Math.min(aimTicks, 2);
		}
		at = where;
		normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
		distance = (float)eye.distanceTo(where);
		past = distance > range;
		aimTicks = Math.min(FADE_TICKS, aimTicks + 1);
	}

	private static void render(LevelRenderContext context) {
		Vec3 where = at;
		if (where == null || aimTicks <= 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float fade = Math.min(1.0F, (aimTicks + partial) / FADE_TICKS);
		float time = (minecraft.level.getGameTime() % 4000L) + partial;
		// a gentle pulse on top of the fade
		float alpha = ALPHA * fade * (0.85F + 0.15F * (float)Math.sin(time * 0.35F));
		// the MagicFx ring is clear at a life of 1 and solid at 0 (its radius swells with life too): hold it by life and size it back up
		float life = 1.0F - alpha;
		float swell = 1.0F - (1.0F - life) * (1.0F - life);
		float radius = Math.min(RADIUS_MAX, RADIUS + RADIUS_PER_BLOCK * distance) / Math.max(0.2F, swell);
		MagicFxClient.drawRing(context.submitNodeCollector(), context.poseStack(), where.subtract(context.levelState().cameraRenderState.pos), normal,
			radius, past ? COLOR_PAST_RANGE : COLOR_IN_REACH, time, life);
	}
}
