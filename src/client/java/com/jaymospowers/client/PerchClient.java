package com.jaymospowers.client;

import com.jaymospowers.core.Perch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * A small animal perched on someone ({@link Perch}), on this client: in your own first-person view it's drawn at the
 * edge of what you see (Perch moves it there for the camera's own player only), and on anyone it settles into the
 * perch: a parrot as it sits on a shoulder, a cat lying draped across the shoulders, nothing walking.
 */
public final class PerchClient {
	private PerchClient() {
	}

	public static void init() {
		Perch.setFirstPerson(player -> {
			Minecraft minecraft = Minecraft.getInstance();
			return player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
		});
	}

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		int spot = Perch.spot(entity);
		if (spot < 0) {
			return;
		}
		state.walkAnimationSpeed = 0.0F;
		state.walkAnimationPos = 0.0F;
		if (state instanceof ParrotRenderState parrot) {
			parrot.pose = ParrotModel.Pose.ON_SHOULDER;
		} else if (state instanceof FelineRenderState feline) {
			feline.isSitting = spot != Perch.DRAPED;
			feline.isCrouching = false;
			feline.isSprinting = false;
			if (spot == Perch.DRAPED) {
				// lying across the shoulders, tail and all
				feline.lieDownAmount = 1.0F;
				feline.lieDownAmountTail = 1.0F;
			}
		}
	}
}
