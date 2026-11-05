package com.jaymospowers.client.stamina;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.LandingRoll;
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
 * (0.15.5) The dodge on this side (see {@link Dodge}): the Dodge key (Left Alt). R and Sneak + R belong to the powers.
 * The hop goes the way the movement keys point (back with none held), at once, and the server is told.
 * <p>(0.20.65) Andy: "alt should be roll". Sneak tapped twice no longer dodges (Combat Roll's old gesture): a double
 * tap held is the zoom now ({@code SneakZoom}), and OK Zoomer's own key came off Left Alt so Alt only dodges.
 */
public final class DodgeClient {
	private static @Nullable KeyMapping dodgeKey;
	/** Ticks until the next dodge (the server's {@link Dodge#COOLDOWN}, kept here too so the hop isn't spent twice). */
	private static int cooldown;
	/** (0.15.67) Ticks left in which a dodge pressed in the air still rolls you as you land ({@link LandingRoll}; everyone's since Motion patch 0). */
	private static int landingRoll;
	/** Ticks since this player last dodged (the HUD flashes the row). */
	public static int sinceDodge = Integer.MAX_VALUE;
	/** (0.15.74) Client game time our own last roll started and which way, drawn at once ({@link DodgeRollPose}). */
	static long rollStart = Long.MIN_VALUE;
	static float rollX, rollZ;

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
			landingRoll = 0;
			return;
		}
		boolean wanted = false;
		KeyMapping key = dodgeKey;
		while (key != null && key.consumeClick()) {
			wanted = true;
		}
		if (wanted && !player.onGround() && player.getDeltaMovement().y < 0.0) {
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
		rollStart = player.level().getGameTime();
		rollX = (float)dir.x;
		rollZ = (float)dir.z;
		if (ClientPlayNetworking.canSend(Dodge.Start.TYPE)) {
			ClientPlayNetworking.send(new Dodge.Start((float)dir.x, (float)dir.z));
		}
	}
}
