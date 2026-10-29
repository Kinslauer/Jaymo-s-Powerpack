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
	 * (0.7.355) Andy: "i dont like when the narrator reads exactly what's on screen. it can keep the headline, but the white
	 * text should cycle, with a series of a minimum 40 lines hinting at how different game mechanics work. The narrator can
	 * continue saying what it does now". So under the title a hint ({@code gui.jaymoskingdoms.story.hint.<1..HINTS>}) shows
	 * for HINT_MS each, from a random one on, while the narrator still reads what the hold is for. Widest a hint may run.
	 */
	private static final int HINTS = 44, HINT_W = 320;
	private static final long HINT_MS = 7000L;

	/** What the server holds this client for (a lang key), or "" for nothing. */
	private static String holding = "";
	/**
	 * (0.7.352) The narrator reads the screen ({@link Voices}): its title once, then each line as the hold moves on. What it
	 * last began reading, the line being read, those still to read, and when the line began (ms).
	 */
	private static String read = "";
	private static @Nullable SimpleSoundInstance reading;
	private static final ArrayDeque<String> toRead = new ArrayDeque<>();
	private static long readFrom;
	/** A sound only counts as playing once the engine has picked it up. */
	private static final long READ_START_MS = 500L;

	/** The hint the screen opened on, and when (ms). */
	private final int firstHint;
	private final long opened = System.currentTimeMillis();

	private StoryHoldScreen() {
		super(Component.translatable("gui.jaymoskingdoms.story.hold.title"));
		this.firstHint = java.util.concurrent.ThreadLocalRandom.current().nextInt(HINTS);
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StoryGround.Hold.TYPE, (payload, context) -> {
			holding = payload.key();
			Minecraft minecraft = context.client();
			if (!holding.isEmpty()) {
				StoryCurtain.raise();
			}
			if (holding.isEmpty()) {
				hush(minecraft);
				if (minecraft.gui.screen() instanceof StoryHoldScreen) {
					minecraft.gui.setScreen(null);
				}
			} else if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new StoryHoldScreen());
			}
		});
		// (up again if anything took its place and went, while they're still held)
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!holding.isEmpty() && minecraft.player != null && minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new StoryHoldScreen());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			holding = "";
			hush(minecraft);
		});
	}

	/** The hold is over: stop reading, so the life story's narrator isn't talked over. */
	private static void hush(Minecraft minecraft) {
		if (reading != null) {
			minecraft.getSoundManager().stop(reading);
			reading = null;
		}
		toRead.clear();
		read = "";
	}

	private static void narrate() {
		if (!holding.equals(read)) {
			if (read.isEmpty()) {
				toRead.add("gui.jaymoskingdoms.story.hold.title");
			}
			read = holding;
			toRead.add(holding);
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
		return !holding.isEmpty();
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
		if (holding.isEmpty()) {
			this.onClose();
			return;
		}
		narrate();
		graphics.fill(0, 0, this.width, this.height, BACKGROUND);
		int cx = this.width / 2, cy = this.height / 2;
		long now = System.currentTimeMillis();
		String dots = ".".repeat((int)(now / DOT_MS % 4));
		graphics.centeredText(this.font, this.title.copy().append(dots), cx, cy - 24, Ui.GOLD);
		// (0.7.355) a hint at how something works, a new one every HINT_MS (what the hold is for is only heard)
		int hint = (this.firstHint + (int)((now - this.opened) / HINT_MS)) % HINTS + 1;
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
