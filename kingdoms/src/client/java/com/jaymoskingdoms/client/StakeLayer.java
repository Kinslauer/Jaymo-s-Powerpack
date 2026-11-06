package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Stakes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
 * (0.7.641) A stake in a vampire ({@link Stakes#STAKE}) is seen: the skewer itself, driven into the left of the chest, its
 * point buried and its grip standing out in front. Drawn on the body bone, so it moves with every pose, on anything humanoid
 * (players, MCA villagers, folk).
 */
public final class StakeLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	static final RenderStateDataKey<ItemStackRenderState> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:stake");
	/** Where it goes in (model pixels from the neck: left of the breastbone, a third down, on the chest), its size, and how far back along the blade from the centre (item units). */
	static final float X = 1.2F, Y = 4.0F, Z = -2.0F, SCALE = 0.5F, SINK = -0.28F;

	private StakeLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static StakeLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new StakeLayer((RenderLayerParent) renderer);
	}

	/** From the render-state mixin: the stake's item, or nothing. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		ItemStack stake = entity.getAttached(Stakes.STAKE);
		if (stake == null || stake.isEmpty()) {
			state.setData(KEY, null);
			return;
		}
		ItemStackRenderState item = new ItemStackRenderState();
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getItemModelResolver().updateForTopItem(item, stake, ItemDisplayContext.NONE, minecraft.level, entity, entity.getId());
		state.setData(KEY, item);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		ItemStackRenderState item = state.getData(KEY);
		if (item == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		poseStack.translate(X / 16.0F, Y / 16.0F, Z / 16.0F);
		// the sprite's blade runs bottom-left to top-right: turn that diagonal to point into the chest (+z on the model)
		poseStack.rotateDegrees(Axis.YP, -90.0F);
		poseStack.rotateDegrees(Axis.ZP, -45.0F);
		// a little tilt so it doesn't stand dead straight
		poseStack.rotateDegrees(Axis.YP, 12.0F);
		poseStack.scale(SCALE, SCALE, SCALE);
		// slide it back along the blade: the point (a third of it) buried in the chest, the rest standing out
		poseStack.translate(SINK, SINK, 0.0F);
		item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
