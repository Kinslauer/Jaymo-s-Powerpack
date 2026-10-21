package com.jaymospowers.client.aether.gear;

import com.jaymospowers.aether.gear.AercloudGliderItem;
import com.jaymospowers.aether.gear.AetherGear;
import com.jaymospowers.aether.gear.ArmorAbilities;
import com.jaymospowers.aether.gear.GravititeTools;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Aether gear's client side: the altar's screen, the gravitite armour's extra jump (the move is the player's own, so it
 * happens here and the server is told), and the gliders' boosts coming back when you land.
 */
public final class AetherGearClient {
	/** The extra jump has been used since the last landing; the jump key was down last tick. */
	private static boolean spent, wasJump;

	private AetherGearClient() {
	}

	public static void init() {
		MenuScreens.register(AetherGear.ALTAR_MENU, AltarScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(AetherGearClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			wasJump = false;
			return;
		}
		AercloudGliderItem.landed(player);
		boolean jump = minecraft.options.keyJump.isDown();
		boolean landed = player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying;
		if (landed) {
			spent = false;
		} else if (jump && !wasJump && !spent && !player.isSpectator() && !player.isPassenger() && !player.isFallFlying() && !player.onClimbable()
			&& ArmorAbilities.gravitite(player)) {
			spent = true;
			double yaw = Math.toRadians(player.getYRot());
			double dx = -Math.sin(yaw) * player.zza + Math.cos(yaw) * player.xxa;
			double dz = Math.cos(yaw) * player.zza + Math.sin(yaw) * player.xxa;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length > 1.0) {
				dx /= length;
				dz /= length;
			}
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x + dx * GravititeTools.JUMP_PUSH, GravititeTools.BASE_JUMP * GravititeTools.JUMP_POWER, v.z + dz * GravititeTools.JUMP_PUSH);
			ClientPlayNetworking.send(new GravititeTools.DoubleJump());
		}
		wasJump = jump;
	}
}
