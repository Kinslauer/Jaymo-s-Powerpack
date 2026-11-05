package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Fainting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.111) Fainting ({@link Fainting}) on your own screen, and the swoon's lost control.
 * <ul>
 * <li><b>Light-headed:</b> darkness closes in from the edges ({@link #TUNNEL_LEAST} to {@link #TUNNEL_MOST} of the way
 * in), deeper the emptier you are, slowly breathing in and out. No change to the field of view itself: only the edges
 * darken, so the view stays with your head.</li>
 * <li><b>Swooning:</b> the darkness comes in further ({@link #TUNNEL_SWOON}) and the letterbox (Adrenaline's bars) slides
 * shut over the whole swoon. The head droops toward the ground ({@link #DROOP}) and sways ({@link #SWAY}), and your
 * keys stop answering ({@code FaintInputMixin}): whatever you were doing as it began goes on, slowing ({@link #SLOW}) and
 * weaving ({@link #WEAVE}). A slow heartbeat, slower as you go.</li>
 * <li><b>Out:</b> shut, black. <b>Coming round:</b> the bars open over {@link Fainting#WAKE}.</li>
 * </ul>
 */
public final class FaintHud {
	/** The darkness: how far in it reaches (share of the shorter side) just light-headed, at nothing, and as you go. */
	static final float TUNNEL_LEAST = 0.06F, TUNNEL_MOST = 0.36F, TUNNEL_SWOON = 0.5F;
	/** Its thickness at the very edge (0-255), light-headed and at nothing; the bands it's drawn in. */
	static final float EDGE_LEAST = 90.0F, EDGE_MOST = 235.0F;
	static final int BANDS = 16;
	/** It breathes by this share, once every this many ticks. */
	static final float BREATHE = 0.07F, BREATHE_TICKS = 70.0F;
	/** How fast the darkness follows your food and water (share a tick). */
	static final float EASE = 0.02F;
	/** Swooning: the pitch the head droops toward (degrees), how fast (share a tick), and its sway (degrees a tick). */
	static final float DROOP = 55.0F, DROOP_RATE = 0.035F, SWAY = 0.9F;
	/** Swooning: the walk slows to this share by the end, and weaves side to side this much. */
	static final float SLOW = 0.3F, WEAVE = 0.45F;
	/** Swooning: a heartbeat every this many ticks as it starts, and as it ends. */
	static final float BEAT_FIRST = 22.0F, BEAT_LAST = 46.0F;

	private static float weak, lastWeak;
	private static int sinceBeat;
	/** The keys as the swoon began (held from then on), or null. */
	private static @Nullable Input heldKeys;
	private static Vec2 heldMove = Vec2.ZERO;

	private FaintHud() {
	}

	/** From {@link WoundsHud#init}. */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(FaintHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("faint_tunnel"), FaintHud::renderTunnel);
		// over the hotbar and bars too: shut is shut
		HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, JaymosPowers.id("faint_letterbox"), FaintHud::renderBars);
	}

	private static void tick(Minecraft minecraft) {
		lastWeak = weak;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
			weak = lastWeak = 0.0F;
			heldKeys = null;
			return;
		}
		float target = Fainting.weakness(player);
		weak = target > weak ? Math.min(target, weak + EASE) : Math.max(target, weak - EASE);
		if (!Fainting.swooning(player)) {
			heldKeys = null;
			sinceBeat = 0;
			return;
		}
		float p = swoon(player, 0.0F);
		// the head goes: drooping toward the ground and swaying
		float time = player.tickCount;
		player.setXRot(Mth.clamp(Mth.lerp(DROOP_RATE * (0.4F + p), player.getXRot(), DROOP), -90.0F, 90.0F));
		player.setYRot(player.getYRot() + Mth.sin(time * 0.13F) * SWAY * p);
		if (++sinceBeat >= Mth.lerp(p, BEAT_FIRST, BEAT_LAST)) {
			sinceBeat = 0;
			player.level().playLocalSound(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, Mth.lerp(p, 0.6F, 0.45F));
		}
	}

	/** How far through the swoon (0-1). */
	private static float swoon(LocalPlayer player, float partial) {
		return Mth.clamp((Fainting.since(player) + partial) / Fainting.SWOON, 0.0F, 1.0F);
	}

	/**
	 * From {@code FaintInputMixin}, after the keys are read: swooning, the keys pressed as it began are held (no jumping,
	 * sneaking or sprinting), slowing and weaving. Returns whether it changed them.
	 */
	public static boolean steer(Input keys, Vec2 move, java.util.function.BiConsumer<Input, Vec2> set) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !Fainting.swooning(player)) {
			return false;
		}
		if (heldKeys == null) {
			heldKeys = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), false, false, false);
			heldMove = move;
		}
		float p = swoon(player, 0.0F);
		float pace = Mth.lerp(p, 1.0F, SLOW);
		boolean walking = heldMove.lengthSquared() > 1.0E-4F;
		float weave = walking ? Mth.sin(player.tickCount * 0.21F) * WEAVE * p : 0.0F;
		set.accept(heldKeys, new Vec2(heldMove.x * pace + weave, heldMove.y * pace));
		return true;
	}

	private static void renderTunnel(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float w0 = Mth.lerp(partial, lastWeak, weak);
		float swoon = Fainting.since(player) >= 0 ? swoon(player, partial) : 0.0F;
		if (w0 <= 0.0F && swoon <= 0.0F) {
			return;
		}
		float reach = Mth.lerp(w0, TUNNEL_LEAST, TUNNEL_MOST);
		float edge = Mth.lerp(w0, EDGE_LEAST, EDGE_MOST) * Math.min(1.0F, w0 * 4.0F);
		if (swoon > 0.0F) {
			float s = swoon * swoon * (3.0F - 2.0F * swoon);
			reach = Mth.lerp(s, reach, TUNNEL_SWOON);
			edge = Mth.lerp(s, edge, 255.0F);
		}
		float breath = 0.5F - 0.5F * Mth.cos((player.tickCount + partial) * Mth.TWO_PI / BREATHE_TICKS);
		tunnel(graphics, reach * (1.0F + BREATHE * breath), edge);
	}

	private static void renderBars(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float t = Fainting.since(player) + delta.getGameTimeDeltaPartialTick(false);
		if (t < 0.0F) {
			return;
		}
		float shut;
		if (t < Fainting.SWOON) {
			float p = t / Fainting.SWOON;
			// slow to start, then closing fast at the end
			shut = p * p;
		} else if (t < Fainting.SWOON + Fainting.OUT) {
			shut = 1.0F;
		} else {
			float p = Mth.clamp((t - Fainting.SWOON - Fainting.OUT) / Fainting.WAKE, 0.0F, 1.0F);
			shut = 1.0F - p * p * (3.0F - 2.0F * p);
		}
		AdrenalineHud.letterbox(graphics, 0.5F * shut);
	}

	/** Black from the edges in, {@code reach} of the shorter side, {@code edge} thick (0-255) at the edge, thinning inward. */
	private static void tunnel(GuiGraphicsExtractor graphics, float reach, float edge) {
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		float far = Math.min(w, h) * reach, band = far / BANDS;
		for (int i = 0; i < BANDS; i++) {
			float t = 1.0F - i / (float) BANDS;
			int alpha = Math.round(edge * t * t);
			if (alpha <= 0) {
				continue;
			}
			int a = Math.round(i * band), b = Math.round((i + 1) * band);
			if (b <= a || a * 2 >= Math.min(w, h)) {
				continue;
			}
			int color = Math.min(255, alpha) << 24;
			// one frame: the top and bottom strips full width, the sides between them
			graphics.fill(a, a, w - a, b, color);
			graphics.fill(a, h - b, w - a, h - a, color);
			graphics.fill(a, b, b, h - b, color);
			graphics.fill(w - b, b, w - a, h - b, color);
		}
	}
}
