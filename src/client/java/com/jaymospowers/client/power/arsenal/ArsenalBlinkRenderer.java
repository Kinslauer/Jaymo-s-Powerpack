package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalBlinks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Arsenal's blinks ({@link ArsenalBlinks}), drawn as real rifts: each blink leaves a pair, one where he went in
 * and one where he came out, torn open fast from a slit (as the pocket rift opens), held a moment, then closed. They look
 * as his own rifts look (the End's starfield when corrupted, else the nether-portal swirl) and are sized to the body.
 */
final class ArsenalBlinkRenderer {
	/** A pair's whole life, how fast it tears open, and how long it takes to close (ticks). */
	private static final float LIFE = 14.0F, OPEN_TICKS = 3.0F, CLOSE_TICKS = 4.0F;
	/** At most this many pairs at once (a Flicker opens one every few ticks). */
	private static final int MAX = 48;
	/** The rift to the body: half-width over half the body's width, half-height over its height, and the least of each. */
	private static final double WIDTH_PAD = 0.35, HEIGHT_SHARE = 0.64, MIN_RX = 0.4, MIN_RY = 0.5;

	private record Live(ArsenalBlinks.Tear tear, long born) {
	}

	private static final List<Live> LIVE = new ArrayList<>();
	private static ClientLevel shownIn;

	private ArsenalBlinkRenderer() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ArsenalBlinks.Tear.TYPE, (payload, context) -> {
			ClientLevel level = context.client().level;
			if (level == null) {
				return;
			}
			if (level != shownIn) {
				LIVE.clear();
				shownIn = level;
			}
			if (LIVE.size() < MAX) {
				LIVE.add(new Live(payload, level.getGameTime()));
			}
		});
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (LIVE.isEmpty()) {
			return;
		}
		if (level == null || level != shownIn) {
			LIVE.clear();
			return;
		}
		float now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		LIVE.removeIf(live -> now - live.born() > LIFE);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Live live : LIVE) {
			ArsenalBlinks.Tear t = live.tear();
			float age = now - live.born();
			float open;
			if (age < OPEN_TICKS) {
				float x = Mth.clamp(age / OPEN_TICKS, 0.0F, 1.0F);
				open = 1.0F - (1.0F - x) * (1.0F - x);
			} else {
				open = Mth.clamp((LIFE - age) / CLOSE_TICKS, 0.0F, 1.0F);
			}
			if (open < 0.02F) {
				continue;
			}
			double rx = Math.max(MIN_RX, t.width() * 0.5 + WIDTH_PAD);
			double ry = Math.max(MIN_RY, t.height() * HEIGHT_SHARE);
			draw(context, camera, new Vec3(t.fx(), t.fy(), t.fz()), t.fromYaw(), rx, ry, open, now, t.voidRift());
			draw(context, camera, new Vec3(t.tx(), t.ty(), t.tz()), t.toYaw(), rx, ry, open, now, t.voidRift());
		}
	}

	private static void draw(LevelRenderContext context, Vec3 camera, Vec3 base, float yaw, double rx, double ry, float open, float now,
		boolean voidRift) {
		double rad = Math.toRadians(yaw);
		Vec3 normal = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		Vec3 right = up.cross(normal).normalize();
		// from a slit: full height first, then width (and back the same way as it closes)
		double w = rx * open * open;
		double h = ry * Math.min(1.0, open * 1.6);
		Vec3 at = base.add(0.0, ry, 0.0).subtract(camera);
		RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), at, right, up, normal, w, h, now * 0.06, voidRift);
	}
}
