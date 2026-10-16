package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebShooters;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * (0.12.3) Web shooters on the client (see {@link WebShooters}): drawn on both wrists of everyone who wears them
 * ({@link Layer}) and on your own first-person hands, their fluid on the Web HUD, and taking them off (Sneak + use with
 * an empty main hand, aiming at nothing: vanilla sends nothing for an empty hand, so this tells the server).
 * <p>
 * The model ({@code textures/entity/web_shooter.png}, from tools/gen_web_shooters.py), in pixels about the middle of the
 * arm: a silver cuff round the wrist ({@code CUFF_*}), the nozzle on the palm side, and the cartridge on the back of the
 * wrist while one is loaded. The symbiote's suit hides them (its webs are its own).
 */
public final class WebShootersClient {
	// ===== the knobs ================================================================================================
	/** The cuff: from this far down the arm (pixels below the shoulder pivot), this tall, this far out round the arm. */
	static final float CUFF_TOP = 6.5F, CUFF_HEIGHT = 3.0F, CUFF_HALF = 2.5F;
	/** The middle of a wide arm, and of a slim one, off the shoulder pivot (pixels, toward the body's outside). */
	static final float WIDE_ARM_MIDDLE = 1.0F, SLIM_ARM_MIDDLE = 0.5F;
	/** The HUD's fluid gauge: tank colours, full and running low (under {@code LOW_SHOTS}), and for a dry wrist. */
	static final int TANK_FULL = 0xFFEEF2F8, TANK_LOW = 0xFFE0A040, TANK_DRY = 0xFF602020;
	static final int LOW_SHOTS = 12;
	// ================================================================================================================

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_shooter.png");
	/** On a player's render state: {main hand's wrist fluid, off hand's}, or null if none are shown. */
	static final RenderStateDataKey<int[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_shooters");

	private static ModelPart rightCuff, leftCuff, rightCartridge, leftCartridge;
	private static boolean useWasDown;

	private WebShootersClient() {
	}

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(WebShootersClient::tick);
	}

	/** Sneak + a fresh press of use, main hand empty, aiming at nothing, wearing shooters: take them off. */
	private static void tick(Minecraft minecraft) {
		boolean down = minecraft.options.keyUse.isDown();
		boolean pressed = down && !useWasDown;
		useWasDown = down;
		LocalPlayer player = minecraft.player;
		if (!pressed || player == null || minecraft.getConnection() == null || minecraft.gui.screen() != null) {
			return;
		}
		if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && WebShooters.worn(player) != null
			&& (minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS)) {
			ClientPlayNetworking.send(new WebShooters.TakeOff());
		}
	}

	/** From the avatar renderer's extractRenderState: whether (and how loaded) this player's shooters are drawn. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		WebShooters.Load load = entity instanceof Player player && !WebSymbiote.suitShown(player) ? WebShooters.worn(player) : null;
		state.setData(KEY, load == null ? null : new int[] {load.main(), load.off()});
	}

	private static void build() {
		if (rightCuff != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// about the arm's middle; the right arm's palm faces +x (in toward the body), so its nozzle is there
		root.addOrReplaceChild("right_cuff", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-CUFF_HALF, CUFF_TOP, -CUFF_HALF, CUFF_HALF * 2.0F, CUFF_HEIGHT, CUFF_HALF * 2.0F)
			.texOffs(0, 10).addBox(CUFF_HALF - 0.5F, CUFF_TOP + 0.75F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("left_cuff", CubeListBuilder.create()
			.texOffs(0, 0).mirror().addBox(-CUFF_HALF, CUFF_TOP, -CUFF_HALF, CUFF_HALF * 2.0F, CUFF_HEIGHT, CUFF_HALF * 2.0F)
			.texOffs(0, 10).addBox(-CUFF_HALF - 1.5F, CUFF_TOP + 0.75F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("right_cartridge", CubeListBuilder.create()
			.texOffs(0, 16).addBox(-CUFF_HALF - 1.5F, CUFF_TOP, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("left_cartridge", CubeListBuilder.create()
			.texOffs(0, 16).mirror().addBox(CUFF_HALF - 0.5F, CUFF_TOP, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		rightCuff = baked.getChild("right_cuff");
		leftCuff = baked.getChild("left_cuff");
		rightCartridge = baked.getChild("right_cartridge");
		leftCartridge = baked.getChild("left_cartridge");
	}

	/** One shooter, on the arm the pose stack is at (its pivot): {@code fluid} &lt; 0 has no cartridge to show. */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean right, boolean slim, int fluid) {
		build();
		poseStack.pushPose();
		float middle = slim ? SLIM_ARM_MIDDLE : WIDE_ARM_MIDDLE;
		poseStack.translate((right ? -middle : middle) / 16.0F, 0.0F, 0.0F);
		collector.submitModelPart(right ? rightCuff : leftCuff, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		if (fluid >= 0) {
			collector.submitModelPart(right ? rightCartridge : leftCartridge, poseStack, RenderTypes.entityCutout(TEXTURE), light,
				OverlayTexture.NO_OVERLAY, null);
		}
		poseStack.popPose();
	}

	/** First person: after the game draws your own arm, your shooter on its wrist. */
	public static void firstPersonHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		WebShooters.Load load = player == null || WebSymbiote.suitShown(player) ? null : WebShooters.worn(player);
		if (load == null) {
			return;
		}
		boolean right = arm == source.rightArm;
		int hand = right == (player.getMainArm() == HumanoidArm.RIGHT) ? WebNet.MAIN : WebNet.OFF;
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		submit(poseStack, collector, light, right, player.getSkin().model() == PlayerModelType.SLIM, load.fluid(hand));
		poseStack.popPose();
	}

	/**
	 * The fluid gauge, a chip like the Web HUD's at ({@code x}, {@code y}): a tank for each wrist (left wrist on the left),
	 * filling with what its cartridge has left, amber when low, dark red when dry or empty.
	 */
	static void renderHud(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y, int size, int accent) {
		WebShooters.Load load = WebSymbiote.suitShown(player) ? null : WebShooters.worn(player);
		if (load == null) {
			return;
		}
		graphics.fill(x, y, x + size, y + size, 0x90000000);
		graphics.outline(x, y, size, size, 0xFF000000 | accent);
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		int[] wrists = {mainRight ? WebNet.OFF : WebNet.MAIN, mainRight ? WebNet.MAIN : WebNet.OFF};
		int tankWidth = (size - 7) / 2;
		int tankHeight = size - 6;
		for (int i = 0; i < 2; i++) {
			int fluid = load.fluid(wrists[i]);
			int tx = x + 3 + i * (tankWidth + 1);
			int ty = y + 3;
			graphics.fill(tx, ty, tx + tankWidth, ty + tankHeight, 0xFF202228);
			if (fluid <= 0) {
				graphics.fill(tx, ty + tankHeight - 2, tx + tankWidth, ty + tankHeight, TANK_DRY);
				continue;
			}
			int filled = Math.max(1, Math.round(tankHeight * fluid / (float)WebShooters.CARTRIDGE_SHOTS));
			graphics.fill(tx, ty + tankHeight - filled, tx + tankWidth, ty + tankHeight, fluid < LOW_SHOTS ? TANK_LOW : TANK_FULL);
		}
		// the shots left, on the hand you swing with most
		int main = Math.max(0, load.fluid(WebNet.MAIN));
		String shots = Integer.toString(main);
		graphics.text(font, shots, x + size - 1 - font.width(shots), y + size - 8, main > 0 ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Draws the shooters on both wrists as the arms are posed now. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			int[] fluid = state.getData(KEY);
			if (fluid == null || state.isInvisible) {
				return;
			}
			PlayerModel body = this.getParentModel();
			boolean slim = state.skin.model() == PlayerModelType.SLIM;
			boolean mainRight = state.mainArm == HumanoidArm.RIGHT;
			for (int side = 0; side < 2; side++) {
				boolean right = side == 0;
				ModelPart arm = right ? body.rightArm : body.leftArm;
				if (!arm.visible) {
					continue;
				}
				poseStack.pushPose();
				body.root().translateAndRotate(poseStack);
				arm.translateAndRotate(poseStack);
				WebShootersClient.submit(poseStack, collector, lightCoords, right, slim, fluid[right == mainRight ? 0 : 1]);
				poseStack.popPose();
			}
		}
	}
}
