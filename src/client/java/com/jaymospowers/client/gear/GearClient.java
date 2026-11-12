package com.jaymospowers.client.gear;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.combat.ShieldBang;
import com.jaymospowers.gear.GearNet;
import com.jaymospowers.gear.GearPicks;
import com.jaymospowers.gear.shield.ShieldMoves;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * The gear on this side: the thrown gear's drawing, the three gear keys (next arrowhead in the worn quiver, next throwable on
 * the belt, throw it), and the shield moves' inputs. The shield moves read the keys directly while a shield is up (vanilla
 * eats clicks while you block): attack throws it (sneak: slams it down; with a weapon in the main hand the shield bang owns the
 * plain click), sprint charges behind it. Standing still to brace needs no input.
 */
public final class GearClient {
	/** The charge's push each tick (blocks/tick) and how long it lasts (ticks; matches the server's). */
	static final double CHARGE_SPEED = 0.55;

	private static KeyMapping quiverKey, beltCycleKey, beltThrowKey;
	private static boolean attackWas, sprintWas;
	private static int charging, chargeWait;

	private GearClient() {
	}

	public static void init() {
		quiverKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.quiver_next", InputConstants.KEY_LBRACKET, JaymosPowersClient.CATEGORY));
		beltCycleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.belt_next", InputConstants.KEY_BACKSLASH, JaymosPowersClient.CATEGORY));
		beltThrowKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.belt_throw", InputConstants.KEY_M, JaymosPowersClient.CATEGORY));
		EntityRenderers.register(GearPicks.THROWN, ThrownItemRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(GearClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.getConnection() == null) {
			return;
		}
		boolean open = minecraft.gui.screen() == null;
		while (quiverKey.consumeClick()) {
			if (open) {
				ClientPlayNetworking.send(new GearNet.Key(GearNet.QUIVER_CYCLE));
			}
		}
		while (beltCycleKey.consumeClick()) {
			if (open) {
				ClientPlayNetworking.send(new GearNet.Key(GearNet.BELT_CYCLE));
			}
		}
		while (beltThrowKey.consumeClick()) {
			if (open) {
				ClientPlayNetworking.send(new GearNet.Key(GearNet.BELT_THROW));
			}
		}
		boolean shield = open && !player.isSpectator() && Guard.raised(player) && !Guard.weaponGuard(player.getUseItem());
		boolean attack = minecraft.options.keyAttack.isDown();
		boolean sprint = minecraft.options.keySprint.isDown();
		if (shield) {
			if (attack && !attackWas) {
				if (player.isShiftKeyDown()) {
					ClientPlayNetworking.send(new GearNet.Shield(ShieldMoves.SLAM));
				} else if (!ShieldBang.ready(player)) {
					ClientPlayNetworking.send(new GearNet.Shield(ShieldMoves.THROW));
				}
			}
			if (sprint && !sprintWas && player.onGround() && charging <= 0 && chargeWait <= 0 && !player.isPassenger()) {
				ClientPlayNetworking.send(new GearNet.Shield(ShieldMoves.CHARGE));
				charging = ShieldMoves.CHARGE_TICKS;
				chargeWait = ShieldMoves.CHARGE_COOLDOWN;
			}
		}
		attackWas = attack;
		sprintWas = sprint;
		if (chargeWait > 0) {
			chargeWait--;
		}
		if (charging > 0) {
			charging--;
			if (player.onGround() && !player.isPassenger()) {
				Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
				player.setDeltaMovement(look.x * CHARGE_SPEED, player.getDeltaMovement().y, look.z * CHARGE_SPEED);
			}
		}
	}
}
