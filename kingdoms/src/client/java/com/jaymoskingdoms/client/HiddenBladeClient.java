package com.jaymoskingdoms.client;

import com.jaymoskingdoms.HiddenBlade;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.HitResult;

/**
 * (0.7.113) The hidden blade worn on the wrist, on the client (see {@link HiddenBlade}): drawn on the wrists of everyone
 * who wears one ({@link Layer}) and on your own first-person hands, and the empty-handed use that springs it (vanilla
 * sends nothing for an empty hand, so {@code HiddenBladeUseMixin} asks {@link #use} once nothing else took the click).
 * <p>
 * The model ({@code textures/entity/hidden_blade.png}, from tools/gen_hidden_blade.py), in pixels about the middle of the
 * arm: a leather bracer round the wrist ({@code BRACER_*}), the spring's steel housing on the palm side, and, out, the
 * blade along the palm side past the fingers ({@code BLADE_*}).
 * <p>
 * (0.7.122) It moves: springing raises the arm in front of you into a guard, then the blade shoots out of the housing
 * ({@link #SPRING_TICKS}); sheathing runs it back. While it's out over an empty hand that arm is held up ahead
 * ({@code GUARD_*}, from {@code HiddenBladePoseMixin}, so costumes follow it), and a blow with it is a straight thrust
 * ({@code STAB_*}) rather than a punch. The bracer stands {@link #BRACER_GROW} proud so it shows over a costume's sleeve.
 */
public final class HiddenBladeClient {
	// ===== the knobs ================================================================================================
	/** The bracer: from this far down the arm (pixels below the shoulder pivot), this tall, this far out round the arm. */
	static final float BRACER_TOP = 5.5F, BRACER_HEIGHT = 3.0F, BRACER_HALF = 2.5F;
	/** (0.7.122) How far the bracer and all on it stand out past that (pixels): clear of a costume's sleeve and bracer (2.5 out). */
	static final float BRACER_GROW = 0.4F;
	/** The housing on the palm side: from this far down, this long. */
	static final float HOUSING_TOP = 5.0F, HOUSING_LENGTH = 4.0F;
	/** The blade out: from this far down the arm to this far (the hand ends at 10), this thin and this wide. */
	static final float BLADE_TOP = 6.0F, BLADE_TIP = 15.0F, BLADE_THICK = 0.5F, BLADE_WIDTH = 1.0F;
	/** The middle of a wide arm, and of a slim one, off the shoulder pivot (pixels, toward the body's outside). */
	static final float WIDE_ARM_MIDDLE = 1.0F, SLIM_ARM_MIDDLE = 0.5F;
	/** (0.7.122) Springing it out (or in) takes this long (ticks): the first {@link #RAISE_SHARE} of it raises the arm, the rest runs the blade out. */
	static final float SPRING_TICKS = 5.0F, RAISE_SHARE = 0.5F;
	/**
	 * (0.7.122) The guard, blade out: the arm raised ahead this far (radians, 0 hanging, -pi/2 straight out), plus this
	 * share of where you look up or down; turned in across the body this far; this much of the walk's arm swing kept.
	 */
	static final float GUARD_PITCH = -1.15F, GUARD_LOOK = 0.5F, GUARD_IN = 0.4F, GUARD_SWAY = 0.25F;
	/** (0.7.122) A blow with the blade out: the arm thrust straight ahead (plus this share of the look) and this far forward (pixels). */
	static final float STAB_LOOK = 0.8F, STAB_REACH = 3.0F;
	// ================================================================================================================

	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/hidden_blade.png");
	/** On a player's render state: {@link HiddenBlade#WORN}'s bits, or null if none are shown. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:hidden_blades");
	/** (0.7.122) On a player's render state: how far each wrist's blade is sprung, {main, off}, 0 sheathed to 1 out. */
	static final RenderStateDataKey<float[]> SPRUNG = RenderStateDataKey.create(() -> "jaymoskingdoms:hidden_blades_sprung");
	/** (0.7.122) Player → each wrist's spring {main, off} now, then {main, off} a tick ago (players seen wearing a blade). */
	private static final Map<Player, float[]> SPRINGS = new WeakHashMap<>();

	private static ModelPart rightBracer, leftBracer, rightBlade, leftBlade;
	/** Use was let go since the last spring (holding it repeats the click every few ticks; one spring a press). */
	private static boolean released = true;

	private HiddenBladeClient() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.options.keyUse.isDown()) {
				released = true;
			}
			if (minecraft.level == null) {
				SPRINGS.clear();
				return;
			}
			for (Player player : minecraft.level.players()) {
				tickSpring(player);
			}
		});
	}

	/**
	 * Use with {@code hand} empty, nothing else having taken the click: if a blade is on that wrist, spring it (or, Sneak
	 * + use at nothing with it sheathed, take it off). True if the click was the blade's.
	 */
	public static boolean use(Minecraft minecraft, InteractionHand hand) {
		LocalPlayer player = minecraft.player;
		int wrist = hand == InteractionHand.OFF_HAND ? HiddenBlade.OFF : HiddenBlade.MAIN;
		if (player == null || !HiddenBlade.ready(player, wrist)) {
			return false;
		}
		if (released) {
			released = false;
			boolean takeOff = player.isShiftKeyDown() && !HiddenBlade.out(player, wrist)
				&& (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS);
			ClientPlayNetworking.send(new HiddenBlade.Spring(wrist, takeOff));
		}
		return true;
	}

	/** (0.7.122) One tick of a player's springs: each runs toward out or in, as their blade is. */
	private static void tickSpring(Player player) {
		int bits = HiddenBlade.bits(player);
		float[] spring = SPRINGS.get(player);
		if (spring == null) {
			if (bits == 0) {
				return;
			}
			// first seen: as they are, with no springing
			spring = new float[4];
			for (int wrist = HiddenBlade.MAIN; wrist <= HiddenBlade.OFF; wrist++) {
				spring[wrist] = (bits & 4 << wrist) != 0 ? 1.0F : 0.0F;
			}
			SPRINGS.put(player, spring);
		}
		for (int wrist = HiddenBlade.MAIN; wrist <= HiddenBlade.OFF; wrist++) {
			spring[2 + wrist] = spring[wrist];
			float target = (bits & 4 << wrist) != 0 ? 1.0F : 0.0F;
			spring[wrist] = spring[wrist] < target ? Math.min(target, spring[wrist] + 1.0F / SPRING_TICKS)
				: Math.max(target, spring[wrist] - 1.0F / SPRING_TICKS);
		}
	}

	/** (0.7.122) How far a player's blade on {@code wrist} is sprung right now, 0 sheathed to 1 out. */
	private static float sprung(Player player, int wrist, float partialTicks) {
		float[] spring = SPRINGS.get(player);
		if (spring == null) {
			return HiddenBlade.out(player, wrist) ? 1.0F : 0.0F;
		}
		return Mth.lerp(partialTicks, spring[2 + wrist], spring[wrist]);
	}

	/** (0.7.122) The arm's share of a spring: up over the first part, eased. */
	private static float raise(float sprung) {
		float t = Math.min(1.0F, sprung / RAISE_SHARE);
		return t * t * (3.0F - 2.0F * t);
	}

	/** (0.7.122) The blade's share of a spring: out over the rest, fast at first like a spring. */
	private static float extend(float sprung) {
		float t = Mth.clamp((sprung - RAISE_SHARE) / (1.0F - RAISE_SHARE), 0.0F, 1.0F);
		return 1.0F - (1.0F - t) * (1.0F - t);
	}

	/** From the avatar renderer's extractRenderState: which blades this player has on, and how far each is sprung. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		int bits = entity instanceof Player player ? HiddenBlade.bits(player) : 0;
		state.setData(KEY, bits == 0 ? null : bits);
		if (bits == 0) {
			state.setData(SPRUNG, null);
			return;
		}
		Player player = (Player) entity;
		float main = sprung(player, HiddenBlade.MAIN, partialTicks), off = sprung(player, HiddenBlade.OFF, partialTicks);
		state.setData(SPRUNG, main == 0.0F && off == 0.0F ? null : new float[] {main, off});
	}

	/**
	 * (0.7.122) After the player model is posed ({@code HiddenBladePoseMixin}): an arm whose hand is empty with its blade
	 * springing or out comes up into the guard ahead of you, and a blow with it is a thrust.
	 */
	public static void pose(PlayerModel model, AvatarRenderState state) {
		float[] sprung = state.getData(SPRUNG);
		if (sprung == null || state.isFallFlying || state.swimAmount > 0.0F
			|| state.rightArmPose.affectsOffhandPose() || state.leftArmPose.affectsOffhandPose()) {
			return;
		}
		HumanoidArm swingArm = state.currentSwing != null && state.swingAnimation > 0.0F ? state.currentSwing.hand().asArm(state.mainArm) : null;
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			float raise = raise(sprung[wrist(right, state.mainArm)]);
			if (raise <= 0.0F || (right ? state.rightArmPose : state.leftArmPose) != HumanoidModel.ArmPose.EMPTY) {
				continue;
			}
			ModelPart arm = right ? model.rightArm : model.leftArm;
			float look = Mth.clamp(model.head.xRot, -0.8F, 0.6F);
			float guardX = GUARD_PITCH + look * GUARD_LOOK + arm.xRot * GUARD_SWAY + (state.isCrouching ? 0.4F : 0.0F);
			float guardY = (right ? -GUARD_IN : GUARD_IN) + Mth.clamp(model.head.yRot, -0.5F, 0.5F) * 0.5F;
			if (swingArm == (right ? HumanoidArm.RIGHT : HumanoidArm.LEFT)) {
				// the blow: straight out along the look, and forward, then back to the guard
				float stab = Mth.sin(state.swingAnimation * (float) Math.PI);
				guardX = Mth.lerp(stab, guardX, (float) (-Math.PI / 2.0) + look * STAB_LOOK);
				guardY = Mth.lerp(stab, guardY, guardY * 0.3F);
				arm.z -= stab * STAB_REACH * raise;
			}
			arm.xRot = Mth.lerp(raise, arm.xRot, guardX);
			arm.yRot = Mth.lerp(raise, arm.yRot, guardY);
			arm.zRot = Mth.lerp(raise, arm.zRot, 0.0F);
		}
	}

	private static void build() {
		if (rightBracer != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float out = BRACER_HALF + BRACER_GROW;
		// about the arm's middle; the right arm's palm faces +x (in toward the body), so the housing and blade are there
		root.addOrReplaceChild("right_bracer", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-BRACER_HALF, BRACER_TOP, -BRACER_HALF, BRACER_HALF * 2.0F, BRACER_HEIGHT, BRACER_HALF * 2.0F, new CubeDeformation(BRACER_GROW))
			.texOffs(0, 10).addBox(out, HOUSING_TOP, -1.0F, 1.0F, HOUSING_LENGTH, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("left_bracer", CubeListBuilder.create()
			.texOffs(0, 0).mirror().addBox(-BRACER_HALF, BRACER_TOP, -BRACER_HALF, BRACER_HALF * 2.0F, BRACER_HEIGHT, BRACER_HALF * 2.0F, new CubeDeformation(BRACER_GROW))
			.texOffs(0, 10).addBox(-out - 1.0F, HOUSING_TOP, -1.0F, 1.0F, HOUSING_LENGTH, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("right_blade", CubeListBuilder.create()
			.texOffs(0, 18).addBox(out + 0.25F, BLADE_TOP, -BLADE_WIDTH / 2.0F, BLADE_THICK, BLADE_TIP - BLADE_TOP, BLADE_WIDTH), PartPose.ZERO);
		root.addOrReplaceChild("left_blade", CubeListBuilder.create()
			.texOffs(0, 18).mirror().addBox(-out - 0.25F - BLADE_THICK, BLADE_TOP, -BLADE_WIDTH / 2.0F, BLADE_THICK, BLADE_TIP - BLADE_TOP, BLADE_WIDTH),
			PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		rightBracer = baked.getChild("right_bracer");
		leftBracer = baked.getChild("left_bracer");
		rightBlade = baked.getChild("right_blade");
		leftBlade = baked.getChild("left_blade");
	}

	/** One blade, on the arm the pose stack is at (its pivot), sprung {@code sprung} (0 sheathed to 1 out). */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean right, boolean slim, float sprung) {
		build();
		poseStack.pushPose();
		float middle = slim ? SLIM_ARM_MIDDLE : WIDE_ARM_MIDDLE;
		poseStack.translate((right ? -middle : middle) / 16.0F, 0.0F, 0.0F);
		collector.submitModelPart(right ? rightBracer : leftBracer, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		float extend = extend(sprung);
		if (extend > 0.0F) {
			// (0.7.122) run out of the housing: grown from its root, the rest still inside the housing
			float shown = (HOUSING_TOP + HOUSING_LENGTH - BLADE_TOP) / (BLADE_TIP - BLADE_TOP);
			float length = shown + (1.0F - shown) * extend;
			poseStack.translate(0.0F, BLADE_TOP / 16.0F, 0.0F);
			poseStack.scale(1.0F, length, 1.0F);
			poseStack.translate(0.0F, -BLADE_TOP / 16.0F, 0.0F);
			collector.submitModelPart(right ? rightBlade : leftBlade, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		}
		poseStack.popPose();
	}

	/** Which of {@link HiddenBlade}'s wrists the {@code right} arm is, for a player whose main arm is {@code mainArm}. */
	private static int wrist(boolean right, HumanoidArm mainArm) {
		return right == (mainArm == HumanoidArm.RIGHT) ? HiddenBlade.MAIN : HiddenBlade.OFF;
	}

	/** First person: after the game draws your own arm, your blade on its wrist. */
	public static void firstPersonHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		boolean right = arm == source.rightArm;
		int wrist = wrist(right, player.getMainArm());
		if (!HiddenBlade.worn(player, wrist)) {
			return;
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		submit(poseStack, collector, light, right, player.getSkin().model() == PlayerModelType.SLIM,
			sprung(player, wrist, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		poseStack.popPose();
	}

	/** First person: the off hand is empty, so the game draws no arm there; with its blade out, draw the arm (and blade) anyway. */
	public static boolean showOffArm() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getOffhandItem().isEmpty()
			&& sprung(player, HiddenBlade.OFF, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) > RAISE_SHARE;
	}

	/** Draws the blades on the wrists as the arms are posed now. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Integer bits = state.getData(KEY);
			if (bits == null || state.isInvisible) {
				return;
			}
			float[] sprung = state.getData(SPRUNG);
			PlayerModel body = this.getParentModel();
			boolean slim = state.skin.model() == PlayerModelType.SLIM;
			for (int side = 0; side < 2; side++) {
				boolean right = side == 0;
				int wrist = wrist(right, state.mainArm);
				ModelPart arm = right ? body.rightArm : body.leftArm;
				if ((bits & 1 << wrist) == 0 || !arm.visible) {
					continue;
				}
				poseStack.pushPose();
				body.root().translateAndRotate(poseStack);
				arm.translateAndRotate(poseStack);
				HiddenBladeClient.submit(poseStack, collector, lightCoords, right, slim, sprung == null ? 0.0F : sprung[wrist]);
				poseStack.popPose();
			}
		}
	}
}
