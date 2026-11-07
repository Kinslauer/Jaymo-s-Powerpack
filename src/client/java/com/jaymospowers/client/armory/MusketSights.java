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
 * <p>(Motion patch 4) WeaponMod's flintlock pistol the same way, with less of a sight picture (Andy: "make flintlock work
 * similarly, with less of a sight picture (smooth barrel) and dual wielding"): a smooth-bore pistol is pointed, not
 * sighted, so held ready it comes up to the eye line out at its own side ({@link #PISTOL_X} etc.) instead of onto the
 * crosshair. Each hand rises on its own, so with one in each hand only the one about to fire comes up
 * ({@code WeaponModDualFlintlockMixin} fires them in turn).
 */
public final class MusketSights {
	private static final String MUSKET = "weaponmod:musket";
	private static final String FLINTLOCK = "weaponmod:flintlock";
	/** (Motion patch 4) The pistol held ready: up to the eye line, out at its own side, for the right hand (blocks). */
	static final float PISTOL_X = -0.12F;
	static final float PISTOL_Y = 0.16F;
	static final float PISTOL_Z = -0.06F;
	/** And turned a touch in, as you'd point it (degrees). */
	static final float PISTOL_YAW = 6.0F;
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

	/** Per arm (right 0, left 1): how far up to the sights or eye line. */
	private static final float[] aim = new float[2];
	private static final float[] lastAim = new float[2];
	private static float low;
	private static float lastLow;

	private MusketSights() {
	}

	public static boolean isMusket(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(MUSKET);
	}

	/** (Motion patch 4) WeaponMod's flintlock pistol. */
	public static boolean isPistol(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(FLINTLOCK);
	}

	/** Either gun that's raised rather than drawn like a bow. */
	public static boolean isGun(ItemStack stack) {
		return isMusket(stack) || isPistol(stack);
	}

	/** Holding a loaded gun ready to fire (WeaponMod's bow-like hold) in {@code arm}. */
	private static boolean aiming(LocalPlayer player, HumanoidArm arm) {
		ItemStack used = player.getUseItem();
		HumanoidArm using = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? player.getMainArm()
			: player.getMainArm().getOpposite();
		return player.isUsingItem() && using == arm && isGun(used) && used.getUseAnimation() == ItemUseAnimation.BOW;
	}

	/** Moves the first-person musket in {@code arm} from the hip toward the sights, or down to the low ready. */
	public static void raise(PoseStack poseStack, HumanoidArm arm, ItemStack stack) {
		float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int i = arm == HumanoidArm.RIGHT ? 0 : 1;
		float raised = ease(Mth.lerp(partialTicks, lastAim[i], aim[i]));
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		if (isPistol(stack)) {
			if (raised > 0.0F) {
				poseStack.translate(PISTOL_X * side * raised, PISTOL_Y * raised, PISTOL_Z * raised);
				poseStack.rotateDegrees(Axis.YP, PISTOL_YAW * side * raised);
			}
			return;
		}
		float lowered = ease(Mth.lerp(partialTicks, lastLow, low)) * (1.0F - raised);
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
		for (int i = 0; i < 2; i++) {
			lastAim[i] = aim[i];
			boolean up = minecraft.player != null && aiming(minecraft.player, i == 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
			aim[i] = Mth.clamp(aim[i] + (up ? RAISE_SPEED : -RAISE_SPEED), 0.0F, 1.0F);
		}
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
			ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
				TaczFirstPerson.addToVanillaHands(MUSKET);
				TaczFirstPerson.addToVanillaHands(FLINTLOCK);
			});
		}
	}
}
