package com.jaymospowers.client.power.chrono;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymospowers.power.chrono.future.FutureMech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The future's mechs on our own {@link AndroidMechModel}, in one of its four builds (picked by the mech's id, so it keeps
 * it), visor, core and lights aglow. The war mech is drawn bigger than the android mech the Mad Tinkerer sets on you. When
 * it fires, its cannon arms come up level at its target.
 */
public class FutureMechRenderer extends GeoEntityRenderer<FutureMech, LivingEntityRenderState> {
	/** The cannons' old aiming pitch, and how much of the head's yaw they follow. */
	static final float AIM = -1.45F, CANNON_YAW = 0.5F;
	/** The old renderer's whole-mech roll during a heavy stride. */
	static final float WALK_ROLL = 2.5F;
	/** How much bigger than the model each is drawn: the war mech (about 4 blocks), the android mech (about 3). */
	static final float WAR_SCALE = 1.3F, ANDROID_SCALE = 1.0F;

	private final float scale;

	public static FutureMechRenderer war(EntityRendererProvider.Context context) {
		return new FutureMechRenderer(context, WAR_SCALE);
	}

	public static FutureMechRenderer android(EntityRendererProvider.Context context) {
		return new FutureMechRenderer(context, ANDROID_SCALE);
	}

	private FutureMechRenderer(EntityRendererProvider.Context context, float scale) {
		super(context, new AndroidMechModel());
		this.scale = scale;
		this.shadowRadius = 0.8F * scale;
		this.withRenderLayer(new Glow(this));
	}

	@Override
	public void addRenderData(FutureMech mech, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(mech, related, state, partialTick);
		state.addGeckolibData(AndroidMechModel.BUILD, Math.floorMod(mech.getUUID().hashCode(), AndroidMechModel.BUILDS.length));
		state.addGeckolibData(AndroidMechModel.AIMING, mech.aiming());
		state.addGeckolibData(AndroidMechModel.SMASHING, mech.getAttackAnimationTick() > 0);
	}

	@Override
	public void scaleModelForRender(RenderPassInfo<LivingEntityRenderState> info, float widthScale, float heightScale) {
		super.scaleModelForRender(info, widthScale * this.scale, heightScale * this.scale);
	}

	@Override
	protected void applyRotations(RenderPassInfo<LivingEntityRenderState> info, PoseStack poseStack, float bodyRot) {
		super.applyRotations(info, poseStack, bodyRot);
		LivingEntityRenderState state = info.renderState();
		if (state.walkAnimationSpeed >= 0.01F) {
			float wave = (Math.abs((state.walkAnimationPos + 6.0F) % 13.0F - 6.5F) - 3.25F) / 3.25F;
			poseStack.rotateDegrees(Axis.ZP, WALK_ROLL * wave);
		}
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		int build = info.getOrDefaultGeckolibData(AndroidMechModel.BUILD, 0);
		int bit = 1 << build;
		for (Map.Entry<String, Integer> part : AndroidMechModel.PART_BUILDS.entrySet()) {
			snapshots.ifPresent(part.getKey(), bone -> bone.skipRender((part.getValue() & bit) == 0));
		}

		LivingEntityRenderState state = info.renderState();
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		// GeckoLib's x/y rotations are the negative of the old ModelPart's.
		snapshots.ifPresent("head", bone -> bone.setRotY(bone.getRotY() - yaw).setRotX(bone.getRotX() - pitch));
		if (info.getOrDefaultGeckolibData(AndroidMechModel.AIMING, false)
			&& !info.getOrDefaultGeckolibData(AndroidMechModel.SMASHING, false)) {
			float aim = AIM + pitch;
			snapshots.ifPresent("right_arm", bone -> bone.setRotX(-aim).setRotY(-yaw * CANNON_YAW));
			if ((AndroidMechModel.PART_BUILDS.get("cannon_l") & bit) != 0) {
				snapshots.ifPresent("left_arm", bone -> bone.setRotX(-aim).setRotY(-yaw * CANNON_YAW));
			}
		}
	}

	/** The original visor, core and lights, still drawn at full brightness in the current build's colours. */
	private static final class Glow extends TextureLayerGeoLayer<FutureMech, Void, LivingEntityRenderState> {
		Glow(FutureMechRenderer renderer) {
			super(renderer, AndroidMechModel.GLOW[0], RenderTypes::eyes);
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return AndroidMechModel.GLOW[state.getOrDefaultGeckolibData(AndroidMechModel.BUILD, 0)];
		}
	}
}
