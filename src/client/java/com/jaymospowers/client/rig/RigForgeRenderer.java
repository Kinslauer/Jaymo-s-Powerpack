package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigForgeBlock;
import com.jaymospowers.rig.RigForgeEntity;
import com.jaymospowers.rig.RigFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig Forge's hologram: the suit as a small turning figure of light over the Forge, in the frame picked there and
 * the paint chosen (plates and trim). The piece the Forge is set to build lights as salvage is fed in
 * ({@link RigForgeEntity#fed}); a Rig arm or leg lights the arm or the shins. Built from boxes of our own
 * ({@code textures/entity/rig/holo.png}, scanlines), drawn full bright and see-through, flickering a little.
 */
public class RigForgeRenderer implements BlockEntityRenderer<RigForgeEntity, RigForgeRenderer.State> {
	/** How high the feet float over the block’s base and how tall the figure stands (blocks); the bob. */
	static final float FEET = 1.3F, HEIGHT = 1.25F, BOB = 0.03F;
	/** Degrees it turns a tick; its opacity unlit and lit, and the flicker’s depth and speed. */
	static final float TURN = 3.0F, DIM = 0.16F, LIT = 0.8F, FLICKER = 0.12F, FLICKER_SPEED = 1.7F;
	/** The hologram’s own colour, and how far the plates’ paint pulls it. */
	static final int CYAN = 0x5FE6FF;
	static final float PAINT_PULL = 0.7F;
	/** A humanoid model’s height in pixels (feet at this y, the neck at 0). */
	static final float MODEL_HEIGHT = 24.0F;
	static final Identifier HOLO = JaymosPowers.id("textures/entity/rig/holo.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	/** Piece groups, in order: the four pieces ({@link Rig.Piece}), then the trim. */
	private static final int TRIM = 4;
	private static final Map<RigFrame, ModelPart[]> MODELS = new EnumMap<>(RigFrame.class);

	public static class State extends BlockEntityRenderState {
		float fed, time;
		int piece = -1, limb;
		RigFrame frame = RigFrame.STANDARD;
		int plates = RigForgeEntity.NO_PAINT, trim = RigForgeEntity.NO_PAINT;
	}

	public RigForgeRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RigForgeEntity forge, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(forge, state, partialTicks, camera, breakProgress);
		Rig.Piece piece = forge.piece();
		state.piece = piece == null ? -1 : piece.ordinal();
		state.limb = forge.limb();
		state.frame = forge.frame();
		state.plates = forge.plates();
		state.trim = forge.trim();
		int need = Math.max(1, RigForgeBlock.pointsFor(forge));
		state.fed = Mth.clamp(forge.fed() / (float)need, 0.0F, 1.0F);
		state.time = (forge.getLevel() == null ? 0L : forge.getLevel().getGameTime()) + partialTicks;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		ModelPart[] parts = MODELS.computeIfAbsent(state.frame, RigForgeRenderer::build);
		float flicker = 1.0F - FLICKER * (0.5F + 0.5F * Mth.sin(state.time * FLICKER_SPEED));
		if ((int)(state.time * 2.0F) % 23 == 0) {
			flicker *= 0.6F;
		}
		int paint = state.plates == RigForgeEntity.NO_PAINT ? CYAN : lerp(CYAN, state.plates, PAINT_PULL);
		float scale = HEIGHT / (MODEL_HEIGHT / 16.0F);
		poseStack.pushPose();
		poseStack.translate(0.5F, FEET + BOB * Mth.sin(state.time * 0.07F) + HEIGHT, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.time * TURN);
		// the model is built y-down
		poseStack.rotateDegrees(Axis.ZP, 180.0F);
		poseStack.scale(scale, scale, scale);
		for (int i = 0; i < parts.length; i++) {
			float alpha;
			int color;
			if (i == TRIM) {
				alpha = state.fed > 0.0F || state.piece < 0 && state.limb == 0 ? 0.5F : DIM;
				color = state.trim == RigForgeEntity.NO_PAINT ? lerp(CYAN, 0xFFFFFF, 0.6F) : state.trim;
			} else {
				boolean picked = i == state.piece || state.limb == RigForgeEntity.ARM && i == Rig.Piece.ARMS.ordinal()
					|| state.limb == RigForgeEntity.LEG && i == Rig.Piece.THRUSTERS.ordinal();
				// the picked piece lights as it is fed; when it can be built it holds full and shimmers
				float lit = picked ? state.fed : 0.0F;
				alpha = Mth.lerp(lit, DIM, LIT) * (picked && lit >= 1.0F ? 0.9F + 0.1F * Mth.sin(state.time * 0.4F) : 1.0F);
				color = lerp(CYAN, paint, lit);
			}
			int a = Mth.clamp((int)(alpha * flicker * 255.0F), 0, 255);
			collector.submitModelPart(parts[i], poseStack, RenderTypes.eyes(HOLO), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, a << 24 | color & 0xFFFFFF);
		}
		poseStack.popPose();
	}

	private static int lerp(int from, int to, float t) {
		int r = Mth.lerpInt(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int g = Mth.lerpInt(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = Mth.lerpInt(t, from & 0xFF, to & 0xFF);
		return r << 16 | g << 8 | b;
	}

	// ---- the figure ---------------------------------------------------------------------------------------------------

	/** The figure for a frame: its pieces’ boxes (a thick Scrap and Siege, a thin Shade, a Siege shoulder gun) and the trim. */
	private static ModelPart[] build(RigFrame frame) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float grow = switch (frame) {
			case SCRAP -> 0.5F;
			case SIEGE -> 0.9F;
			case SHADE -> -0.35F;
			default -> 0.0F;
		};
		CubeDeformation g = new CubeDeformation(grow);
		CubeDeformation slim = new CubeDeformation(grow * 0.6F);
		// the Helm: the head, with a visor ridge on a Shade hood
		CubeListBuilder helm = CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, slim);
		if (frame == RigFrame.SHADE) {
			helm.texOffs(0, 0).addBox(-4.5F, -9.0F, -3.0F, 9.0F, 3.0F, 8.0F, new CubeDeformation(-0.2F));
		}
		root.addOrReplaceChild("helm", helm, PartPose.ZERO);
		// the Core: chest and back unit (and a Siege shoulder gun on the right shoulder, a Scrap patch on the left)
		CubeListBuilder core = CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 8.0F, 4.0F, g)
			.texOffs(0, 0).addBox(-3.0F, 1.0F, 2.0F, 6.0F, 6.0F, 3.0F, slim);
		if (frame == RigFrame.SIEGE) {
			core.texOffs(0, 0).addBox(3.5F, -4.0F, -5.0F, 3.0F, 3.0F, 9.0F);
		}
		if (frame == RigFrame.SCRAP) {
			core.texOffs(0, 0).addBox(-6.5F, -1.5F, -2.5F, 3.0F, 3.0F, 5.0F);
		}
		root.addOrReplaceChild("core", core, PartPose.ZERO);
		// the Arms: the arms, and the hip harness
		root.addOrReplaceChild("arms", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 10.0F, 4.0F, g)
			.texOffs(0, 0).addBox(4.0F, -2.0F, -2.0F, 4.0F, 10.0F, 4.0F, g)
			.texOffs(0, 0).addBox(-4.0F, 8.0F, -2.0F, 8.0F, 4.0F, 4.0F, g), PartPose.ZERO);
		// the Thrusters: the legs and the boots
		root.addOrReplaceChild("thrusters", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 12.0F, -2.0F, 4.0F, 12.0F, 4.0F, slim)
			.texOffs(0, 0).addBox(0.0F, 12.0F, -2.0F, 4.0F, 12.0F, 4.0F, slim), PartPose.ZERO);
		// the trim: the crest, the collar, the belt, the cuffs
		root.addOrReplaceChild("trim", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -9.0F - Math.max(0.0F, grow), -3.0F, 1.0F, 1.0F, 6.0F)
			.texOffs(0, 0).addBox(-4.5F - grow, -0.5F, -2.5F - grow, 9.0F + 2.0F * grow, 1.0F, 5.0F + 2.0F * grow)
			.texOffs(0, 0).addBox(-4.5F - grow, 11.0F, -2.5F - grow, 9.0F + 2.0F * grow, 1.0F, 5.0F + 2.0F * grow)
			.texOffs(0, 0).addBox(-4.5F, 23.0F, -2.5F, 4.0F, 1.0F, 5.0F)
			.texOffs(0, 0).addBox(0.5F, 23.0F, -2.5F, 4.0F, 1.0F, 5.0F), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		return new ModelPart[] {baked.getChild("helm"), baked.getChild("core"), baked.getChild("arms"), baked.getChild("thrusters"), baked.getChild("trim")};
	}
}
