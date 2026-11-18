package com.jaymoskingdoms.client;

import com.jaymoskingdoms.equestrian.Horsemanship;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A Rider hanging off the side of a horse (Trick Riding, {@link Horsemanship#HANGING}), for everyone to see: the whole body
 * rolled down over the horse's left flank from the hips, which stay in the saddle, the head turned to look ahead.
 */
public final class HangPose {
	/** How far the body rolls down the flank (radians), where it turns about (model pixels below the neck: the hips). */
	static final float ROLL = 1.05F, HIPS = 12.0F;
	/** The head turned back up toward the way ahead (radians), and the near arm reaching down (radians). */
	static final float HEAD_BACK = -0.6F, REACH = -0.5F;

	static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:rider_hanging");

	private HangPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity instanceof Player player && player.isPassenger() && Boolean.TRUE.equals(player.getAttached(Horsemanship.HANGING))) {
			state.setData(KEY, true);
		}
	}

	/** Right after the model was posed: rolled down the flank about the hips. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)
			|| !Boolean.TRUE.equals(living.getData(KEY))) {
			return;
		}
		ModelPart root = m.root();
		// turned about the hips, not the neck: the hips are put back where they were (rotateZ takes (0, HIPS) to (-sin, cos))
		root.zRot = ROLL;
		root.x = HIPS * Mth.sin(ROLL);
		root.y = HIPS * (1.0F - Mth.cos(ROLL));
		m.head.zRot = HEAD_BACK;
		m.leftArm.xRot = REACH;
		m.leftArm.zRot = 0.0F;
	}
}
