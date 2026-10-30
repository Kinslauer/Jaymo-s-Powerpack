package com.jaymospowers.client.power.zfighter.pod;

import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

/**
 * (0.18.48, Andy) Draws a {@link PodEntity} from {@link PodModel}: only the pieces whose slots are filled, turned to its
 * heading. A damaged fin is drawn bent out {@link #BEND} degrees about its foot, a damaged canopy as its lower step only,
 * a damaged plate with its dark gash. (Parts are drawn by pose, not by moving the shared model parts, since the draw is
 * queued and made later.)
 */
public class PodRenderer extends EntityRenderer<PodEntity, PodRenderer.State> {
	/** How far a damaged fin is bent (degrees). */
	static final float BEND = 35.0F;
	private static final String[] FINS = {"fin_l", "fin_r", "fin_b"};
	private static final String[] PLATES = {"plate_f", "plate_r", "plate_b", "plate_l"};
	private static final String[] GASHES = {"gash_f", "gash_r", "gash_b", "gash_l"};

	public static class State extends EntityRenderState {
		float yaw;
		int filled;
		int damaged;
	}

	public PodRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.1F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(PodEntity entity, float partialTicks) {
		// the fins reach past its box
		return entity.getBoundingBox().inflate(0.6, 0.2, 0.6);
	}

	@Override
	public void extractRenderState(PodEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.filled = entity.filled();
		state.damaged = entity.damagedMask();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yaw);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		RenderType solid = RenderTypes.entityCutout(PodModel.TEXTURE);
		RenderType glass = RenderTypes.entityTranslucent(PodModel.TEXTURE);
		if ((state.filled & PodPart.KEEL.bit(0)) != 0) {
			draw(collector, poseStack, state, "keel", solid);
		}
		for (int i = 0; i < FINS.length; i++) {
			int bit = PodPart.FIN.bit(i);
			if ((state.filled & bit) == 0) {
				continue;
			}
			poseStack.pushPose();
			if ((state.damaged & bit) != 0) {
				// bent out about its foot (y is down in model space: the foot is 3 px up)
				float side = i == 0 ? -1.0F : 1.0F;
				poseStack.translate(i == 2 ? 0.0F : side * 11.0F / 16.0F, -3.0F / 16.0F, i == 2 ? -11.0F / 16.0F : 0.0F);
				poseStack.rotateDegrees(i == 2 ? Axis.XP : Axis.ZP, i == 2 ? -BEND : side * BEND);
				poseStack.translate(i == 2 ? 0.0F : -side * 11.0F / 16.0F, 3.0F / 16.0F, i == 2 ? 11.0F / 16.0F : 0.0F);
			}
			draw(collector, poseStack, state, FINS[i], solid);
			poseStack.popPose();
		}
		if ((state.filled & PodPart.BELL.bit(0)) != 0) {
			draw(collector, poseStack, state, "bell", solid);
		}
		if ((state.filled & PodPart.TANK.bit(0)) != 0) {
			draw(collector, poseStack, state, "tank", solid);
		}
		for (int i = 0; i < PLATES.length; i++) {
			int bit = PodPart.PLATE.bit(i);
			if ((state.filled & bit) != 0) {
				draw(collector, poseStack, state, PLATES[i], solid);
				if ((state.damaged & bit) != 0) {
					draw(collector, poseStack, state, GASHES[i], solid);
				}
			}
		}
		if ((state.filled & PodPart.CANOPY.bit(0)) != 0) {
			draw(collector, poseStack, state, (state.damaged & PodPart.CANOPY.bit(0)) != 0 ? "canopy_low" : "canopy", glass);
		}
		if ((state.filled & PodPart.MAST.bit(0)) != 0) {
			draw(collector, poseStack, state, "mast", solid);
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static void draw(SubmitNodeCollector collector, PoseStack poseStack, State state, String name, RenderType type) {
		ModelPart part = PodModel.part(name);
		collector.submitModelPart(part, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
	}
}
