package com.jaymoskingdoms.client;

import com.jaymoskingdoms.HiddenBlade;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
 */
public final class HiddenBladeClient {
	// ===== the knobs ================================================================================================
	/** The bracer: from this far down the arm (pixels below the shoulder pivot), this tall, this far out round the arm. */
	static final float BRACER_TOP = 5.5F, BRACER_HEIGHT = 3.0F, BRACER_HALF = 2.5F;
	/** The housing on the palm side: from this far down, this long. */
	static final float HOUSING_TOP = 5.0F, HOUSING_LENGTH = 4.0F;
	/** The blade out: from this far down the arm to this far (the hand ends at 10), this thin and this wide. */
	static final float BLADE_TOP = 6.0F, BLADE_TIP = 15.0F, BLADE_THICK = 0.5F, BLADE_WIDTH = 1.0F;
	/** The middle of a wide arm, and of a slim one, off the shoulder pivot (pixels, toward the body's outside). */
	static final float WIDE_ARM_MIDDLE = 1.0F, SLIM_ARM_MIDDLE = 0.5F;
	// ================================================================================================================

	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/hidden_blade.png");
	/** On a player's render state: {@link HiddenBlade#WORN}'s bits, or null if none are shown. */
	static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:hidden_blades");

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

	/** From the avatar renderer's extractRenderState: which blades this player has on, and which are out. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		int bits = entity instanceof Player player ? HiddenBlade.bits(player) : 0;
		state.setData(KEY, bits == 0 ? null : bits);
	}

	private static void build() {
		if (rightBracer != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// about the arm's middle; the right arm's palm faces +x (in toward the body), so the housing and blade are there
		root.addOrReplaceChild("right_bracer", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-BRACER_HALF, BRACER_TOP, -BRACER_HALF, BRACER_HALF * 2.0F, BRACER_HEIGHT, BRACER_HALF * 2.0F)
			.texOffs(0, 10).addBox(BRACER_HALF, HOUSING_TOP, -1.0F, 1.0F, HOUSING_LENGTH, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("left_bracer", CubeListBuilder.create()
			.texOffs(0, 0).mirror().addBox(-BRACER_HALF, BRACER_TOP, -BRACER_HALF, BRACER_HALF * 2.0F, BRACER_HEIGHT, BRACER_HALF * 2.0F)
			.texOffs(0, 10).addBox(-BRACER_HALF - 1.0F, HOUSING_TOP, -1.0F, 1.0F, HOUSING_LENGTH, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("right_blade", CubeListBuilder.create()
			.texOffs(0, 18).addBox(BRACER_HALF + 0.25F, BLADE_TOP, -BLADE_WIDTH / 2.0F, BLADE_THICK, BLADE_TIP - BLADE_TOP, BLADE_WIDTH), PartPose.ZERO);
		root.addOrReplaceChild("left_blade", CubeListBuilder.create()
			.texOffs(0, 18).mirror().addBox(-BRACER_HALF - 0.25F - BLADE_THICK, BLADE_TOP, -BLADE_WIDTH / 2.0F, BLADE_THICK, BLADE_TIP - BLADE_TOP, BLADE_WIDTH),
			PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		rightBracer = baked.getChild("right_bracer");
		leftBracer = baked.getChild("left_bracer");
		rightBlade = baked.getChild("right_blade");
		leftBlade = baked.getChild("left_blade");
	}

	/** One blade, on the arm the pose stack is at (its pivot), sheathed or {@code out}. */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean right, boolean slim, boolean out) {
		build();
		poseStack.pushPose();
		float middle = slim ? SLIM_ARM_MIDDLE : WIDE_ARM_MIDDLE;
		poseStack.translate((right ? -middle : middle) / 16.0F, 0.0F, 0.0F);
		collector.submitModelPart(right ? rightBracer : leftBracer, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		if (out) {
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
		submit(poseStack, collector, light, right, player.getSkin().model() == PlayerModelType.SLIM, HiddenBlade.out(player, wrist));
		poseStack.popPose();
	}

	/** First person: the off hand is empty, so the game draws no arm there; with its blade out, draw the arm (and blade) anyway. */
	public static boolean showOffArm() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && HiddenBlade.out(player, HiddenBlade.OFF) && player.getOffhandItem().isEmpty();
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
				HiddenBladeClient.submit(poseStack, collector, lightCoords, right, slim, (bits & 4 << wrist) != 0);
				poseStack.popPose();
			}
		}
	}
}
