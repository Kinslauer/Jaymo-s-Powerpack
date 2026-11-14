package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.ragdoll.MobRagdolls;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * A mob knocked limp (see MobRagdolls) is drawn as the ragdoll the server sends for it, as a corpse is: the limbs
 * follow the physics (RagdollPose, in RagdollModelMixin), whatever it doesn't drive hangs loose (LooseParts), and the
 * whole body is placed onto the ragdoll instead of the renderer's own turn ({@link #place}). A GeckoLib mob is laid
 * down the same way, as a whole (GeoLimpMobMixin).
 */
public final class LimpMobs {
	/** Where a limp mob's body goes this frame (about the spot its renderer stands it), or none. */
	public static final RenderStateDataKey<Matrix4f> PLACE = RenderStateDataKey.create(() -> "jaymoskitchen:limp_mob_place");

	private LimpMobs() {
	}

	/** Once a mob's render state is filled in: its ragdoll this frame, if it's lying limp. */
	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		ClientRagdolls.Track track = MobRagdolls.isLimp(entity) ? ClientRagdolls.get(entity.getId()) : null;
		if (track == null) {
			if (state.getData(PLACE) != null) {
				// a state drawn limp before, got up since
				state.setData(RagdollPose.KEY, null);
				state.setData(PLACE, null);
			}
			return;
		}
		RagdollPose pose = RagdollPose.of(track.rig, track.points(partialTicks), state.x, state.y, state.z, track.side(partialTicks),
			track.sideUp(), false);
		if (track.rig.shape != RagdollRig.Shape.HUMANOID) {
			float scale = state instanceof LivingEntityRenderState living ? living.scale : 1.0F;
			pose.loosen(LooseParts.motion(entity), state.ageInTicks, track.asleep(), scale);
		}
		if (state instanceof LivingEntityRenderState living) {
			// nothing flinching or walking: the physics does all the posing
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
		}
		state.setData(RagdollPose.KEY, pose);
		state.setData(PLACE, pose.body);
	}

	/**
	 * Instead of the renderer's own turn: onto the ragdoll, then turned as the renderer turns a standing model (bodyRot
	 * 0). False if the body isn't limp (the renderer turns it as usual). {@code scale} is the size already on the stack.
	 */
	public static boolean place(EntityRenderState state, PoseStack poseStack, float scale) {
		Matrix4f body = state.getData(PLACE);
		if (body == null) {
			return false;
		}
		// the stack already carries the entity's scale: the move is in blocks, so divide it back out
		Matrix4f place = new Matrix4f(body);
		float s = Math.max(scale, 0.01F);
		place.setTranslation(place.m30() / s, place.m31() / s, place.m32() / s);
		poseStack.mulPose(place);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
		return true;
	}
}
