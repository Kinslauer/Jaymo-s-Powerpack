package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Armory;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.74) The Fang Gauntlets worn on the hands, not held: a leather cuff round each wrist, an iron plate over the back
 * of the hand and three fang claws running past the knuckles, always out (modelled on Kingdoms' hidden blade, without the
 * spring). Held in the main hand they're drawn on both arms (the off hand's only while it's empty) and the held item is
 * hidden, in third person ({@link Layer}, {@code FangGauntletsItemInHandLayerMixin}) and first person
 * ({@code FangGauntletsFirstPersonMixin}, {@link #firstPersonGuard}).
 * <p>
 * Idle, both arms are held up ahead in a fighting guard ({@link #applyModel}), laid over whatever animated the model (it's
 * in {@code ModelPoses}, and as each model is finally drawn), and a pose of ours, so Entity Model Features pauses for it
 * ({@code PoseTakeover}). While an attack swings, vanilla's or Better Combat's (their combo animations are untouched),
 * the guard lets go and they drive the arms; there is no custom swing.
 */
public final class FangGauntletsClient {
	// ===== the knobs ================================================================================================
	/** The cuff: from this far down the arm (pixels below the shoulder pivot), this tall, this far out round the arm, and this far proud of it. */
	static final float CUFF_TOP = 5.0F, CUFF_HEIGHT = 3.0F, CUFF_HALF = 2.5F, CUFF_GROW = 0.4F;
	/** The plate over the back of the hand: from this far down, this long, this wide, this thick, and its inner face this far out from the arm's middle. */
	static final float PLATE_TOP = 7.6F, PLATE_LENGTH = 2.8F, PLATE_WIDTH = 3.0F, PLATE_THICK = 0.4F, PLATE_OUT = 2.45F;
	/** The claws: from this far down to their tips (the hand ends at 10), the outer two a little shorter; this wide, this thick, this far apart. */
	static final float CLAW_TOP = 8.8F, CLAW_TIP = 15.5F, CLAW_SIDE_SHORTER = 1.0F, CLAW_WIDTH = 0.8F, CLAW_THICK = 0.5F, CLAW_GAP = 1.25F;
	/** The middle of a wide arm, and of a slim one, off the shoulder pivot (pixels, toward the body's outside). */
	static final float WIDE_ARM_MIDDLE = 1.0F, SLIM_ARM_MIDDLE = 0.5F;
	/** The guard: the arm raised ahead this far (radians, 0 hanging, -pi/2 straight out), plus this share of where you look; turned in this far; this much of the walk's arm swing kept; the extra when crouched. */
	static final float GUARD_PITCH = -1.3F, GUARD_LOOK = 0.5F, GUARD_IN = 0.35F, GUARD_SWAY = 0.2F, GUARD_CROUCH = 0.4F;
	/** First person: the arm moved in toward the middle of the view, up and forward (blocks), then turned about the shoulder: raised, turned in, tipped (degrees). */
	static final float FIRST_IN = 0.16F, FIRST_UP = 0.1F, FIRST_FORWARD = 0.05F, FIRST_RAISE = 12.0F, FIRST_TURN = 10.0F, FIRST_TIP = 4.0F;
	/** First person: the shoulder the arm turns about (right arm; x right, y up, z toward you), and how far below the view the empty off hand starts. */
	static final float FIRST_SHOULDER_X = 0.75F, FIRST_SHOULDER_Y = -0.85F, FIRST_ENTRY_DROP = 0.6F;
	// ================================================================================================================

	/** Bits on a render state: the right arm wears a gauntlet, the left does, and (4) they're in the guard (no attack swinging). */
	private static final int RIGHT = 1, LEFT = 2, GUARD = 4;
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/fang_gauntlets.png");
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:fang_gauntlets");

	private static ModelPart rightGauntlet, leftGauntlet;

	private FangGauntletsClient() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
	}

	public static boolean isGauntlets(ItemStack stack) {
		return Armory.FANG_GAUNTLETS != null && stack.is(Armory.FANG_GAUNTLETS);
	}

	/** From the avatar renderer's extractRenderState: which arms wear a gauntlet, and whether they're in the guard. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!isGauntlets(entity.getMainHandItem())) {
			state.setData(KEY, null);
			return;
		}
		boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
		int bits = mainRight ? RIGHT : LEFT;
		if (entity.getOffhandItem().isEmpty()) {
			bits |= mainRight ? LEFT : RIGHT;
		}
		boolean swinging = state.currentSwing != null && state.swingAnimation > 0.0F;
		if (!swinging && CombatAnimCompat.attack(entity) == null && !entity.isUsingItem()) {
			bits |= GUARD;
		}
		state.setData(KEY, bits);
	}

	/** Whether the guard is on this state (for PoseTakeover: Entity Model Features pauses so it holds). */
	public static boolean guarding(AvatarRenderState state) {
		Integer bits = state.getData(KEY);
		return bits != null && (bits & GUARD) != 0;
	}

	/** Whether this state wears gauntlets (the main-hand item is then not drawn). */
	public static boolean worn(AvatarRenderState state) {
		return state.getData(KEY) != null;
	}

	/**
	 * Right after a player's model is animated (for the layers, and as each model is drawn): each gauntlet arm comes up
	 * into the guard ahead of you, still but for the walk. Not mid-attack: then the swing's own animation has the arms.
	 */
	public static void applyModel(Model<?> m, Object s) {
		if (!(m instanceof HumanoidModel<?> model) || !(s instanceof AvatarRenderState state) || !guarding(state)) {
			return;
		}
		if (state.isFallFlying || state.swimAmount > 0.0F || state.isVisuallySwimming || state.isPassenger || CombatAnimCompat.firstPersonPass(state)
			|| PoseTakeover.posedOtherwise(state) || state.getData(com.jaymospowers.client.wounds.WoundNursePose.KEY) != null) {
			return;
		}
		int bits = state.getData(KEY);
		float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.8F, 0.6F);
		float turn = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.5F, 0.5F) * 0.5F;
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			if ((bits & (right ? RIGHT : LEFT)) == 0) {
				continue;
			}
			ModelPart arm = right ? model.rightArm : model.leftArm;
			// still but for the walk: vanilla's own arm swing, scaled down, and none of the idle bob
			float walk = Mth.cos(state.walkAnimationPos * 0.6662F + (right ? (float) Math.PI : 0.0F)) * state.walkAnimationSpeed / Math.max(state.speedValue, 0.001F);
			arm.xRot = GUARD_PITCH + look * GUARD_LOOK + walk * GUARD_SWAY + (state.isCrouching ? GUARD_CROUCH : 0.0F);
			arm.yRot = (right ? -GUARD_IN : GUARD_IN) + turn;
			arm.zRot = 0.0F;
		}
	}

	/**
	 * First person, before the game draws your bare arm for {@code hand}: with a gauntlet on it, moves the arm into the
	 * guard and returns true. Not while a swing runs ({@code attack} above 0, or a Better Combat animation): then the
	 * arm is left to the swing, drawn bare where the game puts it.
	 */
	public static boolean firstPersonGuard(PoseStack poseStack, AvatarRenderState avatar, InteractionHand hand, float attack, float inverseArmHeight) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || avatar.id != player.getId() || attack > 0.0F || CombatAnimCompat.attack(player) != null) {
			return false;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		boolean right = (main ? avatar.mainArm : avatar.mainArm.getOpposite()) == HumanoidArm.RIGHT;
		float side = right ? 1.0F : -1.0F;
		// the empty off hand isn't drawn by the game: it rises in with the swap of the main hand's height
		float drop = main ? 0.0F : Mth.clamp(inverseArmHeight, 0.0F, 1.0F) * FIRST_ENTRY_DROP;
		poseStack.translate(-side * FIRST_IN, FIRST_UP - drop, -FIRST_FORWARD);
		poseStack.translate(side * FIRST_SHOULDER_X, FIRST_SHOULDER_Y, 0.0F);
		poseStack.rotateDegrees(Axis.XP, FIRST_RAISE);
		poseStack.rotateDegrees(Axis.YP, side * FIRST_TURN);
		poseStack.rotateDegrees(Axis.ZP, side * FIRST_TIP);
		poseStack.translate(-side * FIRST_SHOULDER_X, -FIRST_SHOULDER_Y, 0.0F);
		return true;
	}

	/** Whether your own hand {@code hand}, showing {@code shown}, wears a gauntlet in first person (main hand holding them; the off hand empty). */
	public static boolean wornInHand(InteractionHand hand, ItemStack shown) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !isGauntlets(player.getMainHandItem())) {
			return false;
		}
		return hand == InteractionHand.MAIN_HAND ? isGauntlets(shown) : shown.isEmpty();
	}

	private static void build() {
		if (rightGauntlet != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float clawX = PLATE_OUT + PLATE_THICK - CLAW_THICK * 0.2F;
		float[] tips = {CLAW_TIP - CLAW_SIDE_SHORTER, CLAW_TIP, CLAW_TIP - CLAW_SIDE_SHORTER};
		// about the arm's middle; the right arm's back of hand faces -x (out from the body)
		CubeListBuilder right = CubeListBuilder.create()
			.texOffs(0, 0).addBox(-CUFF_HALF, CUFF_TOP, -CUFF_HALF, CUFF_HALF * 2.0F, CUFF_HEIGHT, CUFF_HALF * 2.0F, new CubeDeformation(CUFF_GROW))
			.texOffs(0, 10).addBox(-PLATE_OUT - PLATE_THICK, PLATE_TOP, -PLATE_WIDTH / 2.0F, PLATE_THICK, PLATE_LENGTH, PLATE_WIDTH);
		CubeListBuilder left = CubeListBuilder.create().mirror()
			.texOffs(0, 0).addBox(-CUFF_HALF, CUFF_TOP, -CUFF_HALF, CUFF_HALF * 2.0F, CUFF_HEIGHT, CUFF_HALF * 2.0F, new CubeDeformation(CUFF_GROW))
			.texOffs(0, 10).addBox(PLATE_OUT, PLATE_TOP, -PLATE_WIDTH / 2.0F, PLATE_THICK, PLATE_LENGTH, PLATE_WIDTH);
		for (int claw = 0; claw < 3; claw++) {
			float z = (claw - 1) * CLAW_GAP - CLAW_WIDTH / 2.0F;
			right.texOffs(claw * 8, 18).addBox(-clawX - CLAW_THICK, CLAW_TOP, z, CLAW_THICK, tips[claw] - CLAW_TOP, CLAW_WIDTH);
			left.texOffs(claw * 8, 18).addBox(clawX, CLAW_TOP, z, CLAW_THICK, tips[claw] - CLAW_TOP, CLAW_WIDTH);
		}
		root.addOrReplaceChild("right_gauntlet", right, PartPose.ZERO);
		root.addOrReplaceChild("left_gauntlet", left, PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		rightGauntlet = baked.getChild("right_gauntlet");
		leftGauntlet = baked.getChild("left_gauntlet");
	}

	/** One gauntlet, on the arm the pose stack is at (its pivot). */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean right, boolean slim) {
		build();
		poseStack.pushPose();
		float middle = slim ? SLIM_ARM_MIDDLE : WIDE_ARM_MIDDLE;
		poseStack.translate((right ? -middle : middle) / 16.0F, 0.0F, 0.0F);
		collector.submitModelPart(right ? rightGauntlet : leftGauntlet, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}

	/** First person: after the game draws your own arm, your gauntlet on it. */
	public static void firstPersonHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !isGauntlets(player.getMainHandItem())) {
			return;
		}
		boolean right = arm == source.rightArm;
		boolean mainArm = right == (player.getMainArm() == HumanoidArm.RIGHT);
		if (!mainArm && !player.getOffhandItem().isEmpty()) {
			return;
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		submit(poseStack, collector, light, right, player.getSkin().model() == PlayerModelType.SLIM);
		poseStack.popPose();
	}

	/** Draws the gauntlets on the hands as the arms are posed now. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			@Nullable Integer bits = state.getData(KEY);
			if (bits == null || state.isInvisible) {
				return;
			}
			PlayerModel body = this.getParentModel();
			boolean slim = state.skin.model() == PlayerModelType.SLIM;
			for (int side = 0; side < 2; side++) {
				boolean right = side == 0;
				ModelPart arm = right ? body.rightArm : body.leftArm;
				if ((bits & (right ? RIGHT : LEFT)) == 0 || !arm.visible) {
					continue;
				}
				poseStack.pushPose();
				body.root().translateAndRotate(poseStack);
				arm.translateAndRotate(poseStack);
				FangGauntletsClient.submit(poseStack, collector, lightCoords, right, slim);
				poseStack.popPose();
			}
		}
	}
}
