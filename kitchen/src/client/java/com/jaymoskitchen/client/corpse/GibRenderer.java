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
import net.minecraft.client.model.geom.builders.PartDefinition;
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
 * <p>
 * A piece of the body's insides ({@link Gib#piece}) is one of our own small models off {@link #GORE} instead: an organ
 * and a rope of gut in the body's blood colour, ribs, spine, a bone shard, a scrap of cloth. None of it is drawn for a
 * player who has blood turned off (Jaymo's Powers' Video Settings button).
 */
public class GibRenderer extends EntityRenderer<Gib, GibRenderer.State> {
	static final Identifier MEAT = JaymosKitchen.id("textures/entity/gib.png");
	/** A box bigger than this (pixels, its longest side) is drawn shrunk to it: a chunk, not a whole torso. */
	static final float MAX_PIXELS = 9.0F;
	/** Tries for a box that is solid (no side under a pixel: not a flat hair or whisker plane) before taking any. */
	static final int TRIES = 6;
	/** How deep it has sunk at the end (blocks, for a man's size). */
	static final float SINK_DEPTH = 0.25F;

	/** The insides' sheet (64x32, painted by tools/gen_gore.py): soft pieces in greys for the blood's tint, bone, cloth. */
	static final Identifier GORE = JaymosKitchen.id("textures/entity/gore.png");
	/** A soft piece is its body's blood this much brighter, and a rope of gut this far toward {@link #GUT_PINK}. */
	static final float SOFT_BRIGHT = 1.6F, GUT_PALE = 0.3F;
	static final int GUT_PINK = 0xFFC8C8;

	private static @Nullable ModelPart meat;
	/** The insides' models by {@link Gib#piece} (none for a chunk), and half their height at rest (pixels). */
	private static ModelPart @Nullable [] gore;
	private static final float[] GORE_HALF_HEIGHT = {0.0F, 1.5F, 1.0F, 1.0F, 1.0F, 0.5F, 0.125F};
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
		/** Its tint (ARGB; -1 for none). */
		int tint = -1;
		/** The viewer has blood turned off: not drawn. */
		boolean hidden;
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
		state.tint = tint(gib);
		state.hidden = bloodOff();
		this.shadowRadius = state.hidden ? 0.0F : 0.12F * state.size * (1.0F - state.sunk);
	}

	/** One box of the body's model, in its skin, picked by the chunk's id; a lump of meat if there's no body to ask. */
	@SuppressWarnings("unchecked")
	private Piece piece(Gib gib, float partialTicks) {
		int inside = gib.piece();
		if (inside != Gib.CHUNK) {
			build();
			int which = Mth.clamp(inside, 1, gore.length - 1);
			return new Piece(gore[which], GORE, 0.0F, 0.0F, 0.0F, 1.0F, GORE_HALF_HEIGHT[which]);
		}
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
		if (piece == null || state.hidden) {
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
			OverlayTexture.NO_OVERLAY, null, state.tint);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** A soft piece's tint: its body's blood, brightened (gut paler and pinker); none for bone, cloth and chunks. */
	private static int tint(Gib gib) {
		int piece = gib.piece();
		if (piece != Gib.ORGAN && piece != Gib.GUTS) {
			return -1;
		}
		int blood = gib.tint();
		float r = Math.min(255.0F, (blood >> 16 & 255) * SOFT_BRIGHT), g = Math.min(255.0F, (blood >> 8 & 255) * SOFT_BRIGHT),
			b = Math.min(255.0F, (blood & 255) * SOFT_BRIGHT);
		if (piece == Gib.GUTS) {
			r = Mth.lerp(GUT_PALE, r, GUT_PINK >> 16 & 255);
			g = Mth.lerp(GUT_PALE, g, GUT_PINK >> 8 & 255);
			b = Mth.lerp(GUT_PALE, b, GUT_PINK & 255);
		}
		return 0xFF000000 | (int) r << 16 | (int) g << 8 | (int) b;
	}

	private static java.lang.invoke.@Nullable MethodHandle bloodOff;
	private static boolean lookedForBloodOff;

	/** Whether this player has blood turned off (Jaymo's Powers' {@code BloodSettings.off()}, by name; never without Powers). */
	public static boolean bloodOff() {
		if (!lookedForBloodOff) {
			lookedForBloodOff = true;
			try {
				bloodOff = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.blood.BloodSettings"),
					"off", java.lang.invoke.MethodType.methodType(boolean.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				bloodOff = null;
			}
		}
		if (bloodOff != null) {
			try {
				return (boolean) bloodOff.invokeExact();
			} catch (Throwable e) {
				bloodOff = null;
			}
		}
		return false;
	}

	/** A lump of meat, 4x3x4 about its middle (texture 16x16, the box's strip at 0,0); and the insides, each about its middle. */
	private static void build() {
		if (meat != null) {
			return;
		}
		MeshDefinition insides = new MeshDefinition();
		PartDefinition root = insides.getRoot();
		root.addOrReplaceChild("organ", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
			.texOffs(0, 6).addBox(1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("guts", CubeListBuilder.create().texOffs(16, 0).addBox(-3.0F, -1.0F, -2.5F, 6.0F, 2.0F, 2.0F)
			.texOffs(16, 4).addBox(1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 5.0F)
			.texOffs(16, 11).addBox(-3.0F, -1.0F, 2.5F, 4.0F, 2.0F, 2.0F), PartPose.ZERO);
		CubeListBuilder ribs = CubeListBuilder.create().texOffs(32, 2).addBox(-0.5F, -1.0F, -2.5F, 1.0F, 1.0F, 5.0F);
		for (float z = -2.5F; z < 2.0F; z += 2.0F) {
			// each rib arches: across, then down at both ends
			ribs.texOffs(32, 0).addBox(-3.0F, -0.5F, z, 6.0F, 1.0F, 1.0F)
				.texOffs(32, 8).addBox(-3.0F, 0.5F, z, 1.0F, 1.0F, 1.0F)
				.texOffs(32, 8).addBox(2.0F, 0.5F, z, 1.0F, 1.0F, 1.0F);
		}
		root.addOrReplaceChild("ribs", ribs, PartPose.ZERO);
		CubeListBuilder spine = CubeListBuilder.create().texOffs(48, 4).addBox(-0.5F, -0.5F, -3.5F, 1.0F, 1.0F, 7.0F);
		for (float z = -3.5F; z < 3.0F; z += 2.0F) {
			spine.texOffs(48, 0).addBox(-1.0F, -1.0F, z, 2.0F, 2.0F, 1.0F);
		}
		root.addOrReplaceChild("spine", spine, PartPose.ZERO);
		root.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 16).addBox(-0.5F, -0.5F, -2.5F, 1.0F, 1.0F, 5.0F)
			.texOffs(0, 22).addBox(-1.0F, -0.5F, 2.0F, 2.0F, 1.0F, 1.0F), PartPose.ZERO);
		// a scrap of cloth: three thin strips, so its edge is ragged
		root.addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(16, 16).addBox(-3.0F, -0.125F, -1.0F, 6.0F, 0.25F, 2.0F)
			.texOffs(16, 19).addBox(-2.0F, -0.125F, 1.0F, 3.0F, 0.25F, 3.0F)
			.texOffs(16, 23).addBox(1.0F, -0.125F, -4.0F, 2.0F, 0.25F, 3.0F), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(insides, 64, 32).bakeRoot();
		gore = new ModelPart[] {null, baked.getChild("organ"), baked.getChild("guts"), baked.getChild("ribs"), baked.getChild("spine"),
			baked.getChild("bone"), baked.getChild("tatters")};
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("meat", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
			PartPose.ZERO);
		meat = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("meat");
	}
}
