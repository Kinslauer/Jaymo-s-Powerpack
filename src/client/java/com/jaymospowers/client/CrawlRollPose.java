package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/** A crawling player's roll: the body turns once over along its length while sliding a block sideways. */
public final class CrawlRollPose {
	static final RenderStateDataKey<Float> ROLL = RenderStateDataKey.create(() -> "jaymospowers:crawl_roll");

	private CrawlRollPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Float progress = null;
		if (entity instanceof Player player) {
			long time = player.level().getGameTime();
			progress = player == Minecraft.getInstance().player ? CrawlTaps.localRollProgress(time, partialTicks)
				: Crawl.rollProgress(player, time, partialTicks);
		}
		state.setData(ROLL, progress);
	}

	/** After the swimming tilt the model's Y axis runs head to foot, so a turn about it rolls the body over. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Float progress = state.getData(ROLL);
		if (progress == null || !state.isVisuallySwimming) {
			return;
		}
		float t = Math.abs(progress);
		float eased = t * t * (3.0F - 2.0F * t);
		poseStack.rotateDegrees(Axis.YP, Math.signum(progress) * 360.0F * eased);
	}
}
