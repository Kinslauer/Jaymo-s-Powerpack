package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How a player crawling by the gesture looks, in place of vanilla's swimming strokes: on land they drag themselves
 * along elbow over elbow with the legs trailing, and a roll turns the body over once along its length, arms stretched
 * past the head and legs together.
 */
public final class CrawlPose {
	/** {@code roll}: progress through a roll (signed by direction), or null; {@code stride}/{@code speed}: the crawl's walk cycle. */
	record State(@Nullable Float roll, float stride, float speed) {
	}

	static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:crawl");

	private CrawlPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !Crawl.crawling(player) || player.isInWater()) {
			state.setData(KEY, null);
			return;
		}
		long time = player.level().getGameTime();
		Float roll = player == Minecraft.getInstance().player ? CrawlTaps.localRollProgress(time, partialTicks)
			: Crawl.rollProgress(player, time, partialTicks);
		state.setData(KEY, new State(roll, state.walkAnimationPos, state.walkAnimationSpeed));
	}

	/** After the swimming tilt the model's Y axis runs head to foot, so a turn about it rolls the body over. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		State crawl = state.getData(KEY);
		if (crawl == null || crawl.roll() == null || !state.isVisuallySwimming) {
			return;
		}
		float t = Math.abs(crawl.roll());
		// mostly even, eased a little, so held rolls run into each other without a hitch
		float turn = Mth.lerp(0.35F, t, t * t * (3.0F - 2.0F * t));
		poseStack.rotateDegrees(Axis.YP, Math.signum(crawl.roll()) * 360.0F * turn);
	}

	/** Right after the model animates (vanilla has just posed the swimming strokes): the crawl's or roll's limbs instead. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || !state.isVisuallySwimming) {
			return;
		}
		State crawl = state.getData(KEY);
		if (crawl == null) {
			return;
		}
		if (crawl.roll() != null) {
			// log roll: arms straight out past the head, legs together
			set(h.rightArm, -Mth.PI, 0.0F, 0.12F);
			set(h.leftArm, -Mth.PI, 0.0F, -0.12F);
			set(h.rightLeg, 0.0F, 0.0F, 0.03F);
			set(h.leftLeg, 0.0F, 0.0F, -0.03F);
			h.body.yRot = 0.0F;
			return;
		}
		// elbow crawl: one forearm reaches ahead while the other pulls back under the chest, elbows out, legs trailing
		float moving = Math.min(1.0F, 0.25F + crawl.speed() * 2.5F);
		float pull = Mth.sin(crawl.stride() * 0.6662F) * moving;
		set(h.rightArm, -2.3F + 0.75F * pull, -0.2F, 0.55F - 0.15F * pull);
		set(h.leftArm, -2.3F - 0.75F * pull, 0.2F, -0.55F - 0.15F * pull);
		set(h.rightLeg, 0.15F * pull, 0.0F, 0.14F);
		set(h.leftLeg, -0.15F * pull, 0.0F, -0.14F);
		h.body.yRot = 0.07F * pull;
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
