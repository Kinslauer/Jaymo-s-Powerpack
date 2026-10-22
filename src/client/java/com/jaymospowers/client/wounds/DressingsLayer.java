package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.BodyCover;
import com.jaymospowers.client.costume.CostumeModels;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Breaks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.89) Andy: "can the bandages and splints be visible on players?" A dressed part wears its linen (or a rag) and a
 * splinted arm or leg its two wooden slats, lashed with linen, for everyone to see ({@link Bleeding#WRAPPED} and
 * {@link Breaks#SPLINTED} are synced to all). Players and any humanoid mob (a villager a cleric tended). Nothing shows
 * under armour on that part, under a costume or the symbiote's suit ({@link BodyCover}), on a part that's gone, or on
 * an invisible body.
 * <p>
 * (0.15.122) Andy: "the splint seems to break the animations like the old costumes did, all of the medical items seem
 * to cause clipping problems". On a player they were hung off the limbs as the layer was submitted, which is before
 * the body is drawn: whatever animates the body as it's drawn (other mods, MCA's body) left them behind, floating or
 * through the limb. Now a player's are a model worn the way a costume is ({@link CostumeModels#worn}), taking the pose
 * the body is drawn in. And they sit clear of what they touch: a limb's dressing grows only a hair on the side that
 * presses on the body or the other leg (it stood out through them), and a splint's ties stand just over the dressing
 * (they fought it).
 */
public final class DressingsLayer {
	/** On a living body's render state: {wrapped parts (rags above {@link Bleeding#RAG_SHIFT}), splinted parts}, both by {@link BodyPart#ordinal()}. */
	public static final RenderStateDataKey<int[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:dressings");
	static final Identifier LINEN = JaymosPowers.id("textures/entity/wounds/linen_wrap.png");
	static final Identifier RAG = JaymosPowers.id("textures/entity/wounds/rag_wrap.png");
	static final Identifier SPLINT = JaymosPowers.id("textures/entity/wounds/splint.png");
	/**
	 * How far a wrap stands off the skin (pixels): over the sleeves and trousers (0.25), MCA's clothes (0.3125) and a
	 * costume's suit (0.35); the head's over the hat layer (0.5) and MCA's hats (0.5625). (0.15.122) On the side that
	 * presses on the body or the other leg, only {@link #SEAM}.
	 */
	static final float WRAP = 0.4F, HEAD_WRAP = 0.6F, SEAM = 0.1F;
	/** A splint's slats: how far out from the limb (pixels, to their inner face) and how thick. */
	static final float SLAT_GAP = 0.3F, SLAT = 0.7F;
	/** (0.15.122) The ties: over the slats, half their height again above and below, and just over a dressing elsewhere. */
	static final float TIE_OUT = 0.1F, TIE_HEIGHT = 0.2F, TIE_OVER = 0.1F;

	private static @Nullable Pieces wide, slim;

	private DressingsLayer() {
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** From every living renderer's extractRenderState: what's dressed and splinted, less what's gone. */
	public static void extract(LivingEntity entity, EntityRenderState state) {
		int wrapped = Bleeding.wrapped(entity), splinted = Breaks.splinted(entity);
		if (wrapped == 0 && splinted == 0) {
			state.setData(KEY, null);
			return;
		}
		int missing = 0;
		for (BodyPart part : BodyPart.values()) {
			if (gone(entity, part)) {
				missing |= bit(part);
			}
		}
		wrapped &= ~(missing | missing << Bleeding.RAG_SHIFT);
		splinted &= ~missing;
		state.setData(KEY, wrapped == 0 && splinted == 0 ? null : new int[] {wrapped, splinted});
	}

	/** A part cut off (Dismember's head, arms and legs; a foot goes with its leg). */
	private static boolean gone(LivingEntity entity, BodyPart part) {
		int lost = Dismember.missing(entity);
		return switch (part) {
			case HEAD -> (lost & Dismember.HEAD) != 0;
			case LEFT_ARM -> (lost & Dismember.LEFT_ARM) != 0;
			case RIGHT_ARM -> (lost & Dismember.RIGHT_ARM) != 0;
			case LEFT_LEG, LEFT_FOOT -> (lost & Dismember.LEFT_LEG) != 0;
			case RIGHT_LEG, RIGHT_FOOT -> (lost & Dismember.RIGHT_LEG) != 0;
			default -> false;
		};
	}

	/** What shows on this body just now: {linen, rags, splints} by part, or null for nothing (armour, a costume, invisible). */
	static int @Nullable [] shown(LivingEntityRenderState living) {
		int[] dressings = living.getData(KEY);
		if (dressings == null || living.isInvisible || BodyCover.body(living) || !(living instanceof HumanoidRenderState state)) {
			return null;
		}
		int wrapped = dressings[0] & (1 << Bleeding.RAG_SHIFT) - 1, rags = dressings[0] >>> Bleeding.RAG_SHIFT & wrapped;
		int splinted = dressings[1];
		int hidden = 0;
		if (!state.headEquipment.isEmpty() || BodyCover.head(state)) {
			hidden |= bit(BodyPart.HEAD);
		}
		if (!state.chestEquipment.isEmpty() && !state.chestEquipment.has(DataComponents.GLIDER)) {
			hidden |= bit(BodyPart.BODY) | bit(BodyPart.LEFT_ARM) | bit(BodyPart.RIGHT_ARM);
		}
		if (!state.legsEquipment.isEmpty()) {
			hidden |= bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG);
		}
		if (!state.feetEquipment.isEmpty()) {
			hidden |= bit(BodyPart.LEFT_FOOT) | bit(BodyPart.RIGHT_FOOT);
		}
		wrapped &= ~hidden;
		rags &= ~hidden;
		splinted &= ~hidden;
		return wrapped == 0 && splinted == 0 ? null : new int[] {wrapped & ~rags, rags, splinted};
	}

	// ---- the pieces --------------------------------------------------------------------------------------------------------

	/**
	 * Every piece in its bone's own space (as the player model's: an arm hangs from its shoulder at y -2, its inner side
	 * against the body, the right arm's outer side at -x; a leg from the hip at y 0, its inner side against the other).
	 * The textures are tiled cloth and wood ({@code tools/vanilla_style/dressings.py}), so any box reads right.
	 */
	private record Pieces(CubeListBuilder head, CubeListBuilder body, CubeListBuilder rightArm, CubeListBuilder leftArm,
		CubeListBuilder rightLeg, CubeListBuilder leftLeg, CubeListBuilder rightAnkle, CubeListBuilder leftAnkle,
		CubeListBuilder rightArmSplint, CubeListBuilder leftArmSplint, CubeListBuilder rightLegSplint, CubeListBuilder leftLegSplint) {
	}

	private static Pieces pieces(boolean slimArms) {
		Pieces pieces = slimArms ? slim : wide;
		if (pieces == null) {
			pieces = build(slimArms ? 3.0F : 4.0F);
			if (slimArms) {
				slim = pieces;
			} else {
				wide = pieces;
			}
		}
		return pieces;
	}

	/** A box from one corner to the other. */
	private static CubeListBuilder box(CubeListBuilder cubes, int u, int v, float x0, float y0, float z0, float x1, float y1, float z1) {
		return cubes.texOffs(u, v).addBox(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0);
	}

	private static Pieces build(float arm) {
		float w = WRAP;
		// the right arm spans x [1 - arm, 1], the left [-1, arm - 1]; their inner sides (1, -1) against the body
		float rightOut = 1.0F - arm, leftOut = arm - 1.0F;
		float h = HEAD_WRAP;
		CubeListBuilder head = box(CubeListBuilder.create(), 0, 0, -4.0F - h, -7.0F - h, -4.0F - h, 4.0F + h, -5.0F + h, 4.0F + h);
		// round the ribs (its sides only a hair out, under the arms), and a strip over the left shoulder down to it
		CubeListBuilder body = box(CubeListBuilder.create(), 0, 10, -4.0F - SEAM, 5.0F - w, -2.0F - w, 4.0F + SEAM, 9.0F + w, 2.0F + w);
		box(body, 24, 10, 1.0F - w, -w, -2.0F - w, 3.0F + w, 5.0F - w, 2.0F + w);
		// the forearm, a turn above the wrist
		CubeListBuilder rightArm = box(CubeListBuilder.create(), 0, 18, rightOut - w, 3.0F - w, -2.0F - w, 1.0F + SEAM, 8.0F + w, 2.0F + w);
		CubeListBuilder leftArm = box(CubeListBuilder.create(), 0, 18, -1.0F - SEAM, 3.0F - w, -2.0F - w, leftOut + w, 8.0F + w, 2.0F + w);
		// the shin, and the ankle for a foot; the right leg's inner side at +x, the left's at -x
		CubeListBuilder rightLeg = box(CubeListBuilder.create(), 0, 27, -2.0F - w, 4.0F - w, -2.0F - w, 2.0F + SEAM, 9.0F + w, 2.0F + w);
		CubeListBuilder leftLeg = box(CubeListBuilder.create(), 0, 27, -2.0F - SEAM, 4.0F - w, -2.0F - w, 2.0F + w, 9.0F + w, 2.0F + w);
		CubeListBuilder rightAnkle = box(CubeListBuilder.create(), 0, 36, -2.0F - w, 9.0F - w, -2.0F - w, 2.0F + SEAM, 12.0F, 2.0F + w);
		CubeListBuilder leftAnkle = box(CubeListBuilder.create(), 0, 36, -2.0F - SEAM, 9.0F - w, -2.0F - w, 2.0F + w, 12.0F, 2.0F + w);
		// a splint: a slat down the outer side and one down the front, the length of the bone, tied at both ends
		float slatIn = SLAT_GAP, slatOut = SLAT_GAP + SLAT, tieOut = slatOut + TIE_OUT, tieBack = w + TIE_OVER, tieIn = SEAM + TIE_OVER;
		float rightMid = rightOut + arm / 2.0F, leftMid = -1.0F + arm / 2.0F;
		CubeListBuilder rightArmSplint = splint(rightOut - slatOut, rightOut - slatIn, rightMid, -0.5F, 8.5F,
			rightOut - tieOut, 1.0F + tieIn, 0.0F, 7.0F, tieBack);
		CubeListBuilder leftArmSplint = splint(leftOut + slatIn, leftOut + slatOut, leftMid, -0.5F, 8.5F,
			-1.0F - tieIn, leftOut + tieOut, 0.0F, 7.0F, tieBack);
		CubeListBuilder rightLegSplint = splint(-2.0F - slatOut, -2.0F - slatIn, 0.0F, 1.0F, 11.0F,
			-2.0F - tieOut, 2.0F + tieIn, 2.0F, 9.0F, tieBack);
		CubeListBuilder leftLegSplint = splint(2.0F + slatIn, 2.0F + slatOut, 0.0F, 1.0F, 11.0F,
			-2.0F - tieIn, 2.0F + tieOut, 2.0F, 9.0F, tieBack);
		return new Pieces(head, body, rightArm, leftArm, rightLeg, leftLeg, rightAnkle, leftAnkle, rightArmSplint, leftArmSplint,
			rightLegSplint, leftLegSplint);
	}

	/**
	 * One limb's splint: the side slat across {@code sideX0..sideX1}, the front slat centred on {@code midX}, both from
	 * {@code top} to {@code bottom}; ties across {@code tieX0..tieX1} at {@code tie1} and {@code tie2} (each a pixel tall,
	 * then {@link #TIE_HEIGHT} more above and below), from just over the front slat to {@code back} behind the limb.
	 */
	private static CubeListBuilder splint(float sideX0, float sideX1, float midX, float top, float bottom, float tieX0, float tieX1,
		float tie1, float tie2, float back) {
		float front = -2.0F - SLAT_GAP - SLAT;
		CubeListBuilder cubes = CubeListBuilder.create();
		box(cubes, 0, 0, sideX0, top, -0.5F, sideX1, bottom, 0.5F);
		box(cubes, 4, 0, midX - 0.5F, top, front, midX + 0.5F, bottom, -2.0F - SLAT_GAP);
		box(cubes, 8, 0, tieX0, tie1 - TIE_HEIGHT, front - TIE_OUT, tieX1, tie1 + 1.0F + TIE_HEIGHT, 2.0F + back);
		box(cubes, 8, 5, tieX0, tie2 - TIE_HEIGHT, front - TIE_OUT, tieX1, tie2 + 1.0F + TIE_HEIGHT, 2.0F + back);
		return cubes;
	}

	// ---- on a player: worn like a costume ----------------------------------------------------------------------------------

	/** The names the pieces hang by, off each bone. */
	private static final String WRAP_PIECE = "wrap", ANKLE_PIECE = "ankle", SPLINT_PIECE = "splint";

	/** One renderer's worn models, for wide arms and slim: linen, rag and splint. */
	private static final class Worn {
		private final PlayerModel[] wide = new PlayerModel[3], slim = new PlayerModel[3];
		private final java.util.function.Supplier<PlayerModel> wearer;

		Worn(java.util.function.Supplier<PlayerModel> wearer) {
			this.wearer = wearer;
		}

		PlayerModel get(int kind, boolean slimArms) {
			PlayerModel[] models = slimArms ? this.slim : this.wide;
			if (models[kind] == null) {
				models[kind] = CostumeModels.worn(kind == 2 ? splints(slimArms) : wraps(slimArms), slimArms, this.wearer,
					(model, state) -> show(model, state, kind));
			}
			return models[kind];
		}
	}

	private static LayerDefinition wraps(boolean slimArms) {
		Pieces pieces = pieces(slimArms);
		MeshDefinition mesh = CostumeModels.bareBones(slimArms);
		PartDefinition root = mesh.getRoot();
		root.getChild("head").addOrReplaceChild(WRAP_PIECE, pieces.head(), PartPose.ZERO);
		root.getChild("body").addOrReplaceChild(WRAP_PIECE, pieces.body(), PartPose.ZERO);
		root.getChild("right_arm").addOrReplaceChild(WRAP_PIECE, pieces.rightArm(), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild(WRAP_PIECE, pieces.leftArm(), PartPose.ZERO);
		root.getChild("right_leg").addOrReplaceChild(WRAP_PIECE, pieces.rightLeg(), PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild(WRAP_PIECE, pieces.leftLeg(), PartPose.ZERO);
		root.getChild("right_leg").addOrReplaceChild(ANKLE_PIECE, pieces.rightAnkle(), PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild(ANKLE_PIECE, pieces.leftAnkle(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	private static LayerDefinition splints(boolean slimArms) {
		Pieces pieces = pieces(slimArms);
		MeshDefinition mesh = CostumeModels.bareBones(slimArms);
		PartDefinition root = mesh.getRoot();
		root.getChild("right_arm").addOrReplaceChild(SPLINT_PIECE, pieces.rightArmSplint(), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild(SPLINT_PIECE, pieces.leftArmSplint(), PartPose.ZERO);
		root.getChild("right_leg").addOrReplaceChild(SPLINT_PIECE, pieces.rightLegSplint(), PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild(SPLINT_PIECE, pieces.leftLegSplint(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	/** As a worn model animates, for the player it's drawn on: only their pieces of this kind (0 linen, 1 rags, 2 splints). */
	private static void show(PlayerModel model, AvatarRenderState state, int kind) {
		int[] shown = shown(state);
		int mask = shown == null ? 0 : shown[kind];
		if (kind == 2) {
			piece(model.rightArm, SPLINT_PIECE, mask, BodyPart.RIGHT_ARM);
			piece(model.leftArm, SPLINT_PIECE, mask, BodyPart.LEFT_ARM);
			piece(model.rightLeg, SPLINT_PIECE, mask, BodyPart.RIGHT_LEG);
			piece(model.leftLeg, SPLINT_PIECE, mask, BodyPart.LEFT_LEG);
			return;
		}
		piece(model.head, WRAP_PIECE, mask, BodyPart.HEAD);
		piece(model.body, WRAP_PIECE, mask, BodyPart.BODY);
		piece(model.rightArm, WRAP_PIECE, mask, BodyPart.RIGHT_ARM);
		piece(model.leftArm, WRAP_PIECE, mask, BodyPart.LEFT_ARM);
		piece(model.rightLeg, WRAP_PIECE, mask, BodyPart.RIGHT_LEG);
		piece(model.leftLeg, WRAP_PIECE, mask, BodyPart.LEFT_LEG);
		piece(model.rightLeg, ANKLE_PIECE, mask, BodyPart.RIGHT_FOOT);
		piece(model.leftLeg, ANKLE_PIECE, mask, BodyPart.LEFT_FOOT);
	}

	private static void piece(ModelPart bone, String name, int mask, BodyPart part) {
		if (bone.hasChild(name)) {
			bone.getChild(name).visible = (mask & bit(part)) != 0;
		}
	}

	// ---- on a humanoid mob: hung off the posed limbs -----------------------------------------------------------------------

	/** A mob's pieces, baked once each (wide arms only). */
	private static @Nullable ModelPart[] mobParts;

	private static ModelPart[] mobParts() {
		if (mobParts == null) {
			Pieces p = pieces(false);
			mobParts = new ModelPart[] {bakeOne(p.head(), 64), bakeOne(p.body(), 64), bakeOne(p.rightArm(), 64), bakeOne(p.leftArm(), 64),
				bakeOne(p.rightLeg(), 64), bakeOne(p.leftLeg(), 64), bakeOne(p.rightAnkle(), 64), bakeOne(p.leftAnkle(), 64),
				bakeOne(p.rightArmSplint(), 32), bakeOne(p.leftArmSplint(), 32), bakeOne(p.rightLegSplint(), 32), bakeOne(p.leftLegSplint(), 32)};
		}
		return mobParts;
	}

	private static ModelPart bakeOne(CubeListBuilder cubes, int size) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("piece", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, size, size).bakeRoot().getChild("piece");
	}

	/** On any renderer with a humanoid model; checks the state's a humanoid's (an armour stand's isn't) before it reads it. */
	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		private @Nullable Worn worn;

		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S living, float yRot, float xRot) {
			int[] shown = shown(living);
			if (shown == null) {
				return;
			}
			if (living instanceof AvatarRenderState state && this.getParentModel() instanceof PlayerModel parent) {
				// (0.15.122) worn like a costume: posed as the body's drawn
				if (this.worn == null) {
					this.worn = new Worn(() -> (PlayerModel)this.getParentModel());
				}
				CostumeModels.wear(state, parent);
				boolean slimArms = CostumeModels.slim(parent, state);
				Identifier[] textures = {LINEN, RAG, SPLINT};
				for (int kind = 0; kind < 3; kind++) {
					if (shown[kind] != 0) {
						renderColoredCutoutModel(this.worn.get(kind, slimArms), textures[kind], poseStack, collector, lightCoords, state, -1, 1);
					}
				}
				return;
			}
			if (!(this.getParentModel() instanceof HumanoidModel<?> model)) {
				return;
			}
			ModelPart[] parts = mobParts();
			int overlay = LivingEntityRenderer.getOverlayCoords(living, 0.0F);
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			ModelPart[] bones = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, model.rightLeg, model.leftLeg};
			BodyPart[] wraps = {BodyPart.HEAD, BodyPart.BODY, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG,
				BodyPart.RIGHT_FOOT, BodyPart.LEFT_FOOT};
			for (int i = 0; i < wraps.length; i++) {
				int bit = bit(wraps[i]);
				if (((shown[0] | shown[1]) & bit) != 0) {
					submitOn(poseStack, collector, lightCoords, overlay, bones[i], parts[i], (shown[1] & bit) != 0 ? RAG : LINEN);
				}
			}
			BodyPart[] limbs = {BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG};
			for (int i = 0; i < limbs.length; i++) {
				if ((shown[2] & bit(limbs[i])) != 0) {
					submitOn(poseStack, collector, lightCoords, overlay, bones[2 + i], parts[8 + i], SPLINT);
				}
			}
			poseStack.popPose();
		}

		private static void submitOn(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, ModelPart bone, ModelPart piece,
			Identifier texture) {
			if (!bone.visible || bone.skipDraw) {
				return;
			}
			poseStack.pushPose();
			bone.translateAndRotate(poseStack);
			collector.submitModelPart(piece, poseStack, RenderTypes.entityCutout(texture), light, overlay, null);
			poseStack.popPose();
		}
	}

	private static int bit(BodyPart part) {
		return 1 << part.ordinal();
	}
}
