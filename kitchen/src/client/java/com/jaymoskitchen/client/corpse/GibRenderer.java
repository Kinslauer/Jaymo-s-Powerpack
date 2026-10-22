package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.corpse.Gib;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) A chunk of a body blown apart ({@link Gib}): one box of the creature's own model (a head, a leg, a slab of
 * torso), cut loose and drawn alone in the skin its renderer gives it, so a zombie's chunks are a zombie's and a
 * Kingdoms kind's are its own. Each chunk picks its box by its id. A body the client can't rebuild (a player's) throws
 * plain lumps of meat ({@link #MEAT}). It tumbles as it flies, lies at rest, and sinks into the ground at the end.
 */
public class GibRenderer extends EntityRenderer<Gib, GibRenderer.State> {
	static final Identifier MEAT = JaymosKitchen.id("textures/entity/gib.png");
	/** A box bigger than this (pixels, its longest side) is drawn shrunk to it: a chunk, not a whole torso. */
	static final float MAX_PIXELS = 9.0F;
	/** Tries for a box that is solid (no side under a pixel: not a flat hair or whisker plane) before taking any. */
	static final int TRIES = 6;
	/** How deep it has sunk at the end (blocks, for a man's size). */
	static final float SINK_DEPTH = 0.25F;

	private static @Nullable ModelPart meat;
	/** Each chunk's box and skin, worked out once. */
	private final Map<Gib, Piece> pieces = new java.util.WeakHashMap<>();

	/** A box cut loose (alone in a part of its own, at no pose), its skin, its middle (pixels) and how much it's shrunk. */
	record Piece(ModelPart part, Identifier texture, float x, float y, float z, float fit, float halfHeight) {
		static Piece of(ModelPart.Cube cube, Identifier texture) {
			float longest = Math.max(cube.maxX - cube.minX, Math.max(cube.maxY - cube.minY, cube.maxZ - cube.minZ));
			float fit = longest > MAX_PIXELS ? MAX_PIXELS / longest : 1.0F;
			return new Piece(new ModelPart(List.of(cube), Map.of()), texture, (cube.minX + cube.maxX) / 2.0F,
				(cube.minY + cube.maxY) / 2.0F, (cube.minZ + cube.maxZ) / 2.0F, fit, (cube.maxY - cube.minY) / 2.0F * fit);
		}
	}

	public static class State extends EntityRenderState {
		@Nullable Piece piece;
		float size;
		float yaw;
		float spin;
		float sunk;
	}

	public GibRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.12F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Gib gib, State state, float partialTicks) {
		super.extractRenderState(gib, state, partialTicks);
		state.size = gib.size();
		state.yaw = gib.getYRot();
		state.spin = Mth.lerp(partialTicks, gib.spinO, gib.spin);
		state.sunk = gib.sunk(partialTicks);
		state.piece = this.pieces.computeIfAbsent(gib, g -> piece(g, partialTicks));
		this.shadowRadius = 0.12F * state.size * (1.0F - state.sunk);
	}

	/** One box of the body's model, in its skin, picked by the chunk's id; a lump of meat if there's no body to ask. */
	@SuppressWarnings("unchecked")
	private Piece piece(Gib gib, float partialTicks) {
		Entity body = gib.clientBody();
		if (body != null) {
			EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
			if (renderer instanceof LivingEntityRenderer<?, ?, ?> living
				&& renderer.createRenderState(body, partialTicks) instanceof LivingEntityRenderState bodyState) {
				Identifier texture = ((LivingEntityRenderer<?, LivingEntityRenderState, ?>) living).getTextureLocation(bodyState);
				List<ModelPart> parts = living.getModel().allParts().stream().filter(part -> !part.isEmpty()).toList();
				if (!parts.isEmpty()) {
					RandomSource random = RandomSource.create(gib.getId() * 7919L);
					ModelPart.Cube cube = null;
					for (int i = 0; i < TRIES; i++) {
						cube = parts.get(random.nextInt(parts.size())).getRandomCube(random);
						if (cube.maxX - cube.minX >= 1.0F && cube.maxY - cube.minY >= 1.0F && cube.maxZ - cube.minZ >= 1.0F) {
							break;
						}
					}
					return Piece.of(cube, texture);
				}
			}
		}
		build();
		return new Piece(meat, MEAT, 0.0F, 0.0F, 0.0F, 1.0F, 1.5F);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Piece piece = state.piece;
		if (piece == null) {
			return;
		}
		float size = state.size;
		float scale = size * piece.fit() / 16.0F;
		poseStack.pushPose();
		poseStack.translate(0.0F, piece.halfHeight() / 16.0F * size - SINK_DEPTH * size * state.sunk, 0.0F);
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		// tumbling end over end; at rest (a half turn) it lies as it fell
		poseStack.rotateDegrees(Axis.ZP, state.spin);
		poseStack.scale(-scale, -scale, scale);
		poseStack.translate(-piece.x(), -piece.y(), -piece.z());
		collector.submitModelPart(piece.part(), poseStack, RenderTypes.entityCutout(piece.texture()), state.lightCoords,
			OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** A lump of meat, 4x3x4 about its middle (texture 16x16, the box's strip at 0,0). */
	private static void build() {
		if (meat != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("meat", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
			PartPose.ZERO);
		meat = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("meat");
	}
}
