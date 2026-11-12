package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.client.ShackleTether;
import com.jaymoskingdoms.forms.Lasso;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The lasso's rope as everyone sees it ({@link Lasso#HOLDER}): from the holder's hand to the held one's middle, drawn with the
 * shackle rope's own twisted tube and texture, sagging a little between them.
 */
public final class LassoRope {
	/** How far below the eyes, and how far ahead of them, the holder's hand is (blocks); the held one's rope is tied at this share of its height. */
	static final double HAND_DROP = 0.45, HAND_AHEAD = 0.35, TIED_AT = 0.6;

	private LassoRope() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(LassoRope::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity held)) {
				continue;
			}
			Integer holderId = held.getAttached(Lasso.HOLDER);
			if (holderId == null || !(level.getEntity(holderId) instanceof LivingEntity holder) || held.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			Vec3 hand = holder.getEyePosition(partialTick).subtract(0.0, HAND_DROP, 0.0).add(holder.getViewVector(partialTick).scale(HAND_AHEAD));
			Vec3 tied = held.getPosition(partialTick).add(0.0, held.getBbHeight() * TIED_AT, 0.0);
			int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(tied));
			PoseStack poseStack = context.poseStack();
			Vec3 from = hand.subtract(camera), to = tied.subtract(camera);
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(ShackleTether.ROPE),
				(pose, buffer) -> ShackleTether.rope(buffer, pose, from, to, light));
		}
	}
}
