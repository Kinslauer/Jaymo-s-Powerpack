package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;

/**
 * Wisp's held Phase on the client ({@link XenoformState#PHASE}; the pass through blocks itself is {@code WispPhaseMixin}).
 * Everyone sees a phasing Wisp thinned out, its skin nearly gone over fainter ki ({@link WispKi}), flickering through its
 * last second. On its own screen the world takes a pale wash that pulses as the time runs down; with its eyes inside a
 * block it sees out rather than the block's face, and the world beyond is still drawn ({@code WispPhaseViewMixin},
 * {@code WispPhaseCameraMixin}).
 */
public final class WispPhaseClient {
	// ---- tuning ----
	/** How fast the look eases in and out (share a tick). */
	static final float EASE = 0.25F;
	/** The last ticks of a phase flicker, this fast (radians a tick), losing up to this share of the look. */
	static final int WARN_TICKS = 24;
	static final float FLICKER_RATE = 1.4F, FLICKER = 0.5F;
	/** Your own screen: the wash's colour and its opacity (0-255), steady and at the height of a warning pulse. */
	static final int WASH = 0xBFEFFF, WASH_ALPHA = 34, WASH_WARN_ALPHA = 70;

	/** For a frame: how phased the body looks, 0 solid to 1 (null: not at all). */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:wisp_phase");

	/** entity id → {amount, amount before}. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	private WispPhaseClient() {
	}

	/** From {@link XenoformPowerClient#init}. */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WispPhaseClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("xenoform_wisp_phase"), WispPhaseClient::render);
	}

	/** Whether you yourself are phasing (the view mixins ask). */
	public static boolean local() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && XenoformState.phasing(player);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			AMOUNTS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean phasing = XenoformState.phasing(player);
			float[] a = AMOUNTS.get(player.getId());
			if (a == null) {
				if (!phasing) {
					continue;
				}
				a = new float[2];
				AMOUNTS.put(player.getId(), a);
			}
			a[1] = a[0];
			a[0] += ((phasing ? 1.0F : 0.0F) - a[0]) * EASE;
			if (!phasing && a[0] < 0.01F) {
				a[0] = 0.0F;
			}
		}
		AMOUNTS.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	/** How phased {@code entity} looks this frame, the last second's flicker included. */
	private static float amount(Avatar entity, float partialTicks) {
		float[] a = AMOUNTS.get(entity.getId());
		if (a == null) {
			return 0.0F;
		}
		return Mth.lerp(partialTicks, a[1], a[0]) * (1.0F - FLICKER * warning(entity, partialTicks) * (0.5F + 0.5F * Mth.sin((entity.tickCount + partialTicks) * FLICKER_RATE)));
	}

	/** 0 until the last {@link #WARN_TICKS} of a phase, rising to 1 as it runs dry. */
	private static float warning(Avatar entity, float partialTicks) {
		Long dry = entity.getAttached(XenoformState.PHASE);
		if (dry == null) {
			return 0.0F;
		}
		float left = dry - entity.level().getGameTime() - partialTicks;
		return Mth.clamp(1.0F - left / WARN_TICKS, 0.0F, 1.0F);
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float amount = amount(entity, partialTicks);
		state.setData(KEY, amount <= 0.0F ? null : amount);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		float[] a = AMOUNTS.get(player.getId());
		if (a == null) {
			return;
		}
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float amount = Mth.lerp(partialTicks, a[1], a[0]);
		float pulse = warning(player, partialTicks) * (0.5F + 0.5F * Mth.sin((player.tickCount + partialTicks) * FLICKER_RATE * 0.5F));
		int alpha = Mth.clamp(Math.round(amount * Mth.lerp(pulse, WASH_ALPHA, WASH_WARN_ALPHA)), 0, 255);
		if (alpha > 0) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | WASH);
		}
	}
}
