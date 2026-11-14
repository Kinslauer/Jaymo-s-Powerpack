package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.client.bb.BbModel;
import com.jaymoskitchen.corpse.Gib;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
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
import net.minecraft.client.renderer.texture.UvMapping;
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
 * A piece of the body's insides ({@link Gib#piece}) is one of our own Blockbench models instead
 * ({@code tools/bbmodels/gore-pieces.bbmodel}, baked to {@code bbmodels/gore.json}): an organ and a rope of gut in the
 * body's blood colour, ribs, spine, a bone shard, a scrap of cloth. Andy: "the tatters should use the colors on the
 * corpse' model that it came from": a scrap is cut from the body's own clothes (an MCA villager's or player's
 * clothing texture), a player's skin, or a patch off the creature's own model ({@link #tatters}); only a body the
 * client can't rebuild leaves plain cloth. None of it is drawn for a player who has blood turned off (Jaymo's Powers'
 * Video Settings button).
 */
public class GibRenderer extends EntityRenderer<Gib, GibRenderer.State> {
	static final Identifier MEAT = JaymosKitchen.id("textures/entity/gib.png");
	/** A box bigger than this (pixels, its longest side) is drawn shrunk to it: a chunk, not a whole torso. */
	static final float MAX_PIXELS = 9.0F;
	/** Tries for a box that is solid (no side under a pixel: not a flat hair or whisker plane) before taking any. */
	static final int TRIES = 6;
	/** How deep it has sunk at the end (blocks, for a man's size). */
	static final float SINK_DEPTH = 0.25F;

	/** The insides' bones in the Blockbench model, by {@link Gib#piece} (none for a chunk). */
	static final String[] GORE_BONES = {"", "organ", "guts", "ribs", "spine", "bone", "tatters"};
	/**
	 * Where clothes lie on a skin laid out as a player's (64x64; x, y, width, height): the front and back of the trunk,
	 * an arm and a leg of each side. A scrap is a {@link #PATCH}-pixel square of one of them (smaller on a limb).
	 */
	static final int[][] CLOTHED = {{20, 20, 8, 12}, {32, 20, 8, 12}, {44, 20, 4, 12}, {4, 20, 4, 12}, {20, 52, 4, 12}, {36, 52, 4, 12}};
	static final int PATCH = 6;
	/** A soft piece is its body's blood this much brighter, and a rope of gut this far toward {@link #GUT_PINK}. */
	static final float SOFT_BRIGHT = 1.6F, GUT_PALE = 0.3F;
	static final int GUT_PINK = 0xFFC8C8;

	private static @Nullable ModelPart meat;
	/** The insides by {@link Gib#piece} (none for a chunk), and their sheet. */
	private static Inside @Nullable [] gore;
	private static @Nullable Identifier goreTexture;

	/** One of the insides: its bone, drawn about its own middle; half its height at rest (pixels); the rectangle of the sheet its faces use. */
	record Inside(ModelPart part, float halfHeight, float u0, float v0, float u1, float v1) {
	}

	/** Lays a scrap's faces, drawn over {@code from} on the insides' sheet, over {@code to} on another texture (a skin). */
	record Cut(float fromU0, float fromV0, float fromU1, float fromV1, float u0, float v0, float u1, float v1) implements UvMapping {
		@Override
		public float getU(float u) {
			return Mth.lerp(Mth.clamp((u - fromU0) / (fromU1 - fromU0), 0.0F, 1.0F), u0, u1);
		}

		@Override
		public float getV(float v) {
			return Mth.lerp(Mth.clamp((v - fromV0) / (fromV1 - fromV0), 0.0F, 1.0F), v0, v1);
		}
	}
	/** Each chunk's box and skin, worked out once. */
	private final Map<Gib, Piece> pieces = new java.util.WeakHashMap<>();

	/** A box cut loose (alone in a part of its own, at no pose), its skin, its middle (pixels) and how much it's shrunk. */
	record Piece(ModelPart part, Identifier texture, float x, float y, float z, float fit, float halfHeight, @Nullable UvMapping uv) {
		Piece(ModelPart part, Identifier texture, float x, float y, float z, float fit, float halfHeight) {
			this(part, texture, x, y, z, fit, halfHeight, null);
		}

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
			Inside which = gore[Mth.clamp(inside, 1, gore.length - 1)];
			UvMapping cut = null;
			Identifier texture = goreTexture;
			if (inside == Gib.TATTERS) {
				Map.Entry<Identifier, float[]> cloth = tatters(gib, partialTicks);
				if (cloth != null) {
					float[] to = cloth.getValue();
					texture = cloth.getKey();
					cut = new Cut(which.u0(), which.v0(), which.u1(), which.v1(), to[0], to[1], to[2], to[3]);
				}
			}
			return new Piece(which.part(), texture, 0.0F, 0.0F, 0.0F, 1.0F, which.halfHeight(), cut);
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
			OverlayTexture.NO_OVERLAY, piece.uv(), state.tint);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * What a scrap of cloth off {@code gib}'s body is cut from: a texture and the rectangle of it (u0, v0, u1, v1) the scrap
	 * shows. The body's MCA clothes first (a villager's, or the villager MCA draws that player as), then a player's own
	 * skin, both where clothes lie on a player's skin ({@link #CLOTHED}); for any other creature a face of one box of its
	 * model (a humanoid's trunk, arms or legs, never its head). Null for a body the client can't rebuild.
	 */
	@SuppressWarnings("unchecked")
	private Map.@Nullable Entry<Identifier, float[]> tatters(Gib gib, float partialTicks) {
		RandomSource random = RandomSource.create(gib.getId() * 7919L);
		java.util.Optional<java.util.UUID> owner = gib.owner();
		Entity body = owner.isPresent() ? McaLooks.of(owner) : gib.clientBody();
		Identifier clothes = McaLooks.clothes(body);
		if (clothes == null && owner.isPresent()) {
			clothes = Skins.of(owner).body().texturePath();
		}
		if (clothes != null) {
			int[] where = CLOTHED[random.nextInt(CLOTHED.length)];
			int w = Math.min(PATCH, where[2]), h = Math.min(PATCH, where[3]);
			int x = where[0] + random.nextInt(where[2] - w + 1), y = where[1] + random.nextInt(where[3] - h + 1);
			return Map.entry(clothes, new float[] {x / 64.0F, y / 64.0F, (x + w) / 64.0F, (y + h) / 64.0F});
		}
		if (body == null) {
			return null;
		}
		EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
		if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)
			|| !(renderer.createRenderState(body, partialTicks) instanceof LivingEntityRenderState bodyState)) {
			return null;
		}
		Identifier texture = ((LivingEntityRenderer<?, LivingEntityRenderState, ?>) living).getTextureLocation(bodyState);
		List<ModelPart> parts = living.getModel() instanceof HumanoidModel<?> humanoid
			? List.of(humanoid.body, humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg)
			: living.getModel().allParts();
		parts = parts.stream().filter(part -> !part.isEmpty()).toList();
		if (parts.isEmpty()) {
			return null;
		}
		// the widest face of one of its boxes
		ModelPart.Cube cube = parts.get(random.nextInt(parts.size())).getRandomCube(random);
		float[] best = null;
		float bestArea = 0.0F;
		for (ModelPart.Polygon polygon : cube.polygons) {
			float u0 = 1.0F, v0 = 1.0F, u1 = 0.0F, v1 = 0.0F;
			for (ModelPart.Vertex vertex : polygon.vertices()) {
				u0 = Math.min(u0, vertex.u());
				v0 = Math.min(v0, vertex.v());
				u1 = Math.max(u1, vertex.u());
				v1 = Math.max(v1, vertex.v());
			}
			float area = (u1 - u0) * (v1 - v0);
			if (area > bestArea) {
				bestArea = area;
				best = new float[] {u0, v0, u1, v1};
			}
		}
		return best == null ? null : Map.entry(texture, best);
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

	/** A lump of meat, 4x3x4 about its middle (texture 16x16, the box's strip at 0,0); and the insides, off their Blockbench model. */
	private static void build() {
		if (meat != null) {
			return;
		}
		BbModel model = BbModel.load(JaymosKitchen.MOD_ID, "gore");
		goreTexture = model.texture;
		gore = new Inside[GORE_BONES.length];
		for (int i = 1; i < GORE_BONES.length; i++) {
			ModelPart part = model.part(GORE_BONES[i]);
			// drawn alone, about its own middle: not where it stands in the Blockbench file
			part.setPos(0.0F, 0.0F, 0.0F);
			float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, 1.0F, 1.0F, 0.0F, 0.0F};
			part.visit(new PoseStack(), (pose, path, index, cube) -> {
				bounds[0] = Math.min(bounds[0], cube.minY);
				bounds[1] = Math.max(bounds[1], cube.maxY);
				for (ModelPart.Polygon polygon : cube.polygons) {
					for (ModelPart.Vertex vertex : polygon.vertices()) {
						bounds[2] = Math.min(bounds[2], vertex.u());
						bounds[3] = Math.min(bounds[3], vertex.v());
						bounds[4] = Math.max(bounds[4], vertex.u());
						bounds[5] = Math.max(bounds[5], vertex.v());
					}
				}
			});
			gore[i] = new Inside(part, (bounds[1] - bounds[0]) / 2.0F, bounds[2], bounds[3], bounds[4], bounds[5]);
		}
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("meat", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 3.0F, 4.0F),
			PartPose.ZERO);
		meat = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("meat");
	}
}
