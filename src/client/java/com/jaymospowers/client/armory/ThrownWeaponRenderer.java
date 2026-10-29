package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.ThrownWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * A thrown Armory weapon, drawn as its item: point first along its flight (sprites have their tip in the top-right
 * corner, so it's rolled 45 degrees, as RiftBladeRenderer does), or tumbling end over end for hatchets, sticks and stars.
 * (0.17.130) Andy: the dagger lands tip first. The 3D Blockbench models stand upright (tip straight up), so they're rolled
 * 90 instead of 45, and a point-first weapon is drawn back along its flight so only its tip sinks into what it hits.
 */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon, ThrownWeaponRenderer.State> {
	public static class State extends ThrownItemRenderState {
		float yRot;
		float xRot;
		boolean spins;
		boolean upright;
		/** How far its tip reaches past the middle (model pixels). */
		float tip;
	}

	/** Tumbling weapons turn this many degrees a tick. */
	private static final float SPIN_DEGREES = 40.0F;
	private static final float SCALE = 1.1F;
	/** Items whose model stands upright (tools/blockbench/make.py), not a sprite drawn corner to corner. */
	private static final Set<Identifier> UPRIGHT = Set.of(JaymosPowers.id("iron_dagger"));
	/** How far the tip reaches past the middle (model pixels): an upright model's, a sprite's corner. */
	private static final float UPRIGHT_TIP = 12.5F, SPRITE_TIP = 11.3F;
	/**
	 * (Andy's batch 4) Models with a tip of their own: the harpoon is the Blockbench Workshop model drawn on the sprite diagonal
	 * (tools/blockbench/harpoon.py), so it rolls 45 like a sprite but reaches 12.5 pixels past the middle, not 11.3.
	 */
	private static final java.util.Map<Identifier, Float> TIPS = java.util.Map.of(JaymosPowers.id("harpoon"), 12.5F);
	/** How deep the tip sinks in (blocks). */
	private static final float TIP_SINK = 0.2F;

	private final ItemModelResolver itemModelResolver;

	public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownWeapon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.xRot = entity.getXRot(partialTicks);
		state.spins = entity.spins();
		Identifier id = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
		state.upright = UPRIGHT.contains(id);
		state.tip = TIPS.getOrDefault(id, state.upright ? UPRIGHT_TIP : SPRITE_TIP);
		this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
		poseStack.rotateDegrees(Axis.ZP, state.xRot);
		if (!state.spins) {
			float tip = state.tip * SCALE / 16.0F;
			poseStack.translate(TIP_SINK - tip, 0.0F, 0.0F);
		}
		poseStack.rotateDegrees(Axis.ZN, state.upright ? 90.0F : 45.0F);
		if (state.spins) {
			poseStack.rotateDegrees(Axis.ZN, state.ageInTicks * SPIN_DEGREES);
		}
		poseStack.scale(SCALE, SCALE, SCALE);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
