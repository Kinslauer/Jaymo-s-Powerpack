package com.jaymospowers.client.stamina;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.LandingRoll;
import com.jaymospowers.power.web.WebParkour;
import com.jaymospowers.stamina.Dodge;
import com.jaymospowers.stamina.Feathers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.5) The dodge on this side (see {@link Dodge}): the Dodge key (Left Alt), or Sneak tapped twice within
 * {@link #DOUBLE_TAP_TICKS} (the gesture Combat Roll had here, kept). R and Sneak + R belong to the powers. The hop
 * goes the way the movement keys point (back with none held), at once, and the server is told.
 * <p>Carried over from the Sneak gesture: no dodge from Sneak while clinging to a wall or hanging from a bar (web
 * parkour's or a freerunner's: Sneak lets go there), nor until {@link #CLING_SETTLE} ticks after landing from one; and
 * none from Web's double-Sneak yank.
 */
public final class DodgeClient {
	/** The most ticks between two Sneak presses that count as a double tap. */
	private static final int DOUBLE_TAP_TICKS = 6;
	/** A first press held longer than this (ticks) is a hold, not a tap (a freerunner's slide is a held Sneak). */
	private static final int TAP_HOLD_TICKS = 3;
	/** After a cling or bar hang, ticks on the ground before Sneak taps dodge again. */
	private static final int CLING_SETTLE = 10;

	private static @Nullable KeyMapping dodgeKey;
	private static boolean sneakWasDown;
	private static int sinceLastTap = Integer.MAX_VALUE;
	/** How long Sneak has been held down this press. */
	private static int held;
	private static boolean afterCling;
	private static int groundedAfterCling;
	/** Ticks until the next dodge (the server's {@link Dodge#COOLDOWN}, kept here too so the hop isn't spent twice). */
	private static int cooldown;
	/** (0.15.67) Sure Footing: ticks left in which a dodge pressed in the air still rolls you as you land ({@link LandingRoll}). */
	private static int landingRoll;
	/** Ticks since this player last dodged (the HUD flashes the row). */
	public static int sinceDodge = Integer.MAX_VALUE;

	private DodgeClient() {
	}

	public static void init() {
		dodgeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.dodge", InputConstants.KEY_LALT, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(DodgeClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (cooldown > 0) {
			cooldown--;
		}
		if (sinceDodge < Integer.MAX_VALUE) {
			sinceDodge++;
		}
		if (player == null || minecraft.gui.screen() != null) {
			sneakWasDown = false;
			sinceLastTap = Integer.MAX_VALUE;
			landingRoll = 0;
			return;
		}
		boolean wanted = false;
		KeyMapping key = dodgeKey;
		while (key != null && key.consumeClick()) {
			wanted = true;
		}
		if (sneakDoubleTap(minecraft, player)) {
			wanted = true;
		}
		if (wanted && !player.onGround() && player.getDeltaMovement().y < 0.0 && Freerun.perk(player, Freerun.Perk.SURE_FOOTING) > 0) {
			// falling: held until you land
			landingRoll = LandingRoll.EARLY_TICKS;
		} else if (wanted) {
			tryDodge(player, false);
		}
		if (landingRoll > 0) {
			if (player.onGround()) {
				landingRoll = 0;
				tryDodge(player, true);
			} else if (player.isInWater() || player.getAbilities().flying) {
				landingRoll = 0;
			} else {
				landingRoll--;
			}
		}
	}

	/** Sneak pressed twice in quick succession (and not one of the moves where Sneak means something else). */
	private static boolean sneakDoubleTap(Minecraft minecraft, LocalPlayer player) {
		boolean sneak = minecraft.options.keyShift.isDown();
		if (sinceLastTap < Integer.MAX_VALUE) {
			sinceLastTap++;
		}
		Integer move = player.getAttached(WebParkour.MOVE);
		if (move != null && WebParkour.kind(move) == WebParkour.CLING || FreerunClient.holding()) {
			afterCling = true;
			groundedAfterCling = 0;
		} else if (afterCling && (player.onGround() || player.isInWater()) && ++groundedAfterCling >= CLING_SETTLE) {
			afterCling = false;
		}
		// double Sneak with two webs on small things is Web's yank, not a dodge
		if (afterCling || player.tickCount - com.jaymospowers.client.power.web.WebSwing.reelReadyTick <= 1) {
			sinceLastTap = Integer.MAX_VALUE;
			sneakWasDown = sneak;
			return false;
		}
		held = sneak ? held + 1 : 0;
		if (held > TAP_HOLD_TICKS) {
			// held, not tapped: it doesn't start a double tap
			sinceLastTap = Integer.MAX_VALUE;
		}
		boolean tapped = false;
		if (sneak && !sneakWasDown) {
			if (sinceLastTap <= DOUBLE_TAP_TICKS) {
				tapped = true;
				sinceLastTap = Integer.MAX_VALUE;
			} else {
				sinceLastTap = 0;
			}
		}
		sneakWasDown = sneak;
		return tapped;
	}

	/** {@code landing}: a landing roll, which goes forward when no movement key is held. */
	private static void tryDodge(LocalPlayer player, boolean landing) {
		if (cooldown > 0 || player.isSpectator() || !Dodge.canDodge(player) || FreerunClient.active() || !Feathers.canSpend(player, Dodge.COST)) {
			return;
		}
		Input input = player.input.keyPresses;
		float forward = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float strafe = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		if (forward == 0.0F && strafe == 0.0F) {
			forward = landing ? 1.0F : -1.0F;
		}
		// the movement keys turned to the world by the player's facing (as vanilla's own input is)
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		Vec3 dir = new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(dir.x * Dodge.SPEED + motion.x * 0.2, Dodge.UP, dir.z * Dodge.SPEED + motion.z * 0.2);
		player.setSprinting(false);
		cooldown = Dodge.COOLDOWN;
		sinceDodge = 0;
		if (ClientPlayNetworking.canSend(Dodge.Start.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Start((float)dir.x, (float)dir.z));
		}
	}
}
