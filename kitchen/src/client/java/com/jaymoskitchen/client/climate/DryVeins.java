package com.jaymoskitchen.client.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.climate.Thirst;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.5.43) Andy: veins for water thirst too, "smaller, less emphasized, just to show the user they need water". Once the
 * water runs low ({@link #FROM} points or fewer) thin, dusky veins show at the screen's edges and corners, reaching a
 * little further in and showing a little more as the water runs out; they fade back once you drink. Much quieter than a
 * vampire's blood-thirst veins (Kingdoms {@code ThirstVeins}): three short layers, muted colour, no heartbeat.
 *
 * <p>Art: {@code textures/gui/dry_veins_1..3.png}, drawn by Kingdoms' {@code tools/gen_thirst_veins.py water}.
 */
public final class DryVeins {
	/** The growth layers, outermost first. */
	static final int LAYERS = 3;
	/** Water (points of {@link Thirst#MAX}) at or below which the veins start to show; they're full at none. */
	static final int FROM = 8;
	/** The veins' opacity when they first show and at no water. */
	static final float ALPHA_FAINT = 0.35F, ALPHA_FULL = 0.65F;
	/** How fast they creep in and fade back (per tick). */
	static final float CREEP = 0.01F, RECEDE = 0.03F;

	private static final Identifier[] TEXTURES = new Identifier[LAYERS];

	static {
		for (int i = 0; i < LAYERS; i++) {
			TEXTURES[i] = JaymosKitchen.id("textures/gui/dry_veins_" + (i + 1) + ".png");
		}
	}

	/** Thirst shown (0-1), eased toward the real one. */
	private static float shown, lastShown;

	private DryVeins() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DryVeins::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKitchen.id("dry_veins"), DryVeins::draw);
	}

	private static void tick(Minecraft minecraft) {
		lastShown = shown;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			shown = lastShown = 0.0F;
			return;
		}
		float target = player.isCreative() || player.isSpectator() ? 0.0F
			: Mth.clamp((FROM + 1 - Thirst.water(player)) / (float) (FROM + 1), 0.0F, 1.0F);
		shown = target > shown ? Math.min(target, shown + CREEP) : Math.max(target, shown - RECEDE);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float t = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastShown, shown);
		if (t <= 0.0F) {
			return;
		}
		float reach = t * LAYERS;
		float alpha = Mth.lerp(t, ALPHA_FAINT, ALPHA_FULL);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		for (int i = 0; i < LAYERS; i++) {
			float part = Mth.clamp(reach - i, 0.0F, 1.0F);
			if (part <= 0.0F) {
				break;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[i], 0, 0, 0.0F, 0.0F, w, h, w, h, ARGB.white(alpha * part));
		}
	}
}
