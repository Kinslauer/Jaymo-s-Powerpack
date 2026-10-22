package com.jaymospowers.client.power.heritage;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.heritage.HeritageMoves;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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
 * (0.15.72) Thunderclap's and Sunflare's client half ({@link HeritageMoves}): the held left mouse (asked through
 * {@code RaceHoldClient}, which decides when the hold is the race's), the charge bar over the crosshair, and the pose on
 * everyone charging: a Stormborn's right hand raised to the sky, a Dawnborn's hands cupped out in front.
 */
public final class HeritageMovesClient {
	/** Ticks held before the charge starts (a quick click stays a punch), and how often a held button says so again. */
	static final int CHARGE_HOLD = 4, REFRESH = 5;
	/** The bar's width (pixels), how far over the crosshair, and its colours. */
	static final int BAR_WIDTH = 52, BAR_UP = 33;
	static final int STORM_COLOR = 0x8FD3FF, DAWN_COLOR = 0xFFD45A, FULL_COLOR = 0xFFFFFF;
	/** The pose's blood on everyone charging ({@link HeritageMoves#STORM} or {@link HeritageMoves#DAWN}), or null. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:heritage_charge");

	private static int held;
	private static boolean on;

	private HeritageMovesClient() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("heritage_charge"), HeritageMovesClient::renderHud);
	}

	/** From {@code RaceHoldClient}'s tick: {@code down} is the race's left hold, held now by a Stormborn or Dawnborn. */
	public static void held(boolean down) {
		if (!down) {
			held = 0;
			if (on) {
				on = false;
				if (Minecraft.getInstance().getConnection() != null) {
					ClientPlayNetworking.send(new HeritageMoves.Charge(false));
				}
			}
			return;
		}
		held++;
		if (!on && held >= CHARGE_HOLD) {
			on = true;
			ClientPlayNetworking.send(new HeritageMoves.Charge(true));
		} else if (on && held % REFRESH == 0) {
			ClientPlayNetworking.send(new HeritageMoves.Charge(true));
		}
	}

	/** Whether the local player is charging (vanilla's held-click digging stands aside). */
	public static boolean charging() {
		return on;
	}

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		float charge = HeritageMoves.charge(player);
		if (charge < 0.0F) {
			return;
		}
		boolean storm = HeritageMoves.blood(player) == HeritageMoves.STORM;
		boolean full = charge >= 1.0F;
		int color = full ? FULL_COLOR : storm ? STORM_COLOR : DAWN_COLOR;
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 - BAR_UP;
		int x0 = cx - BAR_WIDTH / 2;
		graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(BAR_WIDTH * Mth.clamp(charge, 0.0F, 1.0F)), y + 3, 0xFF000000 | color);
		String move = storm ? "thunderclap" : "sunflare";
		Component label = Component.translatable("hud.jaymospowers.heritage." + move + (full ? ".full" : ""));
		graphics.centeredText(minecraft.font, label.copy().withColor(color), cx, y - 11, 0xFFFFFFFF);
	}

	/** From the avatar renderer's extractRenderState (everyone): which blood's pose, if they're charging. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		Integer pose = null;
		if (entity instanceof Player player && HeritageMoves.charge(player) >= 0.0F) {
			pose = HeritageMoves.blood(player);
		}
		state.setData(KEY, pose);
	}

	/** A Stormborn's right hand raised straight to the sky; a Dawnborn's two hands cupped out along the look. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Integer pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		if (pose == HeritageMoves.STORM) {
			h.rightArm.xRot = -Mth.PI + 0.1F;
			h.rightArm.yRot = 0.0F;
			h.rightArm.zRot = -0.12F;
		} else if (pose == HeritageMoves.DAWN) {
			float aim = -Mth.HALF_PI + avatar.xRot * Mth.DEG_TO_RAD;
			h.rightArm.xRot = aim;
			h.rightArm.yRot = h.head.yRot - 0.35F;
			h.rightArm.zRot = 0.0F;
			h.leftArm.xRot = aim;
			h.leftArm.yRot = h.head.yRot + 0.35F;
			h.leftArm.zRot = 0.0F;
		}
	}
}
