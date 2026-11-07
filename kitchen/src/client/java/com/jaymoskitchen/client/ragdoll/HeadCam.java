package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.ragdoll.RagdollRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.74) Andy: "the first person POV should follow the head too, falling as you fall, etc like it does for
 * decapitations". In first person, a player lying limp (LimpController) or dead (their corpse's ragdoll) looks out from
 * the body's head: the view sits just in front of the face and turns as the head does, the way Jaymo's Powers' severed
 * head camera does.
 */
public final class HeadCam {
	/** The view sits this far in front of the head's middle (blocks, for a 1.8-tall body), so the head isn't seen from inside. */
	private static final float FORWARD = 0.27F;
	/** A dead player's own corpse counts within this distance (blocks). */
	private static final double CORPSE_REACH = 6.0;

	private static int cachedCorpse = -1;

	private HeadCam() {
	}

	/**
	 * The view from the local player's ragdoll head this frame, with the eye written into {@code eye}; null when the
	 * camera isn't theirs or they aren't limp or dead in a humanoid ragdoll.
	 */
	public static @Nullable Quaternionf view(@Nullable Entity camera, float partialTicks, Vector3d eye) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (camera == null || camera != player || minecraft.level == null) {
			return null;
		}
		ClientRagdolls.Track track = null;
		if (player.isAlive()) {
			if (LimpController.active()) {
				track = ClientRagdolls.get(player.getId());
			}
		} else {
			CorpseEntity corpse = ownCorpse(player);
			if (corpse != null && corpse.carrier() == null) {
				track = ClientRagdolls.get(corpse.getId());
			}
		}
		if (track == null || track.rig.shape != RagdollRig.Shape.HUMANOID) {
			return null;
		}
		double[] p = new double[6];
		double[] head = new double[3];
		track.point(RagdollRig.HEAD, partialTicks, head, 0);
		track.point(RagdollRig.NECK, partialTicks, p, 0);
		Vector3f up = new Vector3f((float) (head[0] - p[0]), (float) (head[1] - p[1]), (float) (head[2] - p[2]));
		track.point(RagdollRig.R_SHOULDER, partialTicks, p, 0);
		track.point(RagdollRig.L_SHOULDER, partialTicks, p, 3);
		Vector3f right = new Vector3f((float) (p[0] - p[3]), (float) (p[1] - p[4]), (float) (p[2] - p[5]));
		if (up.lengthSquared() < 1.0E-6F || right.lengthSquared() < 1.0E-6F) {
			return null;
		}
		up.normalize();
		// the face looks out of the chest's front: up x right (standing at yaw 0, right is -x and that's +z)
		Vector3f forward = new Vector3f(up).cross(right);
		if (forward.lengthSquared() < 1.0E-6F) {
			return null;
		}
		forward.normalize();
		right.set(forward).cross(up).normalize();
		// columns: the camera's +x (right), +y (up) and +z (behind: it looks down -z)
		Matrix3f basis = new Matrix3f(right.x, right.y, right.z, up.x, up.y, up.z, -forward.x, -forward.y, -forward.z);
		Quaternionf view = basis.getNormalizedRotation(new Quaternionf());
		float scale = track.rig.height / 1.8F;
		eye.set(head[0] + forward.x * FORWARD * scale, head[1] + forward.y * FORWARD * scale, head[2] + forward.z * FORWARD * scale);
		return view;
	}

	private static @Nullable CorpseEntity ownCorpse(LocalPlayer player) {
		if (player.level().getEntity(cachedCorpse) instanceof CorpseEntity corpse && !corpse.isRemoved() && mine(corpse, player)) {
			return corpse;
		}
		cachedCorpse = -1;
		for (CorpseEntity corpse : player.level().getEntitiesOfClass(CorpseEntity.class, player.getBoundingBox().inflate(CORPSE_REACH),
			c -> mine(c, player))) {
			cachedCorpse = corpse.getId();
			return corpse;
		}
		return null;
	}

	private static boolean mine(CorpseEntity corpse, LocalPlayer player) {
		return corpse.owner().filter(player.getUUID()::equals).isPresent();
	}
}
