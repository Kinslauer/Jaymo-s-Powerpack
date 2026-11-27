package com.jaymospowers.client.freerun;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.aether.gear.ArmorAbilities;
import com.jaymospowers.client.baubles.ArtifactBaublesClient;
import com.jaymospowers.client.compat.CoreMotionPal;
import com.jaymospowers.client.freerun.moves.CoyoteTimeClient;
import com.jaymospowers.client.jump.PowerJumpClient;
import com.jaymospowers.client.moving.LateJumps;
import com.jaymospowers.freerun.moves.SkyDive;
import com.jaymospowers.freerun.CoreMotion;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

public final class CoreMotionClient {
	private static final boolean PAL = FabricLoader.getInstance().isModLoaded("player_animation_library");

	private CoreMotionClient() {
	}

	public static void init() {
		if (PAL) {
			CoreMotionPal.init();
		}
		KeyMapping airStep = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.jaymospowers.air_step", InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (airStep.consumeClick()) {
				step(minecraft);
			}
			tickJump(minecraft);
		});
	}

	/** Jump presses in the air for powers with no second jump of their own ({@code PowerJumpClient} has the rest). */
	private static boolean wasJump, spent;
	private static int air;
	/** Ticks off the ground before a press counts, as {@code PowerJumpClient}'s. */
	private static final int AIR_TICKS = 5;

	/** Free keys for a default are scarce (every letter but L and T is a power's, and those are vanilla's), so a fresh Jump press in the air steps too; the key stays as an option. */
	private static void tickJump(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			wasJump = false;
			spent = false;
			air = 0;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		boolean pressed = jump && !wasJump;
		wasJump = jump;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isPassenger()
			|| player.onClimbable()) {
			spent = false;
			air = 0;
			return;
		}
		air++;
		if (!pressed || spent || air < AIR_TICKS || PowerJumpClient.busy(player) || CoyoteTimeClient.justTook(player) || otherJump(player)) {
			return;
		}
		if (step(minecraft)) {
			spent = true;
		}
	}

	/** Another move already takes this press of Jump: the step only fires when nothing else did. */
	private static boolean otherJump(LocalPlayer player) {
		return !LateJumps.free(player) || ArtifactBaublesClient.cloudJump(player) || PowerJumpClient.takes(player)
			|| ArmorAbilities.gravitite(player) || SkyDive.active(player) || sorcererJump(player)
			|| player.fallDistance >= SkyDive.MIN_FALL && -player.getDeltaMovement().y >= SkyDive.MIN_SPEED && SkyDive.free(player);
	}

	private static java.lang.invoke.MethodHandle sorcerer;
	private static boolean looked;

	/** Kingdoms' sorcerer jump (looked up by name: Kingdoms is a separate mod). */
	private static boolean sorcererJump(LocalPlayer player) {
		if (!looked) {
			looked = true;
			try {
				sorcerer = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.SorcererJump"), "has",
					java.lang.invoke.MethodType.methodType(boolean.class, Player.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				sorcerer = null;
			}
		}
		try {
			return sorcerer != null && (boolean)sorcerer.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}

	private static boolean step(Minecraft minecraft) {
		if (minecraft.gui.screen() == null && minecraft.player != null && CoreMotion.canJump(minecraft.player)
			&& com.jaymospowers.stamina.Feathers.canSpend(minecraft.player, CoreMotion.JUMP_COST) && ClientPlayNetworking.canSend(CoreMotion.Jump.TYPE)) {
			ClientPlayNetworking.send(new CoreMotion.Jump());
			return true;
		}
		return false;
	}

	public static boolean playing(Player player, String group) {
		Minecraft minecraft = Minecraft.getInstance();
		return PAL && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
			&& CoreMotionPal.playing(player, group);
	}

	public static boolean active(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		return PAL && !(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
			&& CoreMotionPal.active(player);
	}
}
