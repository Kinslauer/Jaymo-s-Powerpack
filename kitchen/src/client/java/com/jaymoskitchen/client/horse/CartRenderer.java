package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.horse.CartEntity;
import com.jaymoskitchen.horse.CartKind;
import com.jaymoskitchen.horse.NiftyCarts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Draws the carts in their wood, their wheels turning as they roll: the wagon with a vanilla chest whose lid opens,
 * the coach with its three benches,
 * the plow with its hoes on its arms and the supply cart with its cargo piled in the bed (both after
 * NiftyCarts' renderers: Copyright (c) 2019 MennoMax, (c) 2023 jmb19905; MIT licensed, see LICENSE_niftycarts).
 */
public class CartRenderer extends EntityRenderer<CartEntity, CartRenderer.State> {

	public static class State extends EntityRenderState {
		float yRot;
		/** Degrees, tipped forward positive. */
		float pitch;
		float wheelRot;
		float hurt;
		/** Rolled over onto its right side to tip its load out (degrees). */
		float tip;
		CartKind kind = CartKind.WAGON;
		Identifier texture;
		/** The wagon's chest lid, 0 shut to 1 open. */
		float lid;
		float armAngle;
		/** On board: the plow's hoes, or the supply cart's cargo piles. */
		final ItemStackRenderState[] shown = new ItemStackRenderState[CartEntity.SHOWN_COUNT];
		final boolean[] hasShown = new boolean[CartEntity.SHOWN_COUNT];
		final boolean[] solid = new boolean[CartEntity.SHOWN_COUNT];
		final int[] counts = new int[CartEntity.SHOWN_COUNT];
		final int[] seeds = new int[CartEntity.SHOWN_COUNT];

		State() {
			for (int i = 0; i < shown.length; i++) {
				shown[i] = new ItemStackRenderState();
			}
		}
	}

	private final Map<CartKind, NiftyCartModel> nifty = new EnumMap<>(CartKind.class);
	private final ChestModel chest;
	private final SpriteGetter sprites;
	private final ItemModelResolver itemModelResolver;

	public CartRenderer(EntityRendererProvider.Context context) {
		super(context);
		nifty.put(CartKind.WAGON, new NiftyCartModel(context.bakeLayer(NiftyCartModels.WAGON)));
		nifty.put(CartKind.COACH, new NiftyCartModel(context.bakeLayer(NiftyCartModels.COACH)));
		nifty.put(CartKind.SUPPLY_CART, new NiftyCartModel(context.bakeLayer(NiftyCartModels.SUPPLY_CART)));
		nifty.put(CartKind.ANIMAL_CART, new NiftyCartModel(context.bakeLayer(NiftyCartModels.ANIMAL_CART)));
		nifty.put(CartKind.PLOW, new NiftyCartModel(context.bakeLayer(NiftyCartModels.PLOW)));
		this.chest = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
		this.sprites = context.getSprites();
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.9F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CartEntity cart, State state, float partialTicks) {
		super.extractRenderState(cart, state, partialTicks);
		state.yRot = cart.getYRot(partialTicks);
		state.pitch = cart.getXRot(partialTicks);
		state.wheelRot = cart.wheelRot(partialTicks);
		state.hurt = Math.max(cart.hurtTime() - partialTicks, 0.0F);
		state.tip = cart.tipAngle(partialTicks);
		state.kind = cart.kind();
		state.texture = CartTextures.get(state.kind, cart.wood());
		state.lid = cart.lidOpenness(partialTicks);
		state.armAngle = cart.armAngle(partialTicks);
		for (int i = 0; i < CartEntity.SHOWN_COUNT; i++) {
			ItemStack stack = cart.shown(i);
			state.hasShown[i] = !stack.isEmpty();
			if (state.hasShown[i]) {
				// cargo blocks sit as small blocks in the bed; everything else lies flat
				this.itemModelResolver.updateForNonLiving(state.shown[i], stack, ItemDisplayContext.FIXED, cart);
				state.solid[i] = state.kind == CartKind.SUPPLY_CART && state.shown[i].usesBlockLight();
				if (state.solid[i]) {
					this.itemModelResolver.updateForNonLiving(state.shown[i], stack, ItemDisplayContext.NONE, cart);
				}
				state.counts[i] = stack.getCount();
				state.seeds[i] = 32 * i + BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		if (state.hurt > 0.0F) {
			// knocked about when hit
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurt) * state.hurt * 0.6F);
		}
		if (state.tip > 0.0F) {
			// rolled over onto its right side (local +x), pivoting on that wheel
			float side = state.kind == CartKind.COACH ? 1.0F : 0.7F;
			poseStack.translate(side, 0.0F, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, -state.tip);
			poseStack.translate(-side, 0.0F, 0.0F);
		}
		if (state.kind == CartKind.WAGON || state.kind == CartKind.COACH) {
			// the wagon is the supply cart's frame a size up, and the coach is drawn at the same size
			poseStack.scale(NiftyCarts.WAGON_SCALE, NiftyCarts.WAGON_SCALE, NiftyCarts.WAGON_SCALE);
		}
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		NiftyCartModel model = nifty.get(state.kind);
		collector.submitModel(model, state, poseStack, state.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		// pose the model now too, to find where things on board go
		model.setupAnim(state);
		switch (state.kind) {
			case PLOW -> hoes(state, poseStack, collector, model);
			case SUPPLY_CART -> cargo(state, poseStack, collector, model);
			case WAGON -> wagonChest(state, poseStack, collector, model);
			default -> {
			}
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** Each hoe on the end of its plow arm (placement from NiftyCarts' plow renderer). */
	private static void hoes(State state, PoseStack poseStack, SubmitNodeCollector collector, NiftyCartModel model) {
		for (int i = 0; i < 3; i++) {
			if (!state.hasShown[i]) {
				continue;
			}
			poseStack.pushPose();
			model.toArmEnd(poseStack, i);
			poseStack.rotateDegrees(Axis.XP, -90.0F);
			poseStack.rotateDegrees(Axis.YP, 90.0F);
			poseStack.translate(-4.0F / 16.0F, 1.0F / 16.0F, 0.0F);
			state.shown[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}

	/** The supply cart's cargo piled in the bed, in four places (after NiftyCarts' supply cart renderer). */
	private static void cargo(State state, PoseStack poseStack, SubmitNodeCollector collector, NiftyCartModel model) {
		poseStack.pushPose();
		model.toBody(poseStack);
		Random random = new Random();
		for (int i = 0; i < CartEntity.SHOWN_COUNT; i++) {
			if (!state.hasShown[i]) {
				continue;
			}
			int ix = i % 2, iz = i / 2;
			float x = (ix - 0.5F) * 11.0F / 16.0F;
			float z = (iz * 11.0F - 9.0F) / 16.0F;
			poseStack.pushPose();
			if (state.solid[i]) {
				poseStack.translate(x, -0.46F, z);
				poseStack.scale(0.65F, 0.65F, 0.65F);
				poseStack.rotateDegrees(Axis.ZP, 180.0F);
				poseStack.rotateDegrees(Axis.YP, 180.0F);
				state.shown[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			} else {
				random.setSeed(state.seeds[i]);
				poseStack.translate(x, -0.15F + ((ix + iz) % 2 == 0 ? 0.0F : 1.0E-4F), z);
				poseStack.scale(0.7F, 0.7F, 0.7F);
				poseStack.rotate(Axis.YP.rotation(random.nextFloat() * Mth.PI));
				poseStack.rotateDegrees(Axis.XP, -90.0F);
				int copies = Math.min(state.counts[i], (state.counts[i] - 1) / 16 + 2);
				state.shown[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				for (int n = 1; n < copies; n++) {
					poseStack.pushPose();
					poseStack.rotate(Axis.ZP.rotation(random.nextFloat() * Mth.PI));
					poseStack.translate((random.nextFloat() * 2.0F - 1.0F) * 0.05F, (random.nextFloat() * 2.0F - 1.0F) * 0.05F, -0.1F * n);
					state.shown[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
					poseStack.popPose();
				}
			}
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** The wagon's vanilla chest, on the back of the bed with its lock facing the tailboard; its lid opens as it's used. */
	private void wagonChest(State state, PoseStack poseStack, SubmitNodeCollector collector, NiftyCartModel model) {
		poseStack.pushPose();
		model.toBody(poseStack);
		// the bed's top is 2 px above the axle; the chest's middle 6 px back (model space: +Y down)
		poseStack.translate(0.0F, -2.0F / 16.0F, 6.0F / 16.0F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		collector.submitModel(chest, state.lid, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
			Sheets.CHEST_REGULAR.select(ChestType.SINGLE), sprites, state.outlineColor);
		poseStack.popPose();
	}
}
