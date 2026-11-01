package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.spirits.SpiritBarrage;
import com.jaymoskingdoms.spirits.SpiritBarrage.Barrage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.467) Your side of a barrage clash ({@link SpiritBarrage}). The spirit key's presses are read every frame, not every
 * tick, and timed against the beats on your own clock (the game time and the frame's part of a tick), so a tap is judged to
 * the hundredth of a tick and lag doesn't move the beat ({@link SpiritBarrage.Tap}). The drum track ({@link #THEME}) starts
 * with the barrage; its beats are the barrage's. When the winner's flurry breaks through, the rush plays ({@link #RUSH}); a
 * draw ends on a crash ({@link #DRAW}). Over the film's tug bar ({@link ClashCinematic}) it draws the beat: a square closing on
 * your fist, which flashes on the drum, how your last tap was timed, and what came of it.
 */
public final class BarrageClient {
	/** The drum track, the flurry breaking through, and a draw's crash (assets/jaymoskingdoms/sounds/spirit/). */
	static final SoundEvent THEME = SoundEvent.createVariableRangeEvent(JaymosKingdoms.id("spirit.barrage_theme")),
		RUSH = SoundEvent.createVariableRangeEvent(JaymosKingdoms.id("spirit.barrage_rush")),
		DRAW = SoundEvent.createVariableRangeEvent(JaymosKingdoms.id("spirit.barrage_draw"));
	/** The music is started only if the barrage is seen this soon after it began (ticks); later it would be off the beat. */
	static final int MUSIC_LATE = 10;
	/** The beat square: the fist's size, how far out the closing square starts (pixels a tick before the beat), its lift above the tug bar. */
	static final int FIST = 10, CLOSE_PER_TICK = 4, LIFT = 44;
	/** How long (ticks) a tap's timing and a beat's outcome stay up. */
	static final float SHOW_TICKS = 14.0F;

	private static boolean wasDown;
	/** The game time a frame last read the key (the tick reads it when no frame has). */
	private static long polledAt = -1L;
	/** The barrage the music and the sting were started for (its start, and its rush). */
	private static long musicFor = -1L, rushFor = -1L, drawFor = -1L;
	/** Your last tap: when (game time, with the tick's part), and how it was timed (a {@code message.jaymoskingdoms.spirit.barrage.*} key). */
	private static double tappedAt = -100.0;
	private static String tapped = "";
	/** The last outcome the server sent for your side, and when it came. */
	private static int feed = 0;
	private static double fedAt = -100.0;

	private BarrageClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BarrageClient::tick);
		HudElementRegistry.addLast(JaymosKingdoms.id("spirit_barrage_key"), (HudElement)(graphics, delta) -> poll(Minecraft.getInstance(), delta));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			wasDown = false;
			musicFor = -1L;
			rushFor = -1L;
			drawFor = -1L;
			feed = 0;
		});
	}

	/** Your barrage, if you are in one (counting the flurry and the flash). */
	private static @Nullable Barrage mine(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		return self == null || minecraft.level == null ? null : self.getAttached(SpiritBarrage.BARRAGE);
	}

	/** Whether you are in a barrage that still takes taps (the spirit key beats time, and casts nothing). */
	public static boolean active() {
		Barrage bar = mine(Minecraft.getInstance());
		return bar != null && !bar.over() && bar.rush() == 0L;
	}

	/** Whether the drum track should go on: the barrage is running and not yet decided. */
	static boolean musicOn() {
		Barrage bar = mine(Minecraft.getInstance());
		return bar != null && !bar.over() && bar.rush() == 0L && bar.start() == musicFor;
	}

	private static void tick(Minecraft minecraft) {
		Barrage bar = mine(minecraft);
		if (bar == null || minecraft.level == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		if (!bar.over() && bar.start() != musicFor) {
			musicFor = bar.start();
			feed = 0;
			if (now - bar.start() <= MUSIC_LATE) {
				minecraft.getSoundManager().play(new Music(THEME, true));
			}
		}
		if (bar.rush() != 0L && bar.rush() != rushFor) {
			rushFor = bar.rush();
			minecraft.getSoundManager().play(new Music(RUSH, false));
		}
		if (bar.over() && bar.winner() == 0 && bar.end() != drawFor) {
			drawFor = bar.end();
			minecraft.getSoundManager().play(new Music(DRAW, false));
		}
		LocalPlayer self = minecraft.player;
		int mineFeed = self != null && bar.a() == self.getId() ? bar.feedA() : bar.feedB();
		if (mineFeed != feed) {
			feed = mineFeed;
			fedAt = now;
		}
		if (polledAt < now - 1L) {
			// (no frame read the key: the HUD is hidden)
			poll(minecraft, null);
		}
	}

	/** Reads the spirit key (each frame, from the HUD; each tick when the HUD isn't drawn): a fresh press is a tap. */
	private static void poll(Minecraft minecraft, @Nullable DeltaTracker delta) {
		if (minecraft.level == null || minecraft.player == null || SpiritKey.mapping() == null) {
			return;
		}
		polledAt = minecraft.level.getGameTime();
		boolean down = SpiritKey.mapping().isDown() && minecraft.gui.screen() == null;
		if (down && !wasDown && active()) {
			float partial = delta == null ? 0.0F : delta.getGameTimeDeltaPartialTick(false);
			tap(minecraft, minecraft.player, minecraft.level.getGameTime() + partial);
		}
		wasDown = down;
	}

	private static void tap(Minecraft minecraft, LocalPlayer self, double now) {
		Barrage bar = mine(minecraft);
		if (bar == null) {
			return;
		}
		double t = now - bar.start();
		int k = nearest(t);
		double off = t - SpiritBarrage.BEATS[k];
		double abs = Math.abs(off);
		tappedAt = now;
		tapped = abs <= SpiritBarrage.PERFECT ? "perfect" : abs <= SpiritBarrage.GOOD ? "good" : off < 0.0 ? "early" : "late";
		if (ClientPlayNetworking.canSend(SpiritBarrage.Tap.TYPE)) {
			ClientPlayNetworking.send(new SpiritBarrage.Tap(k, (int)Math.round(off * 100.0), self.isShiftKeyDown()));
		}
	}

	/** The beat nearest {@code t} ticks into the barrage. */
	private static int nearest(double t) {
		int best = 0;
		for (int k = 1; k < SpiritBarrage.BEATS.length; k++) {
			if (Math.abs(t - SpiritBarrage.BEATS[k]) < Math.abs(t - SpiritBarrage.BEATS[best])) {
				best = k;
			}
		}
		return best;
	}

	/** The beat after {@code t} (or the last). */
	private static int next(double t) {
		for (int k = 0; k < SpiritBarrage.BEATS.length; k++) {
			if (SpiritBarrage.BEATS[k] + SpiritBarrage.GOOD >= t) {
				return k;
			}
		}
		return SpiritBarrage.BEATS.length - 1;
	}

	/** Over the tug bar (whose top is {@code tugTop}): the beat, your tap, the outcome, the hint. From {@link ClashCinematic}. */
	static void hud(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer self, int width, int tugTop) {
		Barrage bar = mine(minecraft);
		if (bar == null || minecraft.level == null) {
			return;
		}
		Font font = minecraft.font;
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double now = minecraft.level.getGameTime() + partial;
		double t = now - bar.start();
		boolean first = bar.a() == self.getId();
		int color = ARGB.opaque(first ? bar.colorA() : bar.colorB());
		int cx = width / 2, cy = tugTop - LIFT;
		if (bar.rush() != 0L) {
			boolean won = bar.winner() == (first ? 1 : 2);
			Component line = Component.translatable(won ? "message.jaymoskingdoms.spirit.barrage.break_yours" : "message.jaymoskingdoms.spirit.barrage.break_theirs");
			graphics.text(font, line, cx - font.width(line) / 2, cy - 4, won ? 0xFFFFD34D : 0xFFFF5A5A, true);
			return;
		}
		int k = next(t);
		double to = SpiritBarrage.BEATS[k] - t;
		double gap = k == 0 ? SpiritBarrage.LEAD_IN : SpiritBarrage.BEATS[k] - SpiritBarrage.BEATS[k - 1];
		// the fist, flashing white on the drum
		double since = t - SpiritBarrage.BEATS[nearest(t)];
		boolean onBeat = Math.abs(since) <= 1.0;
		int fist = onBeat ? 0xFFFFFFFF : color;
		graphics.fill(cx - FIST / 2, cy - FIST / 2, cx + FIST / 2, cy + FIST / 2, fist);
		// the square closing on it
		if (to > -SpiritBarrage.GOOD && to <= gap) {
			int half = FIST / 2 + (int)Math.round(Math.max(0.0, to) * CLOSE_PER_TICK);
			int alpha = (int)(255 * Math.clamp(1.0 - to / gap * 0.6, 0.3, 1.0));
			outline(graphics, cx, cy, half, ARGB.color(alpha, 255, 255, 255));
		}
		// the good window, faintly
		outline(graphics, cx, cy, FIST / 2 + (int)Math.round(SpiritBarrage.GOOD * CLOSE_PER_TICK), 0x55FFFFFF);
		if (t < SpiritBarrage.LEAD_IN - SpiritBarrage.GOOD) {
			Component ready = Component.translatable("message.jaymoskingdoms.spirit.barrage.ready");
			graphics.text(font, ready, cx - font.width(ready) / 2, cy - FIST - 14, 0xFFFFFFFF, true);
		}
		// your tap's timing, beside the fist
		double age = now - tappedAt;
		if (age < SHOW_TICKS && !tapped.isEmpty()) {
			int c = switch (tapped) {
				case "perfect" -> 0xFFFFD34D;
				case "good" -> 0xFFFFFFFF;
				default -> 0xFFFF5A5A;
			};
			Component line = Component.translatable("message.jaymoskingdoms.spirit.barrage." + tapped);
			graphics.text(font, line, cx + FIST + 8, cy - 4, fade(c, age), true);
		}
		// what came of the last beat
		double fed = now - fedAt;
		int code = feed & 15;
		if (fed < SHOW_TICKS * 1.5 && code != 0) {
			String key = switch (code) {
				case SpiritBarrage.FEED_PERFECT, SpiritBarrage.FEED_GOOD -> "landed";
				case SpiritBarrage.FEED_MISS -> "missed";
				case SpiritBarrage.FEED_STAGGER -> "staggered";
				case SpiritBarrage.FEED_SLIPPED -> "slipped";
				case SpiritBarrage.FEED_CAUGHT -> "caught";
				default -> "wasted";
			};
			int c = switch (code) {
				case SpiritBarrage.FEED_PERFECT, SpiritBarrage.FEED_GOOD -> 0xFFB8FFB0;
				case SpiritBarrage.FEED_SLIPPED -> 0xFF7FE8FF;
				case SpiritBarrage.FEED_STAGGER, SpiritBarrage.FEED_CAUGHT -> 0xFFFF7A5A;
				default -> 0xFFBBBBBB;
			};
			Component line = Component.translatable("message.jaymoskingdoms.spirit.barrage." + key);
			graphics.text(font, line, cx - FIST - 8 - font.width(line), cy - 4, fade(c, fed / 1.5), true);
		}
		Component key = SpiritKey.mapping() == null ? Component.literal("L") : SpiritKey.mapping().getTranslatedKeyMessage();
		Component hint = Component.translatable("message.jaymoskingdoms.spirit.barrage.hint", key);
		graphics.text(font, hint, (width - font.width(hint)) / 2, tugTop - 16, 0xFFFFFFFF, true);
	}

	private static int fade(int color, double age) {
		int alpha = (int)(255 * Math.clamp(1.0 - age / SHOW_TICKS, 0.0, 1.0));
		return ARGB.color(Math.max(alpha, 8), ARGB.red(color), ARGB.green(color), ARGB.blue(color));
	}

	private static void outline(GuiGraphicsExtractor graphics, int cx, int cy, int half, int color) {
		graphics.fill(cx - half, cy - half, cx + half, cy - half + 1, color);
		graphics.fill(cx - half, cy + half - 1, cx + half, cy + half, color);
		graphics.fill(cx - half, cy - half, cx - half + 1, cy + half, color);
		graphics.fill(cx + half - 1, cy - half, cx + half, cy + half, color);
	}

	/** The drum track (stopped when the barrage is decided or ends), or a sting (played out). Heard everywhere, on the jukebox volume (the music volume is often off, and the drum is the clock). */
	private static final class Music extends AbstractTickableSoundInstance {
		private final boolean theme;

		Music(SoundEvent sound, boolean theme) {
			super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			this.theme = theme;
			this.volume = 1.0F;
			this.pitch = 1.0F;
			this.looping = false;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0.0;
			this.y = 0.0;
			this.z = 0.0;
		}

		@Override
		public void tick() {
			if (this.theme && !musicOn()) {
				this.stop();
			}
		}
	}
}
