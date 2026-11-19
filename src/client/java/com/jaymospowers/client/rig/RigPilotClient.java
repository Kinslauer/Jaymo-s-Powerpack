package com.jaymospowers.client.rig;

import com.jaymospowers.rig.RigPilot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Mind flying the suit ({@link RigPilot}), on the wearer's client: in full flight it steers for the course's spot, climbing to
 * {@link #CRUISE_ABOVE} over it while far off and coming down near it, and turns the wearer's view along the way. Any movement key
 * takes the controls back.
 */
final class RigPilotClient {
	/** How high over the spot it cruises (blocks), from how far off it starts down (blocks), how fast it turns the view (share a tick). */
	static final double CRUISE_ABOVE = 24.0, DESCEND_FROM = 32.0;
	static final float TURN = 0.15F;

	private static @Nullable BlockPos course;

	private RigPilotClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(RigPilot.Course.TYPE, (payload, context) -> course = payload.to().orElse(null));
	}

	/** The velocity the Mind wants now, at up to {@code speed}, or null (no course, or the wearer took the controls back). */
	static @Nullable Vec3 want(Minecraft minecraft, LocalPlayer player, double speed) {
		if (course == null) {
			return null;
		}
		var o = minecraft.options;
		if (o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown() || o.keyJump.isDown() || o.keyShift.isDown()) {
			course = null;
			ClientPlayNetworking.send(new RigPilot.Cancel());
			return null;
		}
		Vec3 to = Vec3.atBottomCenterOf(course);
		double dx = to.x - player.getX(), dz = to.z - player.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		double aimY = flat > DESCEND_FROM ? Math.max(player.getY(), to.y + CRUISE_ABOVE) : to.y + 2.0;
		Vec3 dir = new Vec3(dx, aimY - player.getY(), dz);
		if (dir.lengthSqr() < 1.0E-4) {
			return Vec3.ZERO;
		}
		// slowing in over the last stretch
		double pace = speed * Mth.clamp(flat / DESCEND_FROM, 0.3, 1.0);
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		player.setYRot(Mth.rotLerp(TURN, player.getYRot(), yaw));
		return dir.normalize().scale(pace);
	}
}
