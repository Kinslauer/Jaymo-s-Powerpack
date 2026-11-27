package com.jaymoskingdoms.client;

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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * (fighting AI patch 7, st-dodge) What a humanoid mob carries on its back in its body slot (the dodgers' shield, or the off-hand
 * blade it put away to take the shield up, {@code DualBlades}): a shield drawn flat across the back, a blade slung slantwise.
 * Armour made for the body slot (a horse's, a wolf's) is never drawn here.
 */
public final class BackGearLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	/** Where a shield sits on the back (model pixels: down from the neck, out from the spine) and its size. */
	static final float SHIELD_DOWN = 6.0F, SHIELD_OUT = 2.6F, SHIELD_SIZE = 0.9F;
	/** Where a blade sits on the back (model pixels), its size and its slant (degrees). */
	static final float BLADE_DOWN = 5.0F, BLADE_OUT = 2.4F, BLADE_SIZE = 0.85F, BLADE_SLANT = 0.0F;

	static final RenderStateDataKey<ItemStack> BACK = RenderStateDataKey.create(() -> "jaymoskingdoms:back_gear");

	private final ItemStackRenderState item = new ItemStackRenderState();

	private BackGearLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
	}

	/** Puts this layer on every humanoid renderer. */
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BackGearLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new BackGearLayer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the mob's body-slot stack, unless it's armour made for that slot. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		ItemStack back = entity instanceof Mob && !(entity instanceof Avatar) && !entity.isInvisible() ? entity.getItemBySlot(EquipmentSlot.BODY) : ItemStack.EMPTY;
		Equippable equippable = back.isEmpty() ? null : back.get(DataComponents.EQUIPPABLE);
		state.setData(BACK, back.isEmpty() || equippable != null && equippable.slot() == EquipmentSlot.BODY ? null : back.copy());
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		ItemStack back = state.getData(BACK);
		if (back == null || state.isInvisible || QuiverLayer.severedPart(state)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getItemModelResolver().updateForTopItem(this.item, back, ItemDisplayContext.FIXED, minecraft.level, null, 0);
		boolean shield = back.has(DataComponents.BLOCKS_ATTACKS);
		HumanoidModel<S> model = this.getParentModel();
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		model.body.translateAndRotate(poseStack);
		if (shield) {
			poseStack.translate(0.0F, SHIELD_DOWN / 16.0F, SHIELD_OUT / 16.0F);
			// upright, its face out from the back
			poseStack.scale(-SHIELD_SIZE, -SHIELD_SIZE, SHIELD_SIZE);
			poseStack.rotateDegrees(Axis.YP, 180.0F);
		} else {
			poseStack.translate(0.0F, BLADE_DOWN / 16.0F, BLADE_OUT / 16.0F);
			// upright item space, flat against the back; the sprite already runs corner to corner
			poseStack.scale(-BLADE_SIZE, -BLADE_SIZE, BLADE_SIZE);
			poseStack.rotateDegrees(Axis.ZP, BLADE_SLANT);
		}
		this.item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
