package com.jaymospowers.client.armory;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * (0.18.67) WeaponMod's musket in first person: held level at the hip (its first-person display in the held_guns pack),
 * and brought up to the eye while you hold it ready to fire, the front sight on the crosshair. WeaponMod draws a loaded
 * musket held ready like a drawn bow; the first-person hand gets no bow pose for it ({@code MusketSightsMixin}) but
 * slides from the hip to the sights instead. First-person Model would draw it in your body's hand, held like a sword;
 * the musket goes on its vanilla-hands list, as TaCZ's guns do ({@link TaczFirstPerson}).
 */
public final class MusketSights {
	private static final String MUSKET = "weaponmod:musket";
	/** From the hip to the sights, in view space (blocks), for the right hand; worked out on the model's preview. */
	static final float AIM_X = -0.379F;
	static final float AIM_Y = 0.293F;
	static final float AIM_Z = 0.078F;
	/** Share of the way up per tick (about a fifth of a second to the eye). */
	static final float RAISE_SPEED = 0.25F;

	private static float aim;
	private static float lastAim;

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

	/** Moves the first-person musket in {@code arm} from the hip toward the sights, as far as it's been raised. */
	public static void raise(PoseStack poseStack, HumanoidArm arm) {
		float raised = Mth.lerp(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false), lastAim, aim);
		if (raised <= 0.0F) {
			return;
		}
		float eased = raised * raised * (3.0F - 2.0F * raised);
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		poseStack.translate(AIM_X * side * eased, AIM_Y * eased, AIM_Z * eased);
	}

	private static void tick(Minecraft minecraft) {
		lastAim = aim;
		boolean up = minecraft.player != null && aiming(minecraft.player);
		aim = Mth.clamp(aim + (up ? RAISE_SPEED : -RAISE_SPEED), 0.0F, 1.0F);
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
