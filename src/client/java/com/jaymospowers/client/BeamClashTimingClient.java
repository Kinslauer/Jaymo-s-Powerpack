package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BeamClash;
import com.jaymospowers.core.BeamClashTiming;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.17.42, Andy's NOEA picks) The beam clash timing game on screen ({@link BeamClashTiming}): in a clash a marker sweeps a
 * bar near the bottom of the screen, with a gold Perfect zone inside a blue Good zone that moves every pass. Right click
 * as the marker crosses it. The click is taken at the start of the tick (so it does nothing else), and the age sent is
 * the one the bar last drew, so what you saw is what is judged. One try a pass; the server answers with the grade, which
 * flashes over the bar. In the clash film the bar sits in the bottom bar beside the balance panel.
 */
public final class BeamClashTimingClient {
	static final int BAR_W = 120, BAR_H = 7;
	/** The result's words stay up this many ticks. */
	static final int RESULT_TICKS = 24;
	private static final int[] GRADE_COLORS = {0xFFFF5050, 0xFF7CC8FF, 0xFFFFD54A};

	/** The clash age the bar last drew (what the player saw). */
	private static float shownAge;
	/** The pass already tried (-1: none), and the grade last answered with, when. */
	private static int triedPass = -1;
	private static int grade = -1;
	private static long gradeAt = Long.MIN_VALUE / 2;

	private BeamClashTimingClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BeamClashTiming.Result.TYPE, (payload, ctx) -> {
			Minecraft minecraft = ctx.client();
			grade = payload.grade();
			gradeAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
			if (minecraft.player != null) {
				float pitch = grade == BeamClashTiming.PERFECT ? 1.8F : grade == BeamClashTiming.GOOD ? 1.3F : 0.6F;
				minecraft.player.level().playLocalSound(minecraft.player, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, pitch);
			}
		});
		ClientTickEvents.START_CLIENT_TICK.register(BeamClashTimingClient::click);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("beam_clash_timing"), BeamClashTimingClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			triedPass = -1;
			grade = -1;
		});
	}

	private static BeamClash.State clash(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player == null || player.isSpectator() ? null : player.getAttached(BeamClash.CLASH);
	}

	/** Start of the tick: in a clash, a right click is the timing try. */
	private static void click(Minecraft minecraft) {
		BeamClash.State state = clash(minecraft);
		if (state == null) {
			triedPass = -1;
			return;
		}
		if (minecraft.gui.screen() != null) {
			return;
		}
		while (minecraft.options.keyUse.consumeClick()) {
			int pass = BeamClashTiming.pass(shownAge);
			if (pass != triedPass) {
				triedPass = pass;
				ClientPlayNetworking.send(new BeamClashTiming.Press(shownAge));
			}
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (clash(minecraft) == null || BeamCinematic.running()) {
			return;
		}
		int x = graphics.guiWidth() / 2 - BAR_W / 2;
		int y = graphics.guiHeight() - 80;
		bar(graphics, delta, x, y, BAR_W);
	}

	/** In the clash film: the bar in the bottom bar, right of the balance panel. */
	static void film(GuiGraphicsExtractor graphics, DeltaTracker delta, int width, int height, int bar) {
		if (clash(Minecraft.getInstance()) == null) {
			return;
		}
		int w = Math.min(BAR_W, width / 2 - BeamClashClient.PANEL_W / 2 - 24);
		if (w < 40) {
			return;
		}
		bar(graphics, delta, width / 2 + BeamClashClient.PANEL_W / 2 + 12, height - bar + Math.max(10, (bar - BAR_H) / 2 + 4), w);
	}

	/** The bar itself, its top left at {@code x, y}, {@code w} wide, with the words above it. */
	private static void bar(GuiGraphicsExtractor graphics, DeltaTracker delta, int x, int y, int w) {
		Minecraft minecraft = Minecraft.getInstance();
		BeamClash.State state = clash(minecraft);
		if (state == null) {
			return;
		}
		Font font = minecraft.font;
		float age = state.age() + delta.getGameTimeDeltaPartialTick(false);
		shownAge = age;
		int pass = BeamClashTiming.pass(age);
		boolean tried = pass == triedPass;
		float centre = BeamClashTiming.centre(pass);
		long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		boolean result = grade >= 0 && now - gradeAt < RESULT_TICKS;
		graphics.fill(x - 1, y - 1, x + w + 1, y + BAR_H + 1, 0xC0000000);
		int goodFrom = x + Math.round(w * (centre - BeamClashTiming.GOOD_HALF)), goodTo = x + Math.round(w * (centre + BeamClashTiming.GOOD_HALF));
		int perfectFrom = x + Math.round(w * (centre - BeamClashTiming.PERFECT_HALF)), perfectTo = x + Math.round(w * (centre + BeamClashTiming.PERFECT_HALF));
		graphics.fill(x, y, x + w, y + BAR_H, 0xFF202028);
		graphics.fill(goodFrom, y, goodTo, y + BAR_H, tried ? 0xFF2A3A55 : 0xFF3A7BD5);
		graphics.fill(perfectFrom, y, perfectTo, y + BAR_H, tried ? 0xFF6A5A24 : 0xFFFFD54A);
		int mark = x + Math.round(w * BeamClashTiming.marker(age));
		graphics.fill(mark - 1, y - 2, mark + 1, y + BAR_H + 2, 0xFFFFFFFF);
		graphics.outline(x - 1, y - 1, w + 2, BAR_H + 2, result ? GRADE_COLORS[Mth.clamp(grade, 0, 2)] : 0xFFFFFFFF);
		Component words = result ? Component.translatable("hud.jaymospowers.beam_clash.timing_" + (grade == BeamClashTiming.PERFECT ? "perfect"
			: grade == BeamClashTiming.GOOD ? "good" : "miss")) : Component.translatable("hud.jaymospowers.beam_clash.timing");
		int color = result ? GRADE_COLORS[Mth.clamp(grade, 0, 2)] : 0xFFC0C0D0;
		graphics.text(font, words, x + w / 2 - font.width(words) / 2, y - 12, color);
	}
}
