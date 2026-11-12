package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MobAmmo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Andy: "skeletons should carry tattered quivers". Every bow skeleton (skeleton, stray, bogged, parched) wears a
 * {@link MobAmmo#TATTERED_QUIVER} slung across its back, arrows over the shoulder; how many it has left is
 * {@link MobAmmo}'s count.
 */
public final class QuiverLayer {
	/** The skeletons that carry one (the wither skeleton fights with a sword). */
	static final Set<EntityType<?>> CARRIERS = Set.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED);
	/** Where it sits on the back (model pixels: across, down from the neck, out from the spine), its size and slant (degrees). */
	static final float ACROSS = 0.0F, DOWN = 5.5F, OUT = 2.6F, SIZE = 0.75F, SLANT = 0.0F;

	private QuiverLayer() {
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (CARRIERS.contains(entityType) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final ItemStackRenderState quiver = new ItemStackRenderState();

		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getItemModelResolver().updateForTopItem(this.quiver, new ItemStack(MobAmmo.TATTERED_QUIVER), ItemDisplayContext.FIXED,
				minecraft.level, null, 0);
			HumanoidModel<S> model = this.getParentModel();
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.body.translateAndRotate(poseStack);
			poseStack.translate(ACROSS / 16.0F, DOWN / 16.0F, OUT / 16.0F);
			// upright item space, flat against the back; the sprite already runs corner to corner
			poseStack.scale(-SIZE, -SIZE, SIZE);
			poseStack.rotateDegrees(Axis.ZP, SLANT);
			this.quiver.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
