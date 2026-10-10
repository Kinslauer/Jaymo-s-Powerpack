package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.horse.CartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A NiftyCarts cart model (see {@link NiftyCartModels}): pitches its body, turns its wheels, and for the plow swings
 * the three arms up or down (down, they follow the ground as the cart pitches, as in NiftyCarts).
 */
class NiftyCartModel extends EntityModel<CartRenderer.State> {
	private final ModelPart body;
	private final ModelPart leftWheel;
	private final ModelPart rightWheel;
	private final @Nullable ModelPart parts;
	private final ModelPart[] upperArms = new ModelPart[3];
	private final ModelPart[] lowerArms = new ModelPart[3];

	NiftyCartModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.leftWheel = root.getChild("leftWheel_0");
		this.rightWheel = root.getChild("rightWheel_0");
		this.parts = body.hasChild("parts") ? body.getChild("parts") : null;
		if (parts != null) {
			for (int i = 0; i < upperArms.length; i++) {
				upperArms[i] = parts.getChild("plow_shaft_upper_" + i);
				lowerArms[i] = upperArms[i].getChild("plow_shaft_lower_" + i);
			}
		}
	}

	@Override
	public void setupAnim(CartRenderer.State state) {
		super.setupAnim(state);
		float pitch = state.pitch * Mth.DEG_TO_RAD;
		body.xRot = pitch;
		leftWheel.xRot = state.wheelRot;
		rightWheel.xRot = state.wheelRot;
		if (parts != null) {
			float down = (CartEntity.ARMS_RAISED - state.armAngle) / (CartEntity.ARMS_RAISED - CartEntity.ARMS_LOWERED);
			for (ModelPart arm : upperArms) {
				arm.xRot = state.armAngle - down * pitch;
			}
		}
	}

	/** Moves the pose to the end of plow arm {@code i}, where its hoe hangs (call after {@link #setupAnim}). */
	void toArmEnd(PoseStack poseStack, int i) {
		body.translateAndRotate(poseStack);
		if (parts != null) {
			parts.translateAndRotate(poseStack);
			upperArms[i].translateAndRotate(poseStack);
			lowerArms[i].translateAndRotate(poseStack);
		}
	}

	/** Moves the pose into the cart's body (its bed), for drawing what's loaded on it. */
	void toBody(PoseStack poseStack) {
		body.translateAndRotate(poseStack);
	}
}
