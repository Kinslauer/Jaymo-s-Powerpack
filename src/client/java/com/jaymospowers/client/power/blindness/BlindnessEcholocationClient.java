package com.jaymospowers.client.power.blindness;

import com.jaymospowers.power.blindness.BlindnessEcho;
import com.jaymospowers.power.blindness.BlindnessEcholocation;
import com.jaymospowers.power.resonance.ResonancePower;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Sound, drawn on the dark. Each noise the server sends ({@link BlindnessEcho}) is a ring that opens on the screen at
 * the place it came from: where you'd see its source if you could see, sized by how near and how loud it was, and
 * fading as it widens. A noise behind you or off to the side shows as its ring pressed against that edge of the view.
 * Plain noises are pale; one made on purpose (a Pulse, a Shriek) is Resonance's violet.
 */
public final class BlindnessEcholocationClient {
	/** How long a ring lasts (ticks), the most at once, and the dots a ring is drawn with. */
	static final int RING_TICKS = 24, MAX_RINGS = 24, DOTS = 40;
	/** How wide (blocks) a ring from the quietest and the loudest noise opens, and the least it shows on screen (pixels). */
	static final float QUIET_RADIUS = 0.6F, LOUD_RADIUS = 2.6F, MIN_PIXELS = 3.0F;
	static final int PLAIN = 0xCFE8FF;
	/** How far in from the edge (pixels) a noise that's out of view is pinned. */
	static final int EDGE = 14;

	private static final List<Ring> RINGS = new ArrayList<>();

	private static final class Ring {
		final Vec3 origin;
		final float volume;
		final int color;
		int age;

		Ring(Vec3 origin, float volume, int color) {
			this.origin = origin;
			this.volume = volume;
			this.color = color;
		}
	}

	private BlindnessEcholocationClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
	}

	public static void onEcho(BlindnessEcho payload) {
		boolean made = BlindnessEcholocation.PULSE.equals(payload.soundId());
		RINGS.add(new Ring(new Vec3(payload.x(), payload.y(), payload.z()), Mth.clamp(payload.volume(), 0.1F, 1.0F), made ? ResonancePower.COLOR : PLAIN));
		if (RINGS.size() > MAX_RINGS) {
			RINGS.remove(0);
		}
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null) {
			RINGS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		RINGS.removeIf(ring -> ++ring.age >= RING_TICKS);
	}

	/** The rings, each where its noise came from. */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || RINGS.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		Vec3 eye = player.getEyePosition(partial);
		Vec3 forward = player.getViewVector(partial);
		Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getViewYRot(partial) + 90.0F) : right.normalize();
		Vec3 up = right.cross(forward).normalize();
		// pixels one block spans at one block away
		float scale = h * 0.5F / (float) Math.tan(Math.toRadians(minecraft.options.fov().get() * 0.5));
		for (Ring ring : RINGS) {
			float life = (ring.age + partial) / RING_TICKS;
			Vec3 to = ring.origin.subtract(eye);
			double depth = to.dot(forward);
			double sx = to.dot(right);
			double sy = to.dot(up);
			float cx;
			float cy;
			float near = (float) Math.max(1.0, to.length());
			if (depth > 0.2) {
				cx = w * 0.5F + (float) (sx / depth) * scale;
				cy = h * 0.5F - (float) (sy / depth) * scale;
			} else {
				// behind: thrown out past the edge on the side it's on
				double len = Math.max(1.0E-4, Math.sqrt(sx * sx + sy * sy));
				cx = w * 0.5F + (float) (sx / len) * w;
				cy = h * 0.5F - (float) (sy / len) * h;
			}
			cx = Mth.clamp(cx, EDGE, w - EDGE);
			cy = Mth.clamp(cy, EDGE, h - EDGE);
			float blocks = Mth.lerp(ring.volume, QUIET_RADIUS, LOUD_RADIUS) * (0.25F + 0.75F * life);
			float radius = Math.max(MIN_PIXELS, blocks / near * scale);
			int alpha = (int) (255 * (1.0F - life) * (1.0F - life) * (0.45F + 0.55F * ring.volume));
			if (alpha <= 4) {
				continue;
			}
			int color = alpha << 24 | ring.color;
			int dots = Mth.clamp((int) (radius * 0.9F), 10, DOTS);
			for (int i = 0; i < dots; i++) {
				float angle = Mth.TWO_PI * i / dots;
				int x = Math.round(cx + radius * Mth.cos(angle));
				int y = Math.round(cy + radius * Mth.sin(angle));
				graphics.fill(x, y, x + 1, y + 1, color);
			}
		}
	}
}
