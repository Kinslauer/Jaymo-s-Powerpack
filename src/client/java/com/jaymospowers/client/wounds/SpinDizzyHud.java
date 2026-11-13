package com.jaymospowers.client.wounds;

import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.SpinDizzy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/**
 * The warning signs of spinning too long ({@link SpinDizzy}) on your own screen, growing from nothing at
 * {@link SpinDizzy#WARN} to their worst at {@link SpinDizzy#LIMIT}:
 * <ul>
 * <li><b>Sway:</b> the view drifts side to side and up and down ({@link #YAW_SWAY}, {@link #PITCH_SWAY}) and rolls
 * ({@link #ROLL}), as the world keeps turning after you've stopped.</li>
 * <li><b>Wobbly steering:</b> walking, your feet weave aside ({@link #WEAVE}) and you go slower ({@link #SLOW});
 * standing, you stagger a little once it's bad ({@link #STAGGER}) ({@code FaintInputMixin}).</li>
 * <li><b>Sound:</b> a woozy warble in your own ears every {@link #WARBLE_TICKS}, louder and lower as it gets worse.</li>
 * </ul>
 * Nothing while you're down (out cold is black). After coming round it wears off over a few seconds.
 */
public final class SpinDizzyHud {
	/** At the worst: the view's sideways and up-down drift (degrees a tick) and its roll (degrees). */
	static final float YAW_SWAY = 1.6F, PITCH_SWAY = 0.6F, ROLL = 8.0F;
	/** At the worst: how far walking weaves aside, the share of speed lost, and the stagger standing (from halfway). */
	static final float WEAVE = 0.55F, SLOW = 0.25F, STAGGER = 0.3F;
	/** How fast the wooziness follows the server's (share a tick). */
	static final float EASE = 0.08F;
	/** The warble: every this many ticks; its volume and pitch at the first sign and at the worst. */
	static final int WARBLE_TICKS = 40;
	static final float WARBLE_QUIET = 0.25F, WARBLE_LOUD = 0.6F, WARBLE_HIGH = 1.5F, WARBLE_LOW = 0.7F;

	private static float woozy, lastWoozy;
	private static int sinceWarble = WARBLE_TICKS;

	private SpinDizzyHud() {
	}

	/** From {@link WoundsHud#init}. */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SpinDizzyHud::tick);
	}

	private static void tick(Minecraft minecraft) {
		lastWoozy = woozy;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !player.isAlive() || player.isSpectator()) {
			woozy = lastWoozy = 0.0F;
			sinceWarble = WARBLE_TICKS;
			return;
		}
		float target = Downed.isDowned(player) ? 0.0F : SpinDizzy.woozy(player);
		woozy = target > woozy ? Math.min(target, woozy + EASE) : Math.max(target, woozy - EASE);
		if (woozy <= 0.0F) {
			sinceWarble = WARBLE_TICKS;
			return;
		}
		// the world keeps turning: the view drifts
		float t = player.tickCount;
		player.setYRot(player.getYRot() + Mth.sin(t * 0.15F) * YAW_SWAY * woozy);
		player.setXRot(Mth.clamp(player.getXRot() + Mth.sin(t * 0.09F + 1.0F) * PITCH_SWAY * woozy, -90.0F, 90.0F));
		if (++sinceWarble >= WARBLE_TICKS) {
			sinceWarble = 0;
			player.level().playLocalSound(player, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS,
				Mth.lerp(woozy, WARBLE_QUIET, WARBLE_LOUD), Mth.lerp(woozy, WARBLE_HIGH, WARBLE_LOW));
		}
	}

	/** From {@code FaintInputMixin}, after the keys are read: the walk as it comes out, weaving and slowed. */
	public static Vec2 wobble(Vec2 move) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || woozy <= 0.0F) {
			return move;
		}
		float t = player.tickCount;
		boolean walking = move.lengthSquared() > 1.0E-4F;
		float aside;
		float ahead = move.y;
		if (walking) {
			aside = move.x * (1.0F - SLOW * woozy) + Mth.sin(t * 0.27F) * WEAVE * woozy;
			ahead = move.y * (1.0F - SLOW * woozy);
		} else {
			// standing: a stagger once it's past halfway
			float bad = Math.max(0.0F, woozy * 2.0F - 1.0F);
			aside = Mth.sin(t * 0.19F) * STAGGER * bad;
		}
		return new Vec2(Mth.clamp(aside, -1.0F, 1.0F), Mth.clamp(ahead, -1.0F, 1.0F));
	}

	/** From {@code ScreenShakeMixin}: the first-person view rolls side to side. */
	public static void applyCamera(PoseStack poseStack, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float w = Mth.lerp(partialTicks, lastWoozy, woozy);
		if (player == null || w <= 0.0F || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
			return;
		}
		poseStack.rotateDegrees(Axis.ZP, Mth.sin((player.tickCount + partialTicks) * 0.12F) * ROLL * w);
	}
}
