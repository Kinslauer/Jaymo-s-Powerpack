package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Gloom;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * (BWM batch 5) The gloom, felt on the client ({@link Gloom}): from its first stage the dark whispers (cave sounds,
 * quiet, somewhere close by), from its second the screen's edges darken, deeper and slowly breathing at its worst. No
 * meter: the server only says which stage it is.
 */
public final class GloomClient {
	/** Chance each tick of a whisper, by stage (none, whispers, edges, dread). */
	static final float[] WHISPER = {0.0F, 0.004F, 0.008F, 0.015F};
	/** How loud a whisper is, and how far from the player it seems to come (blocks). */
	static final float WHISPER_VOLUME = 0.35F, WHISPER_RANGE = 6.0F;
	/** How dark the edges get, by stage, and how much they breathe at the worst. */
	static final float[] EDGE = {0.0F, 0.0F, 0.45F, 0.75F};
	static final float EDGE_BREATH = 0.12F, BREATH_TICKS = 80.0F;
	/** How fast the edges come in and go out (per tick). */
	static final float FADE = 0.02F;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	/** The edges' darkness now, eased toward the stage's. */
	private static float edge, lastEdge;

	private GloomClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Gloom.Sync.TYPE, (payload, context) -> Gloom.clientStage = Mth.clamp(payload.stage(), 0, 3));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Gloom.clientStage = 0;
			edge = lastEdge = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(GloomClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("gloom"), GloomClient::edges);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		ClientLevel level = minecraft.level;
		lastEdge = edge;
		if (self == null || level == null) {
			edge = lastEdge = 0.0F;
			return;
		}
		int stage = Gloom.clientStage;
		float target = EDGE[stage];
		edge = target > edge ? Math.min(target, edge + FADE) : Math.max(target, edge - FADE);
		if (minecraft.isPaused()) {
			return;
		}
		RandomSource random = self.getRandom();
		if (random.nextFloat() < WHISPER[stage]) {
			double x = self.getX() + (random.nextDouble() * 2.0 - 1.0) * WHISPER_RANGE;
			double y = self.getEyeY() + (random.nextDouble() - 0.5) * 2.0;
			double z = self.getZ() + (random.nextDouble() * 2.0 - 1.0) * WHISPER_RANGE;
			level.playLocalSound(x, y, z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, WHISPER_VOLUME, 0.8F + random.nextFloat() * 0.3F, false);
		}
	}

	/** The dark edges: the vignette pipeline takes away as much of each colour as given, so grey darkens them all. */
	private static void edges(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float e = Mth.lerp(partialTicks, lastEdge, edge);
		if (e <= 0.0F || minecraft.level == null) {
			return;
		}
		if (Gloom.clientStage >= 3) {
			float t = ((minecraft.level.getGameTime() % 10000L) + partialTicks) / BREATH_TICKS;
			e += EDGE_BREATH * (0.5F + 0.5F * Mth.sin(t * Mth.TWO_PI));
		}
		float k = Math.min(1.0F, e);
		int color = ARGB.colorFromFloat(1.0F, k, k, k);
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), color);
	}
}
