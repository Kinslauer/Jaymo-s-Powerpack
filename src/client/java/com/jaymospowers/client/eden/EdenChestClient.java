package com.jaymospowers.client.eden;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.eden.EdenWoodwork;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Verdant World's wooden chests with vanilla's chest model and a texture of their own wood
 * ({@code textures/entity/chest/eden/<wood>.png}, in the chest atlas). They never join, so only the single model is used.
 */
public final class EdenChestClient implements BlockEntityRenderer<EdenWoodwork.EdenChestEntity, EdenChestClient.State> {
	/** A chest's render state with the sprite of its wood. */
	public static final class State extends ChestRenderState {
		SpriteId sprite;
	}

	private final SpriteGetter sprites;
	private final ChestModel model;

	public EdenChestClient(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
		this.model = new ChestModel(context.bakeLayer(ChestRenderer.LAYERS.single()));
	}

	public static void init() {
		BlockEntityRenderers.register(EdenWoodwork.CHEST_ENTITY, EdenChestClient::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EdenWoodwork.EdenChestEntity chest, State state, float partialTick, Vec3 cameraPos,
		ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(chest, state, partialTick, cameraPos, breakProgress);
		BlockState block = chest.getBlockState();
		state.type = ChestType.SINGLE;
		state.facing = block.hasProperty(ChestBlock.FACING) ? block.getValue(ChestBlock.FACING) : net.minecraft.core.Direction.SOUTH;
		state.open = chest.getOpenNess(partialTick);
		String path = BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath();
		String wood = path.endsWith("_chest") ? path.substring(0, path.length() - "_chest".length()) : path;
		state.sprite = Sheets.CHEST_MAPPER.apply(JaymosPowers.id("eden/" + wood));
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.mulPose(ChestRenderer.modelTransformation(state.facing));
		float open = 1.0F - state.open;
		open = 1.0F - open * open * open;
		collector.submitModel(model, open, pose, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.sprite, sprites, 0);
		pose.popPose();
	}
}
