package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.BodyCover;
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
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * (0.15.90) Andy: "can the bandages and splints be visible on players?" A dressed part wears its linen (or a rag) and a
 * splinted arm or leg its two wooden slats, lashed with linen, drawn on the part as it's posed, for everyone to see
 * ({@link Bleeding#WRAPPED} and {@link Breaks#SPLINTED} are synced to all). Players and any humanoid mob (a villager a
 * cleric tended). Nothing shows under armour on that part, under a costume or the symbiote's suit ({@link BodyCover}),
 * on a part that's gone, or on an invisible body.
 */
public final class DressingsLayer {
	/** On a living body's render state: {wrapped parts (rags above {@link Bleeding#RAG_SHIFT}), splinted parts}, both by {@link BodyPart#ordinal()}. */
	public static final RenderStateDataKey<int[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:dressings");
	static final Identifier LINEN = JaymosPowers.id("textures/entity/wounds/linen_wrap.png");
	static final Identifier RAG = JaymosPowers.id("textures/entity/wounds/rag_wrap.png");
	static final Identifier SPLINT = JaymosPowers.id("textures/entity/wounds/splint.png");
	/** How far a wrap stands off the skin (pixels): over the sleeves and trousers (0.25); the head's over the hat (0.5). */
	static final float WRAP = 0.4F, HEAD_WRAP = 0.6F;
	/** A splint's slats: how far out from the limb (pixels, to their inner face) and how thick; its ties wrap round both. */
	static final float SLAT_GAP = 0.3F, SLAT = 0.7F, TIE = 1.1F, TIE_HEIGHT = 0.2F;

	private static Parts wide, slim;

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
				missing |= 1 << part.ordinal();
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

	/** The wraps and splints, baked once for wide arms and once for slim (3 pixels wide). */
	private record Parts(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart leg, ModelPart ankle,
		ModelPart rightArmSplint, ModelPart leftArmSplint, ModelPart rightLegSplint, ModelPart leftLegSplint,
		ModelPart rightArmTies, ModelPart leftArmTies, ModelPart legTies) {
	}

	private static Parts parts(boolean slimArms) {
		if (slimArms ? slim == null : wide == null) {
			Parts parts = bake(slimArms ? 3.0F : 4.0F);
			if (slimArms) {
				slim = parts;
			} else {
				wide = parts;
			}
		}
		return slimArms ? slim : wide;
	}

	/**
	 * Every piece in its part's own space (as the player model's: an arm hangs from its shoulder at y -2, the right arm's
	 * outer side at -x; a leg from the hip at y 0). The wrap textures and the splint's are laid out the same way, one
	 * box under another down the sheet ({@code tools/vanilla_style/dressings.py}).
	 */
	private static Parts bake(float arm) {
		// the right arm spans x [1 - arm, 1], the left [-1, arm - 1]
		float rightIn = 1.0F, rightOut = 1.0F - arm, leftOut = arm - 1.0F;
		CubeDeformation wrap = new CubeDeformation(WRAP);
		ModelPart head = wrapPart(CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(HEAD_WRAP)));
		// round the ribs, and a strip over the left shoulder down to it
		ModelPart body = wrapPart(CubeListBuilder.create().texOffs(0, 10).addBox(-4.0F, 5.0F, -2.0F, 8.0F, 4.0F, 4.0F, wrap)
			.texOffs(24, 10).addBox(1.0F, 0.0F, -2.0F, 2.0F, 5.0F, 4.0F, wrap));
		// the forearm, a turn above the wrist
		ModelPart rightArm = wrapPart(CubeListBuilder.create().texOffs(0, 18).addBox(rightOut, 3.0F, -2.0F, arm, 5.0F, 4.0F, wrap));
		ModelPart leftArm = wrapPart(CubeListBuilder.create().texOffs(0, 18).addBox(-1.0F, 3.0F, -2.0F, arm, 5.0F, 4.0F, wrap));
		// the shin, and the ankle for a foot
		ModelPart leg = wrapPart(CubeListBuilder.create().texOffs(0, 27).addBox(-2.0F, 4.0F, -2.0F, 4.0F, 5.0F, 4.0F, wrap));
		ModelPart ankle = wrapPart(CubeListBuilder.create().texOffs(0, 36).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, wrap));
		// a splint: a slat down the outer side and one down the front, the length of the bone, tied at both ends
		float slat = SLAT, out = SLAT_GAP + SLAT;
		ModelPart rightArmSplint = splintPart(CubeListBuilder.create()
			.texOffs(0, 0).addBox(rightOut - out, -0.5F, -0.5F, slat, 9.0F, 1.0F)
			.texOffs(4, 0).addBox(rightOut + arm / 2.0F - 0.5F, -0.5F, -2.0F - out, 1.0F, 9.0F, slat));
		ModelPart leftArmSplint = splintPart(CubeListBuilder.create()
			.texOffs(0, 0).addBox(leftOut + SLAT_GAP, -0.5F, -0.5F, slat, 9.0F, 1.0F)
			.texOffs(4, 0).addBox(-1.0F + arm / 2.0F - 0.5F, -0.5F, -2.0F - out, 1.0F, 9.0F, slat));
		ModelPart rightLegSplint = splintPart(CubeListBuilder.create()
			.texOffs(0, 0).addBox(-2.0F - out, 1.0F, -0.5F, slat, 10.0F, 1.0F)
			.texOffs(4, 0).addBox(-0.5F, 1.0F, -2.0F - out, 1.0F, 10.0F, slat));
		ModelPart leftLegSplint = splintPart(CubeListBuilder.create()
			.texOffs(0, 0).addBox(2.0F + SLAT_GAP, 1.0F, -0.5F, slat, 10.0F, 1.0F)
			.texOffs(4, 0).addBox(-0.5F, 1.0F, -2.0F - out, 1.0F, 10.0F, slat));
		CubeDeformation tie = new CubeDeformation(TIE, TIE_HEIGHT, TIE);
		ModelPart rightArmTies = tiesPart(CubeListBuilder.create().texOffs(8, 0).addBox(rightOut, 0.0F, -2.0F, arm, 1.0F, 4.0F, tie)
			.texOffs(8, 5).addBox(rightOut, 7.0F, -2.0F, arm, 1.0F, 4.0F, tie));
		ModelPart leftArmTies = tiesPart(CubeListBuilder.create().texOffs(8, 0).addBox(-1.0F, 0.0F, -2.0F, arm, 1.0F, 4.0F, tie)
			.texOffs(8, 5).addBox(-1.0F, 7.0F, -2.0F, arm, 1.0F, 4.0F, tie));
		ModelPart legTies = tiesPart(CubeListBuilder.create().texOffs(8, 0).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 1.0F, 4.0F, tie)
			.texOffs(8, 5).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 1.0F, 4.0F, tie));
		return new Parts(head, body, rightArm, leftArm, leg, ankle, rightArmSplint, leftArmSplint, rightLegSplint, leftLegSplint,
			rightArmTies, leftArmTies, legTies);
	}

	private static ModelPart wrapPart(CubeListBuilder cubes) {
		return bakeOne(cubes, 64, 64);
	}

	private static ModelPart splintPart(CubeListBuilder cubes) {
		return bakeOne(cubes, 32, 32);
	}

	private static ModelPart tiesPart(CubeListBuilder cubes) {
		return bakeOne(cubes, 32, 32);
	}

	private static ModelPart bakeOne(CubeListBuilder cubes, int width, int height) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("piece", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, width, height).bakeRoot().getChild("piece");
	}

	/** On any renderer with a humanoid model; checks the state's a humanoid's (an armour stand's isn't) before it reads it. */
	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S living, float yRot, float xRot) {
			int[] dressings = living.getData(KEY);
			if (dressings == null || living.isInvisible || BodyCover.body(living) || !(living instanceof HumanoidRenderState state)
				|| !(this.getParentModel() instanceof HumanoidModel<?> model)) {
				return;
			}
			Parts parts = parts(state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM);
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
			int wrapped = dressings[0], splinted = dressings[1];
			boolean chest = !state.chestEquipment.isEmpty() && !state.chestEquipment.has(DataComponents.GLIDER);
			boolean legs = !state.legsEquipment.isEmpty(), feet = !state.feetEquipment.isEmpty();
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			if (!state.headEquipment.isEmpty() || BodyCover.head(state)) {
				wrapped &= ~bit(BodyPart.HEAD);
			}
			if (chest) {
				wrapped &= ~(bit(BodyPart.BODY) | bit(BodyPart.LEFT_ARM) | bit(BodyPart.RIGHT_ARM));
				splinted &= ~(bit(BodyPart.LEFT_ARM) | bit(BodyPart.RIGHT_ARM));
			}
			if (legs) {
				wrapped &= ~(bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG));
				splinted &= ~(bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG));
			}
			if (feet) {
				wrapped &= ~(bit(BodyPart.LEFT_FOOT) | bit(BodyPart.RIGHT_FOOT));
			}
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.HEAD, model.head, parts.head());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.BODY, model.body, parts.body());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.RIGHT_ARM, model.rightArm, parts.rightArm());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.LEFT_ARM, model.leftArm, parts.leftArm());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.RIGHT_LEG, model.rightLeg, parts.leg());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.LEFT_LEG, model.leftLeg, parts.leg());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.RIGHT_FOOT, model.rightLeg, parts.ankle());
			this.wrap(poseStack, collector, lightCoords, overlay, wrapped, BodyPart.LEFT_FOOT, model.leftLeg, parts.ankle());
			this.splint(poseStack, collector, lightCoords, overlay, splinted, BodyPart.RIGHT_ARM, model.rightArm, parts.rightArmSplint(), parts.rightArmTies());
			this.splint(poseStack, collector, lightCoords, overlay, splinted, BodyPart.LEFT_ARM, model.leftArm, parts.leftArmSplint(), parts.leftArmTies());
			this.splint(poseStack, collector, lightCoords, overlay, splinted, BodyPart.RIGHT_LEG, model.rightLeg, parts.rightLegSplint(), parts.legTies());
			this.splint(poseStack, collector, lightCoords, overlay, splinted, BodyPart.LEFT_LEG, model.leftLeg, parts.leftLegSplint(), parts.legTies());
			poseStack.popPose();
		}

		private void wrap(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int wrapped, BodyPart part, ModelPart on,
			ModelPart piece) {
			int bit = bit(part);
			if ((wrapped & bit) == 0 || !on.visible || on.skipDraw) {
				return;
			}
			Identifier texture = (wrapped & bit << Bleeding.RAG_SHIFT) != 0 ? RAG : LINEN;
			poseStack.pushPose();
			on.translateAndRotate(poseStack);
			collector.submitModelPart(piece, poseStack, RenderTypes.entityCutout(texture), light, overlay, null);
			poseStack.popPose();
		}

		private void splint(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int splinted, BodyPart part, ModelPart on,
			ModelPart slats, ModelPart ties) {
			if ((splinted & bit(part)) == 0 || !on.visible || on.skipDraw) {
				return;
			}
			poseStack.pushPose();
			on.translateAndRotate(poseStack);
			collector.submitModelPart(slats, poseStack, RenderTypes.entityCutout(SPLINT), light, overlay, null);
			collector.submitModelPart(ties, poseStack, RenderTypes.entityCutout(SPLINT), light, overlay, null);
			poseStack.popPose();
		}
	}

	private static int bit(BodyPart part) {
		return 1 << part.ordinal();
	}
}
