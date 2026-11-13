package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Exposure;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Overcome;
import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The client's half of {@link Exposure} and {@link Overcome}:
 * <ul>
 * <li><b>Unease</b> ({@link Overcome#UNEASE}): from the uneasy mark the screen's edges go cold and dark, deeper as it climbs,
 * and your own heart beats in your ears, quicker and louder the nearer you are to going under. No meter, no text.</li>
 * <li><b>Ethereal sight</b> (Initiated): what walks unseen shows to you as a pale see-through shape, as it would to a
 * spectator ({@code EtherealSightMixin}). Players hidden by a potion stay hidden.</li>
 * <li><b>Unveiled</b>: the hidden cursed the server names ({@link Exposure.Unveiled}) are outlined faintly in violet
 * ({@code UnveiledGlowMixin}, {@code UnveiledGlowColorMixin}).</li>
 * </ul>
 */
public final class OccultClient {
	/** Unease from which the edges and the heart start (as {@link Overcome#UNEASY}). */
	static final float FROM = 35.0F;
	/** How dark the edges get at the worst, and how fast they follow (per tick). */
	static final float EDGE = 0.7F, FADE = 0.03F;
	/** The heartbeat's gap (ticks) at the uneasy mark and at the worst, and its loudness at each. */
	static final int BEAT_SLOW = 30, BEAT_FAST = 11;
	static final float BEAT_QUIET = 0.35F, BEAT_LOUD = 0.9F;
	/** The outline the Unveiled see the hidden cursed in. */
	public static final int UNVEILED_COLOR = 0x9B7BD8;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	private static float edge, lastEdge;
	private static int nextBeat;
	private static volatile Set<Integer> unveiled = Set.of();

	private OccultClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Exposure.Unveiled.TYPE, (payload, context) -> unveiled = new HashSet<>(payload.ids()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			unveiled = Set.of();
			edge = lastEdge = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(OccultClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("unease"), OccultClient::edges);
	}

	/** How far into unease the local player is (0 below the uneasy mark, 1 at the worst). */
	private static float depth(LocalPlayer self) {
		return Mth.clamp((Overcome.unease(self) - FROM) / (100.0F - FROM), 0.0F, 1.0F);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		lastEdge = edge;
		if (self == null || minecraft.level == null) {
			edge = lastEdge = 0.0F;
			return;
		}
		boolean uneasy = Overcome.unease(self) >= FROM;
		float d = depth(self);
		float target = uneasy ? 0.25F + (EDGE - 0.25F) * d : 0.0F;
		edge = target > edge ? Math.min(target, edge + FADE) : Math.max(target, edge - FADE);
		if (minecraft.isPaused() || !uneasy) {
			nextBeat = 0;
			return;
		}
		if (--nextBeat <= 0) {
			nextBeat = Math.round(Mth.lerp(d, BEAT_SLOW, BEAT_FAST));
			minecraft.level.playLocalSound(self.getX(), self.getEyeY(), self.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
				Mth.lerp(d, BEAT_QUIET, BEAT_LOUD), 0.9F + d * 0.2F, false);
		}
	}

	private static void edges(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float e = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastEdge, edge);
		if (e <= 0.0F || Minecraft.getInstance().level == null) {
			return;
		}
		float k = Math.min(1.0F, e);
		// cold: the blue is taken away less than the rest
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), ARGB.colorFromFloat(1.0F, k, k * 0.9F, k * 0.6F));
	}

	/** Initiated and up: {@code entity}, though unseen, shows to this player as a pale shape (never another player). */
	public static boolean ethereal(Entity entity) {
		LocalPlayer self = Minecraft.getInstance().player;
		return self != null && entity != self && !(entity instanceof Player) && Exposure.rankOf(self) >= Exposure.INITIATED;
	}

	/** Whether {@code entity} is one of the hidden cursed this Unveiled player sees through just now. */
	public static boolean unveiled(Entity entity) {
		return !unveiled.isEmpty() && unveiled.contains(entity.getId());
	}
}
