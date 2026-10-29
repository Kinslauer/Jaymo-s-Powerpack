package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureMech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The future's mechs on our own {@link AndroidMechModel}, in one of its four builds (picked by the mech's id, so it keeps
 * it), visor, core and lights aglow. The war mech is drawn bigger than the android mech the Mad Tinkerer sets on you. When
 * it fires, its cannon arms come up level at its target.
 */
public class FutureMechRenderer extends MobRenderer<FutureMech, AndroidMechModel.State, AndroidMechModel> {
	static final String[] BUILDS = {"warden", "bulwark", "strider", "reclaimer"};
	static final Identifier[] SKINS = new Identifier[BUILDS.length];
	static final RenderType[] EYES = new RenderType[BUILDS.length];
	/** How much bigger than the model each is drawn: the war mech (about 4 blocks), the android mech (about 3). */
	static final float WAR_SCALE = 1.3F, ANDROID_SCALE = 1.0F;

	static {
		for (int i = 0; i < BUILDS.length; i++) {
			SKINS[i] = JaymosPowers.id("textures/entity/future/android_mech_" + BUILDS[i] + ".png");
			EYES[i] = RenderTypes.eyes(JaymosPowers.id("textures/entity/future/android_mech_" + BUILDS[i] + "_eyes.png"));
		}
	}

	private final float scale;

	public static FutureMechRenderer war(EntityRendererProvider.Context context) {
		return new FutureMechRenderer(context, WAR_SCALE);
	}

	public static FutureMechRenderer android(EntityRendererProvider.Context context) {
		return new FutureMechRenderer(context, ANDROID_SCALE);
	}

	private FutureMechRenderer(EntityRendererProvider.Context context, float scale) {
		super(context, AndroidMechModel.create(), 0.8F * scale);
		this.scale = scale;
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES[0];
			}

			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AndroidMechModel.State state, float yRot, float xRot) {
				collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES[state.build], light, OverlayTexture.NO_OVERLAY,
					state.outlineColor);
			}
		});
	}

	@Override
	public AndroidMechModel.State createRenderState() {
		return new AndroidMechModel.State();
	}

	@Override
	public void extractRenderState(FutureMech entity, AndroidMechModel.State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.build = Math.floorMod(entity.getUUID().hashCode(), BUILDS.length);
		state.aiming = entity.aiming();
		state.attackTicks = entity.getAttackAnimationTick() > 0 ? entity.getAttackAnimationTick() - partialTicks : 0.0F;
	}

	@Override
	public Identifier getTextureLocation(AndroidMechModel.State state) {
		return SKINS[state.build];
	}

	@Override
	protected void scale(AndroidMechModel.State state, PoseStack poseStack) {
		poseStack.scale(this.scale, this.scale, this.scale);
	}

	@Override
	protected void setupRotations(AndroidMechModel.State state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.walkAnimationSpeed >= 0.01F) {
			// a heavy, swaying stride
			float wave = (Math.abs((state.walkAnimationPos + 6.0F) % 13.0F - 6.5F) - 3.25F) / 3.25F;
			poseStack.rotateDegrees(Axis.ZP, 2.5F * wave);
		}
	}
}
