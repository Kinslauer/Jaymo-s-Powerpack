package com.jaymospowers.client.finisher;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.finisher.Prompts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Your side of a prompted finisher ({@link Prompts}): the key each blow asks for is drawn under the crosshair with a
 * bar that runs out as the blow comes, and the first ability key you press while it shows is your answer
 * ({@link Prompts.Answer}; the server judges it). The ability keys do nothing else meanwhile ({@link #seizes}).
 */
public final class PromptClient {
	/** The prompt's box (gui pixels) and the bar under the key's name. */
	static final int WIDTH = 46, HEIGHT = 22, BAR_HEIGHT = 2;
	static final int BACK = 0xB0000000, KEY = 0xFFFFE080, BAR = 0xFFFF4040, RIGHT = 0xFF60E060, WRONG = 0xFFE04040;
	private static final PowerKey[] KEYS = {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};

	/** The finisher these answers belong to (its start), which blows you have answered (a bit each), and which of those rightly. */
	private static long start = Long.MIN_VALUE;
	private static int answered, right;
	private static final boolean[] wasDown = new boolean[KEYS.length];

	private PromptClient() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PromptClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("finisher_prompt"), PromptClient::draw);
	}

	/** Whether {@code player}'s ability keys are a prompted finisher's answers just now. */
	public static boolean seizes(Player player) {
		return Prompts.prompted(player) && Finishers.of(player) != null;
	}

	/** The blow whose prompt is up at tick {@code t} of {@code finish} (shown till the blow lands), or -1. */
	private static int showing(Finishers.Finish finish, int t) {
		Finishers.Kind kind = finish.type();
		for (int i = 0; i < kind.beats.length; i++) {
			if (Prompts.asks(kind, i) && t >= Prompts.opens(kind, i) && t < kind.beats[i]) {
				return i;
			}
		}
		return -1;
	}

	private static void tick(Minecraft minecraft) {
		Player player = minecraft.player;
		Finishers.Finish finish = player == null || !seizes(player) ? null : Finishers.of(player);
		if (finish != null && finish.start() != start) {
			start = finish.start();
			answered = 0;
			right = 0;
		}
		int pressed = -1;
		for (int i = 0; i < KEYS.length; i++) {
			boolean down = finish != null && minecraft.gui.screen() == null && JaymosPowersClient.keyMapping(KEYS[i]).isDown();
			if (down && !wasDown[i] && pressed < 0) {
				pressed = i;
			}
			wasDown[i] = down;
		}
		if (finish == null || pressed < 0) {
			return;
		}
		int t = (int) (player.level().getGameTime() - finish.start());
		int beat = showing(finish, t);
		if (beat < 0 || (answered & 1 << beat) != 0 || t > finish.type().beats[beat] - Prompts.LEAD) {
			return;
		}
		answered |= 1 << beat;
		if (pressed == Prompts.key(finish, beat)) {
			right |= 1 << beat;
		}
		ClientPlayNetworking.send(new Prompts.Answer(beat, pressed));
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		Finishers.Finish finish = player == null || !seizes(player) ? null : Finishers.of(player);
		if (finish == null || finish.start() != start) {
			return;
		}
		Finishers.Kind kind = finish.type();
		float t = player.level().getGameTime() - finish.start() + delta.getGameTimeDeltaPartialTick(false);
		int beat = showing(finish, (int) t);
		if (beat < 0) {
			return;
		}
		int[] at = HudZones.place(HudZones.BELOW, "jaymospowers:finisher_prompt", 20, WIDTH, HEIGHT);
		if (at == null) {
			return;
		}
		boolean done = (answered & 1 << beat) != 0;
		Component name = JaymosPowersClient.keyMapping(KEYS[Prompts.key(finish, beat)]).getTranslatedKeyMessage();
		graphics.fill(at[0], at[1], at[0] + WIDTH, at[1] + HEIGHT, BACK);
		graphics.centeredText(minecraft.font, name, at[0] + WIDTH / 2, at[1] + 6, !done ? KEY : (right & 1 << beat) != 0 ? RIGHT : WRONG);
		if (!done) {
			// the time left to answer, running out toward the blow
			int opens = Prompts.opens(kind, beat), closes = kind.beats[beat] - Prompts.LEAD;
			float left = Mth.clamp((closes - t) / Math.max(1, closes - opens), 0.0F, 1.0F);
			graphics.fill(at[0] + 2, at[1] + HEIGHT - BAR_HEIGHT - 2, at[0] + 2 + Math.round((WIDTH - 4) * left), at[1] + HEIGHT - 2, BAR);
		}
	}
}
