package com.jaymospowers.client.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.Armory;
import com.jaymospowers.boxing.BoxingGear;
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
 * (0.18.75) The Fang Gauntlets worn on the hands, not held: the base gauntlet (below) with three fang spikes running past
 * the knuckles, always out. Held in the main hand they're drawn on both arms (the off hand's only while it's empty) and
 * the held item is hidden, in third person ({@link Layer}, {@code FangGauntletsItemInHandLayerMixin}) and first person
 * ({@code FangGauntletsFirstPersonMixin}, {@link #firstPersonGuard}). A gauntlet in the off hand is worn on that arm,
 * its own piece whatever the main hand holds, and punches with a right click ({@code boxing/OffhandPunch}).
 * <p>
 * Idle, both arms are held up ahead in a fighting guard ({@link #applyModel}), laid over whatever animated the model (it's
 * in {@code ModelPoses}, and as each model is finally drawn), and a pose of ours, so Entity Model Features pauses for it
 * ({@code PoseTakeover}). While an attack swings, vanilla's or Better Combat's (their combo animations are untouched),
 * the guard lets go and they drive the arms; there is no custom swing. Only a gauntlet in the main hand brings the guard.
 * <p>
 * <b>The base gauntlet</b> (Andy, 2026-11-22: "reskin a copy, and use that base for all our gauntlet or fist weapons.
 * except the hidden blade"): every piece here is one shape, the sleeve round the fist of Gallant Gauntlets' gauntlet
 * (by Joshieman, CC BY-NC 4.0, https://modrinth.com/mod/gauntlets; credits and our changes in
 * {@code art/gallant_gauntlets/CREDITS.md}, the licence in {@code docs/licenses/}), its textures reskinned in our
 * colours by {@code tools/gauntlet_assets.py}. Each piece adds its own to it: the Fang Gauntlets' spikes, a cestus's
 * knuckle band, a bracer's guard over the wrist; boxing's Hand Wraps ({@link BoxingGear}) are it in bandage.
 */
public final class FangGauntletsClient {
	// ===== the knobs ================================================================================================
	/**
	 * The base gauntlet's sleeve (Gallant's 4x6x4, round the fist): from this far down the arm (pixels below the shoulder
	 * pivot) to the hand's end at 10, and this far proud of the arm: clear of a skin's sleeve layer (a quarter pixel proud
	 * itself), which it would sink into, so it showed in patches.
	 */
	static final float SLEEVE_TOP = 4.0F, SLEEVE_GROW = 0.55F;
	/**
	 * The Fang Gauntlets' spikes: from this far down to their tips (the hand ends at 10), the outer two a little shorter;
	 * this wide, this thick, this far apart, and their inner face this far out from the arm's middle (on the sleeve).
	 */
	static final float CLAW_TOP = 8.8F, CLAW_TIP = 15.5F, CLAW_SIDE_SHORTER = 1.0F, CLAW_WIDTH = 0.8F, CLAW_THICK = 0.5F, CLAW_GAP = 1.25F,
		CLAW_OUT = 2.45F;
	/** The middle of a wide arm, and of a slim one, off the shoulder pivot (pixels, toward the body's outside). */
	static final float WIDE_ARM_MIDDLE = 1.0F, SLIM_ARM_MIDDLE = 0.5F;
	/** The guard: the arm raised ahead this far (radians, 0 hanging, -pi/2 straight out), plus this share of where you look; turned in this far; this much of the walk's arm swing kept; the extra when crouched. */
	static final float GUARD_PITCH = -1.3F, GUARD_LOOK = 0.5F, GUARD_IN = 0.35F, GUARD_SWAY = 0.2F, GUARD_CROUCH = 0.4F;
	/** First person: the arm moved in toward the middle of the view, up and forward (blocks), then turned about the shoulder: raised, turned in, tipped (degrees). */
	static final float FIRST_IN = 0.16F, FIRST_UP = 0.1F, FIRST_FORWARD = 0.05F, FIRST_RAISE = 12.0F, FIRST_TURN = 10.0F, FIRST_TIP = 4.0F;
	/** First person: the shoulder the arm turns about (right arm; x right, y up, z toward you), and how far below the view the empty off hand starts. */
	static final float FIRST_SHOULDER_X = 0.75F, FIRST_SHOULDER_Y = -0.85F, FIRST_ENTRY_DROP = 0.6F;
	/** The gear is shaped for a 4 pixel arm: on a slim one (3 pixels) it is this much narrower across, to sit as close. */
	static final float SLIM_GEAR = 0.8F;
	/** A cestus's band over the knuckles: from this far down, and this far proud. */
	static final float KNUCKLE_TOP = 9.0F, KNUCKLE_GROW = 0.8F;
	/** A bracer's guard over the sleeve's wrist end: from this far down the arm, this long, and this far proud. */
	static final float BRACER_TOP = 4.0F, BRACER_GROW = 0.9F;
	static final int BRACER_LENGTH = 3;
	// ================================================================================================================

	/** Bits on a render state: the right arm wears a gauntlet, the left does, and (4) they're in the guard (no attack swinging). */
	private static final int RIGHT = 1, LEFT = 2, GUARD = 4;
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:fang_gauntlets");

	/** The Fang Gauntlets' shapes: the spikes are on the back of the hand, so one for each arm. */
	private static ModelPart rightFangs, leftFangs;
	/** The other shapes (the same on either arm): the sleeve alone, with a cestus's knuckle band, with a bracer's guard. */
	private static ModelPart sleeve, cestus, bracer;
	/** Each kind's texture, by {@link #wornKind} less one: the Fang Gauntlets, then boxing's gear by {@link BoxingGear#wornIndex}. */
	private static final Identifier[] TEXTURES = new Identifier[BoxingGear.WORN.length + 1];
	/** On a render state, above the arm bits: the kind ({@link #wornKind}) on the right arm, and on the left. */
	private static final int RIGHT_SHIFT = 8, LEFT_SHIFT = 16, KIND_MASK = 0xFF;

	private FangGauntletsClient() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
	}

	/** Whether {@code stack} is worn on the hands when held: the Fang Gauntlets, or boxing's fist gear. */
	public static boolean isGauntlets(ItemStack stack) {
		return wornKind(stack) != 0;
	}

	/** Which piece {@code stack} is worn as: 0 not worn, 1 the Fang Gauntlets, else boxing's gear (its {@link BoxingGear#wornIndex} plus two). */
	private static int wornKind(ItemStack stack) {
		if (Armory.FANG_GAUNTLETS != null && stack.is(Armory.FANG_GAUNTLETS)) {
			return 1;
		}
		int boxing = BoxingGear.wornIndex(stack);
		return boxing < 0 ? 0 : boxing + 2;
	}

	/** The kinds worn on the main hand's arm and the off hand's: the off hand's own piece, else the main hand's while it's empty. */
	private static int[] kinds(ItemStack main, ItemStack off) {
		int mainKind = wornKind(main), offKind = wornKind(off);
		return new int[] {mainKind, offKind == 0 && off.isEmpty() ? mainKind : offKind};
	}

	/** From the avatar renderer's extractRenderState: which arms wear a gauntlet, which one, and whether they're in the guard. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		// (a boxer's style, punch, evade and tiredness ride along: boxing/BoxingPoses)
		com.jaymospowers.client.boxing.BoxingPoses.extract(entity, state, partialTicks);
		int[] kinds = kinds(entity.getMainHandItem(), entity.getOffhandItem());
		if (kinds[0] == 0 && kinds[1] == 0) {
			state.setData(KEY, null);
			return;
		}
		boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
		int right = mainRight ? kinds[0] : kinds[1], left = mainRight ? kinds[1] : kinds[0];
		int bits = (right != 0 ? RIGHT : 0) | (left != 0 ? LEFT : 0);
		boolean swinging = state.currentSwing != null && state.swingAnimation > 0.0F;
		if (kinds[0] != 0 && !swinging && CombatAnimCompat.attack(entity) == null && !entity.isUsingItem()) {
			bits |= GUARD;
		}
		state.setData(KEY, bits | right << RIGHT_SHIFT | left << LEFT_SHIFT);
	}

	/** Whether the guard is on this state (for PoseTakeover: Entity Model Features pauses so it holds). */
	public static boolean guarding(AvatarRenderState state) {
		Integer bits = state.getData(KEY);
		return bits != null && (bits & GUARD) != 0;
	}

	/** Whether this state wears gauntlets on either arm (PoseTakeover pauses Entity Model Features for it). */
	public static boolean worn(AvatarRenderState state) {
		return state.getData(KEY) != null;
	}

	/** Whether the arm {@code arm} wears a gauntlet on this state (what that hand holds is then not drawn). */
	public static boolean wornOn(AvatarRenderState state, HumanoidArm arm) {
		Integer bits = state.getData(KEY);
		return bits != null && (bits & (arm == HumanoidArm.RIGHT ? RIGHT : LEFT)) != 0;
	}

	/**
	 * Right after a player's model is animated (for the layers, and as each model is drawn): each gauntlet arm comes up
	 * into the guard ahead of you, still but for the walk. Not mid-attack: then the swing's own animation has the arms.
	 */
	public static void applyModel(Model<?> m, Object s) {
		if (m instanceof HumanoidModel<?> boxer && s instanceof AvatarRenderState boxing && com.jaymospowers.client.boxing.BoxingPoses.apply(boxer, boxing)) {
			// fist gear with a style: the style's own stance, block, punch and evade in place of the plain guard
			return;
		}
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
	 * First person, before the game draws your bare arm for {@code hand}: with a gauntlet in the main hand, moves the arm
	 * into the guard and returns true. Not while a swing runs ({@code attack} above 0, or a Better Combat animation): then
	 * the arm is left to the swing, drawn bare where the game puts it. A boxer's arms stay in the guard through a punch,
	 * and the one that throws it goes out from there ({@code BoxingPoses.firstPerson}). A gauntlet only in the off hand
	 * brings no guard: that arm is drawn where the game puts an empty hand.
	 */
	public static boolean firstPersonGuard(PoseStack poseStack, AvatarRenderState avatar, InteractionHand hand, float attack, float inverseArmHeight) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || avatar.id != player.getId() || !isGauntlets(player.getMainHandItem())) {
			return false;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		boolean right = (main ? avatar.mainArm : avatar.mainArm.getOpposite()) == HumanoidArm.RIGHT;
		float side = right ? 1.0F : -1.0F;
		// a boxer's punch is their own: the arm that throws it goes out from the guard (a jab from the lead hand, a
		// cross from the rear), and the game's swing is left out
		float[] punch = com.jaymospowers.client.boxing.BoxingPoses.firstPerson(avatar, right);
		if (punch == null && (attack > 0.0F || CombatAnimCompat.attack(player) != null)) {
			return false;
		}
		if (punch != null) {
			poseStack.translate(-side * punch[1], punch[2], -punch[0]);
		}
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

	/** Whether your own hand {@code hand}, showing {@code shown}, wears a gauntlet in first person. */
	public static boolean wornInHand(InteractionHand hand, ItemStack shown) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		int[] kinds = kinds(player.getMainHandItem(), player.getOffhandItem());
		return hand == InteractionHand.MAIN_HAND ? kinds[0] != 0 && isGauntlets(shown) : kinds[1] != 0 && (shown.isEmpty() || isGauntlets(shown));
	}

	/** The shapes and textures, made once. */
	private static void build() {
		if (sleeve != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float sleeveHeight = 10.0F - SLEEVE_TOP;
		CubeDeformation grow = new CubeDeformation(SLEEVE_GROW);
		// about the arm's middle; the right arm's back of hand faces -x (out from the body)
		CubeListBuilder right = CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, SLEEVE_TOP, -2.0F, 4.0F, sleeveHeight, 4.0F, grow);
		CubeListBuilder left = CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, SLEEVE_TOP, -2.0F, 4.0F, sleeveHeight, 4.0F, grow);
		float[] tips = {CLAW_TIP - CLAW_SIDE_SHORTER, CLAW_TIP, CLAW_TIP - CLAW_SIDE_SHORTER};
		for (int claw = 0; claw < 3; claw++) {
			float z = (claw - 1) * CLAW_GAP - CLAW_WIDTH / 2.0F;
			right.texOffs(32 + claw * 8, 0).addBox(-CLAW_OUT - CLAW_THICK, CLAW_TOP, z, CLAW_THICK, tips[claw] - CLAW_TOP, CLAW_WIDTH);
			left.texOffs(32 + claw * 8, 0).mirror().addBox(CLAW_OUT, CLAW_TOP, z, CLAW_THICK, tips[claw] - CLAW_TOP, CLAW_WIDTH).mirror(false);
		}
		root.addOrReplaceChild("right_fangs", right, PartPose.ZERO);
		root.addOrReplaceChild("left_fangs", left, PartPose.ZERO);
		root.addOrReplaceChild("sleeve", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-2.0F, SLEEVE_TOP, -2.0F, 4.0F, sleeveHeight, 4.0F, grow), PartPose.ZERO);
		root.addOrReplaceChild("cestus", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-2.0F, SLEEVE_TOP, -2.0F, 4.0F, sleeveHeight, 4.0F, grow)
			.texOffs(0, 12).addBox(-2.0F, KNUCKLE_TOP, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(KNUCKLE_GROW)), PartPose.ZERO);
		root.addOrReplaceChild("bracer", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-2.0F, SLEEVE_TOP, -2.0F, 4.0F, sleeveHeight, 4.0F, grow)
			.texOffs(0, 18).addBox(-2.0F, BRACER_TOP, -2.0F, 4.0F, BRACER_LENGTH, 4.0F, new CubeDeformation(BRACER_GROW)), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		rightFangs = baked.getChild("right_fangs");
		leftFangs = baked.getChild("left_fangs");
		sleeve = baked.getChild("sleeve");
		cestus = baked.getChild("cestus");
		bracer = baked.getChild("bracer");
		TEXTURES[0] = JaymosPowers.id("textures/entity/fang_gauntlets.png");
		for (int i = 0; i < BoxingGear.WORN.length; i++) {
			TEXTURES[i + 1] = JaymosPowers.id("textures/entity/boxing/" + BoxingGear.WORN[i] + ".png");
		}
	}

	/** One piece of {@code kind} ({@link #wornKind}), on the arm the pose stack is at (its pivot). */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean right, boolean slim, int kind, float fold,
		boolean across) {
		if (kind <= 0 || kind > TEXTURES.length) {
			return;
		}
		build();
		poseStack.pushPose();
		float middle = slim ? SLIM_ARM_MIDDLE : WIDE_ARM_MIDDLE;
		poseStack.translate((right ? -middle : middle) / 16.0F, 0.0F, 0.0F);
		if (fold != 0.0F) {
			// a boxer's elbow is bent (boxing/BoxingPoses): the gear turns with the forearm, about the elbow
			float elbow = com.jaymospowers.client.boxing.BoxingPoses.ELBOW / 16.0F;
			poseStack.translate(0.0F, elbow, 0.0F);
			poseStack.rotateDegrees(across ? Axis.ZP : Axis.XP, fold * Mth.RAD_TO_DEG);
			poseStack.translate(0.0F, -elbow, 0.0F);
		}
		if (slim) {
			poseStack.scale(SLIM_GEAR, 1.0F, 1.0F);
		}
		ModelPart part;
		if (kind == 1) {
			part = right ? rightFangs : leftFangs;
		} else {
			String shape = BoxingGear.WORN[kind - 2];
			part = shape.endsWith("bracers") ? bracer : shape.endsWith("cestus") ? cestus : sleeve;
		}
		collector.submitModelPart(part, poseStack, RenderTypes.entityCutout(TEXTURES[kind - 1]), light, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}

	/** First person: after the game draws your own arm, your gauntlet on it. */
	public static void firstPersonHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int[] kinds = kinds(player.getMainHandItem(), player.getOffhandItem());
		boolean right = arm == source.rightArm;
		boolean mainArm = right == (player.getMainArm() == HumanoidArm.RIGHT);
		int kind = kinds[mainArm ? 0 : 1];
		if (kind == 0) {
			return;
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		submit(poseStack, collector, light, right, player.getSkin().model() == PlayerModelType.SLIM, kind, 0.0F, false);
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
				com.jaymospowers.client.boxing.BoxingPoses.Bends bends = state.getData(com.jaymospowers.client.boxing.BoxingPoses.BENDS);
				float fold = bends == null ? 0.0F : right ? bends.right() : bends.left();
				boolean across = bends != null && (right ? bends.rightAcross() : bends.leftAcross());
				int kind = bits >> (right ? RIGHT_SHIFT : LEFT_SHIFT) & KIND_MASK;
				FangGauntletsClient.submit(poseStack, collector, lightCoords, right, slim, kind, fold, across);
				poseStack.popPose();
			}
		}
	}
}
