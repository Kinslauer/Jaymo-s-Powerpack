package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.ChronoTimeStopSync;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * A Chrono's Time Stop on every client ({@link com.jaymospowers.power.chrono.ChronoTimeStop}): the world itself is held
 * by vanilla's tick freeze (entities, particles and animations stop mid-motion on their own); this adds the look and
 * the sound. Everyone sees the world drained grey (held players a cold blue-grey and darker at the edges, the Chrono a
 * lighter grey), hears a clock tick... tock... through it, and gets a bright flash and a chime when time resumes. Held
 * players' movement keys do nothing meanwhile ({@code ChronoStunInputMixin}).
 */
public final class ChronoTimeStopClient {
	/** The tint: held players, and the Chrono who stopped time. */
	private static final int HELD_RGB = 0x50607A, OWNER_RGB = 0x8A8A8A;
	private static final int HELD_ALPHA = 120, OWNER_ALPHA = 70;
	/** The darker edge on held players' screens: alpha at the rim, and how far in it reaches (share of the screen). */
	private static final int EDGE_ALPHA = 90;
	private static final float EDGE_DEPTH = 0.18F;
	private static final int EDGE_STEPS = 8;
	/** The tint fades in over this many ticks; the release flash fades over this many. */
	private static final int FADE_IN = 6, FLASH = 12;
	/** The clock ticks this often. */
	private static final int TICK_EVERY = 10;
	/** With no end packet, the stop is dropped this long after it should have run out (a lost packet can't hold anyone). */
	private static final int GRACE = 40;

	private static int owner = -1;
	private static int ticksLeft;
	private static int age;
	private static int flash;

	private ChronoTimeStopClient() {
	}

	/** Called once from {@link ChronoPowerClient#init()}. */
	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ChronoTimeStopSync.TYPE, (payload, ctx) -> accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			owner = -1;
			ticksLeft = age = flash = 0;
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, JaymosPowers.id("chrono_time_stop"), (graphics, delta) -> render(graphics));
	}

	/** Whether time stands still right now (on this client). */
	public static boolean active() {
		return owner >= 0;
	}

	/** Whether the local player is held by a stopped world: anyone but the Chrono who stopped it. */
	public static boolean held() {
		LocalPlayer player = Minecraft.getInstance().player;
		return active() && player != null && player.getId() != owner && !player.isSpectator();
	}

	private static void accept(ChronoTimeStopSync sync) {
		if (sync.ticksLeft() > 0) {
			if (!active()) {
				age = 0;
				play(SoundEvents.BELL_RESONATE, 0.9F, 0.5F);
			}
			owner = sync.owner();
			ticksLeft = sync.ticksLeft();
		} else if (active()) {
			release();
		}
	}

	private static void release() {
		owner = -1;
		ticksLeft = 0;
		flash = FLASH;
		play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
	}

	private static void tick() {
		if (flash > 0) {
			flash--;
		}
		if (!active()) {
			return;
		}
		age++;
		ticksLeft--;
		if (ticksLeft < -GRACE || Minecraft.getInstance().player == null) {
			release();
			return;
		}
		if (age % TICK_EVERY == 0) {
			// tick... tock...
			boolean tock = (age / TICK_EVERY) % 2 == 0;
			play(SoundEvents.COMPARATOR_CLICK, 0.8F, tock ? 0.9F : 1.3F);
		}
	}

	private static void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private static void render(GuiGraphicsExtractor graphics) {
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		if (active()) {
			float in = Mth.clamp(age / (float) FADE_IN, 0.0F, 1.0F);
			boolean held = held();
			int alpha = Math.round((held ? HELD_ALPHA : OWNER_ALPHA) * in);
			graphics.fill(0, 0, width, height, ARGB.color(alpha, held ? HELD_RGB : OWNER_RGB));
			if (held) {
				// darker toward the edges, closing in
				int depth = Math.round(Math.min(width, height) * EDGE_DEPTH);
				int step = Math.max(1, depth / EDGE_STEPS);
				for (int i = 0; i < EDGE_STEPS; i++) {
					int o = i * step;
					int a = Math.round(EDGE_ALPHA * in * (1.0F - i / (float) EDGE_STEPS) / EDGE_STEPS * 2.0F);
					int c = ARGB.color(a, 0x101820);
					graphics.fill(o, o, width - o, o + step, c);
					graphics.fill(o, height - o - step, width - o, height - o, c);
					graphics.fill(o, o + step, o + step, height - o - step, c);
					graphics.fill(width - o - step, o + step, width - o, height - o - step, c);
				}
			}
		}
		if (flash > 0) {
			int a = Math.round(170 * flash / (float) FLASH);
			graphics.fill(0, 0, width, height, ARGB.color(a, 0xFFF4D8));
		}
	}
}
