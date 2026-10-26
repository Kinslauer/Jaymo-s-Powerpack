package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * A Warrior-born's tail ({@code textures/entity/zfighter_tail.png}): a chain of furry segments from the small of the back
 * that droops, curls up at the tip and sways (more when he runs); wrapped, a band of fur round the waist. Each segment is
 * the same box drawn at its own place on the pose stack, so nothing shared is ever re-posed. (0.16.36) Every character's
 * tail is its own ({@link WarriorLooks#tail}): length, thickness, droop, curl, sway and fur shade.
 */
public final class ZFighterTail {
	/** 0 no tail, 1 out, 2 wrapped round the waist. */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_tail");
	/** (0.16.36) Whose tail it is: its shape and fur. */
	public static final RenderStateDataKey<WarriorLooks.Tail> STYLE = RenderStateDataKey.create(() -> "jaymospowers:zfighter_tail_style");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_tail.png");
	static final int SEGMENTS = 6;
	/** Each segment's length (pixels), how much smaller the next is, and how the chain bends. */
	static final float LENGTH = 2.6F;
	static final float TAPER = 0.92F;
	static final float DROOP = -0.75F;
	static final float CURL = 0.32F;
	static final float SWAY = 0.22F;

	private static ModelPart segment, belt;

	private ZFighterTail() {
	}

	private static ModelPart segment() {
		if (segment == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, LENGTH + 0.4F),
				PartPose.ZERO);
			segment = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("part");
		}
		return segment;
	}

	private static ModelPart belt() {
		if (belt == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(0, 8).addBox(-4.5F, 9.5F, -2.5F, 9.0F, 2.0F, 5.0F),
				PartPose.ZERO);
			belt = LayerDefinition.create(mesh, 32, 32).bakeRoot().getChild("part");
		}
		return belt;
	}

	public static void extract(Avatar entity, AvatarRenderState state) {
		int tail = 0;
		if (entity instanceof Player player && ZFighterLineage.of(player) == ZFighterLineage.WARRIOR) {
			tail = ZFighterLineage.tailOut(player) ? 1 : 2;
		}
		com.jaymospowers.power.zfighter.ZFighterFusion.Look fused = entity instanceof Player player ? ZFighterFusionLook.of(player) : null;
		if (tail == 0 && fused != null) {
			// (0.16.16) fused with a Warrior: their tail
			tail = fused.innerTail();
		}
		state.setData(KEY, tail);
		state.setData(STYLE, tail == 0 ? null : WarriorLooks.tail(entity));
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Integer tail = state.getData(KEY);
			if (tail == null || tail == 0 || state.isInvisible || com.jaymospowers.client.BodyCover.body(state)) {
				// (0.12.5) a costume or the symbiote's suit covers it
				return;
			}
			WarriorLooks.Tail style = state.getData(STYLE);
			int fur = style == null ? -1 : style.fur();
			poseStack.pushPose();
			this.getParentModel().body.translateAndRotate(poseStack);
			if (tail == 2) {
				collector.submitModelPart(belt(), poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null, fur);
				poseStack.popPose();
				return;
			}
			// from the small of the back (the body's box is -4..4 x, 0..12 y, -2..2 z; +z is behind him)
			poseStack.translate(0.0F, 10.5F / 16.0F, 1.6F / 16.0F);
			float time = state.ageInTicks;
			float run = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			int segments = style == null ? SEGMENTS : style.segments();
			float length = style == null ? LENGTH : style.length(), thick = style == null ? 1.0F : style.thickness();
			float droop = style == null ? DROOP : style.droop(), curl = style == null ? CURL : style.curl();
			float sway = style == null ? SWAY : style.sway(), speed = style == null ? 1.0F : style.swaySpeed(), phase = style == null ? 0.0F : style.phase();
			for (int i = 0; i < segments; i++) {
				float pitch = (i == 0 ? droop - 0.35F * run : curl * (1.0F - 0.5F * run)) + 0.05F * Mth.sin(time * 0.08F + i);
				if (style != null && i == style.kink()) {
					// an old break, bent hard
					pitch += 0.6F;
				}
				float yaw = sway * (1.0F + run) * Mth.sin(time * (0.1F + 0.15F * run) * speed + phase - i * 0.6F);
				poseStack.rotate(Axis.XP, pitch);
				poseStack.rotate(Axis.YP, yaw);
				poseStack.pushPose();
				// each segment is LENGTH long as baked: stretch it to this tail's own length and thickness
				poseStack.scale(thick, thick, length / LENGTH);
				int color = style != null && style.banded() && i % 2 == 1 ? WarriorLooks.OCHRE : fur;
				collector.submitModelPart(segment(), poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null, color);
				poseStack.popPose();
				poseStack.translate(0.0F, 0.0F, length / 16.0F);
				poseStack.scale(TAPER, TAPER, TAPER);
			}
			poseStack.popPose();
		}
	}
}
