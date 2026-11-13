package com.jaymoskitchen.client.butchery;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.butchery.BoneStandEntity;
import com.jaymoskitchen.butchery.Skeletons;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * The skeleton on a bone stand ({@link Skeletons}): its animal's GeckoLib model
 * (geckolib/models/block/skeleton_{@code animal}.geo.json, made by tools/gen_skeleton_models.py, editable in Blockbench),
 * in the pose its stand is set to, facing the way the stand faces. Uncleaned bones are tinted green; boiled ones ivory;
 * beetle-cleaned ones white.
 */
public final class BoneStandRenderer extends GeoBlockRenderer<BoneStandEntity, BlockEntityRenderState> {
	static final DataTicket<String> MODEL = DataTicket.create("jaymoskitchen_skeleton_model", String.class);
	static final Identifier TEXTURE = JaymosKitchen.id("textures/block/skeleton.png");
	/** The bones' colour by how they were cleaned: green and dirty, greasy ivory from the pot, white from the beetles. */
	static final int DIRTY = 0xFFA8B884, BOILED = 0xFFEDE0BE, BEETLES = 0xFFFFFFFF;

	public BoneStandRenderer(BlockEntityRendererProvider.Context context) {
		super(context, new GeoModel<BoneStandEntity>() {
			@Override
			public Identifier getModelResource(GeoRenderState state) {
				return JaymosKitchen.id("block/skeleton_" + state.getOrDefaultGeckolibData(MODEL, "cow"));
			}

			@Override
			public Identifier getTextureResource(GeoRenderState state) {
				return TEXTURE;
			}

			@Override
			public Identifier getAnimationResource(BoneStandEntity stand) {
				Skeletons.Kind kind = stand.kind();
				return JaymosKitchen.id("block/skeleton_" + (kind == null ? "cow" : kind.model()));
			}
		});
	}

	public static void register() {
		BlockEntityRenderers.register(Skeletons.BONE_STAND_ENTITY, BoneStandRenderer::new);
	}

	@Override
	public void addRenderData(BoneStandEntity stand, Void related, BlockEntityRenderState state, float partialTick) {
		Skeletons.Kind kind = stand.kind();
		if (kind != null) {
			state.addGeckolibData(MODEL, kind.model());
		}
	}

	@Override
	public int getRenderColor(BoneStandEntity stand, Void related, float partialTick) {
		return switch (Skeletons.clean(stand.bones())) {
			case DIRTY -> DIRTY;
			case BOILED -> BOILED;
			case BEETLES -> BEETLES;
		};
	}

	@Override
	public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.getOrDefaultGeckolibData(MODEL, "").isEmpty()) {
			return;
		}
		super.submit(state, poseStack, collector, camera);
	}
}
