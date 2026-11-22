package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebCounter;
import com.jaymospowers.power.web.WebShield;
import com.jaymospowers.power.web.WebStrength;
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
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.HitResult;

/**
 * The web shield (see {@link WebShield}) on the client: a round web disc strapped to the forearm of your main arm
 * (drawn on everyone, and on your own first-person hand), and the right click that asks for it (vanilla sends nothing
 * for an empty hand, so this tells the server, as the web shooters' take-off does). The disc is two crossed 1-pixel
 * boxes painted round ({@code textures/entity/web_shield.png}, from tools/gen_web_shield.py).
 */
public final class WebShieldClient {
	/** Down the arm (pixels from the shoulder pivot) to the disc's middle, and how far out from the arm's middle its inner face sits. */
	static final float DISC_Y = 6.0F, DISC_OUT = 2.25F;

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_shield.png");
	static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_shield");

	private static ModelPart right, left;
	private static boolean useWasDown;

	private WebShieldClient() {
	}

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(WebShieldClient::tick);
	}

	/** A fresh right click, empty main hand, webbed fists on, no shield yet, not a counter, not at a creature: ask for the shield. */
	private static void tick(Minecraft minecraft) {
		boolean down = minecraft.options.keyUse.isDown();
		boolean pressed = down && !useWasDown;
		useWasDown = down;
		LocalPlayer player = minecraft.player;
		if (!pressed || player == null || minecraft.getConnection() == null || minecraft.gui.screen() != null || player.isShiftKeyDown()) {
			return;
		}
		if (player.getMainHandItem().isEmpty() && player.getAttachedOrElse(WebStrength.FISTS, false) && !WebShield.on(player)
			&& !WebCounter.ready(player) && (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.ENTITY)) {
			ClientPlayNetworking.send(new WebShield.Spin());
		}
	}

	public static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player && WebShield.on(player));
	}

	private static void build() {
		if (right != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float x = DISC_OUT;
		// right arm: the disc on its outside (-x); left: mirrored
		root.addOrReplaceChild("right", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-x - 1.0F, DISC_Y - 3.5F, -2.5F, 1.0F, 7.0F, 5.0F)
			.texOffs(0, 14).addBox(-x - 1.0F, DISC_Y - 2.5F, -3.5F, 1.0F, 5.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("left", CubeListBuilder.create()
			.texOffs(0, 0).addBox(x, DISC_Y - 3.5F, -2.5F, 1.0F, 7.0F, 5.0F)
			.texOffs(0, 14).addBox(x, DISC_Y - 2.5F, -3.5F, 1.0F, 5.0F, 7.0F), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		right = baked.getChild("right");
		left = baked.getChild("left");
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean rightArm) {
		build();
		collector.submitModelPart(rightArm ? right : left, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
	}

	/** First person: after the game draws your own arm, the disc on it. */
	public static void firstPersonHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !WebShield.on(player)) {
			return;
		}
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		submit(poseStack, collector, light, arm == source.rightArm);
		poseStack.popPose();
	}

	/** Draws the disc on the main arm as it is posed now. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (!Boolean.TRUE.equals(state.getData(KEY)) || state.isInvisible) {
				return;
			}
			PlayerModel body = this.getParentModel();
			boolean rightArm = state.mainArm == HumanoidArm.RIGHT;
			ModelPart arm = rightArm ? body.rightArm : body.leftArm;
			if (!arm.visible) {
				return;
			}
			poseStack.pushPose();
			body.root().translateAndRotate(poseStack);
			arm.translateAndRotate(poseStack);
			WebShieldClient.submit(poseStack, collector, lightCoords, rightArm);
			poseStack.popPose();
		}
	}
}
