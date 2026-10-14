package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalPocket;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Arsenal's standing doorways, drawn as real portals: the pocket rift Z opens (a swirl of nether-portal light, or the
 * End's starfield when its opener is corrupted), and your own void gates (always the void). The server re-sends the
 * list every few ticks; a list that stops coming is dropped.
 */
final class ArsenalPortalRenderer {
	/** The pocket rift: half its width, half its height, and how long it takes to tear fully open. */
	private static final double RIFT_RX = 0.65, RIFT_RY = 1.15;
	private static final float OPEN_TICKS = 8.0F;
	/** A void gate, centred this high over its spot. */
	private static final double GATE_RX = 0.45, GATE_RY = 0.9, GATE_LIFT = 0.9;
	/** A list not refreshed within this many ticks is stale. */
	private static final long STALE = 20;

	private static List<ArsenalPocket.Portal> portals = List.of();
	private static long receivedAt;

	private ArsenalPortalRenderer() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ArsenalPocket.Portals.TYPE, (payload, context) -> {
			portals = payload.portals();
			ClientLevel level = context.client().level;
			receivedAt = level == null ? 0L : level.getGameTime();
		});
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || portals.isEmpty()) {
			return;
		}
		if (level.getGameTime() - receivedAt > STALE) {
			portals = List.of();
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float now = level.getGameTime() + partialTick;
		for (ArsenalPocket.Portal portal : portals) {
			Vec3 base = new Vec3(portal.x(), portal.y(), portal.z());
			Vec3 normal;
			double rx, ry, lift;
			float open;
			if (portal.gate()) {
				// a gate turns to face you
				Vec3 to = camera.subtract(base);
				normal = new Vec3(to.x, 0.0, to.z);
				normal = normal.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : normal.normalize();
				rx = GATE_RX;
				ry = GATE_RY;
				lift = GATE_LIFT;
				open = 1.0F;
			} else {
				double rad = Math.toRadians(portal.yaw());
				normal = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
				rx = RIFT_RX;
				ry = RIFT_RY;
				lift = RIFT_RY;
				float x = Mth.clamp((now - portal.opened()) / OPEN_TICKS, 0.0F, 1.0F);
				open = 1.0F - (1.0F - x) * (1.0F - x);
			}
			if (open < 0.02F) {
				continue;
			}
			Vec3 up = new Vec3(0.0, 1.0, 0.0);
			Vec3 right = up.cross(normal).normalize();
			// it tears open from a slit: full height first, then width
			double w = rx * open * open;
			double h = ry * Math.min(1.0, open * 1.6);
			Vec3 at = base.add(0.0, lift, 0.0).subtract(camera);
			// a little breathing, as a portal's surface wavers
			double breathe = 1.0 + 0.03 * Math.sin(now * 0.2 + portal.x());
			RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), at, right, up, normal, w * breathe, h * breathe, now * 0.06,
				portal.voidRift());
		}
	}
}
