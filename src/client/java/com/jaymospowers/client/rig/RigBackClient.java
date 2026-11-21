package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigBack;
import com.jaymospowers.rig.RigMoves;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The back rig as everyone sees it ({@link RigBack}): jointed metal that slides out of the Core's back plates and locks. Two wing
 * vanes lie folded down the back, spread wide in full flight (beating a little while they fly) and fold away in hover or on the
 * ground; four spear legs stab out in a ring and back in for the spear move; two pincer arms swing up over the shoulders and hold
 * forward while something is held, and fling forward on the throw. Drawn on the body bone, after the suit ({@link RigSuit}).
 */
public final class RigBackClient {
	// ---- tuning ----
	/** How quickly the vanes open and close (share a frame), their beat in flight (radians, radians a tick). */
	static final float SPREAD_EASE = 0.12F, BEAT = 0.12F, BEAT_RATE = 1.4F;
	/** The vanes' angles folded and spread (radians). */
	static final float WING_FOLDED = 1.35F, WING_SPREAD = -0.15F, WING_SWEEP = 0.35F;
	/** The spear legs folded away (length share) and out (their swing out and back, radians). */
	static final float SPEAR_TUCKED = 0.12F, SPEAR_BACK = -1.9F, SPEAR_OUT = 0.9F;
	/** The pincers folded (down the back) and over the shoulders (radians), and tucked away (length share). */
	static final float PINCER_FOLDED = 1.4F, PINCER_OUT = -2.6F, PINCER_TUCKED = 0.3F;

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/rig/back.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/rig/back_glow.png");
	/** On the render state: {spread, spears out, pincers out, time}; null when the Core isn't worn. */
	static final RenderStateDataKey<float[]> BACK = RenderStateDataKey.create(() -> "jaymospowers:rig_back");

	private static final Map<UUID, Float> SPREAD = new HashMap<>();
	private static ModelPart root;
	private static ModelPart wingR, wingL, pincerR, pincerL;
	private static final ModelPart[] SPEARS = new ModelPart[4];

	private RigBackClient() {
	}

	private static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition back = mesh.getRoot();
		CubeDeformation none = new CubeDeformation(0.0F);
		back.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -1.0F, 0.0F, 12.0F, 6.0F, 0.5F, none), PartPose.offset(-1.5F, 3.0F, 3.6F));
		back.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.0F, -1.0F, 0.0F, 12.0F, 6.0F, 0.5F, none), PartPose.offset(1.5F, 3.0F, 3.6F));
		float[][] spears = {{-2.0F, 4.0F}, {2.0F, 4.0F}, {-2.0F, 8.0F}, {2.0F, 8.0F}};
		for (int i = 0; i < spears.length; i++) {
			back.addOrReplaceChild("spear_" + i, CubeListBuilder.create().texOffs(0, 14).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 12.0F, 1.0F, none),
				PartPose.offset(spears[i][0], spears[i][1], 3.5F));
		}
		back.addOrReplaceChild("pincer_r", CubeListBuilder.create().texOffs(8, 14).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 8.0F, none)
			.texOffs(0, 8).addBox(-1.25F, -1.25F, 7.5F, 2.5F, 2.5F, 2.0F, none), PartPose.offset(-3.0F, 0.5F, 2.5F));
		back.addOrReplaceChild("pincer_l", CubeListBuilder.create().texOffs(8, 14).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 8.0F, none)
			.texOffs(0, 8).addBox(-1.25F, -1.25F, 7.5F, 2.5F, 2.5F, 2.0F, none), PartPose.offset(3.0F, 0.5F, 2.5F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	private static void bake() {
		if (root != null) {
			return;
		}
		root = mesh().bakeRoot();
		wingR = root.getChild("wing_r");
		wingL = root.getChild("wing_l");
		pincerR = root.getChild("pincer_r");
		pincerL = root.getChild("pincer_l");
		for (int i = 0; i < SPEARS.length; i++) {
			SPEARS[i] = root.getChild("spear_" + i);
		}
	}

	/** (From {@link RigSuit#extract}) what the back rig is doing on {@code player} this frame. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		if (Rig.core(player).isEmpty()) {
			state.setData(BACK, null);
			SPREAD.remove(player.getUUID());
			return;
		}
		float time = player.level().getGameTime() + partialTicks;
		boolean flight = RigMoves.thrust(player) == RigMoves.THRUST_FLIGHT;
		RigMoves.Move move = RigMoves.get(player);
		boolean slash = move != null && move.kind() == RigBack.MOVE_SLASH;
		float spread = SPREAD.getOrDefault(player.getUUID(), 0.0F);
		spread += ((flight || slash ? 1.0F : 0.0F) - spread) * SPREAD_EASE;
		SPREAD.put(player.getUUID(), spread);
		float spears = 0.0F, pincers = 0.0F;
		if (move != null) {
			float t = Mth.clamp((time - move.start()) / Math.max(1, RigMoves.length(move.kind())), 0.0F, 1.0F);
			if (move.kind() == RigBack.MOVE_SPEARS) {
				// out fast, back slower
				spears = t < 0.3F ? t / 0.3F : 1.0F - (t - 0.3F) / 0.7F;
			} else if (move.kind() == RigBack.MOVE_GRAB) {
				pincers = Math.min(1.0F, (time - move.start()) / 5.0F);
			} else if (move.kind() == RigBack.MOVE_THROW) {
				pincers = 1.0F - t;
			}
		}
		state.setData(BACK, new float[] {spread, spears, pincers, time, flight ? 1.0F : 0.0F});
	}

	/** (From {@link RigSuit}'s layer) draws the back rig on the body bone. */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel parent) {
		float[] b = state.getData(BACK);
		if (b == null) {
			return;
		}
		bake();
		float spread = b[0], spears = b[1], pincers = b[2], time = b[3];
		float beat = b[4] > 0.5F ? Mth.sin(time * BEAT_RATE) * BEAT * spread : 0.0F;
		wingR.zRot = Mth.lerp(spread, -WING_FOLDED, -WING_SPREAD) + beat;
		wingR.yRot = Mth.lerp(spread, 0.0F, WING_SWEEP);
		wingL.zRot = Mth.lerp(spread, WING_FOLDED, WING_SPREAD) - beat;
		wingL.yRot = Mth.lerp(spread, 0.0F, -WING_SWEEP);
		for (int i = 0; i < SPEARS.length; i++) {
			ModelPart spear = SPEARS[i];
			spear.yScale = Mth.lerp(spears, SPEAR_TUCKED, 1.0F);
			spear.xRot = Mth.lerp(spears, 0.0F, SPEAR_BACK);
			spear.zRot = Mth.lerp(spears, 0.0F, (i % 2 == 0 ? 1.0F : -1.0F) * SPEAR_OUT);
		}
		for (ModelPart pincer : new ModelPart[] {pincerR, pincerL}) {
			pincer.xRot = Mth.lerp(pincers, PINCER_FOLDED, PINCER_OUT);
			pincer.zScale = Mth.lerp(pincers, PINCER_TUCKED, 1.0F);
		}
		poseStack.pushPose();
		parent.body.translateAndRotate(poseStack);
		collector.submitModelPart(root, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
		collector.submitModelPart(root, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
		poseStack.popPose();
	}
}
