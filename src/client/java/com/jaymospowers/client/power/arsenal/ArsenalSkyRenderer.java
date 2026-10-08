package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.jaymospowers.power.arsenal.HundredBlades;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Hundred Blades, seen by everyone: the ring of rifts torn open in the sky (flat, facing down), drawn with the same
 * rift as Reach Through. The blades hanging from them are ordinary Rift Blades.
 */
final class ArsenalSkyRenderer {
	private static final double VIEW_DISTANCE = 128.0;
	private static final Vec3 RIGHT = new Vec3(1.0, 0.0, 0.0);
	private static final Vec3 FORWARD = new Vec3(0.0, 0.0, 1.0);
	private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);

	private ArsenalSkyRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Player player : level.players()) {
			HundredBlades.Sky sky = player.getAttached(HundredBlades.SKY);
			if (sky == null) {
				continue;
			}
			float t = level.getGameTime() - sky.start() + partialTick;
			if (t < 0.0F || t > HundredBlades.CLOSE_END) {
				continue;
			}
			float open = HundredBlades.openness(t);
			if (open <= 0.01F || new Vec3(sky.x(), sky.y(), sky.z()).distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			boolean voidRift = ArsenalVoid.isVoid(player);
			for (int i = 0; i < HundredBlades.PORTALS; i++) {
				Vec3 at = sky.portal(i).subtract(camera);
				// each tears open a beat after the last
				float own = HundredBlades.openness(Math.max(0.0F, t - i * 1.5F));
				double r = HundredBlades.PORTAL_RADIUS * Math.min(open, own) * (i == 0 ? 1.25 : 1.0);
				RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), at, RIGHT, FORWARD, DOWN, r, r, t * 0.1 + i, voidRift);
			}
		}
	}
}
