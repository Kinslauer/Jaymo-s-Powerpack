package com.jaymospowers.client.armory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * (0.18.70) WeaponMod's musket in first person: held level at the hip (its first-person display in the held_guns pack),
 * and brought up to the eye while you hold it ready to fire, the front sight on the crosshair. WeaponMod draws a loaded
 * musket held ready like a drawn bow; the first-person hand gets no bow pose for it ({@code MusketSightsMixin}) but
 * slides from the hip to the sights instead. First-person Model would draw it in your body's hand, held like a sword;
 * the musket goes on its vanilla-hands list, as TaCZ's guns do ({@link TaczFirstPerson}). Crouched and not aiming, it
 * drops to a low ready: muzzle down and in, pivoting on the grip (Andy: "it should lower into a low ready as you crouch").
 */
public final class MusketSights {
	private static final String MUSKET = "weaponmod:musket";
	/** From the hip to the sights, in view space (blocks), for the right hand; worked out on the model's preview. */
	static final float AIM_X = -0.379F;
	static final float AIM_Y = 0.293F;
	static final float AIM_Z = 0.078F;
	/** Share of the way up per tick (about a fifth of a second to the eye). */
	static final float RAISE_SPEED = 0.25F;
	/** The low ready, right hand: moved (blocks), then turned muzzle down and in about the grip. */
	static final float LOW_X = -0.08F;
	static final float LOW_Y = 0.06F;
	static final float LOW_Z = -0.08F;
	static final float LOW_PITCH = -35.0F;
	static final float LOW_YAW = 18.0F;
	/** The grip, from the hand's spot (blocks): the rear of the barrel, where the low ready pivots. */
	static final float GRIP_X = -0.18F;
	static final float GRIP_Y = 0.10F;
	static final float GRIP_Z = 0.19F;
	/** Share of the way down per tick. */
	static final float LOWER_SPEED = 0.2F;

	private static float aim;
	private static float lastAim;
	private static float low;
	private static float lastLow;

	private MusketSights() {
	}

	public static boolean isMusket(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(MUSKET);
	}

	/** Holding a loaded musket ready to fire (WeaponMod's bow-like hold). */
	private static boolean aiming(LocalPlayer player) {
		ItemStack used = player.getUseItem();
		return player.isUsingItem() && isMusket(used) && used.getUseAnimation() == ItemUseAnimation.BOW;
	}

	/** Moves the first-person musket in {@code arm} from the hip toward the sights, or down to the low ready. */
	public static void raise(PoseStack poseStack, HumanoidArm arm) {
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float raised = ease(Mth.lerp(partialTicks, lastAim, aim));
		float lowered = ease(Mth.lerp(partialTicks, lastLow, low)) * (1.0F - raised);
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		if (raised > 0.0F) {
			poseStack.translate(AIM_X * side * raised, AIM_Y * raised, AIM_Z * raised);
		}
		if (lowered > 0.0F) {
			poseStack.translate(LOW_X * side * lowered, LOW_Y * lowered, LOW_Z * lowered);
			poseStack.translate(GRIP_X * side, GRIP_Y, GRIP_Z);
			poseStack.rotateDegrees(Axis.YP, LOW_YAW * side * lowered);
			poseStack.rotateDegrees(Axis.XP, LOW_PITCH * lowered);
			poseStack.translate(-GRIP_X * side, -GRIP_Y, -GRIP_Z);
		}
	}

	private static float ease(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static void tick(Minecraft minecraft) {
		lastAim = aim;
		boolean up = minecraft.player != null && aiming(minecraft.player);
		aim = Mth.clamp(aim + (up ? RAISE_SPEED : -RAISE_SPEED), 0.0F, 1.0F);
		lastLow = low;
		boolean down = minecraft.player != null && minecraft.player.isCrouching()
			&& (isMusket(minecraft.player.getMainHandItem()) || isMusket(minecraft.player.getOffhandItem()));
		low = Mth.clamp(low + (down ? LOWER_SPEED : -LOWER_SPEED), 0.0F, 1.0F);
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("weaponmod")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(MusketSights::tick);
		if (FabricLoader.getInstance().isModLoaded("firstperson")) {
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> TaczFirstPerson.addToVanillaHands(MUSKET));
		}
	}
}
