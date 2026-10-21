package com.jaymospowers.client.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.ember.EmberKindle;
import com.jaymospowers.power.smoke.SmokePower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * (0.14.0) Kindle's client half ({@link EmberKindle}): the held left mouse (asked through {@code RaceHoldClient}, which
 * decides when the hold is the race's), the heat bar over the crosshair (orange, white at full heat, like Smoke's pour
 * bar), and the right arm held out toward the flame, on everyone who kindles. The flame itself is the server's
 * particles, so everyone sees it.
 */
public final class EmberKindleClient {
	/** Ticks held before the flame starts (a quick click stays a punch), and how often a held button says so again. */
	static final int KINDLE_HOLD = 4, REFRESH = 5;
	/** Ticks after asking before the server's word counts: if the flame isn't running by then, it was put out (no food). */
	static final int GRACE = 10;
	/** The bar's width (pixels), how far over the crosshair, and its colours. */
	static final int BAR_WIDTH = 52, BAR_UP = 33, BAR_UP_SMOKE = 47;
	static final int HEAT_COLOR = 0xFF7A1A, WHITE_COLOR = 0xFFF4E0;
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:ember_kindle");

	private static int held;
	private static boolean on;
	/** The server put the flame out while it was still held: nothing more until the button is let go. */
	private static boolean out;

	private EmberKindleClient() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("ember_heat"), EmberKindleClient::renderHud);
	}

	/**
	 * From {@code RaceHoldClient}'s tick: {@code down} is the race's left hold, held now by an Emberborn. Starts the
	 * flame after {@link #KINDLE_HOLD} ticks, says it's still held every {@link #REFRESH}, and lets go.
	 */
	public static void held(boolean down) {
		if (!down) {
			held = 0;
			out = false;
			if (on) {
				on = false;
				if (Minecraft.getInstance().getConnection() != null) {
					ClientPlayNetworking.send(new EmberKindle.Kindle(false));
				}
			}
			return;
		}
		held++;
		LocalPlayer player = Minecraft.getInstance().player;
		if (on && held >= KINDLE_HOLD + GRACE && player != null && !EmberKindle.kindling(player)) {
			// the server stopped it (too hungry, the hand filled): stand aside so the click digs again
			on = false;
			out = true;
		}
		if (out) {
			return;
		}
		if (!on && held >= KINDLE_HOLD) {
			on = true;
			ClientPlayNetworking.send(new EmberKindle.Kindle(true));
		} else if (on && held % REFRESH == 0) {
			ClientPlayNetworking.send(new EmberKindle.Kindle(true));
		}
	}

	/** Whether the local player's flame is running (vanilla's held-click digging and the hold combo stand aside). */
	public static boolean kindling() {
		return on;
	}

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		float heat = EmberKindle.heat(player);
		if (heat <= 0.0F && !EmberKindle.kindling(player)) {
			return;
		}
		Font font = minecraft.font;
		boolean white = heat >= 1.0F;
		int color = white ? WHITE_COLOR : HEAT_COLOR;
		int cx = graphics.guiWidth() / 2;
		// above Smoke's own bar when they have that power too
		int y = graphics.guiHeight() / 2 - (Powers.has(player, SmokePower.INSTANCE) ? BAR_UP_SMOKE : BAR_UP);
		int x0 = cx - BAR_WIDTH / 2;
		graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(BAR_WIDTH * Mth.clamp(heat, 0.0F, 1.0F)), y + 3, 0xFF000000 | color);
		Component label = Component.translatable(white ? "hud.jaymospowers.ember.white" : "hud.jaymospowers.ember.heat");
		graphics.centeredText(font, label.copy().withColor(color), cx, y - 11, 0xFFFFFFFF);
	}

	/** From the avatar renderer's extractRenderState (everyone): whether they're kindling. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player && EmberKindle.kindling(player));
	}

	/** The right arm held straight out along the look, palm to the flame. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || !Boolean.TRUE.equals(avatar.getData(KEY))
			|| avatar.deathTime > 0.0F) {
			return;
		}
		h.rightArm.xRot = -Mth.HALF_PI + avatar.xRot * Mth.DEG_TO_RAD;
		h.rightArm.yRot = h.head.yRot;
		h.rightArm.zRot = 0.0F;
	}
}
