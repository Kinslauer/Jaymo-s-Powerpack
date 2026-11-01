package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.SeizureClient;
import com.jaymoskingdoms.spirits.Grue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.465) The client half of {@link Grue}: while it (or, for a moment, your own spirit pulling you out of flux) has the
 * wheel, it walks you where the server says and turns your head (no movement key answers, {@code GrueInputMixin}); while it
 * has you, your hands answer only for food and drink ({@code GrueHandsMixin}) and the screen's edges throb a deep dark red.
 * Its lashes flash red. The spirit's arms redden with its hold ({@link #tint}), for everyone who sees them.
 */
public final class GrueClient {
	/** The Grue's red in the arms, and the near-black red of their core once it has them. */
	public static final int RED = 0xFF7A0010, CORE = 0xFF2A0006;
	/** The flicker from {@link Grue#STIR}: how often it shows and how red; the streaks from {@link Grue#ASSAULT}: least and most red, and their throb. */
	static final float FLICKER_FROM = 0.8F, FLICKER = 0.55F, FAINT = 0.06F, STREAK_LOW = 0.3F, STREAK_HIGH = 0.7F, STREAK_THROB = 0.12F;
	/** The edges while it has you, their beat (ticks); a lash's flash and how fast it fades a tick; a jolt to your view (degrees). */
	static final float EDGE = 0.7F, BEAT = 24.0F, LASH_FADE = 0.06F, LASH_JOLT = 6.0F;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	private static Vec3 to = Vec3.ZERO;
	private static int face = -1, ticks;
	private static float speed;
	private static boolean grue;
	private static float lash, lastLash;

	private GrueClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Grue.Drive.TYPE, (payload, context) -> {
			to = new Vec3(payload.x(), payload.y(), payload.z());
			face = payload.face();
			speed = payload.speed();
			ticks = payload.ticks();
			grue = payload.grue();
			if (ticks > 0 && grue && context.client().gui.screen() != null && !hostLocked()) {
				context.client().gui.setScreen(null);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Grue.Lash.TYPE, (payload, context) -> {
			lash = Math.max(lash, payload.strength());
			LocalPlayer player = context.client().player;
			if (player != null) {
				player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * 2.0F * LASH_JOLT * payload.strength());
				player.setXRot(Mth.clamp(player.getXRot() + (player.getRandom().nextFloat() - 0.5F) * LASH_JOLT * payload.strength(), -90.0F, 90.0F));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ticks = 0;
			lash = lastLash = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(GrueClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("grue"), GrueClient::hud);
	}

	/** Something else has the wheel: no movement key answers. */
	public static boolean driving() {
		return ticks > 0;
	}

	/** The Grue has you: your hands are its own but for food and drink. */
	public static boolean hostLocked() {
		return Grue.taken(Minecraft.getInstance().player);
	}

	private static void tick(Minecraft minecraft) {
		lastLash = lash;
		lash = Math.max(0.0F, lash - LASH_FADE);
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			ticks = 0;
			return;
		}
		if (ticks <= 0 || minecraft.isPaused()) {
			return;
		}
		ticks--;
		if (!player.isAlive()) {
			ticks = 0;
			return;
		}
		Entity target = face >= 0 ? minecraft.level.getEntity(face) : null;
		SeizureClient.steerAt(player, target != null ? target.getBoundingBox().getCenter() : to, speed);
	}

	// ---- the arms' red ---------------------------------------------------------------------------------------------

	/** How red the arms are (0-1) at this {@code hold}, {@code age} in ticks: nothing, a flicker, streaks, then all. */
	public static float tint(float hold, float age) {
		if (hold < Grue.STIR) {
			return 0.0F;
		}
		if (hold >= Grue.TAKEN) {
			return 1.0F;
		}
		if (hold < Grue.ASSAULT) {
			// a flicker now and then, as if something else showed through
			float s = Mth.sin(age * 0.37F) * Mth.sin(age * 0.11F + 1.3F);
			return s > FLICKER_FROM ? FLICKER : FAINT;
		}
		float share = (hold - Grue.ASSAULT) / (Grue.TAKEN - Grue.ASSAULT);
		return Mth.clamp(Mth.lerp(share, STREAK_LOW, STREAK_HIGH) + STREAK_THROB * Mth.sin(age * 0.3F), 0.0F, 1.0F);
	}

	/** {@code color} reddened by the Grue's hold on {@code entity} (the arms' motes and bursts). */
	public static int tint(Entity entity, int color) {
		float t = tint(Grue.hold(entity), entity.tickCount);
		return t <= 0.0F ? color : ARGB.srgbLerp(t, color, RED);
	}

	/** The arm's pale core, darkened toward near-black red as the Grue takes it (a ghost's light gone out). */
	public static int core(int pale, float grue) {
		return grue <= 0.0F ? pale : ARGB.srgbLerp(grue, pale, grue >= 1.0F ? CORE : RED);
	}

	// ---- the screen ------------------------------------------------------------------------------------------------

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		boolean taken = hostLocked();
		float k = Mth.lerp(partialTicks, lastLash, lash);
		if (taken) {
			float t = ((minecraft.level.getGameTime() % 1000L) + partialTicks) % BEAT / BEAT;
			k = Math.max(k, EDGE * (0.85F + 0.15F * Mth.sin(t * Mth.TWO_PI)));
		}
		if (k > 0.0F) {
			k = Math.min(1.0F, k);
			// (the vignette takes away as much of each colour as given: leave a little red, take the rest)
			int color = ARGB.colorFromFloat(1.0F, 0.35F * k, k, k);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
				graphics.guiHeight(), color);
		}
		if (taken && minecraft.gui.screen() == null) {
			graphics.centeredText(minecraft.font, Component.translatable("hud.jaymoskingdoms.grue.taken"), graphics.guiWidth() / 2,
				graphics.guiHeight() - 72, 0xFFB04050);
		}
	}
}
