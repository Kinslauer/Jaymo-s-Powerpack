package com.jaymospowers.client.armory;

import com.jaymospowers.armory.Lodged;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.117) Weapons stuck in a humanoid body (Lodged), drawn standing out of its back and sides, blade buried: up to three,
 * each in its own spot and lean.
 */
public final class LodgedLayer {
	static final RenderStateDataKey<List<ItemStackRenderState>> KEY = RenderStateDataKey.create(() -> "jaymospowers:lodged");
	/** Where each sits on the body (model pixels: x across, y down from the neck, z out the back) and how it leans (degrees). */
	static final float[][] SPOTS = {{-1.5F, 5.0F, 2.0F, 20.0F, -15.0F}, {2.5F, 8.0F, 2.0F, -25.0F, 10.0F}, {-4.0F, 3.0F, -1.0F, 35.0F, 30.0F}};
	/** Their size, and how far the middle stands out of the skin (blocks, along the blade). */
	static final float SIZE = 0.7F, OUT = 0.12F;

	private LodgedLayer() {
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** From every living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		List<ItemStack> stuck = Lodged.stuck(entity);
		if (stuck.isEmpty()) {
			state.setData(KEY, null);
			return;
		}
		List<ItemStackRenderState> items = new ArrayList<>(stuck.size());
		for (ItemStack stack : stuck) {
			ItemStackRenderState item = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(item, stack, ItemDisplayContext.FIXED, entity);
			items.add(item);
		}
		state.setData(KEY, items);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			List<ItemStackRenderState> items = state.getData(KEY);
			if (items == null || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			for (int i = 0; i < items.size() && i < SPOTS.length; i++) {
				float[] spot = SPOTS[i];
				poseStack.pushPose();
				model.root().translateAndRotate(poseStack);
				model.body.translateAndRotate(poseStack);
				poseStack.translate(spot[0] / 16.0F, spot[1] / 16.0F, spot[2] / 16.0F);
				// upright item space; the sprite's tip (up and right) turned to point into the body (-z), then leant
				poseStack.scale(-SIZE, -SIZE, SIZE);
				poseStack.rotateDegrees(Axis.YP, 90.0F + spot[3]);
				poseStack.rotateDegrees(Axis.XP, spot[4]);
				poseStack.rotateDegrees(Axis.ZP, -45.0F);
				poseStack.translate(-OUT / SIZE, 0.0F, 0.0F);
				items.get(i).submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		}
	}
}
