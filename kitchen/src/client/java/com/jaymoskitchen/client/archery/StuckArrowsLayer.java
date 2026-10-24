package com.jaymoskitchen.client.archery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * (0.5.23) The arrows stuck in a creature or its corpse (see StuckArrows), drawn as the game draws a player's: each one in
 * a random box of the body, chosen from the body's id so they stay put, pointing in from where it went in. Vanilla's
 * layer only works on the player model, so this one walks any model's parts (with their parents' poses).
 */
public class StuckArrowsLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
	/** On a living body's render state: {how many arrows to draw in it, the seed that places them (the entity's id)}. */
	public static final RenderStateDataKey<int[]> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:stuck_arrows");

	private final ArrowModel arrow;
	private final ArrowRenderState arrowState = new ArrowRenderState();
	/** Each model's parts that have boxes, as the path down to them from its root (built once a model). */
	private final Map<ModelPart, List<ModelPart[]>> paths = new IdentityHashMap<>();

	public StuckArrowsLayer(RenderLayerParent<S, M> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.arrow = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		int[] stuck = state.getData(KEY);
		if (stuck == null || stuck[0] <= 0 || state.isInvisible) {
			return;
		}
		int count = stuck[0];
		ModelPart root = this.getParentModel().root();
		List<ModelPart[]> parts = this.paths.computeIfAbsent(root, StuckArrowsLayer::walk);
		if (parts.isEmpty()) {
			return;
		}
		RandomSource random = RandomSource.createThreadLocalInstance(stuck[1]);
		for (int i = 0; i < count; i++) {
			ModelPart[] path = parts.get(random.nextInt(parts.size()));
			ModelPart part = path[path.length - 1];
			ModelPart.Cube cube = part.getRandomCube(random);
			float midX = random.nextFloat(), midY = random.nextFloat(), midZ = random.nextFloat();
			if (!shown(path)) {
				// a hidden part (cut off, under something): its arrow isn't drawn, but the next ones stay where they were
				continue;
			}
			poseStack.pushPose();
			for (ModelPart step : path) {
				step.translateAndRotate(poseStack);
			}
			poseStack.translate(Mth.lerp(midX, cube.minX, cube.maxX) / 16.0F, Mth.lerp(midY, cube.minY, cube.maxY) / 16.0F,
				Mth.lerp(midZ, cube.minZ, cube.maxZ) / 16.0F);
			float dx = -(midX * 2.0F - 1.0F), dy = -(midY * 2.0F - 1.0F), dz = -(midZ * 2.0F - 1.0F);
			float dxz = Mth.sqrt(dx * dx + dz * dz);
			poseStack.rotateDegrees(Axis.YP, (float) (Math.atan2(dx, dz) * 180.0 / Math.PI) - 90.0F);
			poseStack.rotateDegrees(Axis.ZP, (float) (Math.atan2(dy, dxz) * 180.0 / Math.PI));
			collector.submitModel(this.arrow, this.arrowState, poseStack, TippableArrowRenderer.NORMAL_ARROW_LOCATION, lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}

	private static boolean shown(ModelPart[] path) {
		for (ModelPart step : path) {
			if (!step.visible) {
				return false;
			}
		}
		return !path[path.length - 1].skipDraw;
	}

	/** Every part under {@code root} with boxes of its own, as its path from the root (root included). */
	private static List<ModelPart[]> walk(ModelPart root) {
		// the parts' paths ("/body/head"), as the model's own walk over its boxes names them
		java.util.Set<String> named = new java.util.LinkedHashSet<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> named.add(path));
		List<ModelPart[]> found = new ArrayList<>();
		for (String path : named) {
			List<ModelPart> chain = new ArrayList<>();
			chain.add(root);
			ModelPart at = root;
			boolean ok = true;
			for (String name : path.split("/")) {
				if (name.isEmpty()) {
					continue;
				}
				if (!at.hasChild(name)) {
					ok = false;
					break;
				}
				at = at.getChild(name);
				chain.add(at);
			}
			if (ok && !at.isEmpty()) {
				found.add(chain.toArray(new ModelPart[0]));
			}
		}
		return found;
	}
}
