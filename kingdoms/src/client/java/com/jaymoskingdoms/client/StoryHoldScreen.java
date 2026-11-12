package com.jaymoskingdoms.client;

import com.jaymoskingdoms.StoryGround;
import com.jaymoskingdoms.Voices;
import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.236) Andy: "can the player be held in a stasis menu or faux load screen at the beginning while everything
 * loads?" A loading screen that can't be shut, up while the server holds a newcomer ({@link StoryGround.Hold}): their
 * realm's capital being raised, a whaler being fitted out, the future being made, the land where they woke loading.
 * MCA's creator waits behind it (it opens only on an empty screen), and the life story replaces it when it's asked.
 */
final class StoryHoldScreen extends Screen {
	/** Each dot of the "..." this long (ms), and the bar's sweep this long. */
	private static final long DOT_MS = 400L, SWEEP_MS = 1800L;
	private static final int BAR_W = 160, BAR_H = 4, BACKGROUND = 0xFF0C0C12, BAR_BACK = 0xFF2A2A34;
	/**
	 * (0.7.360) Andy: "i dont like when the narrator reads exactly what's on screen. it can keep the headline, but the white
	 * text should cycle, with a series of a minimum 40 lines hinting at how different game mechanics work. The narrator can
	 * continue saying what it does now". So under the title a hint ({@code gui.jaymoskingdoms.story.hint.<1..HINTS>}) shows
	 * for HINT_MS each, from a random one on. Widest a hint may run.
	 */
	private static final int HINTS = 151, HINT_W = 320;
	private static final long HINT_MS = 7000L;

	/** What the server holds this client for (a lang key), or "" for nothing. */
	private static String holding = "";
	/**
	 * Andy: "it should keep them in the loading screen so they can read the tips for as long as it takes to load, rather
	 * than getting teleported". Let go by the server, the screen still stays up till the land round where they were put has
	 * come in and been drawn (or LAND_GIVE_UP_MS has passed): whether that's being waited on, and since when (ms).
	 */
	private static boolean landing;
	private static long landingFrom;
	/** Chunks round the player (each way) that must have come in, how long the screen waits on them at most, and a last beat once they have (ms). */
	static final int LAND_CHUNKS = 2;
	static final long LAND_GIVE_UP_MS = 30000L, LAND_BEAT_MS = 500L;
	private static long landedAt = -1L;
	/**
	 * (0.7.352) The narrator reads the screen ({@link Voices}): its title once (0.7.400: only that). What it began
	 * reading ("" before it has), the line being read, those still to read, and when the line began (ms).
	 */
	private static String read = "";
	private static @Nullable SimpleSoundInstance reading;
	private static final ArrayDeque<String> toRead = new ArrayDeque<>();
	private static long readFrom;
	/** A sound only counts as playing once the engine has picked it up. */
	private static final long READ_START_MS = 500L;

	/**
	 * (0.7.499) Andy: "the hints on loading creen should be randomized". Hints come off a shuffled deck that lasts across
	 * loading screens: every hint shows once, in random order, before any repeats, then the deck is shuffled again (never
	 * starting on the hint just shown).
	 */
	private static final int[] deck = new int[HINTS];
	private static int dealt = HINTS, lastHint = -1;

	/** The hint on screen (1..HINTS), the HINT_MS slot it was dealt for, and when the screen opened (ms). */
	private int hint;
	private long hintSlot = -1L;
	private final long opened = System.currentTimeMillis();

	private StoryHoldScreen() {
		super(Component.translatable("gui.jaymoskingdoms.story.hold.title"));
	}

	/** The next hint off the deck (1..HINTS), reshuffling once every hint has been shown. */
	private static int dealHint() {
		if (dealt >= HINTS) {
			java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
			for (int i = 0; i < HINTS; i++) {
				deck[i] = i + 1;
			}
			for (int i = HINTS - 1; i > 0; i--) {
				int j = random.nextInt(i + 1), swap = deck[i];
				deck[i] = deck[j];
				deck[j] = swap;
			}
			if (deck[0] == lastHint) {
				int j = 1 + random.nextInt(HINTS - 1), swap = deck[0];
				deck[0] = deck[j];
				deck[j] = swap;
			}
			dealt = 0;
		}
		lastHint = deck[dealt++];
		return lastHint;
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StoryGround.Hold.TYPE, (payload, context) -> {
			holding = payload.key();
			Minecraft minecraft = context.client();
			if (!holding.isEmpty()) {
				StoryCurtain.raise();
				landing = false;
			}
			if (holding.isEmpty()) {
				if (minecraft.gui.screen() instanceof StoryHoldScreen) {
					// let go: up till the land they were put in is in and drawn
					landing = true;
					landingFrom = System.currentTimeMillis();
					landedAt = -1L;
					StoryCurtain.raise();
				} else {
					hush(minecraft);
				}
			} else if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new StoryHoldScreen());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (landing) {
				long now = System.currentTimeMillis();
				net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
				if (screen != null && !(screen instanceof StoryHoldScreen)) {
					// something else took its place (the life story, MCA's creator): that holds them now
					landing = false;
				} else if (minecraft.player == null || now - landingFrom > LAND_GIVE_UP_MS) {
					land(minecraft);
				} else if (landed(minecraft)) {
					if (landedAt < 0L) {
						landedAt = now;
					} else if (now - landedAt >= LAND_BEAT_MS) {
						land(minecraft);
					}
				} else {
					landedAt = -1L;
				}
			}
			// (up again if anything took its place and went, while they're still held)
			if (holding() && minecraft.player != null && minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new StoryHoldScreen());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			holding = "";
			landing = false;
			hush(minecraft);
		});
	}

	/** The wait on the land is over: the screen comes down. */
	private static void land(Minecraft minecraft) {
		landing = false;
		hush(minecraft);
		if (minecraft.gui.screen() instanceof StoryHoldScreen) {
			minecraft.gui.setScreen(null);
		}
	}

	/**
	 * Whether the land round the player has come in (the chunks round them) and the ground where they stand is drawn, as
	 * vanilla's own loading screen waits on. Out of the world's height, dead or spectating, there's nothing to wait on.
	 */
	private static boolean landed(Minecraft minecraft) {
		var player = minecraft.player;
		var level = minecraft.level;
		if (player == null || level == null) {
			return true;
		}
		net.minecraft.core.BlockPos pos = player.blockPosition();
		if (level.isOutsideBuildHeight(pos.getY()) || !player.isAlive() || player.isSpectator()) {
			return true;
		}
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
		for (int dx = -LAND_CHUNKS; dx <= LAND_CHUNKS; dx++) {
			for (int dz = -LAND_CHUNKS; dz <= LAND_CHUNKS; dz++) {
				if (!level.hasChunk(cx + dx, cz + dz)) {
					return false;
				}
			}
		}
		return minecraft.levelRenderer.isSectionCompiledAndVisible(pos, 0L);
	}

	/** The hold is over: stop reading, so the life story's narrator isn't talked over. */
	static void hush(Minecraft minecraft) {
		if (reading != null) {
			minecraft.getSoundManager().stop(reading);
			reading = null;
		}
		toRead.clear();
		read = "";
	}

	private static void narrate() {
		// (0.7.400) Andy: "all the narrator should say here is your story is being written." So only the title is read,
		// once, and not what the hold is for. (0.7.360) the life story's narrator never reads over this one.
		if (read.isEmpty()) {
			LifeStoryScreen.hush();
			read = "gui.jaymoskingdoms.story.hold.title";
			toRead.add(read);
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (reading != null) {
			if (System.currentTimeMillis() - readFrom < READ_START_MS || minecraft.getSoundManager().isActive(reading)) {
				return;
			}
			reading = null;
		}
		while (!toRead.isEmpty()) {
			Holder<SoundEvent> voice = Voices.of(toRead.poll());
			if (voice != null) {
				reading = SimpleSoundInstance.forUI(voice.value(), 1.0F, 1.0F);
				readFrom = System.currentTimeMillis();
				minecraft.getSoundManager().play(reading);
				return;
			}
		}
	}

	static boolean holding() {
		return !holding.isEmpty() || landing;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		StoryCurtain.background(graphics, this);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!holding()) {
			this.onClose();
			return;
		}
		narrate();
		graphics.fill(0, 0, this.width, this.height, BACKGROUND);
		int cx = this.width / 2, cy = this.height / 2;
		long now = System.currentTimeMillis();
		String dots = ".".repeat((int)(now / DOT_MS % 4));
		graphics.centeredText(this.font, this.title.copy().append(dots), cx, cy - 24, Ui.GOLD);
		// (0.7.360) a hint at how something works, a new one every HINT_MS (what the hold is for is only heard);
		// (0.7.499) dealt at random off the shared deck
		long slot = (now - this.opened) / HINT_MS;
		if (slot != this.hintSlot) {
			this.hintSlot = slot;
			this.hint = dealHint();
		}
		int hint = this.hint;
		int ty = cy - 6;
		for (FormattedCharSequence line : this.font.split(Component.translatable("gui.jaymoskingdoms.story.hint." + hint),
			Math.min(HINT_W, this.width - 32))) {
			graphics.text(this.font, line, cx - this.font.width(line) / 2, ty, Ui.TEXT);
			ty += Ui.LINE;
		}
		// a bar that sweeps back and forth: something's happening, not how much is left
		int x0 = cx - BAR_W / 2, y0 = ty + 8;
		graphics.fill(x0, y0, x0 + BAR_W, y0 + BAR_H, BAR_BACK);
		double t = now % SWEEP_MS / (double)SWEEP_MS;
		double phase = t < 0.5 ? t * 2.0 : 2.0 - t * 2.0;
		int seg = BAR_W / 4, sx = x0 + (int)((BAR_W - seg) * phase);
		graphics.fill(sx, y0, sx + seg, y0 + BAR_H, Ui.GOLD);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
