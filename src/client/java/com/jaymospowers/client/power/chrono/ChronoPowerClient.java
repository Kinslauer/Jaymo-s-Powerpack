package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.ChronoEchoes;
import com.jaymospowers.power.chrono.ChronoPower;
import com.jaymospowers.power.chrono.ChronoSync;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Chronokinesis, client side: only the HUD. Four little clocks above the hotbar (one per ability, with the bound
 * key under each): the dark part of the dial is the cooldown left, sweeping away clockwise behind a golden hand.
 * A thin bar under a clock shows how long an active Stasis / Overclock / Bloom has left. Overclock adds a faint
 * golden vignette. Everything else (particles, sounds) comes from the server so other players see it too.
 */
public final class ChronoPowerClient implements PowerClient {
	public static final ChronoPowerClient INSTANCE = new ChronoPowerClient();

	private static final Identifier TEXTURE = JaymosPowers.id("textures/gui/chrono_hud.png");
	private static final int TEX_W = 64;
	private static final int TEX_H = 32;
	private static final int FACE = 18;
	/** Gap between dials. The row sits right of the hotbar, like the other powers' ability chips. */
	private static final int GAP = 2;
	private static final int OVERCLOCK = 2;
	/** Chrono Punch's chip: its glyph's place on the sheet, and its rim colours. */
	private static final int ULT_GLYPH_U = 40;
	private static final int ULT_RIM = 0xFF9FE6FF;
	private static final int ULT_RIM_DIM = 0xFF4A6878;
	private static final int ULT_BACK = 0xC0101828;

	// ---- Timeless (ascended) form: two clock hands circling the player, seen by everyone nearby ----
	/** Radius of the hands' circle, its height above the feet, and how far away other players' hands are drawn. */
	private static final double TIMELESS_RADIUS = 0.85;
	private static final double TIMELESS_HEIGHT = 1.05;
	private static final double TIMELESS_VIEW_DISTANCE = 32.0;
	/** Radians per tick of the minute and hour hands. */
	private static final double TIMELESS_MINUTE_SPEED = 0.16;
	private static final double TIMELESS_HOUR_SPEED = 0.035;
	/** A particle every this many ticks per hand. */
	private static final int TIMELESS_INTERVAL = 2;
	private static final DustParticleOptions MINUTE_HAND = new DustParticleOptions(0xFFE8A0, 0.55F);
	private static final DustParticleOptions HOUR_HAND = new DustParticleOptions(0xA8E8FF, 0.6F);

	private static final int GOLD = 0xFFF2C14E;
	private static final int HAND = 0xFFFFE08A;
	private static final int SHADE = 0xB0100A18;
	private static final int TEXT = 0xFFFFE6B0;
	private static final int TEXT_DIM = 0xFFA89C88;
	private static final int DENIED = 0xFFFF5A40;

	private final int[] cooldownLeft = new int[ChronoSync.ABILITIES];
	private final int[] cooldownTotal = new int[ChronoSync.ABILITIES];
	private final int[] activeLeft = new int[ChronoSync.ABILITIES];
	private final int[] activeTotal = new int[ChronoSync.ABILITIES];
	/** Ticks left of the red "not ready" flash and of the bright "ready" flash, per ability. */
	private final int[] deniedFlash = new int[ChronoSync.ABILITIES];
	private final int[] readyFlash = new int[ChronoSync.ABILITIES];
	private int ultimateLeft;
	private int ultimateTotal;
	private int ultimateDenied;
	private int ultimateReady;

	private ChronoPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(ChronoPower.ID, INSTANCE);
		ClientPlayNetworking.registerGlobalReceiver(ChronoSync.TYPE, (payload, ctx) -> INSTANCE.accept(payload));
		EntityRendererRegistry.register(ChronoEchoes.ECHO, ChronoEchoRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(ChronoPowerClient::timelessHands);
		ChronoFlowClient.init();
	}

	/** Every Timeless player in view gets two slow clock hands circling them (yours too, in third person). */
	private static void timelessHands(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || minecraft.player == null) {
			return;
		}
		long time = level.getGameTime();
		if (time % TIMELESS_INTERVAL != 0) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if (!Powers.has(player, ChronoPower.INSTANCE) || !Fate.isAscended(player) || player.isInvisible() || player.isSpectator()) {
				continue;
			}
			if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			if (player.distanceToSqr(minecraft.player) > TIMELESS_VIEW_DISTANCE * TIMELESS_VIEW_DISTANCE) {
				continue;
			}
			double t = time + player.getId() * 37.0;
			double y = player.getY() + TIMELESS_HEIGHT;
			double minute = t * TIMELESS_MINUTE_SPEED;
			double hour = t * TIMELESS_HOUR_SPEED;
			ChronoFx.particle(level, player, MINUTE_HAND, player.getX() + Math.sin(minute) * TIMELESS_RADIUS, y, player.getZ() - Math.cos(minute) * TIMELESS_RADIUS,
				0.0, 0.0, 0.0);
			ChronoFx.particle(level, player, HOUR_HAND, player.getX() + Math.sin(hour) * TIMELESS_RADIUS * 0.65, y + 0.02,
				player.getZ() - Math.cos(hour) * TIMELESS_RADIUS * 0.65, 0.0, 0.0, 0.0);
		}
	}

	private void accept(ChronoSync sync) {
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			cooldownLeft[i] = sync.cooldownLeft()[i];
			cooldownTotal[i] = Math.max(sync.cooldownTotal()[i], sync.cooldownLeft()[i]);
			activeLeft[i] = sync.activeLeft()[i];
			activeTotal[i] = Math.max(sync.activeTotal()[i], sync.activeLeft()[i]);
		}
		ultimateLeft = sync.ultimateLeft();
		ultimateTotal = Math.max(sync.ultimateTotal(), sync.ultimateLeft());
	}

	private void reset() {
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			cooldownLeft[i] = cooldownTotal[i] = activeLeft[i] = activeTotal[i] = deniedFlash[i] = readyFlash[i] = 0;
		}
		ultimateLeft = ultimateTotal = ultimateDenied = ultimateReady = 0;
		ChronoSpeedClient.reset();
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		reset();
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		ChronoSpeedClient.tick(player, activeLeft[OVERCLOCK] > 0);
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			if (cooldownLeft[i] > 0 && --cooldownLeft[i] == 0) {
				readyFlash[i] = 8;
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F + i * 0.1F, 0.5F));
			}
			if (activeLeft[i] > 0) {
				activeLeft[i]--;
			}
			if (deniedFlash[i] > 0) {
				deniedFlash[i]--;
			}
			if (readyFlash[i] > 0) {
				readyFlash[i]--;
			}
		}
		if (ultimateLeft > 0 && --ultimateLeft == 0) {
			ultimateReady = 8;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.5F));
		}
		if (ultimateDenied > 0) {
			ultimateDenied--;
		}
		if (ultimateReady > 0) {
			ultimateReady--;
		}
	}

	@Override
	public void onUltimate(LocalPlayer player, boolean down) {
		if (down && ultimateLeft > 0) {
			ultimateDenied = 6;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.COMPARATOR_CLICK, 0.6F, 0.5F));
		}
	}

	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		int i = key.ordinal();
		if (down && cooldownLeft[i] > 0 && !(i == OVERCLOCK && activeLeft[OVERCLOCK] > 0)) {
			deniedFlash[i] = 6;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.COMPARATOR_CLICK, 0.6F, 0.5F));
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		ChronoFlowClient.renderHud(graphics, player);
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		if (activeLeft[OVERCLOCK] > 0) {
			vignette(graphics, width, height, activeLeft[OVERCLOCK] - partial, activeTotal[OVERCLOCK], player.tickCount + partial);
		}

		// right of the hotbar, past the off-hand slot / attack indicator; above the hunger bar on narrow screens
		boolean ascended = Fate.isAscended(player);
		int chips = ChronoSync.ABILITIES + (ascended ? 1 : 0);
		int total = chips * FACE + (chips - 1) * GAP;
		int x0 = width / 2 + 91 + 30;
		int top = height - FACE - 1;
		if (x0 + total > width - 2) {
			x0 = width / 2 + 91 - total;
			top = height - 22 - 30 - FACE;
		}
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			drawClock(graphics, font, i, x0 + i * (FACE + GAP), top, partial);
		}
		if (ascended) {
			drawParadox(graphics, font, x0 + ChronoSync.ABILITIES * (FACE + GAP), top, partial);
		}
	}

	/** Chrono Punch's chip: a square (not a dial, it's not one of the four), pale cyan rim, the C key in the corner. */
	private void drawParadox(GuiGraphicsExtractor graphics, Font font, int fx, int fy, float partial) {
		boolean cooling = ultimateLeft > 0;
		graphics.fill(fx, fy, fx + FACE, fy + FACE, ULT_BACK);
		graphics.outline(fx, fy, FACE, FACE, cooling ? ULT_RIM_DIM : ULT_RIM);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 4, fy + 4, ULT_GLYPH_U, FACE, 10, 10, TEX_W, TEX_H,
			cooling ? 0xFF7A7068 : 0xFFFFFFFF);
		if (cooling && ultimateTotal > 0) {
			float left = Mth.clamp((ultimateLeft - partial) / ultimateTotal, 0.0F, 1.0F);
			// the part still to go drains downwards
			int shade = Math.round((FACE - 2) * left);
			graphics.fill(fx + 1, fy + 1 + (FACE - 2 - shade), fx + FACE - 1, fy + FACE - 1, SHADE);
			graphics.centeredText(font, Integer.toString(Mth.ceil(ultimateLeft / 20.0F)), fx + FACE / 2, fy + 5, TEXT);
		}
		if (ultimateReady > 0) {
			int a = ultimateReady * 0x18;
			graphics.outline(fx - 1, fy - 1, FACE + 2, FACE + 2, (a << 24) | 0x9FE6FF);
		}
		if (ultimateDenied > 0) {
			graphics.outline(fx - 1, fy - 1, FACE + 2, FACE + 2, DENIED);
		}
		String key = font.plainSubstrByWidth(JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(), FACE - 2);
		graphics.text(font, key, fx + FACE - font.width(key), fy + FACE - 7, cooling ? TEXT_DIM : TEXT);
	}

	private void drawClock(GuiGraphicsExtractor graphics, Font font, int i, int fx, int fy, float partial) {
		if (!JaymosPowersClient.isUnlocked(PowerKey.values()[i])) {
			// not mastered yet: an empty dark slot with just the key
			graphics.fill(fx, fy, fx + FACE, fy + FACE, 0xB0000000);
			graphics.outline(fx, fy, FACE, FACE, 0x60505050);
			String key = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(PowerKey.values()[i]).getTranslatedKeyMessage().getString(), FACE - 2);
			graphics.text(font, key, fx + FACE - font.width(key), fy + FACE - 7, 0xFF505050);
			return;
		}
		boolean active = activeLeft[i] > 0;
		boolean cooling = cooldownLeft[i] > 0;

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx, fy, active ? FACE : 0, 0, FACE, FACE, TEX_W, TEX_H);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, fx + 4, fy + 4, i * 10, FACE, 10, 10, TEX_W, TEX_H,
			cooling && !active ? 0xFF7A7068 : 0xFFFFFFFF);

		if (cooling && cooldownTotal[i] > 0) {
			float left = Mth.clamp((cooldownLeft[i] - partial) / cooldownTotal[i], 0.0F, 1.0F);
			float elapsed = 1.0F - left;
			shadePie(graphics, fx, fy, elapsed);
			hand(graphics, fx, fy, elapsed);
			if (!active) {
				String secs = Integer.toString(Mth.ceil(cooldownLeft[i] / 20.0F));
				graphics.centeredText(font, secs, fx + FACE / 2, fy + 5, TEXT);
			}
		}
		if (readyFlash[i] > 0) {
			int a = readyFlash[i] * 0x18;
			graphics.outline(fx - 1, fy - 1, FACE + 2, FACE + 2, (a << 24) | 0xFFE08A);
		}
		if (deniedFlash[i] > 0) {
			graphics.outline(fx - 1, fy - 1, FACE + 2, FACE + 2, DENIED);
		}

		// how long an active ability has left: a bar just above its dial
		if (active && activeTotal[i] > 0) {
			float frac = Mth.clamp((activeLeft[i] - partial) / activeTotal[i], 0.0F, 1.0F);
			graphics.fill(fx, fy - 3, fx + FACE, fy - 1, 0x90000000);
			graphics.fill(fx, fy - 3, fx + Math.round(FACE * frac), fy - 1, GOLD);
		}

		// the bound key, bottom-right like an item count
		String key = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(PowerKey.values()[i]).getTranslatedKeyMessage().getString(), FACE - 2);
		graphics.text(font, key, fx + FACE - font.width(key), fy + FACE - 7, cooling ? TEXT_DIM : TEXT);
	}

	/** Darkens the part of the dial still on cooldown: from the hand ({@code elapsed}) clockwise round to 12. */
	private static void shadePie(GuiGraphicsExtractor graphics, int fx, int fy, float elapsed) {
		for (int py = 0; py < FACE; py++) {
			int runStart = -1;
			for (int px = 0; px <= FACE; px++) {
				boolean dark = false;
				if (px < FACE) {
					double dx = px + 0.5 - FACE / 2.0;
					double dy = py + 0.5 - FACE / 2.0;
					if (dx * dx + dy * dy <= 7.0 * 7.0) {
						double angle = Math.atan2(dx, -dy) / (Math.PI * 2.0);
						if (angle < 0) {
							angle += 1.0;
						}
						dark = angle >= elapsed;
					}
				}
				if (dark && runStart < 0) {
					runStart = px;
				} else if (!dark && runStart >= 0) {
					graphics.fill(fx + runStart, fy + py, fx + px, fy + py + 1, SHADE);
					runStart = -1;
				}
			}
		}
	}

	/** The clock hand, from the centre out at {@code elapsed} of a turn. */
	private static void hand(GuiGraphicsExtractor graphics, int fx, int fy, float elapsed) {
		double a = elapsed * Math.PI * 2.0;
		double sx = Math.sin(a);
		double sy = -Math.cos(a);
		for (int r = 0; r <= 6; r++) {
			int px = fx + (int) Math.floor(FACE / 2.0 + sx * r);
			int py = fy + (int) Math.floor(FACE / 2.0 + sy * r);
			graphics.fill(px, py, px + 1, py + 1, r == 0 ? GOLD : HAND);
		}
	}

	/** A faint, pulsing golden frame while overclocked. Fades in and out over half a second. */
	private static void vignette(GuiGraphicsExtractor graphics, int width, int height, float left, int total, float time) {
		float fade = Mth.clamp(left / 10.0F, 0.0F, 1.0F) * Mth.clamp((total - left) / 10.0F, 0.0F, 1.0F);
		float pulse = 0.75F + 0.25F * Mth.sin(time * 0.3F);
		int alpha = (int) (0x48 * fade * pulse);
		if (alpha <= 0) {
			return;
		}
		int color = (alpha << 24) | 0xF2C14E;
		int band = Math.max(12, height / 10);
		graphics.fillGradient(0, 0, width, band, color, 0x00F2C14E);
		graphics.fillGradient(0, height - band, width, height, 0x00F2C14E, color);
		for (int s = 0; s < 4; s++) {
			int a = alpha * (4 - s) / 5;
			int c = (a << 24) | 0xF2C14E;
			int w = 3;
			graphics.fill(s * w, 0, s * w + w, height, c);
			graphics.fill(width - s * w - w, 0, width - s * w, height, c);
		}
	}
}
