package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Fairies;
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
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * (0.20.12) A worn bottled fairy, hung on the chest for everyone to see (Andy: "the villagers can wear it too"): any
 * humanoid body that {@link Fairies#wears} one, players (from their amulet or charm slot) and villagers (MCA's too) who
 * were given one. Drawn as the item itself, so the fairy inside flits about as she does in the inventory, and lit full
 * bright, as she glows.
 */
public final class WornFairies {
	// ===== the knobs ================================================================================================
	/** Where the bottle hangs, in the body's pixels (y down from the neck, z out in front), and its size (blocks). */
	static final float CHEST_Y = 2.5F, CHEST_Z = -2.9F, OVER_ARMOUR = 1.0F, SIZE = 0.32F;
	/** The scale the fixed display transform applies already. */
	static final float FIXED_SCALE = 0.5F;
	/** How far she sways as the body walks (radians). */
	static final float SWAY = 0.12F;
	/** Full bright. */
	static final int GLOW_LIGHT = 0xF000F0;
	// ================================================================================================================

	static final RenderStateDataKey<ItemStackRenderState> KEY = RenderStateDataKey.create(() -> "jaymospowers:worn_fairy");

	private WornFairies() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?, ?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** From every living renderer's extractRenderState: the fairy's bottle, if this body wears one. */
	public static void extract(LivingEntity entity, EntityRenderState state) {
		if (Fairies.BOTTLED_FAIRY == null || !Fairies.wears(entity)) {
			state.setData(KEY, null);
			return;
		}
		ItemStackRenderState item = new ItemStackRenderState();
		Minecraft.getInstance().getItemModelResolver().updateForLiving(item, new ItemStack(Fairies.BOTTLED_FAIRY), ItemDisplayContext.FIXED, entity);
		state.setData(KEY, item.isEmpty() ? null : item);
	}

	static final class Layer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
		Layer(RenderLayerParent<S, M> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			ItemStackRenderState item = state.getData(KEY);
			if (item == null || state.isInvisible) {
				return;
			}
			M body = this.getParentModel();
			float out = CHEST_Z - (state.chestEquipment.isEmpty() ? 0.0F : OVER_ARMOUR);
			poseStack.pushPose();
			body.root().translateAndRotate(poseStack);
			body.body.translateAndRotate(poseStack);
			poseStack.translate(0.0F, CHEST_Y / 16.0F, out / 16.0F);
			// hanging straight down whatever the body does, rocking a little with each step
			poseStack.rotateDegrees(Axis.XP, -body.body.xRot * Mth.RAD_TO_DEG);
			poseStack.rotateDegrees(Axis.ZP, SWAY * Mth.sin(state.walkAnimationPos * 0.6662F) * Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F)
				* Mth.RAD_TO_DEG);
			// the layer's space is the model's (y down, x flipped): flipped upright, the sprite's top (the cork) at the origin
			float scale = SIZE / FIXED_SCALE;
			poseStack.scale(-scale, -scale, scale);
			poseStack.translate(0.0F, -FIXED_SCALE, 0.0F);
			item.submit(poseStack, collector, GLOW_LIGHT, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
