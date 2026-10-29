package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteBody;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * (0.17.150) Two things the suit carries on the body: the Mantle, a cape of symbiote that unfurls from the shoulders and
 * hangs (flaring out when you run or fall, as a cape does) and furls back up into the suit, and the Arm's stored things,
 * held to the body by the suit: one at each hip and one at the small of the back. Everyone sees both.
 */
final class WebBodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_mantle.png");
	/** The cape's size in pixels and where it hangs from (the shoulders, a hair behind the back). */
	private static final float CAPE_W = 10.0F, CAPE_H = 16.0F, CAPE_BACK = 2.2F;
	/** It hangs this far out from the back at rest (radians), and the most it flares. */
	private static final float CAPE_REST = 0.09F, CAPE_FLARE = 0.55F;
	/** Where each stored thing hangs (pixels, in the body's space: right hip, left hip, small of the back) and how big it is. */
	private static final float[][] SLOTS = {{-5.2F, 9.5F, -0.3F}, {5.2F, 9.5F, -0.3F}, {0.0F, 7.0F, 2.9F}};
	private static final float ITEM_SCALE = 0.36F;
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private final CapeModel cape = new CapeModel(capeMesh().bakeRoot());

	WebBodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	private static LayerDefinition capeMesh() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-CAPE_W / 2.0F, 0.0F, 0.0F, (int)CAPE_W, (int)CAPE_H, 1),
			PartPose.offset(0.0F, 0.0F, CAPE_BACK));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || state.isInvisible) {
			return;
		}
		Entity entity = minecraft.level.getEntity(state.id);
		if (!(entity instanceof Player player) || !WebSymbiote.isBonded(player) || !WebSymbiote.suitShown(player)) {
			return;
		}
		boolean ownFirst = WebSymbioteClient.ownFirstPerson(state);
		float unfurl = unfurl(player, minecraft);
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		if (unfurl <= 0.0F && arm.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		WebFeral.applyModel(this.getParentModel(), state);
		WebClimbPose.applyModel(this.getParentModel(), state);
		WebHeldGrabPose.applyModel(this.getParentModel(), state);
		WebExecutionPose.applyModel(this.getParentModel(), state);
		WebSuitGrowth.shift(this.getParentModel(), state);
		this.getParentModel().body.translateAndRotate(poseStack);
		if (unfurl > 0.0F && !ownFirst) {
			this.cape.pose(state, unfurl);
			collector.submitModel(this.cape, state, poseStack, RenderTypes.entityCutout(WebStrainLook.texture(TEXTURE, state)), lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
		if (!arm.isEmpty()) {
			List<ItemStack> stacks = arm.stacks();
			for (int slot = 0; slot < stacks.size() && slot < SLOTS.length; slot++) {
				ItemStack stack = stacks.get(slot);
				if (stack.isEmpty()) {
					continue;
				}
				float[] at = SLOTS[slot];
				poseStack.pushPose();
				poseStack.translate(at[0] / 16.0F, at[1] / 16.0F, at[2] / 16.0F);
				if (slot == 2) {
					// on the back, lying along it
					poseStack.rotateDegrees(Axis.YP, 180.0F);
					poseStack.rotateDegrees(Axis.ZP, -45.0F);
				} else {
					// at the hip, hanging by the side
					poseStack.rotateDegrees(Axis.YP, slot == 0 ? 90.0F : -90.0F);
					poseStack.rotateDegrees(Axis.ZP, -45.0F);
				}
				if (slot == arm.selected()) {
					// the picked one stands a little proud
					poseStack.translate(0.0F, 0.0F, -0.03F);
				}
				poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
				minecraft.getItemModelResolver().updateForTopItem(ITEM, stack, ItemDisplayContext.FIXED, minecraft.level, null, state.id + slot);
				ITEM.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}

	/** How far the mantle is unfurled (0 furled away, 1 hanging), from when it last moved. */
	private static float unfurl(Player player, Minecraft minecraft) {
		boolean out = WebSymbioteBody.mantleOut(player);
		Long at = player.getAttached(WebSymbioteBody.MANTLE_AT);
		if (at == null) {
			return out ? 1.0F : 0.0F;
		}
		float now = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float f = Mth.clamp((now - at) / WebSymbioteBody.MANTLE_TICKS, 0.0F, 1.0F);
		f = f * f * (3.0F - 2.0F * f);
		return out ? f : 1.0F - f;
	}

	private static final class CapeModel extends Model<AvatarRenderState> {
		private final ModelPart cape;

		CapeModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.cape = root.getChild("cape");
		}

		/** Hung from the shoulders: flared back by the run and the fall as the vanilla cape is, and rolled up as it furls. */
		void pose(AvatarRenderState state, float unfurl) {
			float flare = Mth.clamp((state.capeLean / 2.0F + state.capeFlap) * Mth.DEG_TO_RAD, 0.0F, CAPE_FLARE);
			this.cape.xRot = CAPE_REST + flare + (1.0F - unfurl) * 0.5F;
			this.cape.zRot = state.capeLean2 / 2.0F * Mth.DEG_TO_RAD * 0.5F;
			// (furling draws it up toward the shoulders, thinner)
			this.cape.yScale = Math.max(0.05F, unfurl);
			this.cape.xScale = 0.5F + 0.5F * unfurl;
			this.cape.visible = unfurl > 0.02F;
		}
	}
}
