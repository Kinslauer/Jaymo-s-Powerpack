package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.client.ragdoll.ClientRagdolls;
import com.jaymoskitchen.corpse.CorpseEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.39) A player dragging a body reaches back to it: each holding arm (the right alone for a small one, both for a cow
 * or a bear) points at the leg it has hold of, wherever the ragdoll has swung it, and the body leans into the pull with
 * a shortened stride. Worked out in {@code AvatarRendererMixin} from the body's points this frame; applied after the
 * model animates, like {@link CarryPose}.
 */
public final class DragPose {
	public static final RenderStateDataKey<Hold> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:dragging");

	/** Leaning into the pull (radians), and the share of the stride kept under the load. */
	static final float LEAN = 0.18F, STRIDE_KEPT = 0.75F;
	/** How far an arm swings out to the side at most (radians), so two hands never cross. */
	static final float SPREAD_MAX = 0.6F;

	/** Each holding arm's swing back (x) and out (z), radians; {@code hands} 1 or 2. */
	public record Hold(int hands, float rightX, float rightZ, float leftX, float leftZ) {
	}

	private DragPose() {
	}

	/** {@code player}'s hold on the body they drag this frame, or null if they drag none (or it isn't drawn yet). */
	public static @Nullable Hold of(Player player, AvatarRenderState state, float partialTicks) {
		CorpseEntity corpse = CorpseEntity.draggedBy(player);
		if (corpse == null) {
			return null;
		}
		ClientRagdolls.Track track = ClientRagdolls.get(corpse.getId());
		if (track == null) {
			return null;
		}
		int hands = corpse.dragHands();
		float[] angles = new float[4];
		double[] at = new double[3];
		float yaw = state.bodyRot * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw), forwardZ = Mth.cos(yaw);
		double rightX = -Mth.cos(yaw), rightZ = -Mth.sin(yaw);
		float scale = state.scale;
		for (int h = 0; h < hands; h++) {
			track.point(CorpseEntity.gripPoint(track.rig, h), partialTicks, at, 0);
			double side = (h == 0 ? 1.0 : -1.0) * CorpseEntity.DRAG_SHOULDER_SPREAD * scale;
			double dx = at[0] - (state.x + rightX * side);
			double dy = at[1] - (state.y + CorpseEntity.DRAG_SHOULDER * scale);
			double dz = at[2] - (state.z + rightZ * side);
			double ahead = dx * forwardX + dz * forwardZ;
			double right = dx * rightX + dz * rightZ;
			// swung back (or forward) toward it from hanging down, then out to its side
			angles[h * 2] = (float) Mth.atan2(-ahead, -dy);
			angles[h * 2 + 1] = Mth.clamp((float) Mth.atan2(right, Math.sqrt(ahead * ahead + dy * dy)), -SPREAD_MAX, SPREAD_MAX);
		}
		return new Hold(hands, angles[0], angles[1], angles[2], angles[3]);
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Hold hold = avatar.getData(KEY);
		if (hold == null) {
			return;
		}
		set(humanoid.rightArm, hold.rightX(), hold.rightZ());
		if (hold.hands() > 1) {
			set(humanoid.leftArm, hold.leftX(), hold.leftZ());
		}
		humanoid.body.xRot += LEAN;
		humanoid.rightLeg.xRot *= STRIDE_KEPT;
		humanoid.leftLeg.xRot *= STRIDE_KEPT;
	}

	private static void set(ModelPart arm, float x, float z) {
		arm.xRot = x;
		arm.yRot = 0.0F;
		arm.zRot = z;
	}
}
